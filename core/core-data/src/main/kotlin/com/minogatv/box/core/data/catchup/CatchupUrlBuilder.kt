package com.minogatv.box.core.data.catchup

import com.minogatv.box.core.model.Channel
import com.minogatv.box.core.model.enums.CatchupType
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * # CatchupUrlBuilder
 *
 * Generates time-shift / catch-up archive URLs based on the channel's [CatchupType].
 *
 * Supports five industry-standard protocols:
 *
 * | Type       | URL pattern |
 * |------------|-------------|
 * | FLUSSONIC  | `{base}/{channel}/timeshift_abs/{utcStart}/{duration}/index.m3u8` |
 * | XTREAM     | `{server}/timeshift/{user}/{pass}/{duration}/{start}/{streamId}.ts` |
 * | SHIFT      | `{streamUrl}?utc={utcStart}&lutc={utcNow}` |
 * | APPEND     | `{streamUrl}?catchup-back={offsetSeconds}` |
 * | AUTO       | Attempts detection based on URL patterns, then falls back to SHIFT |
 *
 * Thread-safe, stateless.
 */
object CatchupUrlBuilder {

    /**
     * Build the catch-up playback URL for the given channel at the specified time.
     *
     * @param channel         The channel to build the catch-up URL for.
     * @param programStartMs  Exact playback/seek timestamp (epoch ms) to start stream from.
     * @param programEndMs    End time of the EPG programme (epoch ms).
     * @param nowMs           Current time (epoch ms). Default: System.currentTimeMillis().
     * @param xtreamUser      Xtream Codes username (from Playlist). Optional if in stream URL.
     * @param xtreamPass      Xtream Codes password (from Playlist). Optional if in stream URL.
     * @return                The catch-up URL, or null if catch-up is not available.
     */
    fun buildUrl(
        channel: Channel,
        programStartMs: Long,
        programEndMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        xtreamUser: String? = null,
        xtreamPass: String? = null,
        overrideCatchupType: CatchupType? = null,
    ): String? {
        val streamUrl = channel.streamUrl
        if (streamUrl.isBlank()) return null

        val utcStartSec = programStartMs / 1000
        val utcEndSec = (programEndMs / 1000).coerceAtLeast(utcStartSec + 1)
        val durationSec = (utcEndSec - utcStartSec).coerceAtLeast(1)
        val utcNowSec = nowMs / 1000
        val offsetSec = (utcNowSec - utcStartSec).coerceAtLeast(0)

        // If an explicit override is NOT forced, use catchupSource template if provided
        val isForced = overrideCatchupType != null && overrideCatchupType != CatchupType.AUTO
        val template = channel.catchupSource
        if (!isForced && !template.isNullOrBlank()) {
            return substituteTemplate(template, streamUrl, programStartMs, utcStartSec, utcEndSec, durationSec, utcNowSec, offsetSec)
        }

        val type = when {
            isForced -> overrideCatchupType!!
            channel.catchupType == CatchupType.AUTO || channel.catchupType == CatchupType.NONE -> detectCatchupType(streamUrl)
            else -> channel.catchupType
        }

        return when (type) {
            CatchupType.FLUSSONIC -> buildFlussonicUrl(streamUrl, utcStartSec, durationSec)
            CatchupType.XTREAM -> buildXtreamUrl(streamUrl, utcStartSec, durationSec, xtreamUser, xtreamPass)
            CatchupType.SHIFT -> buildShiftUrl(streamUrl, utcStartSec, utcNowSec)
            CatchupType.APPEND -> buildAppendUrl(streamUrl, offsetSec)
            CatchupType.AUTO, CatchupType.NONE -> buildShiftUrl(streamUrl, utcStartSec, utcNowSec)
        }
    }

    /**
     * Clean old timeshift parameters from a stream URL so seeking doesn't duplicate query parameters.
     */
    fun cleanStreamUrl(url: String): String {
        if (!url.contains("?")) return url
        val base = url.substringBefore("?")
        val query = url.substringAfter("?")
        val filtered = query.split("&").filter { param ->
            val key = param.substringBefore("=").lowercase(Locale.ROOT)
            key !in setOf("utc", "lutc", "catchup-back", "offset")
        }
        return if (filtered.isEmpty()) base else "$base?${filtered.joinToString("&")}"
    }

    /**
     * Substitute common template variables used in M3U catch-up source attributes.
     */
    private fun substituteTemplate(
        template: String,
        streamUrl: String,
        targetTimestampMs: Long,
        utcStartSec: Long,
        utcEndSec: Long,
        durationSec: Long,
        utcNowSec: Long,
        offsetSec: Long,
    ): String {
        val clean = cleanStreamUrl(streamUrl)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = targetTimestampMs }
        val year = String.format(Locale.US, "%04d", cal.get(Calendar.YEAR))
        val month = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        val day = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
        val hour = String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY))
        val minute = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val second = String.format(Locale.US, "%02d", cal.get(Calendar.SECOND))

        val substituted = template
            .replace("{utc}", utcStartSec.toString())
            .replace("\${utc}", utcStartSec.toString())
            .replace("{start}", utcStartSec.toString())
            .replace("\${start}", utcStartSec.toString())
            .replace("{start_time}", utcStartSec.toString())
            .replace("\${start_time}", utcStartSec.toString())
            .replace("{utcend}", utcEndSec.toString())
            .replace("\${utcend}", utcEndSec.toString())
            .replace("{end}", utcEndSec.toString())
            .replace("\${end}", utcEndSec.toString())
            .replace("{end_time}", utcEndSec.toString())
            .replace("\${end_time}", utcEndSec.toString())
            .replace("{duration}", durationSec.toString())
            .replace("\${duration}", durationSec.toString())
            .replace("{lutc}", utcNowSec.toString())
            .replace("\${lutc}", utcNowSec.toString())
            .replace("{now}", utcNowSec.toString())
            .replace("\${now}", utcNowSec.toString())
            .replace("{timestamp}", utcNowSec.toString())
            .replace("\${timestamp}", utcNowSec.toString())
            .replace("{offset}", offsetSec.toString())
            .replace("\${offset}", offsetSec.toString())
            .replace("{catchup-back}", offsetSec.toString())
            .replace("\${catchup-back}", offsetSec.toString())
            .replace("{url}", clean)
            .replace("\${url}", clean)
            .replace("{Y}", year)
            .replace("{m}", month)
            .replace("{d}", day)
            .replace("{H}", hour)
            .replace("{M}", minute)
            .replace("{S}", second)

        return when {
            substituted.startsWith("http://") || substituted.startsWith("https://") -> substituted
            substituted.startsWith("?") -> {
                val sep = if (clean.contains("?")) "&" else "?"
                "${clean}${sep}${substituted.removePrefix("?")}"
            }
            substituted.startsWith("&") -> {
                val sep = if (clean.contains("?")) "&" else "?"
                "${clean}${sep}${substituted.removePrefix("&")}"
            }
            substituted.startsWith("/") -> {
                try {
                    val uri = URI(clean)
                    "${uri.scheme}://${uri.authority}$substituted"
                } catch (_: Exception) {
                    "${clean.substringBeforeLast("/")}$substituted"
                }
            }
            substituted.contains("=") && !substituted.contains("/") -> {
                val sep = if (clean.contains("?")) "&" else "?"
                "${clean}${sep}${substituted}"
            }
            else -> {
                val base = clean.substringBeforeLast("/")
                "$base/$substituted"
            }
        }
    }

    /**
     * Flussonic-style timeshift URL.
     * Pattern: `http://server/channel/timeshift_abs/{utcStart}/{duration}/index.m3u8`
     */
    private fun buildFlussonicUrl(streamUrl: String, utcStartSec: Long, durationSec: Long): String {
        return try {
            val uri = URI(streamUrl)
            val path = uri.path.trim('/')
            val cleanPath = path.replace(Regex("""timeshift_abs(?:/\d+/\d+|-\d+-\d+)/?"""), "").trim('/')
            val channelPath = if (cleanPath.endsWith(".m3u8", ignoreCase = true)) {
                cleanPath.substringBeforeLast('/')
            } else {
                cleanPath
            }.trim('/')
            val base = "${uri.scheme}://${uri.authority}"
            val query = if (uri.query.isNullOrEmpty()) "" else "?${uri.query}"
            "$base/$channelPath/timeshift_abs/$utcStartSec/$durationSec/index.m3u8$query"
        } catch (_: Exception) {
            val clean = cleanStreamUrl(streamUrl)
            val separator = if (clean.contains("?")) "&" else "?"
            "${clean}${separator}utc=$utcStartSec&lutc=${utcStartSec + durationSec}"
        }
    }

    /**
     * Xtream Codes timeshift URL.
     * Pattern: `http://server/timeshift/{user}/{pass}/{duration}/{utcStart}/{streamId}.ts`
     */
    private fun buildXtreamUrl(
        streamUrl: String,
        utcStartSec: Long,
        durationSec: Long,
        xtreamUser: String?,
        xtreamPass: String?,
    ): String {
        val durationMin = (durationSec / 60).coerceAtLeast(1)
        val parsed = parseXtreamUrl(streamUrl)
        val user = xtreamUser.takeUnless { it.isNullOrBlank() } ?: parsed?.username
        val pass = xtreamPass.takeUnless { it.isNullOrBlank() } ?: parsed?.password
        val server = parsed?.server ?: try {
            val u = URI(streamUrl)
            "${u.scheme}://${u.authority}"
        } catch (_: Exception) { "" }
        val streamId = parsed?.streamId

        if (user.isNullOrBlank() || pass.isNullOrBlank() || streamId.isNullOrBlank() || server.isBlank()) {
            return buildShiftUrl(streamUrl, utcStartSec, System.currentTimeMillis() / 1000)
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(utcStartSec * 1000L))

        return "$server/timeshift/$user/$pass/$durationMin/$dateStr/$streamId.ts"
    }

    private data class XtreamUrlComponents(
        val server: String,
        val username: String,
        val password: String,
        val streamId: String,
    )

    private fun parseXtreamUrl(url: String): XtreamUrlComponents? {
        return try {
            val regex = Regex("""(https?://[^/]+)(?:/live)?/([^/]+)/([^/]+)/(\d+)(?:\.[a-zA-Z0-9]+)?(?:\?.*)?$""")
            val match = regex.find(url) ?: return null
            val (server, user, pass, streamId) = match.destructured
            XtreamUrlComponents(server, user, pass, streamId)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * SHIFT-style timeshift URL (most common).
     * Appends `?utc={startSec}&lutc={nowSec}` query parameters.
     */
    private fun buildShiftUrl(streamUrl: String, utcStartSec: Long, utcNowSec: Long): String {
        val clean = cleanStreamUrl(streamUrl)
        val separator = if (clean.contains("?")) "&" else "?"
        return "${clean}${separator}utc=$utcStartSec&lutc=$utcNowSec"
    }

    /**
     * APPEND-style timeshift URL.
     * Appends `?catchup-back={offsetSeconds}` query parameter.
     */
    private fun buildAppendUrl(streamUrl: String, offsetSec: Long): String {
        val clean = cleanStreamUrl(streamUrl)
        val separator = if (clean.contains("?")) "&" else "?"
        return "${clean}${separator}catchup-back=$offsetSec"
    }

    /**
     * Attempt to auto-detect the catch-up type from URL patterns.
     */
    private fun detectCatchupType(streamUrl: String): CatchupType {
        val lower = streamUrl.lowercase(Locale.ROOT)
        return when {
            lower.contains("timeshift_abs") || lower.contains("flussonic") -> CatchupType.FLUSSONIC
            lower.contains("catchup-back") -> CatchupType.APPEND
            lower.contains("/timeshift/") -> CatchupType.XTREAM
            (lower.contains("/live/") || streamUrl.endsWith(".ts")) && parseXtreamUrl(streamUrl) != null -> CatchupType.XTREAM
            else -> CatchupType.SHIFT // Default fallback for most IPTV providers
        }
    }
}

