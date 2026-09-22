package com.minogatv.box.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * # EpgSyncWorker
 *
 * A [CoroutineWorker] scheduled by WorkManager for background periodic EPG sync.
 * Delegates the actual downloading and parsing to [EpgSyncManager].
 */
@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val epgSyncManager: EpgSyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = "epg_sync_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "EPG Sync",
                android.app.NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Minoga TV")
            .setContentText("Синхронизация телепрограммы…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(
                1002,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            androidx.work.ForegroundInfo(1002, notification)
        }
    }

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (_: Exception) {}

        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)
        val explicitUrl = inputData.getString(KEY_EPG_URL)?.trim()?.takeIf { it.isNotEmpty() }

        val result = epgSyncManager.syncEpg(
            epgUrl = explicitUrl,
            playlistId = if (playlistId > 0) playlistId else 1L,
            forceRefresh = false,
            onProgress = { pct, status ->
                setProgress(workDataOf(
                    KEY_PROGRESS to pct,
                    KEY_STATUS to status,
                ))
            },
        )

        return if (result.isSuccess) {
            Result.success(workDataOf(KEY_PROGRAM_COUNT to (result.getOrNull() ?: 0)))
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_PLAYLIST_ID   = "playlist_id"
        const val KEY_EPG_URL       = "epg_url"
        const val KEY_PROGRESS      = "progress"
        const val KEY_STATUS        = "status"
        const val KEY_ERROR         = "error"
        const val KEY_PROGRAM_COUNT = "program_count"
    }
}
