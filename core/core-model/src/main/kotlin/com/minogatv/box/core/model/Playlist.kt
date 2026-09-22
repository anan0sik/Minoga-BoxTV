package com.minogatv.box.core.model

import com.minogatv.box.core.model.enums.PlaylistType
import com.minogatv.box.core.model.enums.ProxyType

/**
 * Domain model for a playlist / IPTV provider.
 *
 * A single profile can have multiple playlists. Each playlist has its own:
 *  - Network identity (URL, User-Agent, proxy)
 *  - EPG source URL + per-playlist time-shift
 *  - Stalker/Xtream credentials when applicable
 *
 * @param id                  Unique playlist ID
 * @param profileId           Owner profile
 * @param name                Human-readable name shown in the UI
 * @param type                How to fetch / authenticate ([PlaylistType])
 * @param url                 Primary URL (M3U link, Stalker portal address, or Xtream server)
 * @param userAgent           Custom HTTP User-Agent string (overrides default)
 * @param proxyType           [ProxyType.NONE], [ProxyType.HTTP], or [ProxyType.SOCKS5]
 * @param proxyHost           Proxy hostname or IP
 * @param proxyPort           Proxy port
 * @param proxyUsername       Optional proxy auth username
 * @param proxyPassword       Optional proxy auth password
 * @param epgUrl              XMLTV EPG URL (may differ from the playlist URL)
 * @param epgTimeShiftHours   Global EPG time offset for this playlist (−12…+12 hours)
 * @param macAddress          Stalker portal MAC address (used only when type == STALKER)
 * @param xtreamUsername      Xtream Codes username
 * @param xtreamPassword      Xtream Codes password
 * @param lastRefreshedAt     Timestamp of the last successful M3U/EPG download
 * @param isEnabled           Whether to include this playlist in aggregated channel list
 * @param sortOrder           Manual sort position in the playlist manager screen
 */
data class Playlist(
    val id: Long = 0,
    val profileId: Long,
    val name: String,
    val type: PlaylistType = PlaylistType.M3U,
    val url: String,
    val userAgent: String = DEFAULT_USER_AGENT,
    val proxyType: ProxyType = ProxyType.NONE,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val epgUrl: String? = null,
    val epgTimeShiftHours: Float = 0f,
    val macAddress: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val lastRefreshedAt: Long? = null,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "MinogaTVBox/1.0 (Android)"
        /** Primary default XMLTV EPG source (epg.one) */
        const val DEFAULT_EPG_URL = "http://epg.one/epg2.xml.gz"
        /** Secondary public fallback XMLTV EPG source (direct CDN) */
        const val DEFAULT_PUBLIC_EPG_URL = "http://cdn.epg.one/epg2.xml.gz"
        /** GitHub mirror of it999 project */
        const val IT999_GITHUB_EPG_URL = "https://raw.githubusercontent.com/it999/it999.github.io/master/epg2.xml.gz"
        /** it999 server XMLTV source */
        const val IT999_EPG_URL = "https://epg.it999.ru/edem.xml.gz"
        /** it999 github.io mirror */
        const val IT999_GITHUB_MIRROR_URL = "https://it999.github.io/epg.xml.gz"
        /** Alternative public EPG source */
        const val ALTERNATIVE_EPG_URL = "https://epg.pw/xmltv/epg_RU.xml.gz"

        /**
         * Normalizes and cleans up user-entered or playlist-extracted EPG URLs,
         * correcting common typos like 'hppt://', 'htttp://', or web URLs like 'github.com/it999/...'.
         */
        fun normalizeUrl(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            var url = raw.trim()
                .replace("\uFEFF", "")
                .replace("\u200B", "")

            // Handle prefixes like 'it999http://github.com/...'
            if (url.startsWith("it999http://", ignoreCase = true)) {
                url = "http://" + url.substring(12)
            } else if (url.startsWith("it999https://", ignoreCase = true)) {
                url = "https://" + url.substring(13)
            }

            // Fix typical typos in scheme: hppt://, htttp://, etc.
            if (url.startsWith("htttp://", ignoreCase = true)) {
                url = "http://" + url.substring(8)
            } else if (url.startsWith("htttps://", ignoreCase = true)) {
                url = "https://" + url.substring(9)
            } else if (url.startsWith("hppt://", ignoreCase = true)) {
                url = "http://" + url.substring(7)
            } else if (url.startsWith("hppts://", ignoreCase = true)) {
                url = "https://" + url.substring(8)
            } else if (url.startsWith("ttp://", ignoreCase = true)) {
                url = "http://" + url.substring(6)
            } else if (url.startsWith("ttps://", ignoreCase = true)) {
                url = "https://" + url.substring(7)
            }

            // If multiple URLs separated by comma or semicolon or space, take the first valid one
            if (url.contains(',')) {
                url = url.substringBefore(',').trim()
            } else if (url.contains(';')) {
                url = url.substringBefore(';').trim()
            }

            // If user enters github web repo URL for it999, convert to raw gz
            if (url.contains("github.com/it999/it999.github.io", ignoreCase = true)) {
                return IT999_GITHUB_EPG_URL
            }

            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "http://$url"
            }
            return url
        }
    }
}
