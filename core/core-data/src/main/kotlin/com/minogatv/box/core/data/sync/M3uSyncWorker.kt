package com.minogatv.box.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.enums.PlaylistType
import com.minogatv.box.core.data.mapper.toDomain
import com.minogatv.box.core.network.ProxyOkHttpClientFactory
import com.minogatv.box.core.parser.M3uParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * # M3uSyncWorker
 *
 * A [CoroutineWorker] that downloads and imports an M3U playlist in the background.
 */
@HiltWorker
class M3uSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val clientFactory: ProxyOkHttpClientFactory,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)
        if (playlistId == -1L) {
            return Result.failure(workDataOf(KEY_ERROR to "Missing playlist ID"))
        }

        // ── Step 1: Load playlist config
        val playlistEntity = playlistDao.getById(playlistId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Playlist $playlistId not found"))

        val playlist = playlistEntity.toDomain()

        setProgress(workDataOf(KEY_PROGRESS to 5, KEY_STATUS to "Connecting…"))

        // ── Step 2: Resolve URL and Download M3U
        val downloadUrl = resolveDownloadUrl(playlist)
        val client = clientFactory.create(playlist)
        val request = Request.Builder().url(downloadUrl).build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            return Result.retry()
        }

        if (!response.isSuccessful) {
            return Result.retry()
        }

        setProgress(workDataOf(KEY_PROGRESS to 20, KEY_STATUS to "Parsing playlist…"))

        // ── Step 3: Parse M3U (streaming — no full file in memory)
        val parseResult = try {
            response.body!!.byteStream().use { stream ->
                M3uParser.parse(stream)
            }
        } catch (e: Exception) {
            return Result.failure(workDataOf(KEY_ERROR to "Parse error: ${e.message}"))
        }

        setProgress(workDataOf(
            KEY_PROGRESS to 60,
            KEY_STATUS to "Importing ${parseResult.channels.size} channels…",
        ))

        // ── Step 4: Map to entities
        val adultKeywords = M3uParser.ADULT_KEYWORDS
        val channelEntities = parseResult.channels.mapIndexed { index, ch ->
            val groupLower = ch.groupTitle.lowercase()
            val isAdult = adultKeywords.any { groupLower.contains(it) }
            ChannelEntity(
                playlistId   = playlistId,
                epgChannelId = ch.epgChannelId,
                name         = ch.name,
                logoUrl      = ch.logoUrl.takeIf { it.isNotEmpty() },
                streamUrl    = ch.streamUrl,
                groupTitle   = ch.groupTitle,
                catchupType  = ch.catchupType,
                catchupDays  = ch.catchupDays,
                catchupSource = ch.catchupSource.takeIf { it.isNotEmpty() },
                isHidden     = false,
                isFavorite   = false,
                sortOrder    = index,
                isAdult      = isAdult,
            )
        }

        // ── Step 5: Atomic replacement in Room
        try {
            withContext(Dispatchers.IO) {
                channelDao.replaceAll(playlistId, channelEntities)
            }
        } catch (e: Exception) {
            return Result.failure(workDataOf(KEY_ERROR to "DB error: ${e.message}"))
        }

        // ── Step 6: Persist EPG URL (preserve user-configured playlist epgUrl, or take from M3U header, or default)
        val rawEpg = playlistEntity.epgUrl?.takeIf { it.isNotBlank() }
            ?: parseResult.epgUrl?.takeIf { it.isNotBlank() }
            ?: Playlist.DEFAULT_EPG_URL
        val epgUrl = Playlist.normalizeUrl(rawEpg)

        playlistDao.updateEpgUrl(playlistId, epgUrl)

        playlistDao.updateLastRefreshed(playlistId, System.currentTimeMillis())

        setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STATUS to "Done"))
        return Result.success(
            workDataOf(
                KEY_CHANNEL_COUNT to channelEntities.size,
                KEY_EPG_URL to (epgUrl ?: ""),
            ),
        )
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

    companion object {
        const val KEY_PLAYLIST_ID   = "playlist_id"
        const val KEY_PROGRESS      = "progress"
        const val KEY_STATUS        = "status"
        const val KEY_ERROR         = "error"
        const val KEY_CHANNEL_COUNT = "channel_count"
        const val KEY_EPG_URL       = "epg_url"
    }
}
