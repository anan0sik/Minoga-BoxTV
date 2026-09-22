package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single EPG programme event.
 *
 * Table: `epg_program`
 *
 * Indices:
 *  - `(channel_epg_id, start_ms, end_ms)` — composite index for time-range queries.
 *    This is the primary query pattern: "give me all programmes for channel X between T1 and T2".
 *  - `channel_epg_id` alone — for "current programme" lookups (ORDER BY start_ms DESC LIMIT 1).
 *
 * Note: There is NO foreign key to ChannelEntity intentionally.
 * EPG data arrives from a separate XMLTV source and channels are matched by `epg_channel_id`
 * (a string key). This avoids FK constraint violations when EPG is loaded before/after channels.
 */
@Entity(
    tableName = "epg_program",
    indices = [
        Index(value = ["channel_epg_id", "start_ms", "end_ms"]),
        Index(value = ["channel_epg_id"]),
        Index(value = ["end_ms", "start_ms"]),
        Index(value = ["start_ms", "end_ms"]),
    ],
)
data class EpgProgramEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Matches [ChannelEntity.epgChannelId] (or [ChannelSettingsEntity.epgChannelIdOverride]). */
    @ColumnInfo(name = "channel_epg_id")
    val channelEpgId: String,

    /** Programme title — also indexed in the FTS shadow table [EpgFtsEntity]. */
    @ColumnInfo(name = "title")
    val title: String,

    /** Full programme description / synopsis. */
    @ColumnInfo(name = "description")
    val description: String = "",

    /** Start time in epoch milliseconds (UTC). */
    @ColumnInfo(name = "start_ms")
    val startMs: Long,

    /** End time in epoch milliseconds (UTC). */
    @ColumnInfo(name = "end_ms")
    val endMs: Long,

    /** Genre/category string from the XMLTV `<category>` tag. */
    @ColumnInfo(name = "category")
    val category: String = "",

    /** Optional programme poster or thumbnail URL. */
    @ColumnInfo(name = "icon_url")
    val iconUrl: String? = null,

    /** Whether the broadcaster flagged this programme as new. */
    @ColumnInfo(name = "is_new")
    val isNew: Boolean = false,

    /** Content rating string (e.g. "PG-13", "18+", "TV-MA"). */
    @ColumnInfo(name = "rating")
    val rating: String = "",
)
