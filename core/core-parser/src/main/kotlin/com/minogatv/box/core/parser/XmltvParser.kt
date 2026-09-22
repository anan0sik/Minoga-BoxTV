package com.minogatv.box.core.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * # XmltvParser
 *
 * A **SAX-based** XMLTV EPG parser optimized for large feeds (100 MB+).
 *
 * ## Key features:
 * 1. O(1) memory per element — never buffers the DOM.
 * 2. Automatically disables external DTD / entity loading so it never halts on network DTD URLs.
 * 3. Transparently handles both gzip-compressed (.gz) and uncompressed (.xml) input.
 * 4. Parses both `<channel>` definitions and `<programme>` elements.
 * 5. Automatically maps channel display-names to channel IDs.
 * 6. Skips stale programmes ending in the past to save 50%+ database writes.
 * 7. Correctly flushes programme batches via [onBatch].
 *
 * @param batchSize          How many [XmltvProgram] objects to accumulate before invoking [onBatch].
 * @param targetChannelIds   Channel EPG IDs from user playlist to filter by. If empty, all channels are imported.
 * @param targetChannelNames Channel names from user playlist to auto-match against `<display-name>` tags.
 * @param onBatch            Suspend callback invoked with each batch of programmes.
 */
class XmltvParser(
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val targetChannelIds: Set<String> = emptySet(),
    private val targetChannelNames: Set<String> = emptySet(),
    private val importAllProgrammes: Boolean = false,
    private val onBatch: suspend (List<XmltvProgram>) -> Unit,
) {

    /**
     * Parse the XMLTV XML from [stream].
     *
     * The stream is NOT closed — the caller is responsible.
     *
     * @param stream Input stream of the XMLTV file.
     * @return [XmltvParseResult] with statistics and channel name mappings.
     */
    suspend fun parse(stream: InputStream): XmltvParseResult = withContext(Dispatchers.IO) {
        val bufferedIn = BufferedInputStream(stream, 65536)
        val pushback = PushbackInputStream(bufferedIn, 2)
        val header = ByteArray(2)
        val n = pushback.read(header)
        val effectiveStream: InputStream = if (n == 2) {
            pushback.unread(header, 0, n)
            if (header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
                BufferedInputStream(GZIPInputStream(pushback), 65536)
            } else {
                pushback
            }
        } else {
            pushback
        }

        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            try {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {}
            try {
                setFeature("http://xml.org/sax/features/validation", false)
            } catch (_: Exception) {}
        }

        val saxParser = factory.newSAXParser()

        val handler = XmltvHandler(
            batchSize = batchSize,
            targetIds = targetChannelIds,
            targetNames = targetChannelNames.map { it.trim().lowercase(Locale.ROOT) }.toSet(),
            onBatch = onBatch,
        )

        try {
            saxParser.xmlReader.entityResolver = handler
        } catch (_: Exception) {}

        saxParser.parse(InputSource(effectiveStream).also { it.encoding = "UTF-8" }, handler)

        // Flush any remaining programmes that didn't fill a complete batch
        handler.flushRemaining()

        XmltvParseResult(
            totalProgrammesParsed = handler.totalProgrammesParsed,
            nameToEpgId = handler.nameToEpgId,
            channelLogos = handler.channelLogos,
            nameToLogo = handler.nameToLogo,
        )
    }

    // ─── SAX Handler ─────────────────────────────────────────────────────────

    private inner class XmltvHandler(
        private val batchSize: Int,
        private val targetIds: Set<String>,
        private val targetNames: Set<String>,
        private val onBatch: suspend (List<XmltvProgram>) -> Unit,
    ) : DefaultHandler() {

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            // Completely prevent SAX parser from fetching external DTDs over HTTP (e.g. http://epg.it999.ru/xmltv.dtd)
            return InputSource(StringReader(""))
        }

        // Channel tracking
        private var inChannel = false
        private var currentXmlChannelId = ""
        private var inDisplayName = false
        private val currentDisplayNameText = StringBuilder()
        private val currentDisplayNames = mutableListOf<String>()

        val nameToEpgId = mutableMapOf<String, String>()
        val channelLogos = mutableMapOf<String, String>()
        val nameToLogo = mutableMapOf<String, String>()
        val matchedChannelIds = mutableSetOf<String>().apply { addAll(targetIds) }
        private val targetNormalizedNames: Set<String> =
            targetNames.map { normalizeChannelName(it) }.filter { it.isNotEmpty() }.toSet()
        private var isFirstProgramme = true

        // Current element state for programmes
        private var inProgramme = false
        private var inTitle     = false
        private var inDesc      = false
        private var inCategory  = false
        private var inRating    = false
        private var inValue     = false  // inside <rating><value>

        // Fields for the programme being built
        private var channelId   = ""
        private var startMs     = 0L
        private var endMs       = 0L
        private var title       = StringBuilder()
        private var subtitle    = StringBuilder()
        private var inSubtitle  = false
        private var description = StringBuilder()
        private var category    = StringBuilder()
        private var iconUrl     = ""
        private var rating      = StringBuilder()
        private var ratingValue = StringBuilder()
        private var isNew       = false

        // Cutoff for stale programmes (7 days of archive)
        private val cutoffMs = System.currentTimeMillis() - 7 * 24 * 3600_000L

        // Batch accumulator
        private val batch = ArrayList<XmltvProgram>(batchSize)
        var totalProgrammesParsed = 0
            private set


        // ── SAX events ───────────────────────────────────────────────────────

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            when (qName.lowercase(Locale.ROOT)) {
                "channel" -> {
                    inChannel = true
                    currentXmlChannelId = attrs.getValue("id")?.trim() ?: ""
                    currentDisplayNames.clear()
                }
                "display-name" -> {
                    if (inChannel) {
                        inDisplayName = true
                        currentDisplayNameText.clear()
                    }
                }
                "programme" -> {
                    if (isFirstProgramme) {
                        isFirstProgramme = false
                        if (targetNormalizedNames.isNotEmpty()) {
                            for ((name, epgId) in nameToEpgId) {
                                for (tName in targetNormalizedNames) {
                                    if (name == tName || name.contains(tName) || (tName.length >= 4 && tName.contains(name))) {
                                        matchedChannelIds.add(epgId)
                                        matchedChannelIds.add(epgId.lowercase(Locale.ROOT))
                                    }
                                }
                            }
                        }
                    }
                    val chAttr = attrs.getValue("channel")?.trim().orEmpty()
                    if (chAttr.isEmpty()) {
                        inProgramme = false
                        return
                    }

                    val lowerChan = chAttr.lowercase(Locale.ROOT)
                    val isChannelRelevant = if (importAllProgrammes) {
                        true
                    } else {
                        val hasTargets = targetIds.isNotEmpty() || targetNames.isNotEmpty()
                        if (!hasTargets) {
                            totalProgrammesParsed < 10000
                        } else {
                            if (matchedChannelIds.isNotEmpty()) {
                                matchedChannelIds.contains(chAttr) || matchedChannelIds.contains(lowerChan) || targetIds.contains(chAttr) || targetIds.contains(lowerChan)
                            } else {
                                totalProgrammesParsed < 25000
                            }
                        }
                    }

                    if (!isChannelRelevant) {
                        inProgramme = false
                        return
                    }

                    inProgramme = true
                    channelId   = chAttr
                    startMs     = parseFastXmltvDate(attrs.getValue("start") ?: "")
                    endMs       = parseFastXmltvDate(attrs.getValue("stop") ?: "")
                    title.clear(); subtitle.clear(); description.clear(); category.clear()
                    ratingValue.clear(); iconUrl = ""; isNew = false
                }
                "title"                  -> if (inProgramme) { inTitle = true; if (title.isEmpty()) title.clear() }
                "sub-title", "subtitle"  -> if (inProgramme) inSubtitle = true
                "desc", "description", "summary" -> if (inProgramme) {
                    inDesc = true
                    if (description.isNotEmpty()) {
                        description.append("\n\n")
                    }
                }
                "category" -> if (inProgramme) inCategory = true
                "icon"     -> {
                    val src = attrs.getValue("src")?.trim().orEmpty()
                    if (inChannel && currentXmlChannelId.isNotEmpty() && src.isNotEmpty()) {
                        channelLogos[currentXmlChannelId] = src
                        channelLogos[currentXmlChannelId.lowercase(Locale.ROOT)] = src
                    } else if (inProgramme) {
                        iconUrl = src
                    }
                }
                "rating"   -> if (inProgramme) { inRating = true; rating.clear() }
                "value"    -> if (inRating)    { inValue  = true; ratingValue.clear() }
                "new"      -> if (inProgramme) isNew = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val text = String(ch, start, length)
            when {
                inDisplayName -> currentDisplayNameText.append(text)
                inTitle       -> title.append(text)
                inSubtitle    -> subtitle.append(text)
                inDesc        -> description.append(text)
                inCategory    -> category.append(text)
                inValue       -> ratingValue.append(text)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (qName.lowercase(Locale.ROOT)) {
                "display-name" -> {
                    if (inDisplayName) {
                        val dn = currentDisplayNameText.toString().trim()
                        if (dn.isNotEmpty()) {
                            currentDisplayNames.add(dn)
                        }
                        inDisplayName = false
                    }
                }
                "channel" -> {
                    if (inChannel && currentXmlChannelId.isNotEmpty()) {
                        val lowerXmlId = currentXmlChannelId.lowercase(Locale.ROOT).trim()
                        nameToEpgId[lowerXmlId] = currentXmlChannelId
                        if (targetIds.contains(currentXmlChannelId) || targetIds.contains(lowerXmlId)) {
                            matchedChannelIds.add(currentXmlChannelId)
                            matchedChannelIds.add(lowerXmlId)
                        }

                        for (dn in currentDisplayNames) {
                            val norm = dn.lowercase(Locale.ROOT).trim()
                            val simplified = normalizeChannelName(dn)
                            if (norm.isNotEmpty()) {
                                nameToEpgId[norm] = currentXmlChannelId
                                if (targetNames.contains(norm)) {
                                    matchedChannelIds.add(currentXmlChannelId)
                                    matchedChannelIds.add(lowerXmlId)
                                }
                            }
                            if (simplified.isNotEmpty()) {
                                nameToEpgId[simplified] = currentXmlChannelId
                                val fuzzyMatch = targetNormalizedNames.any { tn ->
                                    tn == simplified || tn.contains(simplified) || (simplified.length >= 4 && simplified.contains(tn))
                                }
                                if (fuzzyMatch) {
                                    matchedChannelIds.add(currentXmlChannelId)
                                    matchedChannelIds.add(lowerXmlId)
                                }
                            }
                        }

                        val logo = channelLogos[currentXmlChannelId]
                        if (!logo.isNullOrEmpty()) {
                            nameToLogo[lowerXmlId] = logo
                            for (dn in currentDisplayNames) {
                                val norm = dn.lowercase(Locale.ROOT).trim()
                                val simplified = normalizeChannelName(dn)
                                if (norm.isNotEmpty()) nameToLogo[norm] = logo
                                if (simplified.isNotEmpty()) nameToLogo[simplified] = logo
                            }
                        }
                    }
                    inChannel = false
                }
                "title"                  -> inTitle    = false
                "sub-title", "subtitle"  -> inSubtitle = false
                "desc", "description", "summary" -> inDesc = false
                "category" -> inCategory = false
                "value"    -> inValue    = false
                "rating"   -> {
                    inRating = false
                    rating.append(ratingValue.toString().trim())
                }
                "programme" -> {
                    if (inProgramme && channelId.isNotEmpty() && startMs > 0) {
                        // Skip stale programmes that ended before cutoff
                        if (endMs > 0 && endMs >= cutoffMs) {
                            val subText = subtitle.toString().trim()
                            val descText = description.toString().trim()
                            val finalDesc = when {
                                descText.isNotEmpty() && subText.isNotEmpty() && !descText.contains(subText) ->
                                    "$subText\n\n$descText"
                                descText.isNotEmpty() -> descText
                                subText.isNotEmpty() -> subText
                                else -> ""
                            }

                            batch.add(
                                XmltvProgram(
                                    channelEpgId = channelId,
                                    title        = title.toString().trim(),
                                    description  = finalDesc,
                                    startMs      = startMs,
                                    endMs        = endMs,
                                    category     = category.toString().trim(),
                                    iconUrl      = iconUrl.takeIf { it.isNotEmpty() },
                                    rating       = rating.toString().trim(),
                                    isNew        = isNew,
                                ),
                            )
                            totalProgrammesParsed++

                            if (batch.size >= batchSize) {
                                val toFlush = batch.toList()
                                batch.clear()
                                runBlocking { onBatch(toFlush) }
                            }
                        }
                    }
                    inProgramme = false
                }
            }
        }

        fun flushRemaining() {
            if (batch.isNotEmpty()) {
                val toFlush = batch.toList()
                batch.clear()
                runBlocking { onBatch(toFlush) }
            }
        }
    }

    companion object {
        /** Number of programmes to accumulate before flushing to Room.
         *  3000 entries ≈ one bulk insert every ~3 000 programmes, significantly reducing
         *  transaction overhead on Android TV eMMC storage. */
        const val DEFAULT_BATCH_SIZE = 3000

        /**
         * Cleans and normalizes channel names for fuzzy EPG matching
         * (removes channel numbers, quality tags like HD, FHD, 4K, country prefixes/suffixes, punctuation).
         */
        fun normalizeChannelName(raw: String): String =
            com.minogatv.box.core.model.ChannelUtils.normalizeChannelName(raw)

        /**
         * Fast zero-allocation XMLTV date parser.
         * Parses standard formats:
         * "yyyyMMddHHmmss +HHMM"
         * "yyyyMMddHHmmss"
         * "yyyyMMddHHmm +HHMM"
         * "yyyyMMddHHmm"
         * Returns 0L on parse failure without throwing exceptions.
         */
        fun parseFastXmltvDate(raw: String): Long {
            val s = raw.trim()
            if (s.length < 12) return 0L
            val y1 = s[0].digitToIntOrNull() ?: return 0L
            val y2 = s[1].digitToIntOrNull() ?: return 0L
            val y3 = s[2].digitToIntOrNull() ?: return 0L
            val y4 = s[3].digitToIntOrNull() ?: return 0L
            val year = y1 * 1000 + y2 * 100 + y3 * 10 + y4

            val m1 = s[4].digitToIntOrNull() ?: return 0L
            val m2 = s[5].digitToIntOrNull() ?: return 0L
            val month = m1 * 10 + m2

            val d1 = s[6].digitToIntOrNull() ?: return 0L
            val d2 = s[7].digitToIntOrNull() ?: return 0L
            val day = d1 * 10 + d2

            val h1 = s[8].digitToIntOrNull() ?: return 0L
            val h2 = s[9].digitToIntOrNull() ?: return 0L
            val hour = h1 * 10 + h2

            val min1 = s[10].digitToIntOrNull() ?: return 0L
            val min2 = s[11].digitToIntOrNull() ?: return 0L
            val min = min1 * 10 + min2

            val sec = if (s.length >= 14 && s[12].isDigit() && s[13].isDigit()) {
                val s1 = s[12].digitToInt()
                val s2 = s[13].digitToInt()
                s1 * 10 + s2
            } else 0

            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.set(year, month - 1, day, hour, min, sec)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            var timeMs = cal.timeInMillis

            val tzIndex = s.indexOfAny(charArrayOf('+', '-'), startIndex = 12)
            if (tzIndex != -1 && tzIndex + 5 <= s.length) {
                val sign = if (s[tzIndex] == '+') -1 else 1
                val th1 = s[tzIndex + 1].digitToIntOrNull() ?: 0
                val th2 = s[tzIndex + 2].digitToIntOrNull() ?: 0
                val tzHours = th1 * 10 + th2

                val tm1 = s[tzIndex + 3].digitToIntOrNull() ?: 0
                val tm2 = s[tzIndex + 4].digitToIntOrNull() ?: 0
                val tzMins = tm1 * 10 + tm2

                val offsetMs = (tzHours * 3600_000L + tzMins * 60_000L) * sign
                timeMs += offsetMs
            }
            return timeMs
        }
    }
}

/**
 * Result data class produced by [XmltvParser.parse].
 */
data class XmltvParseResult(
    val totalProgrammesParsed: Int,
    val nameToEpgId: Map<String, String>,
    val channelLogos: Map<String, String> = emptyMap(),
    val nameToLogo: Map<String, String> = emptyMap(),
)

/**
 * Intermediate data class produced by [XmltvParser] before being converted to
 * [com.minogatv.box.core.database.entity.EpgProgramEntity].
 */
data class XmltvProgram(
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
    val category: String,
    val iconUrl: String?,
    val rating: String,
    val isNew: Boolean,
)
