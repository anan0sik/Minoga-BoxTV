package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.minogatv.box.core.model.enums.CatchupType

/**
 * Room entity for [com.minogatv.box.core.model.Channel].
 *
 * Table: `channel`
 *
 * Foreign key cascade: deleting a [PlaylistEntity] removes all its channels.
 * Index on `playlist_id` for fast per-playlist queries.
 * Index on `epg_channel_id` for O(1) EPG join lookups.
 * Index on `group_title` for fast category filtering.
 */
@Entity(
    tableName = "channel",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlist_id"),
        Index("epg_channel_id"),
        Index("group_title"),
    ],
)
data class ChannelEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Parent playlist. */
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    /**
     * The `tvg-id` attribute from the M3U EXTINF line.
     * Used as the key to join with [EpgProgramEntity.channelEpgId].
     */
    @ColumnInfo(name = "epg_channel_id")
    val epgChannelId: String = "",

    /** Channel display name (`tvg-name` or the EXTINF track title). */
    @ColumnInfo(name = "name")
    val name: String,

    /** `tvg-logo` image URL. */
    @ColumnInfo(name = "logo_url")
    val logoUrl: String? = null,

    /** Stream URL (HLS, MPEG-TS, RTMP, RTSP…). */
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,

    /** `group-title` — determines which folder this channel belongs to. */
    @ColumnInfo(name = "group_title")
    val groupTitle: String = "",

    /** Archive / timeshift protocol supported by this channel's stream. */
    @ColumnInfo(name = "catchup_type")
    val catchupType: CatchupType = CatchupType.NONE,

    /** Number of days available in the archive (0 = no archive). */
    @ColumnInfo(name = "catchup_days")
    val catchupDays: Int = 0,

    /**
     * Catch-up URL template, e.g.:
     * `http://provider/{channel}/timeshift_abs/{utc}/{duration}/index.m3u8`
     */
    @ColumnInfo(name = "catchup_source")
    val catchupSource: String? = null,

    /** User has hidden this channel from the list. */
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,

    /**
     * Denormalised favourite flag for quick rendering.
     * The authoritative source is [FavoriteChannelEntity].
     */
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    /** Manual drag-and-drop sort position within its group. */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    /**
     * Auto-detected or manually flagged as adult content.
     * Channels with group names containing "18+", "Adult", "XXX" are flagged automatically.
     */
    @ColumnInfo(name = "is_adult")
    val isAdult: Boolean = false,
)
