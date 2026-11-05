package com.minogatv.box.core.model

/**
 * Configuration extracted or submitted via the remote web page (`minogatvbox.duckdns.org`).
 *
 * @param name          Human-readable playlist name (defaults to "Minoga TV").
 * @param playlistUrl   M3U / M3U8 / Xtream playlist link.
 * @param epgUrl        XMLTV EPG link (.xml or .xml.gz), if provided.
 * @param sourcePageUrl The web page URL from which this config was loaded.
 */
data class WebConfig(
    val name: String = "Minoga TV",
    val playlistUrl: String,
    val epgUrl: String? = null,
    val sourcePageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL,
)

/**
 * Status of a 4-digit pairing code session on `minogatvbox.duckdns.org`.
 */
enum class WebPairingStatus {
    /** Waiting for the user to enter the 4-digit code on the web page */
    WAITING,
    /** User entered the 4-digit code on the web page and is now filling Name, Playlist, and EPG */
    VERIFIED,
    /** User submitted Name, Playlist, and EPG on the web page — ready to install on TV */
    READY,
}

/**
 * Result of polling `/api/code/poll?code=XXXX`.
 */
data class WebPairingPollResult(
    val status: WebPairingStatus,
    val code: String,
    val config: WebConfig? = null,
)
