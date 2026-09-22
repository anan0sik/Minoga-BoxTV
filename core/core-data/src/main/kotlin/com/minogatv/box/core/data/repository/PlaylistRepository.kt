package com.minogatv.box.core.data.repository

import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.dao.UserProfileDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.PlaylistEntity
import com.minogatv.box.core.database.entity.UserProfileEntity
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.enums.PlaylistType
import com.minogatv.box.core.data.mapper.toDomain
import com.minogatv.box.core.data.mapper.toEntity
import com.minogatv.box.core.data.sync.SyncScheduler
import com.minogatv.box.core.network.ProxyOkHttpClientFactory
import com.minogatv.box.core.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request
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
