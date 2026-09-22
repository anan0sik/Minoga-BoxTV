package com.minogatv.box.core.parser

import com.minogatv.box.core.model.enums.CatchupType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * # M3uParser
 *
 * A streaming, line-by-line M3U / M3U8 playlist parser.
 *
 * ## Design goals
 * - **Memory efficient**: never loads the entire file. Uses a [BufferedReader] to
 *   process one line at a time. Safe for 50,000+ channel playlists.
 * - **Coroutine-friendly**: [parse] is a `suspend fun` that runs on [Dispatchers.IO].
 * - **Full attribute coverage**: extracts all attributes used by major IPTV providers.
 *
 * ## Supported M3U attributes (`#EXTINF` line)
 * | Attribute        | Description |
 * |------------------|-------------|
 * | `tvg-id`         | EPG channel identifier |
 * | `tvg-name`       | Channel display name |
 * | `tvg-logo`       | Logo image URL |
 * | `group-title`    | Folder / category |
 * | `catchup`        | Archive type (`flussonic`, `default`, `shift`, `append`) |
 * | `catchup-days`   | Days of archive available |
 * | `catchup-source` | Flussonic / Ace stream archive URL template |
 * | `timeshift`      | Alias for `catchup-days` |
 *
 * ## Supported `#EXTM3U` header attributes
 * | Attribute        | Description |
 * |------------------|-------------|
 * | `url-tvg`        | XMLTV EPG source URL |
 * | `x-tvg-url`      | Alternative attribute for EPG URL |
 * | `tvg-shift`      | Global EPG time shift |
 *
 * ## Adult content auto-detection
 * Channels whose `group-title` contains any of the [ADULT_KEYWORDS] are
 * returned with [M3uChannel.groupTitle] unchanged — the caller (repository /
 * worker) is responsible for setting the `isAdult` flag on the entity.
 */
object M3uParser {

    /**
     * Parse an M3U playlist from an [InputStream].
     *
     * The stream is NOT closed by this function — the caller is responsible.
     *
     * @param stream   Input stream of the M3U file content.
     * @return [M3uParseResult] containing all channels and header metadata.
     */
    suspend fun parse(stream: InputStream): M3uParseResult = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val channels = mutableListOf<M3uChannel>()
        var epgUrl: String? = null
        var epgTimeShift = 0f
        var defaultCatchupAttr: String? = null
        var defaultCatchupDays: Int = 0
        var defaultCatchupSource: String? = null

        var pendingExtInf: String? = null     // the last #EXTINF line not yet matched to a URL
        var pendingGroup: String? = null      // #EXTGRP line if present between #EXTINF and URL
        val pendingVlcOpts = mutableListOf<String>()

        reader.use { br ->
            br.lineSequence().forEach { rawLine ->
                val line = rawLine.removePrefix("\uFEFF").trim()
                when {
                    // ── #EXTM3U header ──────────────────────────────────────────
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        epgUrl = extractAttr(line, "url-tvg")
                            ?: extractAttr(line, "x-tvg-url")
                        epgTimeShift = extractAttr(line, "tvg-shift")?.toFloatOrNull() ?: 0f
                        defaultCatchupAttr = extractAttr(line, "catchup")
                            ?: extractAttr(line, "catchup-type")
                            ?: extractAttr(line, "catchup_type")
                        defaultCatchupDays = extractAttr(line, "catchup-days")?.toIntOrNull()
                            ?: extractAttr(line, "catchup_days")?.toIntOrNull()
                            ?: extractAttr(line, "catchupdays")?.toIntOrNull()
                            ?: extractAttr(line, "timeshift")?.toIntOrNull()
                            ?: extractAttr(line, "arc-days")?.toIntOrNull()
                            ?: extractAttr(line, "arc-time")?.toIntOrNull()
                            ?: parseTvgRec(extractAttr(line, "tvg-rec") ?: extractAttr(line, "tvg_rec") ?: extractAttr(line, "rec"))
                            ?: 0
                        defaultCatchupSource = extractAttr(line, "catchup-source")
                            ?: extractAttr(line, "catchup_source")
                        // If header specifies catchup protocol or source, ensure default days >= 7
                        if (defaultCatchupDays <= 0 && ((defaultCatchupAttr != null && !isAttrDisabled(defaultCatchupAttr!!)) || !defaultCatchupSource.isNullOrBlank())) {
                            defaultCatchupDays = 7
                        }
                    }

                    // ── #EXTINF metadata line ──────────────────────────────────
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingExtInf = line
                        pendingGroup = null
                        pendingVlcOpts.clear()
                    }

                    // ── #EXTGRP category directive ─────────────────────────────
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        pendingGroup = line.substringAfter(':').trim()
                    }

                    // ── #EXTVLCOPT option directive ────────────────────────────
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        pendingVlcOpts.add(line.substringAfter(':').trim())
                    }

                    // ── Stream URL line (non-comment, non-empty) ───────────────
                    line.isNotEmpty() && !line.startsWith("#") -> {
                        val extinf = pendingExtInf
                        val group = pendingGroup
                        val vlcOpts = pendingVlcOpts.toList()
                        pendingExtInf = null  // consume
                        pendingGroup = null
                        pendingVlcOpts.clear()

                        val channel = buildChannel(
                            extinf = extinf,
                            groupOverride = group,
                            vlcOpts = vlcOpts,
                            streamUrl = line,
                            defaultCatchupAttr = defaultCatchupAttr,
                            defaultCatchupDays = defaultCatchupDays,
                            defaultCatchupSource = defaultCatchupSource,
                        )
                        channels.add(channel)
                    }
                }
            }
        }

        M3uParseResult(
            channels = channels,
            epgUrl = epgUrl,
            epgTimeShift = epgTimeShift,
        )
    }

    // ─── Private: build a channel from an EXTINF line + URL ──────────────────

    private fun buildChannel(
        extinf: String?,
        groupOverride: String?,
        vlcOpts: List<String> = emptyList(),
        streamUrl: String,
        defaultCatchupAttr: String? = null,
        defaultCatchupDays: Int = 0,
        defaultCatchupSource: String? = null,
    ): M3uChannel {
        if (extinf == null) {
            // Bare stream URL with no EXTINF metadata
            val hasGlobalArchive = (!defaultCatchupAttr.isNullOrEmpty() && !isAttrDisabled(defaultCatchupAttr)) ||
                    defaultCatchupDays > 0 ||
                    !defaultCatchupSource.isNullOrEmpty()

            val detectedType = if (hasGlobalArchive) {
                parseCatchupType(defaultCatchupAttr ?: "default", defaultCatchupSource ?: "", streamUrl)
            } else {
                CatchupType.NONE
            }
            val days = if (hasGlobalArchive) {
                if (defaultCatchupDays > 0) defaultCatchupDays else 7
            } else 0

            return M3uChannel(
                name = streamUrl.substringAfterLast('/'),
                groupTitle = groupOverride ?: "",
                streamUrl = streamUrl,
                catchupType = detectedType,
                catchupDays = days,
                catchupSource = if (hasGlobalArchive) (defaultCatchupSource ?: "") else "",
            )
        }

        // ── Extract the track title (everything after the last comma)
        //    Example: #EXTINF:-1 tvg-id="foo" tvg-name="Bar",Channel Name
        val commaIndex = extinf.lastIndexOf(',')
        val trackTitle = if (commaIndex >= 0) extinf.substring(commaIndex + 1).trim() else ""

        // ── Parse named attributes (properly handles quotes, spaces, etc.)
        val tvgId      = extractAttr(extinf, "tvg-id")     ?: ""
        val tvgName    = extractAttr(extinf, "tvg-name")   ?: ""
        val tvgLogo    = extractAttr(extinf, "tvg-logo")   ?: ""
        val groupTitle = extractAttr(extinf, "group-title") ?: groupOverride ?: ""

        // ── Catch-up attributes for channel:
        // catchup="..." (main type attribute)
        val chCatchupAttr = extractAttr(extinf, "catchup")
            ?: extractAttr(extinf, "catchup-type")
            ?: extractAttr(extinf, "catchup_type")
            ?: vlcOpts.firstNotNullOfOrNull { extractAttr(it, "catchup") ?: extractAttr(it, "catchup-type") }

        // catchup-days="..." / timeshift="..."
        val chCatchupDays = extractAttr(extinf, "catchup-days")?.toIntOrNull()
            ?: extractAttr(extinf, "catchup_days")?.toIntOrNull()
            ?: extractAttr(extinf, "catchupdays")?.toIntOrNull()
            ?: extractAttr(extinf, "timeshift")?.toIntOrNull()
            ?: extractAttr(extinf, "arc-days")?.toIntOrNull()
            ?: extractAttr(extinf, "arc-time")?.toIntOrNull()
            ?: vlcOpts.firstNotNullOfOrNull {
                extractAttr(it, "catchup-days")?.toIntOrNull()
                    ?: extractAttr(it, "timeshift")?.toIntOrNull()
                    ?: extractAttr(it, "arc-days")?.toIntOrNull()
            }

        // tvg-rec="..." (0 = disabled, 1/true/yes = enabled/default days, >1 = specific days)
        val chTvgRec = parseTvgRec(
            extractAttr(extinf, "tvg-rec")
                ?: extractAttr(extinf, "tvg_rec")
                ?: extractAttr(extinf, "rec")
                ?: vlcOpts.firstNotNullOfOrNull { extractAttr(it, "tvg-rec") ?: extractAttr(it, "rec") },
        )

        // catchup-source="..." (custom template)
        val chCatchupSource = extractAttr(extinf, "catchup-source")
            ?: extractAttr(extinf, "catchup_source")
            ?: vlcOpts.firstNotNullOfOrNull { extractAttr(it, "catchup-source") }

        // ── Check if archive is explicitly disabled on this channel:
        // e.g. tvg-rec="0", catchup-days="0", timeshift="0", catchup="none" / "0" / "false"
        val isExplicitlyDisabled = (chTvgRec != null && chTvgRec <= 0) ||
                (chCatchupDays != null && chCatchupDays <= 0) ||
                (chCatchupAttr != null && isAttrDisabled(chCatchupAttr))

        val finalCatchupType: CatchupType
        val finalCatchupDays: Int
        val finalCatchupSource: String

        if (isExplicitlyDisabled) {
            finalCatchupType = CatchupType.NONE
            finalCatchupDays = 0
            finalCatchupSource = ""
        } else {
            // Effective days: channel attribute takes precedence, then global default
            val effectiveDays = chTvgRec?.takeIf { it > 0 }
                ?: chCatchupDays?.takeIf { it > 0 }
                ?: defaultCatchupDays.takeIf { it > 0 }
                ?: 0

            // Effective source: channel specific, then global default
            val effectiveSource = chCatchupSource ?: defaultCatchupSource ?: ""

            // Effective catchup type attribute: channel specific, then global default
            val effectiveAttr = chCatchupAttr ?: defaultCatchupAttr ?: ""

            // Check if archive is signaled:
            // - catchup="..." attribute present and not disabled
            // - catchup-days / timeshift / tvg-rec > 0
            // - catchup-source="..." template present
            // - streamUrl has explicit archive hints
            val hasArchive = (effectiveAttr.isNotBlank() && !isAttrDisabled(effectiveAttr)) ||
                    effectiveDays > 0 ||
                    effectiveSource.isNotBlank() ||
                    streamUrl.contains("timeshift_abs", ignoreCase = true) ||
                    streamUrl.contains("/timeshift/", ignoreCase = true)

            if (hasArchive) {
                val resolvedType = parseCatchupType(effectiveAttr, effectiveSource, streamUrl)
                if (resolvedType == CatchupType.NONE) {
                    finalCatchupType = CatchupType.NONE
                    finalCatchupDays = 0
                    finalCatchupSource = ""
                } else {
                    finalCatchupType = resolvedType
                    // If days not explicitly specified, default to 7 days
                    finalCatchupDays = if (effectiveDays > 0) effectiveDays else 7
                    finalCatchupSource = effectiveSource
                }
            } else {
                finalCatchupType = CatchupType.NONE
                finalCatchupDays = 0
                finalCatchupSource = ""
            }
        }

        // Track title (after comma) takes priority for clean display; falls back to tvg-name
        val displayName = trackTitle.ifEmpty { tvgName }.ifEmpty { streamUrl.substringAfterLast('/') }

        return M3uChannel(
            epgChannelId = tvgId,
            name         = displayName,
            logoUrl      = tvgLogo,
            groupTitle   = groupTitle,
            streamUrl    = streamUrl,
            catchupType  = finalCatchupType,
            catchupDays  = finalCatchupDays,
            catchupSource = finalCatchupSource,
        )
    }

    // ─── Private: attribute extraction ───────────────────────────────────────

    private fun isAttrDisabled(attr: String): Boolean {
        val lower = attr.trim().lowercase(java.util.Locale.ROOT)
        return lower in setOf("none", "0", "false", "no", "off", "disabled")
    }

    private fun parseTvgRec(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim().lowercase(java.util.Locale.ROOT)
        return when (trimmed) {
            "0", "false", "no", "off", "none", "disabled" -> 0
            "1", "true", "yes", "on", "rec" -> 7
            else -> trimmed.toIntOrNull()?.takeIf { it >= 0 } ?: 7
        }
    }

    /**
     * Extract the value of a named attribute from an EXTINF or EXTM3U line.
     *
     * Handles both quoted (`attr="value"`) and unquoted (`attr=value`) styles.
     *
     * @param line  The raw M3U directive line.
     * @param attr  The attribute name (case-insensitive).
     * @return The attribute value, or null if the attribute is not present.
     */
    private fun extractAttr(line: String, attr: String): String? {
        val pattern = Regex("""(?i)(?:^|[\s,;])${Regex.escape(attr)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^,\s">]+))""")
        val match = pattern.find(line) ?: return null
        return (match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value)
            ?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ─── Private: catchup type resolution ────────────────────────────────────

    private fun parseCatchupType(
        catchupAttr: String,
        catchupSource: String,
        streamUrl: String,
    ): CatchupType {
        // Explicit attribute takes highest precedence
        if (catchupAttr.isNotEmpty()) {
            return when (catchupAttr.lowercase(java.util.Locale.ROOT)) {
                "flussonic", "fs" -> CatchupType.FLUSSONIC
                "default"         -> if (parseXtreamUrl(streamUrl)) CatchupType.XTREAM else CatchupType.SHIFT
                "xc"              -> CatchupType.XTREAM
                "shift", "timeshift" -> CatchupType.SHIFT
                "append"          -> CatchupType.APPEND
                "0", "none", "false", "no", "off", "disabled" -> CatchupType.NONE
                "1", "true", "yes", "archive", "auto" -> if (parseXtreamUrl(streamUrl)) CatchupType.XTREAM else CatchupType.SHIFT
                else              -> CatchupType.AUTO
            }
        }

        // Infer from catchup-source URL pattern
        if (catchupSource.isNotEmpty()) {
            val lowerSource = catchupSource.lowercase(java.util.Locale.ROOT)
            return when {
                lowerSource.contains("timeshift_abs") || lowerSource.contains("flussonic") ->
                    CatchupType.FLUSSONIC
                parseXtreamUrl(catchupSource) || lowerSource.contains("/timeshift/") ->
                    CatchupType.XTREAM
                lowerSource.contains("catchup-back") ->
                    CatchupType.APPEND
                else -> CatchupType.AUTO
            }
        }

        // Infer from stream URL
        val lowerStream = streamUrl.lowercase(java.util.Locale.ROOT)
        return when {
            lowerStream.contains("timeshift_abs") || lowerStream.contains("flussonic") -> CatchupType.FLUSSONIC
            lowerStream.contains("catchup-back") -> CatchupType.APPEND
            lowerStream.contains("/timeshift/") -> CatchupType.XTREAM
            (lowerStream.contains("/live/") || streamUrl.endsWith(".ts")) && parseXtreamUrl(streamUrl) -> CatchupType.XTREAM
            else -> CatchupType.SHIFT
        }
    }

    private fun parseXtreamUrl(url: String): Boolean {
        val regex = Regex("""(https?://[^/]+)(?:/live)?/([^/]+)/([^/]+)/(\d+)(?:\.[a-zA-Z0-9]+)?$""")
        if (!regex.containsMatchIn(url)) return false
        if (!url.contains("/live/") && !url.matches(Regex(""".*/\d+(?:\.[a-zA-Z0-9]+)?$"""))) {
            return false
        }
        return true
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    /** Keywords triggering the adult-content flag on a channel's group/category. */
    val ADULT_KEYWORDS = setOf("18+", "adult", "xxx", "erotic", "porn", "+18", "x-rated")
}
