package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for tracking playback history and "Continue Watching" positions.
 *
 * Table: `watch_history`
 *
 * Used for:
 *  1. "Continue watching" row (resume last position in VOD / Catch-up)
 *  2. Channel last-watched ordering
 *  3. Cloud sync of playback progress across devices
 *
 * Design: upsert by `(profile_id, channel_id, vod_id)` so there is only one row
 * per content item per profile, and it is continuously updated with the latest position.
 */
@Entity(
    tableName = "watch_history",
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
        Index(value = ["profile_id", "channel_id"]),
        Index(value = ["profile_id", "vod_id"]),
    ],
)
data class WatchHistoryEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Owner profile. */
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /**
     * Channel being watched, or null for pure VOD items that have no channel association.
     */
    @ColumnInfo(name = "channel_id")
    val channelId: Long? = null,

    /**
     * VOD / series item identifier (TMDB ID or provider VOD ID).
     * Null for live-TV channels.
     */
    @ColumnInfo(name = "vod_id")
    val vodId: String? = null,

    /** Timestamp when playback started (epoch ms). */
    @ColumnInfo(name = "started_at")
    val startedAt: Long = System.currentTimeMillis(),

    /** Timestamp of the last playback position update (epoch ms). */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * Last known playback position in milliseconds.
     * For live TV channels this is always 0 (live streams don't have a seek position).
     */
    @ColumnInfo(name = "position_ms")
    val positionMs: Long = 0,

    /**
     * Total content duration in milliseconds.
     * 0 for live TV. Used to compute a "progress %" in Continue Watching UI.
     */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,
)
