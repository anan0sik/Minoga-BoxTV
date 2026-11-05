package com.minogatv.box.core.parser

import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.WebConfig
import com.minogatv.box.core.model.WebPairingPollResult
import com.minogatv.box.core.model.WebPairingStatus
import java.net.URI

/**
 * Extracts playlist (M3U / M3U8) and EPG (XMLTV) links from a remote web page or JSON endpoint
 * such as `http://minogatvbox.duckdns.org`.
 *
 * Supported formats on the target page:
 * 1. JSON object (`{"name": "...", "playlistUrl": "...", "epgUrl": "..."}`)
 * 2. Embedded `<script id="minoga-config" type="application/json">...</script>` in HTML
 * 3. HTML `data-minoga-playlist` and `data-minoga-epg` attributes on any element
 * 4. HTML `<meta name="minoga:playlist-url" content="...">` and `<meta name="minoga:epg-url" content="...">`
 * 5. Anchor `<a href="...">` or raw HTTP(S) links ending with `.m3u` / `.m3u8` and `.xml` / `.xml.gz`
 * 6. Direct `#EXTM3U` playlist response
 */
object WebConfigParser {

    private val SCRIPT_CONFIG_REGEX = Regex(
        """<script[^>]*id\s*=\s*["']minoga-config["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val DATA_NAME_REGEX = Regex(
        """data-minoga-name\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val DATA_PLAYLIST_REGEX = Regex(
        """data-minoga-playlist\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val DATA_EPG_REGEX = Regex(
        """data-minoga-epg\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val META_NAME_REGEX = Regex(
        """<meta[^>]*name\s*=\s*["']minoga:name["'][^>]*content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val META_PLAYLIST_REGEX = Regex(
        """<meta[^>]*name\s*=\s*["']minoga:playlist-url["'][^>]*content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val META_EPG_REGEX = Regex(
        """<meta[^>]*name\s*=\s*["']minoga:epg-url["'][^>]*content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    private val URL_TOKEN_REGEX = Regex(
        """https?://[^\s"'<>\\]+""",
        RegexOption.IGNORE_CASE,
    )

    private val M3U_HEADER_EPG_REGEX = Regex(
        """(?:x-tvg-url|url-tvg)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Parses [rawContent] downloaded from [sourcePageUrl] and returns a [WebConfig] if a playlist
     * link (or EPG link) was found.
     */
    fun parse(rawContent: String, sourcePageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL): WebConfig? {
        val text = rawContent.trim()
        if (text.isEmpty()) return null

        // Case 0: Direct M3U file content
        if (text.startsWith("#EXTM3U", ignoreCase = true)) {
            val firstLine = text.lineSequence().firstOrNull().orEmpty()
            val headerEpg = M3U_HEADER_EPG_REGEX.find(firstLine)?.groupValues?.get(1)?.trim()
            return WebConfig(
                name = "Minoga TV",
                playlistUrl = sourcePageUrl,
                epgUrl = headerEpg?.let { resolveUrl(sourcePageUrl, it) },
                sourcePageUrl = sourcePageUrl,
            )
        }

        // Case 1: Pure JSON object
        if (text.startsWith("{") && text.endsWith("}")) {
            parseJsonBlock(text, sourcePageUrl)?.let { return it }
        }

        // Case 2: Embedded <script id="minoga-config" type="application/json"> in HTML
        SCRIPT_CONFIG_REGEX.find(text)?.groupValues?.get(1)?.trim()?.let { jsonBlock ->
            parseJsonBlock(jsonBlock, sourcePageUrl)?.let { return it }
        }

        // Case 3: HTML data-minoga-* attributes or <meta name="minoga:*"> tags
        val attrName = unescapeHtml(
            DATA_NAME_REGEX.find(text)?.groupValues?.get(1)
                ?: META_NAME_REGEX.find(text)?.groupValues?.get(1)
                ?: "Minoga TV"
        ).trim().ifBlank { "Minoga TV" }

        val attrPlaylist = unescapeHtml(
            DATA_PLAYLIST_REGEX.find(text)?.groupValues?.get(1)
                ?: META_PLAYLIST_REGEX.find(text)?.groupValues?.get(1)
                ?: ""
        ).trim()

        val attrEpg = unescapeHtml(
            DATA_EPG_REGEX.find(text)?.groupValues?.get(1)
                ?: META_EPG_REGEX.find(text)?.groupValues?.get(1)
                ?: ""
        ).trim()

        if (attrPlaylist.isNotEmpty()) {
            return WebConfig(
                name = attrName,
                playlistUrl = resolveUrl(sourcePageUrl, attrPlaylist),
                epgUrl = attrEpg.takeIf { it.isNotEmpty() }?.let { resolveUrl(sourcePageUrl, it) },
                sourcePageUrl = sourcePageUrl,
            )
        }

        // Case 4: Generic URL scan on HTML / plain text page
        val discoveredUrls = URL_TOKEN_REGEX.findAll(text)
            .map { unescapeHtml(it.value.trimEnd(',', ';', ')', ']', '.')) }
            .distinct()
            .toList()

        val candidatePlaylist = discoveredUrls.firstOrNull { url ->
            val lower = url.lowercase()
            lower.contains(".m3u8") ||
                lower.contains(".m3u") ||
                lower.contains("get.php") ||
                lower.contains("playlist")
        }

        val candidateEpg = discoveredUrls.firstOrNull { url ->
            val lower = url.lowercase()
            lower.contains(".xml.gz") ||
                lower.contains(".xml") ||
                lower.contains("xmltv") ||
                lower.contains("epg")
        }

        if (candidatePlaylist != null) {
            return WebConfig(
                name = attrName,
                playlistUrl = resolveUrl(sourcePageUrl, candidatePlaylist),
                epgUrl = candidateEpg?.let { resolveUrl(sourcePageUrl, it) },
                sourcePageUrl = sourcePageUrl,
            )
        }

        return null
    }

    /**
     * Parses a JSON response from `/api/code/poll?code=XXXX`.
     */
    fun parsePollResponse(
        rawJson: String,
        code: String,
        sourcePageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL,
    ): WebPairingPollResult? {
        val text = rawJson.trim()
        if (!text.startsWith("{")) return null

        val statusStr = extractJsonField(text, listOf("status"))?.uppercase().orEmpty()
        val status = when (statusStr) {
            "READY" -> WebPairingStatus.READY
            "VERIFIED" -> WebPairingStatus.VERIFIED
            "WAITING" -> WebPairingStatus.WAITING
            else -> return null
        }

        val config = if (status == WebPairingStatus.READY) {
            parseJsonBlock(text, sourcePageUrl)
        } else {
            null
        }

        return WebPairingPollResult(
            status = status,
            code = code,
            config = config,
        )
    }

    private fun parseJsonBlock(json: String, sourcePageUrl: String): WebConfig? {
        val name = extractJsonField(json, listOf("name", "playlistName", "title"))
            ?.ifBlank { "Minoga TV" } ?: "Minoga TV"

        val playlistRaw = extractJsonField(
            json,
            listOf("playlistUrl", "playlist_url", "playlist", "m3uUrl", "m3u_url", "m3u", "url"),
        )?.trim().orEmpty()

        val epgRaw = extractJsonField(
            json,
            listOf("epgUrl", "epg_url", "epg", "xmltvUrl", "xmltv_url", "xmltv"),
        )?.trim().orEmpty()

        if (playlistRaw.isEmpty()) return null

        return WebConfig(
            name = name,
            playlistUrl = resolveUrl(sourcePageUrl, playlistRaw),
            epgUrl = epgRaw.takeIf { it.isNotEmpty() }?.let { resolveUrl(sourcePageUrl, it) },
            sourcePageUrl = sourcePageUrl,
        )
    }

    private fun extractJsonField(json: String, keys: List<String>): String? {
        for (key in keys) {
            val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
            val match = pattern.find(json)
            if (match != null) {
                val value = match.groupValues[1]
                    .replace("""\/""", "/")
                    .replace("""\""", "\"")
                    .replace("""\\""", "\\")
                    .trim()
                if (value.isNotEmpty()) return value
            }
        }
        return null
    }

    private fun resolveUrl(baseUrl: String, target: String): String {
        val cleaned = target.trim()
        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            return Playlist.normalizeUrl(cleaned)
        }
        return try {
            val normalizedBase = if (!baseUrl.startsWith("http://", ignoreCase = true) && !baseUrl.startsWith("https://", ignoreCase = true)) {
                "http://$baseUrl"
            } else {
                baseUrl
            }
            val resolved = URI(normalizedBase).resolve(cleaned).toString()
            Playlist.normalizeUrl(resolved)
        } catch (_: Exception) {
            Playlist.normalizeUrl(cleaned)
        }
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}

