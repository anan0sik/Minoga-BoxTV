package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.minogatv.box.core.model.enums.AspectRatio
import com.minogatv.box.core.model.enums.DecoderType

/**
 * Room entity for per-channel player overrides.
 *
 * Table: `channel_settings`
 *
 * Uses [channelId] as both primary key and foreign key — guaranteeing a
 * strict 1-to-1 relationship with [ChannelEntity]. Cascade delete ensures
 * settings are cleaned up when the channel is removed.
 */
@Entity(
    tableName = "channel_settings",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChannelSettingsEntity(

    /**
     * Same value as [ChannelEntity.id] — serves as both PK and FK.
     */
    @PrimaryKey
    @ColumnInfo(name = "channel_id")
    val channelId: Long,

    /** Per-channel video decoder override. Defaults to AUTO (uses global setting). */
    @ColumnInfo(name = "decoder_type")
    val decoderType: DecoderType = DecoderType.AUTO,

    /** Per-channel aspect ratio / scaling mode. */
    @ColumnInfo(name = "aspect_ratio")
    val aspectRatio: AspectRatio = AspectRatio.FIT,

    /**
     * Per-channel EPG time shift in hours (−12…+12).
     * Stacks on top of the playlist-level offset.
     */
    @ColumnInfo(name = "epg_time_shift_hours")
    val epgTimeShiftHours: Float = 0f,

    /**
     * Manually mapped EPG `tvg-id`.
     * When set, overrides [ChannelEntity.epgChannelId] for EPG lookups.
     */
    @ColumnInfo(name = "epg_channel_id_override")
    val epgChannelIdOverride: String? = null,

    /**
     * Manual stream URL override that replaces the M3U-sourced [ChannelEntity.streamUrl].
     * Useful for testing alternative CDN URLs without re-importing the playlist.
     */
    @ColumnInfo(name = "custom_stream_url")
    val customStreamUrl: String? = null,

    /** Requires PIN entry before this channel is played. */
    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,
)
