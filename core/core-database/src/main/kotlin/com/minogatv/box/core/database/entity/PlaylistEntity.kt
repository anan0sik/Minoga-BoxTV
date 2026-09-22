package com.minogatv.box.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.minogatv.box.core.model.enums.PlaylistType
import com.minogatv.box.core.model.enums.ProxyType

/**
 * Room entity for [com.minogatv.box.core.model.Playlist].
 *
 * Table: `playlist`
 *
 * Foreign key cascade: deleting a [UserProfileEntity] removes all its playlists.
 */
@Entity(
    tableName = "playlist",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class PlaylistEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Owner profile. */
    @ColumnInfo(name = "profile_id")
    val profileId: Long,

    /** Human-readable name shown in the Playlists screen. */
    @ColumnInfo(name = "name")
    val name: String,

    /** Playlist / portal type. */
    @ColumnInfo(name = "type")
    val type: PlaylistType = PlaylistType.M3U,

    /** Primary URL: M3U link, Stalker portal address, or Xtream server base URL. */
    @ColumnInfo(name = "url")
    val url: String,

    /** HTTP User-Agent header sent with every request. */
    @ColumnInfo(name = "user_agent")
    val userAgent: String = "MinogaTVBox/1.0 (Android)",

    /** Proxy protocol. */
    @ColumnInfo(name = "proxy_type")
    val proxyType: ProxyType = ProxyType.NONE,

    /** Proxy hostname or IP (null when [proxyType] == NONE). */
    @ColumnInfo(name = "proxy_host")
    val proxyHost: String? = null,

    /** Proxy port number. */
    @ColumnInfo(name = "proxy_port")
    val proxyPort: Int? = null,

    /** Optional proxy authentication username. */
    @ColumnInfo(name = "proxy_username")
    val proxyUsername: String? = null,

    /** Optional proxy authentication password (stored encrypted via EncryptedSharedPreferences). */
    @ColumnInfo(name = "proxy_password")
    val proxyPassword: String? = null,

    /** Dedicated XMLTV EPG source URL (may differ from the M3U URL). */
    @ColumnInfo(name = "epg_url")
    val epgUrl: String? = null,

    /** EPG time offset applied to all channels in this playlist (−12…+12 hours). */
    @ColumnInfo(name = "epg_time_shift_hours")
    val epgTimeShiftHours: Float = 0f,

    /** Stalker Portal MAC address — only used when [type] == STALKER. */
    @ColumnInfo(name = "mac_address")
    val macAddress: String? = null,

    /** Xtream Codes username. */
    @ColumnInfo(name = "xtream_username")
    val xtreamUsername: String? = null,

    /** Xtream Codes password. */
    @ColumnInfo(name = "xtream_password")
    val xtreamPassword: String? = null,

    /** Timestamp of the last successful M3U/EPG refresh. */
    @ColumnInfo(name = "last_refreshed_at")
    val lastRefreshedAt: Long? = null,

    /** Set to false to temporarily exclude this playlist from the aggregated channel list. */
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    /** Manual sort position in the Playlists management screen. */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
