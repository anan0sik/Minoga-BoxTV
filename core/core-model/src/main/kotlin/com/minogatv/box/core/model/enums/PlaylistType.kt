package com.minogatv.box.core.model.enums

/**
 * The type of playlist/provider this entry represents.
 *
 * - [M3U] – standard M3U/M3U8 playlist URL
 * - [XMLTV] – dedicated XMLTV EPG URL (may accompany an M3U)
 * - [STALKER] – Ministra (formerly Stalker Portal) authenticated via MAC address
 * - [XTREAM] – Xtream Codes API (username + password + server URL)
 */
enum class PlaylistType {
    M3U,
    XMLTV,
    STALKER,
    XTREAM,
}
