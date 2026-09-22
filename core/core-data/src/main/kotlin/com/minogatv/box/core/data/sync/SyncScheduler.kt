package com.minogatv.box.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * # SyncScheduler
 *
 * Central scheduling hub for all background playlist and EPG sync jobs.
 *
 * ## Work naming conventions
 * | Work name | Type | Description |
 * |---|---|---|
 * | `m3u_sync_<playlistId>` | Periodic | Automatic M3U refresh every `intervalHours` |
 * | `m3u_sync_<playlistId>_manual` | One-time (expedited) | User-triggered manual refresh |
 * | `epg_sync_<playlistId>` | Periodic | Daily EPG download at ~3 AM |
 * | `epg_sync_<playlistId>_manual` | One-time (expedited) | User-triggered EPG refresh |
 *
 * ## Constraints
 * - **Network required** for all jobs (any network type by default).
 * - Periodic M3U jobs use `KEEP` policy — existing schedules are preserved if
 *   the user hasn't changed the interval.
 * - Manual (expedited) jobs use `REPLACE` policy.
 *
 * ## Backoff
 * Both worker types use exponential backoff starting at 30 seconds.
 *
 * @param context Application context injected via Hilt.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val epgSyncManager: EpgSyncManager,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    // ─── M3U sync ─────────────────────────────────────────────────────────────

    /**
     * Schedule (or update) the **periodic** M3U sync for a playlist.
     *
     * Uses [ExistingPeriodicWorkPolicy.KEEP] — if a schedule already exists with
     * the same name, it is left unchanged. Call with [ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE]
     * when the user changes the refresh interval.
     *
     * @param playlistId    Target playlist ID.
     * @param intervalHours How often to refresh the M3U (default = 12 hours).
     * @param policy        Conflict policy for existing scheduled work.
     */
    fun scheduleM3uSync(
        playlistId: Long,
        intervalHours: Long = DEFAULT_M3U_INTERVAL_HOURS,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    ) {
        val request = PeriodicWorkRequestBuilder<M3uSyncWorker>(intervalHours, TimeUnit.HOURS)
            .setInputData(workDataOf(M3uSyncWorker.KEY_PLAYLIST_ID to playlistId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            m3uWorkName(playlistId),
            policy,
            request,
        )
    }

    /**
     * Trigger a **manual, expedited** M3U refresh (runs immediately, bypasses quota).
     *
     * This is called when the user taps "Refresh now" in the Playlist settings.
     * An expedited job is prioritised by the OS and typically starts within seconds.
     *
     * @param playlistId Target playlist ID.
     */
    fun triggerManualM3uSync(playlistId: Long) {
        val request = OneTimeWorkRequestBuilder<M3uSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(M3uSyncWorker.KEY_PLAYLIST_ID to playlistId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "${m3uWorkName(playlistId)}_manual",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Cancel all M3U sync jobs (periodic + manual) for a playlist. */
    fun cancelM3uSync(playlistId: Long) {
        workManager.cancelUniqueWork(m3uWorkName(playlistId))
        workManager.cancelUniqueWork("${m3uWorkName(playlistId)}_manual")
    }

    // ─── EPG sync ─────────────────────────────────────────────────────────────

    /**
     * Schedule (or update) the **periodic** EPG sync for a playlist.
     *
     * Default: once per day (~24 hours). EPG providers typically update at midnight.
     *
     * @param playlistId    Target playlist ID.
     * @param epgUrl        Override the EPG URL (if different from the playlist config).
     * @param intervalHours Refresh interval in hours.
     */
    fun scheduleEpgSync(
        playlistId: Long,
        epgUrl: String? = null,
        intervalHours: Long = DEFAULT_EPG_INTERVAL_HOURS,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    ) {
        val inputData = if (epgUrl != null) {
            workDataOf(
                EpgSyncWorker.KEY_PLAYLIST_ID to playlistId,
                EpgSyncWorker.KEY_EPG_URL to epgUrl,
            )
        } else {
            workDataOf(EpgSyncWorker.KEY_PLAYLIST_ID to playlistId)
        }

        val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(intervalHours, TimeUnit.HOURS)
            .setInputData(inputData)
            .addTag(TAG_EPG_SYNC)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            epgWorkName(playlistId),
            policy,
            request,
        )
    }

    /**
     * Trigger a **manual, expedited** EPG refresh.
     *
     * @param playlistId Target playlist ID.
     * @param epgUrl     Override the EPG URL stored in the playlist config.
     */
    fun triggerManualEpgSync(playlistId: Long, epgUrl: String? = null) {
        // Launch directly via EpgSyncManager in persistent background supervisor scope so UI navigation doesn't cancel it
        epgSyncManager.triggerSync(epgUrl = epgUrl, playlistId = playlistId, forceRefresh = true)
    }

    data class EpgSyncInfo(
        val progress: Float,
        val status: String?,
    )

    /**
     * Observe EPG sync progress and status message across all active EPG sync operations.
     * Returns [EpgSyncInfo] when running, or null when idle.
     */
    fun observeEpgSyncProgressInfo(): Flow<EpgSyncInfo?> {
        val workManagerFlow = workManager.getWorkInfosByTagFlow(TAG_EPG_SYNC).map { workInfoList ->
            val active = workInfoList.firstOrNull { 
                it.state == WorkInfo.State.RUNNING
            }
            if (active != null) {
                val progress = active.progress.getInt(EpgSyncWorker.KEY_PROGRESS, 5)
                val status = active.progress.getString(EpgSyncWorker.KEY_STATUS) ?: "Синхронизация EPG..."
                EpgSyncInfo(
                    progress = (progress / 100f).coerceIn(0.01f, 1f),
                    status = status,
                )
            } else {
                null
            }
        }

        return kotlinx.coroutines.flow.combine(epgSyncManager.syncProgress, workManagerFlow) { directProgress, wmProgress ->
            directProgress ?: wmProgress
        }
    }

    /**
     * Observe EPG sync progress across all active EPG workers.
     * Returns a float progress in range 0.0..1.0 when running, or null when idle.
     */
    fun observeEpgSyncProgress(): Flow<Float?> = observeEpgSyncProgressInfo().map { it?.progress }

    /** Cancel all EPG sync jobs (periodic + manual) for a playlist. */
    fun cancelEpgSync(playlistId: Long) {
        workManager.cancelUniqueWork(epgWorkName(playlistId))
        workManager.cancelUniqueWork("${epgWorkName(playlistId)}_manual")
    }

    // ─── Work name helpers ────────────────────────────────────────────────────

    private fun m3uWorkName(playlistId: Long) = "m3u_sync_$playlistId"
    private fun epgWorkName(playlistId: Long) = "epg_sync_$playlistId"

    companion object {
        const val TAG_EPG_SYNC = "epg_sync_all"
        const val DEFAULT_M3U_INTERVAL_HOURS = 24L
        const val DEFAULT_EPG_INTERVAL_HOURS = 24L
    }
}

