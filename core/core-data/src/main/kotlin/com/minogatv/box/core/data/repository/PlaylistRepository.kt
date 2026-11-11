package com.minogatv.box.core.data.repository

import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.dao.UserProfileDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.PlaylistEntity
import com.minogatv.box.core.database.entity.UserProfileEntity
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.WebConfig
import com.minogatv.box.core.model.WebPairingPollResult
import com.minogatv.box.core.model.enums.PlaylistType
import com.minogatv.box.core.data.mapper.toDomain
import com.minogatv.box.core.data.mapper.toEntity
import com.minogatv.box.core.data.sync.SyncScheduler
import com.minogatv.box.core.network.ProxyOkHttpClientFactory
import com.minogatv.box.core.parser.M3uParser
import com.minogatv.box.core.parser.WebConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import java.util.concurrent.TimeUnit

/**
 * Repository interface for playlist (provider / source) CRUD and sync scheduling.
 */
interface PlaylistRepository {
    fun observePlaylists(profileId: Long): Flow<List<Playlist>>
    suspend fun getPlaylist(playlistId: Long): Playlist?
    suspend fun addPlaylist(playlist: Playlist): Long
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun refreshNow(playlistId: Long): Result<Int>
    suspend fun syncPlaylist(playlistId: Long): Result<Int>
    suspend fun validatePlaylistAvailability(playlist: Playlist): Result<Unit>
    suspend fun fetchWebConfig(pageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL): Result<WebConfig>
    suspend fun applyWebConfig(pageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL, profileId: Long = 1L): Result<WebConfig>
    suspend fun applySubmittedWebConfig(webConfig: WebConfig, profileId: Long = 1L): Result<WebConfig>
    suspend fun registerWebPairingCode(code: String, pageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL): Result<String>
    suspend fun pollWebPairingCode(code: String, pageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL): Result<WebPairingPollResult>
}

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val userProfileDao: UserProfileDao,
    private val syncScheduler: SyncScheduler,
    private val clientFactory: ProxyOkHttpClientFactory,
) : PlaylistRepository {

    private fun getEffectiveEpg(fallback: String?): String {
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val customEpg = prefs.getString("custom_epg_url", null)?.trim()?.takeIf { it.isNotBlank() }
        val raw = fallback?.takeIf { it.isNotBlank() } ?: customEpg ?: Playlist.DEFAULT_EPG_URL
        return Playlist.normalizeUrl(raw)
    }

    override fun observePlaylists(profileId: Long): Flow<List<Playlist>> =
        playlistDao.observeByProfile(profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun getPlaylist(playlistId: Long): Playlist? =
        playlistDao.getById(playlistId)?.toDomain()

    override suspend fun validatePlaylistAvailability(playlist: Playlist): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = resolveDownloadUrl(playlist)
            if (downloadUrl.isBlank() || (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://"))) {
                return@withContext Result.failure(IllegalArgumentException("Некорректный адрес ссылки: адрес должен начинаться с http:// или https://"))
            }

            val client = clientFactory.create(playlist).newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            // Try HEAD first, fallback to Range GET for streaming/M3U servers that don't support HEAD
            val headRequest = Request.Builder()
                .url(downloadUrl)
                .head()
                .build()

            var response = try {
                client.newCall(headRequest).execute()
            } catch (_: Exception) {
                null
            }

            if (response == null || response.code == 405 || response.code == 501) {
                val getRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("Range", "bytes=0-1024")
                    .build()
                response = client.newCall(getRequest).execute()
            }

            response.use { res ->
                if (res.isSuccessful || res.code == 206) {
                    Result.success(Unit)
                } else {
                    val code = res.code
                    val errorMsg = when (code) {
                        401, 403 -> "Ошибка доступа (HTTP $code): проверьте логин и пароль"
                        404 -> "Плейлист не найден по указанному адресу (HTTP 404)"
                        in 500..599 -> "Сервер плейлиста временно недоступен (HTTP $code)"
                        else -> "Сервер вернул ошибку: HTTP $code ${res.message}"
                    }
                    Result.failure(IOException(errorMsg))
                }
            }
        } catch (e: Exception) {
            val msg = when {
                e is java.net.UnknownHostException -> "Не удалось найти сервер: проверьте адрес ссылки или подключение к интернету"
                e is java.net.SocketTimeoutException -> "Время ожидания ответа сервера истекло (таймаут)"
                e is java.net.ConnectException -> "Не удалось подключиться к серверу плейлиста"
                else -> e.localizedMessage ?: "Ошибка подключения: ${e.message}"
            }
            Result.failure(IOException(msg, e))
        }
    }

    override suspend fun addPlaylist(playlist: Playlist): Long {
        // Guarantee default profile exists so Foreign Key never fails
        userProfileDao.insert(
            UserProfileEntity(
                id = playlist.profileId.coerceAtLeast(1L),
                name = "Main",
                isActive = true,
            ),
        )

        val rawEpg = playlist.epgUrl?.trim()?.takeIf { it.isNotBlank() }
        val playlistToSave = playlist.copy(epgUrl = rawEpg)

        val id = playlistDao.insert(playlistToSave.toEntity())

        // Schedule periodic background refreshes
        syncScheduler.scheduleM3uSync(id)
        if (rawEpg != null) {
            syncScheduler.scheduleEpgSync(id, rawEpg)
        }

        // Perform immediate sync directly in coroutine (syncPlaylist will also resolve EPG from M3U header and trigger EPG sync)
        val syncResult = syncPlaylist(id)
        if (syncResult.isFailure) {
            val ex = syncResult.exceptionOrNull()
            throw ex ?: IOException("Не удалось загрузить каналы плейлиста")
        }

        return id
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        val rawEpg = playlist.epgUrl?.trim()?.takeIf { it.isNotBlank() }
        val playlistToSave = playlist.copy(epgUrl = rawEpg)
        
        playlistDao.update(playlistToSave.toEntity())
        syncScheduler.scheduleM3uSync(
            playlistId = playlist.id,
            policy = androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
        )
        if (rawEpg != null) {
            syncScheduler.scheduleEpgSync(
                playlistId = playlist.id,
                epgUrl = rawEpg,
                policy = androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            )
        }
        // Refresh channels with updated configuration immediately (syncPlaylist will also trigger EPG sync)
        val syncResult = syncPlaylist(playlist.id)
        if (syncResult.isFailure) {
            val ex = syncResult.exceptionOrNull()
            throw ex ?: IOException("Не удалось обновить каналы плейлиста")
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        syncScheduler.cancelM3uSync(playlistId)
        syncScheduler.cancelEpgSync(playlistId)
        playlistDao.deleteById(playlistId)
        // Room cascade deletes all channels for this playlist automatically
    }

    override suspend fun refreshNow(playlistId: Long): Result<Int> {
        val result = syncPlaylist(playlistId)
        val entity = playlistDao.getById(playlistId)
        val epg = getEffectiveEpg(entity?.epgUrl)
        syncScheduler.triggerManualEpgSync(playlistId, epg)
        return result
    }

    /**
     * Download and parse the playlist directly, inserting all parsed channels into Room.
     * Safe to call from WorkManager or directly from UI ViewModels.
     */
    override suspend fun syncPlaylist(playlistId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val playlistEntity = playlistDao.getById(playlistId)
                ?: return@withContext Result.failure(IllegalArgumentException("Плейлист с ID $playlistId не найден"))

            val playlist = playlistEntity.toDomain()

            // Resolve target download URL (handles Xtream Codes M3U endpoint)
            val downloadUrl = resolveDownloadUrl(playlist)

            val client = clientFactory.create(playlist)
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Ошибка загрузки плейлиста: HTTP ${response.code} ${response.message}"),
                )
            }

            val body = response.body
                ?: return@withContext Result.failure(IOException("Пустой ответ от сервера плейлиста"))

            val parseResult = body.byteStream().use { stream ->
                M3uParser.parse(stream)
            }

            val adultKeywords = M3uParser.ADULT_KEYWORDS
            val channelEntities = parseResult.channels.mapIndexed { index, ch ->
                val groupLower = ch.groupTitle.lowercase()
                val isAdult = adultKeywords.any { groupLower.contains(it) }
                ChannelEntity(
                    playlistId = playlistId,
                    epgChannelId = ch.epgChannelId,
                    name = ch.name,
                    logoUrl = ch.logoUrl.takeIf { it.isNotEmpty() },
                    streamUrl = ch.streamUrl,
                    groupTitle = ch.groupTitle,
                    catchupType = ch.catchupType,
                    catchupDays = ch.catchupDays,
                    catchupSource = ch.catchupSource.takeIf { it.isNotEmpty() },
                    isHidden = false,
                    isFavorite = false,
                    sortOrder = index,
                    isAdult = isAdult,
                )
            }

            // Atomically replace channels in Room
            channelDao.replaceAll(playlistId, channelEntities)

            // Persist manually configured EPG or discovered from M3U header
            val rawEpg = playlistEntity.epgUrl?.takeIf { it.isNotBlank() }
                ?: parseResult.epgUrl?.takeIf { it.isNotBlank() }
            val effectiveEpgUrl = getEffectiveEpg(rawEpg)

            playlistDao.updateEpgUrl(playlistId, effectiveEpgUrl)
            syncScheduler.scheduleEpgSync(playlistId, effectiveEpgUrl)
            syncScheduler.triggerManualEpgSync(playlistId, effectiveEpgUrl)

            // Update refresh timestamp
            playlistDao.updateLastRefreshed(playlistId, System.currentTimeMillis())

            Result.success(channelEntities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchWebConfig(pageUrl: String): Result<WebConfig> = withContext(Dispatchers.IO) {
        try {
            val rawBase = pageUrl.trim().ifBlank { Playlist.DEFAULT_WEB_CONFIG_URL }
            val normalizedBase = Playlist.normalizeUrl(rawBase).trimEnd('/')

            // Build candidate URLs to probe in order:
            // 1. Primary scheme (/api/config, /config.json, and root HTML page)
            // 2. Alternate scheme (HTTPS <-> HTTP) or fallback port :8080
            val bases = mutableListOf(normalizedBase)
            if (normalizedBase.startsWith("http://", ignoreCase = true)) {
                bases.add("https://" + normalizedBase.removePrefix("http://"))
            } else if (normalizedBase.startsWith("https://", ignoreCase = true)) {
                bases.add("http://" + normalizedBase.removePrefix("https://"))
            }
            // If no explicit port was given, also add :8080 as a fallback candidate
            val hostPart = normalizedBase.substringAfter("://").substringBefore("/")
            if (!hostPart.contains(":")) {
                bases.add("http://$hostPart:8080")
            }

            val candidates = mutableListOf<String>()
            for (b in bases.distinct()) {
                if (b.endsWith(".json", ignoreCase = true) || b.endsWith(".html", ignoreCase = true) || b.endsWith(".m3u", ignoreCase = true) || b.endsWith(".m3u8", ignoreCase = true)) {
                    candidates.add(b)
                } else {
                    candidates.add("$b/api/config")
                    candidates.add("$b/config.json")
                    candidates.add(b)
                }
            }

            val dummyPlaylist = Playlist(profileId = 1L, name = "WebConfig", url = normalizedBase)
            val client = clientFactory.create(dummyPlaylist).newBuilder()
                .connectTimeout(7, TimeUnit.SECONDS)
                .readTimeout(7, TimeUnit.SECONDS)
                .build()

            var lastError: Exception? = null
            for (candidateUrl in candidates.distinct()) {
                try {
                    val request = Request.Builder()
                        .url(candidateUrl)
                        .header("Accept", "application/json, text/html, */*")
                        .header("Cache-Control", "no-cache")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string().orEmpty()
                            val parsed = WebConfigParser.parse(bodyStr, candidateUrl)
                            if (parsed != null && parsed.playlistUrl.isNotBlank()) {
                                return@withContext Result.success(parsed.copy(sourcePageUrl = normalizedBase))
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            val errorMsg = when (lastError) {
                is java.net.UnknownHostException -> "Сервер $normalizedBase не найден. Проверьте подключение к интернету или DNS."
                is java.net.SocketTimeoutException -> "Превышено время ожидания ответа от $normalizedBase"
                is java.net.ConnectException -> "Не удалось подключиться к веб-странице $normalizedBase"
                else -> "На странице $normalizedBase не найдена ссылка на плейлист"
            }
            Result.failure(IOException(errorMsg, lastError))
        } catch (e: Exception) {
            Result.failure(IOException("Ошибка чтения веб-страницы: ${e.localizedMessage ?: e.message}", e))
        }
    }

    private fun buildCandidateServerBases(pageUrl: String): List<String> {
        val rawBase = pageUrl.trim().ifBlank { Playlist.DEFAULT_WEB_CONFIG_URL }
        val normalizedBase = Playlist.normalizeUrl(rawBase).trimEnd('/')

        val bases = mutableListOf<String>()
        if (normalizedBase.startsWith("http://", ignoreCase = true)) {
            // Prefer HTTPS: the server redirects HTTP -> HTTPS (301), which drops POST bodies
            bases.add("https://" + normalizedBase.removePrefix("http://"))
            bases.add(normalizedBase)
        } else if (normalizedBase.startsWith("https://", ignoreCase = true)) {
            bases.add(normalizedBase)
            bases.add("http://" + normalizedBase.removePrefix("https://"))
        } else {
            bases.add(normalizedBase)
        }
        val hostPart = normalizedBase.substringAfter("://").substringBefore("/")
        if (!hostPart.contains(":")) {
            bases.add("http://$hostPart:8080")
        }
        // Fallback for local emulator testing with IptvMockServer
        bases.add("http://10.0.2.2:8080")
        return bases.distinct()
    }

    override suspend fun registerWebPairingCode(code: String, pageUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val bases = buildCandidateServerBases(pageUrl)
        val dummyPlaylist = Playlist(profileId = 1L, name = "WebConfig", url = bases.first())
        val client = clientFactory.create(dummyPlaylist).newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val jsonBody = """{"code":"$code"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
        var lastError: Exception? = null

        for (base in bases) {
            try {
                val request = Request.Builder()
                    .url("$base/api/code/register")
                    .post(jsonBody)
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext Result.success(base)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IOException("Не удалось зарегистрировать код на сервере $pageUrl"))
    }

    override suspend fun pollWebPairingCode(code: String, pageUrl: String): Result<WebPairingPollResult> = withContext(Dispatchers.IO) {
        val bases = buildCandidateServerBases(pageUrl)
        val dummyPlaylist = Playlist(profileId = 1L, name = "WebConfig", url = bases.first())
        val client = clientFactory.create(dummyPlaylist).newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()

        var lastError: Exception? = null
        for (base in bases) {
            try {
                val request = Request.Builder()
                    .url("$base/api/code/poll?code=$code")
                    .get()
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string().orEmpty()
                        val parsed = WebConfigParser.parsePollResponse(bodyStr, code, base)
                        if (parsed != null) {
                            return@withContext Result.success(parsed)
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IOException("Сервер привязки недоступен"))
    }

    override suspend fun applyWebConfig(pageUrl: String, profileId: Long): Result<WebConfig> = withContext(Dispatchers.IO) {
        val configResult = fetchWebConfig(pageUrl)
        if (configResult.isFailure) {
            return@withContext configResult
        }
        applySubmittedWebConfig(configResult.getOrThrow(), profileId)
    }

    override suspend fun applySubmittedWebConfig(webConfig: WebConfig, profileId: Long): Result<WebConfig> = withContext(Dispatchers.IO) {
        try {
            val rawPlaylistUrl = webConfig.playlistUrl.trim()
            val cleanPlaylistUrl = if (rawPlaylistUrl.isNotBlank()) Playlist.normalizeUrl(rawPlaylistUrl) else ""
            val rawEpgTrimmed = webConfig.epgUrl?.trim().orEmpty()
            val isDefaultEpg = rawEpgTrimmed.isEmpty() ||
                rawEpgTrimmed.equals(Playlist.DEFAULT_EPG_URL, ignoreCase = true) ||
                rawEpgTrimmed.equals("http://epg.one/epg2.xml.gz", ignoreCase = true) ||
                rawEpgTrimmed.equals("https://epg.one/epg2.xml.gz", ignoreCase = true)
            val cleanEpgUrl = if (isDefaultEpg) null else Playlist.normalizeUrl(rawEpgTrimmed)

            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("web_config_url", webConfig.sourcePageUrl)
            if (cleanEpgUrl != null) {
                editor.putString("custom_epg_url", cleanEpgUrl)
            } else {
                editor.remove("custom_epg_url")
            }
            if (!webConfig.vodUrl.isNullOrBlank() || !webConfig.vodKey.isNullOrBlank()) {
                var cleanVodU = webConfig.vodUrl?.replace("\\/", "/")?.trim().orEmpty()
                var cleanVodK = webConfig.vodKey?.replace("\\/", "/")?.trim().orEmpty()
                while (cleanVodU.matches(Regex("^[a-z]{3,7}:/+[a-z]{3,7}:/+.*", RegexOption.IGNORE_CASE))) {
                    cleanVodU = cleanVodU.replaceFirst(Regex("^[a-z]{3,7}:/+", RegexOption.IGNORE_CASE), "")
                }
                cleanVodU = when {
                    cleanVodU.matches(Regex("^h+t+p+s:/+.*", RegexOption.IGNORE_CASE)) ->
                        cleanVodU.replaceFirst(Regex("^h+t+p+s:/+", RegexOption.IGNORE_CASE), "https://")
                    cleanVodU.matches(Regex("^h+t+p+:/+.*", RegexOption.IGNORE_CASE)) ->
                        cleanVodU.replaceFirst(Regex("^h+t+p+:/+", RegexOption.IGNORE_CASE), "http://")
                    else -> cleanVodU
                }
                val subMatch = Regex("^https?://([0-9a-f]{8,20})\\.([a-z0-9.-]+\\.[a-z]{2,})(?:/.*)?$", RegexOption.IGNORE_CASE).find(cleanVodU)
                if (subMatch != null && cleanVodK.isEmpty()) {
                    cleanVodK = subMatch.groupValues.getOrNull(1).orEmpty().trim()
                }
                editor.putString("vod_portal_url", cleanVodU)
                editor.putString("vod_portal_login", webConfig.vodLogin?.trim().orEmpty())
                editor.putString("vod_portal_password", webConfig.vodPassword?.trim().orEmpty())
                editor.putString("vod_portal_key", cleanVodK)
            }
            editor.apply()

            if (cleanPlaylistUrl.isBlank()) {
                return@withContext Result.success(webConfig)
            }

            val existingPlaylists = playlistDao.getAll()
            val matchingExisting = existingPlaylists.firstOrNull {
                it.url.equals(cleanPlaylistUrl, ignoreCase = true) ||
                    it.name.equals(webConfig.name, ignoreCase = true)
            }

            val targetPlaylist = if (matchingExisting != null) {
                matchingExisting.toDomain().copy(
                    name = webConfig.name.ifBlank { matchingExisting.name },
                    url = cleanPlaylistUrl,
                    epgUrl = cleanEpgUrl,
                )
            } else {
                Playlist(
                    profileId = profileId,
                    name = webConfig.name.ifBlank { "Minoga TV" },
                    url = cleanPlaylistUrl,
                    type = PlaylistType.M3U,
                    epgUrl = cleanEpgUrl,
                )
            }

            // Validate playlist availability first
            val validation = validatePlaylistAvailability(targetPlaylist)
            if (validation.isFailure) {
                return@withContext Result.failure(
                    validation.exceptionOrNull() ?: IOException("Ссылка на плейлист с веб-страницы недоступна: $cleanPlaylistUrl")
                )
            }

            if (matchingExisting != null) {
                updatePlaylist(targetPlaylist)
            } else {
                addPlaylist(targetPlaylist)
            }

            Result.success(
                webConfig.copy(
                    playlistUrl = cleanPlaylistUrl,
                    epgUrl = cleanEpgUrl,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveDownloadUrl(playlist: Playlist): String {
        return when (playlist.type) {
            PlaylistType.XTREAM -> {
                val base = playlist.url.trimEnd('/')
                val user = playlist.xtreamUsername.orEmpty()
                val pass = playlist.xtreamPassword.orEmpty()
                if (user.isNotEmpty() && pass.isNotEmpty()) {
                    "$base/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
                } else {
                    playlist.url
                }
            }
            else -> playlist.url
        }
    }
}
