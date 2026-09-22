package com.minogatv.box.core.data.sync

import android.content.Context
import com.minogatv.box.core.data.mapper.toDomain
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.EpgDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.EpgProgramEntity
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.network.ProxyOkHttpClientFactory
import com.minogatv.box.core.parser.XmltvParser
import com.minogatv.box.core.parser.XmltvProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * # EpgSyncManager
 *
 * Centralized, singleton manager for EPG downloading, parsing, and database synchronization.
 *
 * Priority rules:
 * 1. If user set an EPG link manually (in settings or playlist configuration),
 *    EPG and picons/logos are strictly fetched and assigned from this manual source.
 *    External fallback sources and third-party picon resolvers are not applied.
 * 2. If no manual link is set, falls back to prioritized internet sources:
 *    http://epg.one/epg2.xml.gz -> cdn.epg.one -> it999 github mirror -> etc.
 */
@Singleton
class EpgSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val clientFactory: ProxyOkHttpClientFactory,
    private val piconResolver: com.minogatv.box.core.data.picon.PiconResolver,
) {
    private val _syncProgress = MutableStateFlow<SyncScheduler.EpgSyncInfo?>(null)
    val syncProgress: StateFlow<SyncScheduler.EpgSyncInfo?> = _syncProgress.asStateFlow()

    private val isSyncing = AtomicBoolean(false)

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentSyncJob: Job? = null

    /**
     * Clear HTTP conditional GET cache (ETag / Last-Modified) for a specific EPG URL.
     */
    fun clearHttpCacheForUrl(url: String) {
        val norm = Playlist.normalizeUrl(url)
        httpCachePrefs.edit()
            .remove("etag_$norm")
            .remove("last_mod_$norm")
            .apply()
        val cachedFeedFile = File(epgCacheDir, "feed_${norm.hashCode()}.xml.gz")
        if (cachedFeedFile.exists()) {
            cachedFeedFile.delete()
        }
    }

    /**
     * Start EPG sync asynchronously in the given [scope] (or default [syncScope]) on [Dispatchers.IO].
     */
    fun triggerSync(
        scope: CoroutineScope? = null,
        epgUrl: String? = null,
        playlistId: Long = 1L,
        forceRefresh: Boolean = false,
    ): Job {
        val targetScope = scope ?: syncScope
        if (forceRefresh) {
            currentSyncJob?.cancel()
            isSyncing.set(false)
        }
        val job = targetScope.launch(Dispatchers.IO) {
            syncEpg(epgUrl, playlistId, forceRefresh)
        }
        currentSyncJob = job
        return job
    }

    /**
     * Download and parse EPG directly.
     *
     * @param epgUrl Override EPG URL. If null, resolves from playlist or default.
     * @param playlistId Playlist ID to resolve proxy and credentials.
     * @param forceRefresh If false and existing local EPG is valid for >24h, skips download.
     * @param onProgress Callback for custom progress listeners (e.g. WorkManager setProgress).
     * @return [Result.success] with the count of imported programmes, or [Result.failure].
     */
    suspend fun syncEpg(
        epgUrl: String? = null,
        playlistId: Long = 1L,
        forceRefresh: Boolean = false,
        onProgress: (suspend (Int, String) -> Unit)? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!isSyncing.compareAndSet(false, true)) {
            // Already running
            return@withContext Result.success(0)
        }

        val programCount = AtomicInteger(0)
        var lastBatchReport = 0L

        suspend fun updateProgress(pct: Int, status: String) {
            _syncProgress.value = SyncScheduler.EpgSyncInfo(
                progress = (pct / 100f).coerceIn(0.01f, 1f),
                status = status,
            )
            onProgress?.invoke(pct, status)
        }

        val now = System.currentTimeMillis()
        val latestProgramEnd = epgDao.getLatestProgramEndMs()
        val validCount = epgDao.getValidProgramsCount(now)

        // If not forcing refresh, and we have enough future programs (valid for > 24 hours),
        // keep local EPG and avoid redundant heavy download over network.
        if (!forceRefresh && validCount > 300 && latestProgramEnd != null && (latestProgramEnd - now) > 24 * 3600_000L) {
            updateProgress(100, "Телепрограмма актуальна ($validCount передач)")
            delay(600)
            _syncProgress.value = null
            isSyncing.set(false)
            piconResolver.cacheAllRemoteLogos()
            return@withContext Result.success(validCount)
        }

        val tempFile = File(context.cacheDir, "epg_feed_${System.currentTimeMillis()}.tmp")

        try {
            val playlistEntity = if (playlistId > 0) playlistDao.getById(playlistId) else null
            val playlist = playlistEntity?.toDomain() ?: Playlist(id = 0L, profileId = 1L, name = "Default", url = "")

            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            val customEpgPref = prefs.getString("custom_epg_url", null)?.trim()?.takeIf { it.isNotBlank() }

            val rawTargetUrl = epgUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: playlistEntity?.epgUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: customEpgPref
                ?: Playlist.DEFAULT_EPG_URL

            val targetUrl = Playlist.normalizeUrl(rawTargetUrl)

            val isManualLink = isManualUrl(
                targetUrl = targetUrl,
                customEpgPref = customEpgPref,
                explicitEpgParam = epgUrl,
                playlistEntityEpg = playlistEntity?.epgUrl,
            )

            val baseClient = clientFactory.create(playlist)
            val client = baseClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            var successfulUrl: String? = null

            if (isManualLink) {
                updateProgress(5, "Подключение к заданному источнику EPG…")
                var dl = downloadFeed(client, targetUrl, tempFile, forceRefresh = forceRefresh) { pct, st -> updateProgress(pct, st) }
                if (dl.isFailure) {
                    delay(2000)
                    dl = downloadFeed(client, targetUrl, tempFile, forceRefresh = forceRefresh) { pct, st -> updateProgress(pct, st) }
                }
                if (dl.isFailure) {
                    val err = dl.exceptionOrNull()
                    val msg = "Не удалось загрузить EPG из заданного источника: ${err?.localizedMessage ?: err?.message}"
                    updateProgress(0, msg)
                    delay(2500)
                    _syncProgress.value = null
                    return@withContext Result.failure(err ?: IOException(msg))
                }
                successfulUrl = dl.getOrNull()
            } else {
                val sourcesToTry = listOf(
                    targetUrl,
                    Playlist.DEFAULT_EPG_URL,
                    Playlist.DEFAULT_PUBLIC_EPG_URL,
                ).map { Playlist.normalizeUrl(it) }.distinctBy { it.lowercase(Locale.ROOT) }

                var lastException: Throwable? = null
                for (src in sourcesToTry) {
                    val hostLabel = try {
                        src.substringAfter("://").substringBefore("/")
                    } catch (_: Exception) {
                        src
                    }
                    updateProgress(5, "Подключение к EPG ($hostLabel)…")
                    val dl = downloadFeed(client, src, tempFile, forceRefresh = forceRefresh) { pct, st -> updateProgress(pct, st) }
                    if (dl.isSuccess) {
                        successfulUrl = dl.getOrNull()
                        break
                    } else {
                        lastException = dl.exceptionOrNull()
                    }
                }

                if (successfulUrl == null) {
                    val msg = "Не удалось загрузить EPG ни из одного источника: ${lastException?.localizedMessage ?: lastException?.message}"
                    updateProgress(0, msg)
                    delay(2500)
                    _syncProgress.value = null
                    return@withContext Result.failure(lastException ?: IOException(msg))
                }
            }

            if (successfulUrl == "NOT_MODIFIED") {
                val validCountNow = epgDao.getValidProgramsCount(now)
                updateProgress(100, "Телепрограмма актуальна ($validCountNow передач)")
                _lastSyncTime.value = System.currentTimeMillis()
                delay(1000)
                _syncProgress.value = null
                piconResolver.cacheAllRemoteLogos()
                return@withContext Result.success(validCountNow)
            }

            updateProgress(42, "Подготовка к парсингу EPG…")

            // Load user channels to find target EPG IDs and names
            val allChannels = channelDao.getAll()
            val targetIds = allChannels.mapNotNull { it.epgChannelId.trim().takeIf { id -> id.isNotEmpty() } }.toSet()
            val targetNames = allChannels.map { it.name.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()

            // Delete stale programmes that ended before now - 24 hours to keep the database compact
            val staleCutoff = now - 24 * 3600_000L
            runCatching { epgDao.deleteBeforeTime(staleCutoff) }

            // Stream each batch directly to Room — never accumulate in RAM!
            // This guarantees O(1) memory consumption (< 2 MB) even for 400,000+ programmes.
            suspend fun collectBatch(batch: List<XmltvProgram>) {
                if (batch.isEmpty()) return
                val entities = batch.map { prog ->
                    EpgProgramEntity(
                        channelEpgId = prog.channelEpgId,
                        title        = prog.title,
                        description  = prog.description,
                        startMs      = prog.startMs,
                        endMs        = prog.endMs,
                        category     = prog.category,
                        iconUrl      = prog.iconUrl,
                        isNew        = prog.isNew,
                        rating       = prog.rating,
                    )
                }
                epgDao.upsertAll(entities)
                yield()

                val total = programCount.addAndGet(batch.size)
                val nowTime = System.currentTimeMillis()
                if (nowTime - lastBatchReport > 600) {
                    lastBatchReport = nowTime
                    val progress = (42 + (total.toFloat() / 25000f) * 50f).coerceIn(42f, 95f).toInt()
                    updateProgress(progress, "Импорт передач… ($total)")
                }
            }

            val parser = XmltvParser(
                batchSize = XmltvParser.DEFAULT_BATCH_SIZE,
                targetChannelIds = targetIds,
                targetChannelNames = targetNames,
                importAllProgrammes = isManualLink,
                onBatch = { batch -> collectBatch(batch) },
            )

            val parseResult = FileInputStream(tempFile).use { stream ->
                parser.parse(stream)
            }

            updateProgress(96, "Привязка каналов к телепрограмме…")

            // Auto-link any channels that lacked explicit EPG ID or had minor naming differences
            if (allChannels.isNotEmpty()) {
                val updates = mutableListOf<ChannelDao.ChannelEpgLogoUpdate>()
                for (ch in allChannels) {
                    val norm = ch.name.trim().lowercase(Locale.ROOT)
                    val simplified = XmltvParser.normalizeChannelName(ch.name)
                    val matchedEpgId = parseResult.nameToEpgId[norm]
                        ?: parseResult.nameToEpgId[simplified]
                        ?: parseResult.nameToEpgId.entries.firstOrNull { (k, _) ->
                            k == simplified || k.contains(simplified) || (simplified.length >= 4 && simplified.contains(k))
                        }?.value

                    val newEpgId = if (matchedEpgId != null && ch.epgChannelId != matchedEpgId) matchedEpgId else null

                    // Picon / Logo assignment
                    val xmltvLogo = (matchedEpgId?.let { parseResult.channelLogos[it] })
                        ?: parseResult.nameToLogo[norm]
                        ?: parseResult.nameToLogo[simplified]

                    val newLogo = if (isManualLink) {
                        // User set link manually -> picons must be taken strictly from this source
                        if (!xmltvLogo.isNullOrBlank()) xmltvLogo else null
                    } else {
                        // Auto mode: fallback to resolver if missing
                        if (ch.logoUrl.isNullOrBlank()) {
                            val foundLogo = xmltvLogo ?: piconResolver.resolveLogo(ch.name)
                            if (!foundLogo.isNullOrBlank()) foundLogo else null
                        } else null
                    }

                    if (newEpgId != null || newLogo != null) {
                        updates.add(
                            ChannelDao.ChannelEpgLogoUpdate(
                                id = ch.id,
                                epgChannelId = newEpgId,
                                logoUrl = newLogo,
                            )
                        )
                    }
                }
                if (updates.isNotEmpty()) {
                    channelDao.updateEpgAndLogos(updates)
                }
            }

            // Prune stale programmes older than 7 days (keep 1 week archive)
            val cutoff = System.currentTimeMillis() - 7 * 24 * 3600_000L
            epgDao.deleteBeforeTime(cutoff)

            if (!isManualLink) {
                // Only resolve missing logos in background if NO manual link was set
                piconResolver.resolveMissingLogos()
            }

            // Cache remote logos locally to disk so they are not re-downloaded
            piconResolver.cacheAllRemoteLogos()

            val total = programCount.get()
            updateProgress(100, "Телепрограмма обновлена ($total передач)")
            _lastSyncTime.value = System.currentTimeMillis()
            delay(1200)
            _syncProgress.value = null

            return@withContext Result.success(total)
        } catch (e: Exception) {
            updateProgress(0, "Ошибка EPG: ${e.localizedMessage ?: e.message}")
            delay(2500)
            _syncProgress.value = null
            return@withContext Result.failure(e)
        } finally {
            isSyncing.set(false)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private val epgCacheDir: File by lazy {
        File(context.filesDir, "epg_cache").apply { if (!exists()) mkdirs() }
    }

    private val httpCachePrefs by lazy {
        context.getSharedPreferences("epg_http_cache", Context.MODE_PRIVATE)
    }

    /**
     * Downloads an EPG feed from [url] into [tempFile], handling cleartext HTTP,
     * conditional GET (ETag/If-Modified-Since), redirects across protocols/domains,
     * and updating progress.
     */
    private suspend fun downloadFeed(
        client: OkHttpClient,
        url: String,
        tempFile: File,
        forceRefresh: Boolean = false,
        onProgress: suspend (Int, String) -> Unit,
    ): Result<String> {
        var currentUrl = Playlist.normalizeUrl(url)
        var redirectsCount = 0
        val maxRedirects = 5
        val cachedFeedFile = File(epgCacheDir, "feed_${currentUrl.hashCode()}.xml.gz")

        while (redirectsCount < maxRedirects) {
            val reqBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "MinogaTV/1.0 (Android TV)")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept", "text/xml, application/xml, application/octet-stream, */*")
                .header("Connection", "keep-alive")

            if (!forceRefresh) {
                val cachedEtag = httpCachePrefs.getString("etag_${currentUrl}", null)
                val cachedLastMod = httpCachePrefs.getString("last_mod_${currentUrl}", null)

                if (!cachedEtag.isNullOrBlank()) {
                    reqBuilder.header("If-None-Match", cachedEtag)
                }
                if (!cachedLastMod.isNullOrBlank()) {
                    reqBuilder.header("If-Modified-Since", cachedLastMod)
                }
            }

            val request = reqBuilder.build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                return Result.failure(IOException("Ошибка подключения к $currentUrl: ${e.localizedMessage ?: e.message}", e))
            }

            // Explicit redirect handling (e.g. 301, 302, 303, 307, 308)
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    return Result.failure(IOException("Перенаправление 30x без заголовка Location от $currentUrl"))
                }
                val resolvedUrl = response.request.url.resolve(location)?.toString() ?: location
                currentUrl = Playlist.normalizeUrl(resolvedUrl)
                redirectsCount++
                continue
            }

            // HTTP 304 Not Modified
            if (response.code == 304) {
                response.close()
                val now = System.currentTimeMillis()
                val validCount = epgDao.getValidProgramsCount(now)
                if (validCount > 0) {
                    return Result.success("NOT_MODIFIED")
                }
                if (cachedFeedFile.exists() && cachedFeedFile.length() > 0) {
                    cachedFeedFile.copyTo(tempFile, overwrite = true)
                    return Result.success(currentUrl)
                }
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return Result.failure(IOException("HTTP ошибка сервера EPG: $code для $currentUrl"))
            }

            val body = response.body ?: run {
                response.close()
                return Result.failure(IOException("Пустой ответ от сервера EPG ($currentUrl)"))
            }

            val contentLength = body.contentLength()
            onProgress(8, "Загрузка файла EPG…")

            try {
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastReport = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val nowTime = System.currentTimeMillis()
                            if (nowTime - lastReport > 500) {
                                lastReport = nowTime
                                val downloadPct = if (contentLength > 0) {
                                    (8 + (totalRead.toFloat() / contentLength * 32f)).toInt().coerceIn(8, 40)
                                } else {
                                    (8 + (totalRead / (1024 * 1024))).toInt().coerceIn(8, 38)
                                }
                                val mb = totalRead / (1024 * 1024)
                                onProgress(downloadPct, "Загрузка EPG… ($mb МБ)")
                            }
                        }

                        if (totalRead <= 0L) {
                            return Result.failure(IOException("Файл EPG пуст (0 байт)"))
                        }
                    }
                }

                // Cache feed locally on disk for fast offline / 304 fallback
                try {
                    tempFile.copyTo(cachedFeedFile, overwrite = true)
                } catch (_: Exception) {}

                // Save ETag & Last-Modified
                val etag = response.header("ETag")
                val lastMod = response.header("Last-Modified")
                httpCachePrefs.edit().apply {
                    if (etag != null) putString("etag_${currentUrl}", etag)
                    if (lastMod != null) putString("last_mod_${currentUrl}", lastMod)
                }.apply()

                return Result.success(currentUrl)
            } catch (e: Exception) {
                return Result.failure(IOException("Ошибка при чтении потока EPG ($currentUrl): ${e.localizedMessage ?: e.message}", e))
            }
        }

        return Result.failure(IOException("Слишком много перенаправлений для $url"))
    }

    /**
     * Determines whether the given URL represents an explicit user-configured manual EPG link.
     */
    private fun isManualUrl(
        targetUrl: String,
        customEpgPref: String?,
        explicitEpgParam: String?,
        playlistEntityEpg: String?,
    ): Boolean {
        if (!customEpgPref.isNullOrBlank() && Playlist.normalizeUrl(customEpgPref) == targetUrl) {
            return true
        }
        if (!explicitEpgParam.isNullOrBlank()) {
            val norm = Playlist.normalizeUrl(explicitEpgParam)
            if (!isDefaultFallbackUrl(norm)) return true
        }
        if (!playlistEntityEpg.isNullOrBlank()) {
            val norm = Playlist.normalizeUrl(playlistEntityEpg)
            if (!isDefaultFallbackUrl(norm)) return true
        }
        return false
    }

    private fun isDefaultFallbackUrl(url: String): Boolean {
        val norm = Playlist.normalizeUrl(url).lowercase(Locale.ROOT)
        val defaults = listOf(
            Playlist.DEFAULT_EPG_URL,
            Playlist.DEFAULT_PUBLIC_EPG_URL,
            Playlist.IT999_GITHUB_EPG_URL,
            Playlist.IT999_EPG_URL,
            Playlist.IT999_GITHUB_MIRROR_URL,
            "https://raw.githubusercontent.com/it999/it999.github.io/master/edem.xml.gz",
            "https://iptvx.one/epg/epg_lite.xml.gz",
            "https://epg.pw/xmltv/epg_RU.xml.gz",
        ).map { Playlist.normalizeUrl(it).lowercase(Locale.ROOT) }
        return norm in defaults
    }

    /**
     * Searches fallback open internet EPG feeds for channels that have no EPG,
     * parses matching programmes, and binds them to the database.
     */
    suspend fun syncMissingEpgAndLogos(
        excludeUrl: String? = null,
        onProgress: (suspend (Int, String) -> Unit)? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val customEpg = prefs.getString("custom_epg_url", null)?.trim()?.takeIf { it.isNotBlank() }
        if (!customEpg.isNullOrBlank()) {
            // User set link manually -> skip fallback search
            return@withContext Result.success(0)
        }

        val nowMs = System.currentTimeMillis()
        var missingChannels = channelDao.getChannelsWithoutEpg(nowMs)

        // Also resolve missing logos immediately
        piconResolver.resolveMissingLogos()

        if (missingChannels.isEmpty()) {
            return@withContext Result.success(0)
        }

        var totalImported = 0
        val normalizedExclude = excludeUrl?.let { Playlist.normalizeUrl(it).lowercase(Locale.ROOT) }
        val candidateUrls = FALLBACK_EPG_SOURCES.filter {
            Playlist.normalizeUrl(it).lowercase(Locale.ROOT) != normalizedExclude
        }

        for (sourceUrl in candidateUrls) {
            if (missingChannels.isEmpty()) break

            val imported = fetchFeedForChannels(
                sourceUrl = Playlist.normalizeUrl(sourceUrl),
                channels = missingChannels,
                onProgress = onProgress,
            )
            totalImported += imported

            // Refresh missing channels list
            missingChannels = channelDao.getChannelsWithoutEpg(nowMs)
        }

        piconResolver.resolveMissingLogos()
        Result.success(totalImported)
    }

    /**
     * Fast on-demand fallback discovery for a single channel.
     * Called when the user focuses on a channel that has no EPG.
     */
    suspend fun fetchMissingEpgForChannel(channelId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isSyncing.get()) return@withContext false
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val customEpg = prefs.getString("custom_epg_url", null)?.trim()?.takeIf { it.isNotBlank() }
        if (!customEpg.isNullOrBlank()) {
            // User set link manually -> do NOT query external fallback feeds
            return@withContext false
        }

        val channel = channelDao.getById(channelId) ?: return@withContext false

        // 1. Resolve picon if missing
        if (channel.logoUrl.isNullOrBlank()) {
            val picon = piconResolver.resolveLogo(channel.name)
            if (!picon.isNullOrBlank()) {
                channelDao.updateLogoUrl(channel.id, picon)
            }
        }

        // On-demand fetch is non-blocking: avoid downloading multi-megabyte archives on channel focus
        return@withContext false
    }

    /**
     * Downloads and parses an XMLTV feed filtered strictly for [channels].
     */
    private suspend fun fetchFeedForChannels(
        sourceUrl: String,
        channels: List<ChannelEntity>,
        onProgress: (suspend (Int, String) -> Unit)?,
    ): Int = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "epg_fallback_${System.currentTimeMillis()}.tmp")
        val importedPrograms = AtomicInteger(0)

        try {
            val client = clientFactory.create(Playlist(id = 0L, profileId = 1L, name = "Fallback", url = ""))
                .newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val dl = downloadFeed(client, sourceUrl, tempFile) { _, _ -> }
            if (dl.isFailure) return@withContext 0

            val targetIds = channels.mapNotNull { it.epgChannelId.trim().takeIf { id -> id.isNotEmpty() } }.toSet()
            val targetNames = channels.map { it.name.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()

            val fallbackEntities = ArrayList<EpgProgramEntity>(5000)

            val parser = XmltvParser(
                batchSize = XmltvParser.DEFAULT_BATCH_SIZE,
                targetChannelIds = targetIds,
                targetChannelNames = targetNames,
                onBatch = { batch ->
                    batch.mapTo(fallbackEntities) { prog ->
                        EpgProgramEntity(
                            channelEpgId = prog.channelEpgId,
                            title        = prog.title,
                            description  = prog.description,
                            startMs      = prog.startMs,
                            endMs        = prog.endMs,
                            category     = prog.category,
                            iconUrl      = prog.iconUrl,
                            isNew        = prog.isNew,
                            rating       = prog.rating,
                        )
                    }
                    importedPrograms.addAndGet(batch.size)
                }
            )

            val parseResult = FileInputStream(tempFile).use { stream ->
                parser.parse(stream)
            }

            // Single bulk-insert in one SQLite transaction via EpgDao.bulkUpsert()
            if (fallbackEntities.isNotEmpty()) {
                epgDao.bulkUpsert(fallbackEntities)
            }

            // Auto-link newly matched programmes and logos
            val updates = mutableListOf<ChannelDao.ChannelEpgLogoUpdate>()
            for (ch in channels) {
                val norm = ch.name.trim().lowercase(Locale.ROOT)
                val simplified = XmltvParser.normalizeChannelName(ch.name)
                val matchedEpgId = parseResult.nameToEpgId[norm]
                    ?: parseResult.nameToEpgId[simplified]
                    ?: parseResult.nameToEpgId.entries.firstOrNull { (k, _) ->
                        k == simplified || k.contains(simplified) || (simplified.length >= 4 && simplified.contains(k))
                    }?.value

                if (matchedEpgId != null) {
                    val newEpgId = if (ch.epgChannelId != matchedEpgId) matchedEpgId else null
                    val newLogo = if (ch.logoUrl.isNullOrBlank()) {
                        val logo = parseResult.channelLogos[matchedEpgId]
                            ?: parseResult.nameToLogo[norm]
                            ?: parseResult.nameToLogo[simplified]
                            ?: piconResolver.resolveLogo(ch.name)

                        if (!logo.isNullOrBlank()) logo else null
                    } else null

                    if (newEpgId != null || newLogo != null) {
                        updates.add(
                            ChannelDao.ChannelEpgLogoUpdate(
                                id = ch.id,
                                epgChannelId = newEpgId,
                                logoUrl = newLogo,
                            )
                        )
                    }
                }
            }
            if (updates.isNotEmpty()) {
                channelDao.updateEpgAndLogos(updates)
            }

            importedPrograms.get()
        } catch (_: Exception) {
            0
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    companion object {
        val FALLBACK_EPG_SOURCES = listOf(
            Playlist.DEFAULT_EPG_URL,
            Playlist.DEFAULT_PUBLIC_EPG_URL,
            Playlist.IT999_GITHUB_EPG_URL,
            "https://raw.githubusercontent.com/it999/it999.github.io/master/edem.xml.gz",
            Playlist.IT999_GITHUB_MIRROR_URL,
            Playlist.IT999_EPG_URL,
            "https://iptvx.one/epg/epg_lite.xml.gz",
            "https://epg.pw/xmltv/epg_RU.xml.gz",
            "https://programtv.ru/xmltv.xml.gz",
        )
    }
}
