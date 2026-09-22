package com.minogatv.box.core.parser

import com.minogatv.box.core.model.enums.CatchupType

/**
 * The result of parsing a single M3U playlist file.
 *
 * @param channels     All parsed channel entries (in file order).
 * @param epgUrl       The `url-tvg` or `x-tvg-url` attribute from the `#EXTM3U` header.
 *                     Null if the header doesn't contain an EPG URL.
 * @param epgTimeShift The `tvg-shift` value from the header (-12…+12), or 0f if absent.
 */
data class M3uParseResult(
    val channels: List<M3uChannel>,
    val epgUrl: String? = null,
    val epgTimeShift: Float = 0f,
)

/**
 * A single channel entry parsed from a `#EXTINF` line + the following stream URL.
 *
 * All string fields default to empty rather than null to simplify downstream processing.
 */
data class M3uChannel(
    /** `tvg-id` attribute — used for EPG matching. */
    val epgChannelId: String = "",
    /** `tvg-name` attribute, or falls back to the EXTINF track title. */
    val name: String,
    /** `tvg-logo` attribute. */
    val logoUrl: String = "",
    /** `group-title` attribute — determines the folder/category. */
    val groupTitle: String = "",
    /** The raw stream URL on the line after `#EXTINF`. */
    val streamUrl: String,
    /** `catchup` attribute converted to [CatchupType]. */
    val catchupType: CatchupType = CatchupType.NONE,
    /** `catchup-days` or `timeshift` attribute value. */
    val catchupDays: Int = 0,
    /** `catchup-source` attribute — Flussonic-style URL template. */
    val catchupSource: String = "",
)
