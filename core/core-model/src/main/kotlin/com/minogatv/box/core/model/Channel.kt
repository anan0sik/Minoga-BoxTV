package com.minogatv.box.core.model

import com.minogatv.box.core.model.enums.CatchupType

/**
 * Domain model for an IPTV channel parsed from an M3U playlist.
 *
 * @param id              Unique channel ID (Room auto-generated)
 * @param playlistId      Parent playlist (FK)
 * @param epgChannelId    The `tvg-id` attribute used to join with EPG data
 * @param name            Display name (`tvg-name` or fallback to track title)
 * @param logoUrl         `tvg-logo` URL for channel artwork
 * @param streamUrl       The actual playback URL (HLS, MPEG-TS, RTMP…)
 * @param groupTitle      `group-title` attribute → used as folder/category name
 * @param catchupType     Archive/timeshift protocol ([CatchupType])
 * @param catchupDays     Number of days available in the archive
 * @param catchupSource   Catch-up URL template (e.g. Flussonic pattern)
 * @param isHidden        Whether this channel is hidden from the list
 * @param isFavorite      Cached favourite flag for the current profile (denormalized)
 * @param sortOrder       Manual drag-and-drop sort position within its group
 * @param isAdult         Auto-detected or manually flagged as adult content
 */
data class Channel(
    val id: Long = 0,
    val playlistId: Long,
    val epgChannelId: String = "",
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val groupTitle: String = "",
    val catchupType: CatchupType = CatchupType.NONE,
    val catchupDays: Int = 0,
    val catchupSource: String? = null,
    val isHidden: Boolean = false,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
    val isAdult: Boolean = false,
) {
    /**
     * Whether this channel supports catch-up / archive playback.
     * Optionally accepts a [forcedOverride] from user settings.
     */
    fun hasArchive(forcedOverride: CatchupType? = null): Boolean {
        if (forcedOverride == CatchupType.NONE) return false
        if (forcedOverride != null && forcedOverride != CatchupType.AUTO) return true
        return (catchupType != CatchupType.NONE) || (catchupDays > 0) || (!catchupSource.isNullOrBlank())
    }

    val hasArchive: Boolean
        get() = hasArchive(null)
}

object ChannelUtils {
    /**
     * Cleans and normalizes channel names for fuzzy EPG matching
     * (removes channel numbers, quality tags like HD, FHD, 4K, country prefixes/suffixes, punctuation).
     */
    fun normalizeChannelName(raw: String): String {
        return raw.lowercase(java.util.Locale.ROOT)
            .replace(Regex("^((\\d+([\\.\\-\\s\\|]+))|(\\[[^\\]]+\\]\\s*)|([a-z]{2,3}[:\\|\\-]\\s*))+"), "")
            .replace(Regex("\\b(hd|fhd|4k|uhd|sd|hevc|50fps|h\\.265|h265|orig|original|ua|ru|by|kz|резерв)\\b"), "")
            .replace(Regex("\\(\\+[0-9]\\)"), "")
            .replace(Regex("[\\(\\)\\[\\]\\+\\-_\\.\\|\\/\\\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

