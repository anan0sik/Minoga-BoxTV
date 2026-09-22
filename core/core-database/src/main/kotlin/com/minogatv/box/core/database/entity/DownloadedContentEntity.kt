package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for offline-downloaded Catch-up or VOD content.
 *
 * Table: `downloaded_content`
 *
 * ExoPlayer's [DownloadManager] stores the actual media in its own internal directory.
 * This table stores metadata so the app can display the downloads list without
 * querying ExoPlayer's download database directly.
 *
 * Cascade: deleting the profile removes all its download records (but NOT the
 * downloaded files themselves — a separate cleanup job handles that).
 */
@Entity(
    tableName = "downloaded_content",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profile_id"),
        Index("expires_at"),
    ],
)
data class DownloadedContentEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Owner profile. */
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** Source channel ID (null for pure VOD downloads). */
    @ColumnInfo(name = "channel_id")
    val channelId: Long? = null,

    /** VOD item identifier (null for Catch-up downloads). */
    @ColumnInfo(name = "vod_id")
    val vodId: String? = null,

    /** Display name shown in the Downloads screen. */
    @ColumnInfo(name = "title")
    val title: String,

    /** Path to the thumbnail/poster cached locally. */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    /**
     * ExoPlayer download ID / content URI.
     * Passed to [DownloadManager] to resume or remove the download.
     */
    @ColumnInfo(name = "download_id")
    val downloadId: String,

    /** Absolute path to the downloaded media file (for direct playback). */
    @ColumnInfo(name = "local_path")
    val localPath: String,

    /** Size of the downloaded file in bytes. */
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0,

    /** Download completion percentage [0..100]. */
    @ColumnInfo(name = "progress_percent")
    val progressPercent: Int = 0,

    /**
     * Epoch ms when this download expires and should be auto-deleted.
     * Null means it never expires (user-initiated permanent download).
     */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    /** Timestamp when the download was initiated (epoch ms). */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
