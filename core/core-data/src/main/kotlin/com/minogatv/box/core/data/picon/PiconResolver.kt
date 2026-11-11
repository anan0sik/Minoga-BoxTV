package com.minogatv.box.core.data.picon

import android.content.Context
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.model.ChannelUtils
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.network.di.BaseOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * # PiconResolver
 *
 * Automatically resolves and links high-resolution channel logos (picons)
 * for channels that lack a `logoUrl` from the user's M3U playlist.
 *
 * Channel logos are stored locally in internal storage (`context.filesDir/picons/`)
 * so they are never downloaded repeatedly over the network.
 *
 * Sources:
 * 1. Curated high-res CDN database covering popular RU/CIS & international channels.
 * 2. Fallback fuzzy matching against open CDN picon repositories.
 */
@Singleton
class PiconResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    @BaseOkHttpClient private val okHttpClient: OkHttpClient,
) {
    private val piconsDir: File by lazy {
        File(context.filesDir, "picons").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolve a logo URL for a given [channelName].
     * Returns a valid HTTPS URL or null if no matching picon could be identified.
     */
    fun resolveLogo(channelName: String): String? {
        if (channelName.isBlank()) return null

        val norm = ChannelUtils.normalizeChannelName(channelName)
        if (norm.isEmpty()) return null

        // 1. Direct match in curated dictionary
        PICON_MAP[norm]?.let { return it }

        // 2. Exact match on raw lowercased name
        val lowerRaw = channelName.lowercase(Locale.ROOT).trim()
        PICON_MAP[lowerRaw]?.let { return it }

        // 3. Substring / token matching for variants (e.g. "Первый канал HD (+2)")
        for ((key, url) in PICON_MAP) {
            if (key.length >= 3 && (norm == key || norm.startsWith("$key ") || norm.endsWith(" $key") || norm.contains(" $key "))) {
                return url
            }
        }

        return null
    }

    /**
     * Returns local picon file if it exists, or downloads [remoteUrl] and saves it locally.
     * Returns a "file:///..." URI pointing to local disk if successful, or [remoteUrl] on failure.
     */
    suspend fun cachePiconLocally(channelId: Long, remoteUrl: String): String = withContext(Dispatchers.IO) {
        if (remoteUrl.isBlank()) return@withContext remoteUrl
        if (remoteUrl.startsWith("file:")) return@withContext remoteUrl

        val localFile = File(piconsDir, "picon_$channelId.png")
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext "file://${localFile.absolutePath}"
        }

        try {
            val request = Request.Builder().url(remoteUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val tempFile = File(piconsDir, "picon_${channelId}.tmp")
                        tempFile.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            if (localFile.exists()) localFile.delete()
                            tempFile.renameTo(localFile)
                            return@withContext "file://${localFile.absolutePath}"
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Silently fall back to remote URL on network/read error
        }

        return@withContext remoteUrl
    }

    /**
     * Batch resolve and persist logos for all channels in the local database
     * that currently have no `logoUrl` or have legacy broken URLs.
     * Queries the Minoga TV Live EPG server (`/api/tv/epg/live`) first to obtain
     * real XMLTV channel icons, with local fallback.
     *
     * @return Number of channels updated with new logos.
     */
    suspend fun resolveMissingLogos(): Int = withContext(Dispatchers.IO) {
        val allChannels = channelDao.getAll()
        if (allChannels.isEmpty()) return@withContext 0

        val candidates = allChannels.filter { ch ->
            val u = ch.logoUrl?.trim().orEmpty()
            if (u.isEmpty()) {
                true
            } else if (u.contains("vattik/picons-rtrs", ignoreCase = true) ||
                u.contains("it999.github.io", ignoreCase = true)
            ) {
                true
            } else if (u.startsWith("file://")) {
                val f = File(u.removePrefix("file://"))
                !f.exists() || f.length() < 100L
            } else {
                false
            }
        }
        if (candidates.isEmpty()) return@withContext 0

        val updates = mutableMapOf<Long, String>()

        // 1. Fetch pre-indexed channel icons from Minoga TV server (/api/tv/epg/live)
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val rawWebBase = prefs.getString("web_config_url", null)?.trim()?.takeIf { it.isNotBlank() }
            ?: Playlist.DEFAULT_WEB_CONFIG_URL
        val cleanBase = rawWebBase.trimEnd('/')
        val serverBases = listOf(
            "https://minogatvbox.duckdns.org",
            if (cleanBase.startsWith("http://", ignoreCase = true)) "https://" + cleanBase.removePrefix("http://") else cleanBase,
            cleanBase,
        ).distinct()

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        for (base in serverBases) {
            var baseSucceeded = false
            for (chunk in candidates.chunked(200)) {
                val sb = StringBuilder(chunk.size * 96 + 64)
                sb.append("{\"url\":\"http://epg.one/epg2.xml.gz\",\"full\":false,\"channels\":[")
                chunk.forEachIndexed { idx, ch ->
                    if (idx > 0) sb.append(',')
                    sb.append("{\"id\":").append(ch.id)
                        .append(",\"tvgId\":\"").append(escapeJson(ch.epgChannelId)).append("\"")
                        .append(",\"name\":\"").append(escapeJson(ch.name)).append("\"}")
                }
                sb.append("]}")

                val req = Request.Builder()
                    .url("$base/api/tv/epg/live")
                    .post(sb.toString().toRequestBody(jsonMediaType))
                    .header("Accept", "application/json")
                    .build()

                val respStr = try {
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                } catch (_: Throwable) {
                    null
                } ?: break

                if (!respStr.startsWith("{")) break
                val liveObj = try {
                    JSONObject(respStr).optJSONObject("live")
                } catch (_: Throwable) {
                    null
                } ?: break

                baseSucceeded = true
                for (ch in chunk) {
                    val chObj = liveObj.optJSONObject(ch.id.toString()) ?: continue
                    val ic = chObj.optString("ic").trim()
                    if (ic.isNotEmpty()) {
                        updates[ch.id] = ic
                        val staleLocal = File(piconsDir, "picon_${ch.id}.png")
                        if (staleLocal.exists()) staleLocal.delete()
                    }
                }
            }
            if (baseSucceeded) break
        }

        // 2. Clear any remaining legacy dead 404 URLs so UI shows clean TV badge instead of blank space
        for (ch in candidates) {
            if (!updates.containsKey(ch.id)) {
                val u = ch.logoUrl?.trim().orEmpty()
                if (u.contains("vattik/picons-rtrs", ignoreCase = true) ||
                    u.contains("it999.github.io", ignoreCase = true) ||
                    (u.startsWith("file://") && !File(u.removePrefix("file://")).exists())
                ) {
                    updates[ch.id] = ""
                }
            }
        }

        if (updates.isEmpty()) return@withContext 0

        val updateList = updates.entries.map { it.key to it.value }
        for (chunk in updateList.chunked(100)) {
            channelDao.updateLogoUrls(chunk)
            kotlinx.coroutines.yield()
        }
        return@withContext updateList.size
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")

    /**
     * Download and persistently cache all remote channel logos into internal storage (filesDir/picons/),
     * and update the database with local file:// URIs for instant offline display.
     */
    suspend fun cacheAllRemoteLogos(): Int = withContext(Dispatchers.IO) {
        val channelsWithRemote = channelDao.getChannelsWithRemoteLogo()
        if (channelsWithRemote.isEmpty()) return@withContext 0

        val updates = mutableListOf<Pair<Long, String>>()
        for (ch in channelsWithRemote) {
            val remoteUrl = ch.logoUrl ?: continue
            val localUri = cachePiconLocally(ch.id, remoteUrl)
            if (localUri.startsWith("file:")) {
                updates.add(ch.id to localUri)
            }
        }
        if (updates.isNotEmpty()) {
            for (chunk in updates.chunked(100)) {
                channelDao.updateLogoUrls(chunk)
                kotlinx.coroutines.yield()
            }
        }
        return@withContext updates.size
    }


    companion object {
        private const val RTRS_BASE = "https://raw.githubusercontent.com/vattik/picons-rtrs/master/picons"
        private const val IT999_BASE = "https://raw.githubusercontent.com/it999/it999.github.io/master/img"
        private const val IPTV_ORG_BASE = "https://raw.githubusercontent.com/iptv-org/epg/master/logos"

        /**
         * Curated map: normalized channel title -> high-res picon URL.
         */
        val PICON_MAP: Map<String, String> = mapOf(
            // ── Федеральные и общедоступные каналы ──
            "первый канал" to "$RTRS_BASE/1tv.png",
            "первый" to "$RTRS_BASE/1tv.png",
            "1 канал" to "$RTRS_BASE/1tv.png",
            "россия 1" to "$RTRS_BASE/russia1.png",
            "россия1" to "$RTRS_BASE/russia1.png",
            "россия 24" to "$RTRS_BASE/russia24.png",
            "россия24" to "$RTRS_BASE/russia24.png",
            "россия к" to "$RTRS_BASE/russiak.png",
            "россия культура" to "$RTRS_BASE/russiak.png",
            "культура" to "$RTRS_BASE/russiak.png",
            "нтв" to "$RTRS_BASE/ntv.png",
            "тнт" to "$RTRS_BASE/tnt.png",
            "тнт4" to "$RTRS_BASE/tnt4.png",
            "тнт 4" to "$RTRS_BASE/tnt4.png",
            "стс" to "$RTRS_BASE/sts.png",
            "стс love" to "$RTRS_BASE/stslove.png",
            "рен тв" to "$RTRS_BASE/rentv.png",
            "рентв" to "$RTRS_BASE/rentv.png",
            "пятница" to "$RTRS_BASE/friday.png",
            "пятница!" to "$RTRS_BASE/friday.png",
            "пятый канал" to "$RTRS_BASE/5tv.png",
            "5 канал" to "$RTRS_BASE/5tv.png",
            "тв-3" to "$RTRS_BASE/tv3.png",
            "тв3" to "$RTRS_BASE/tv3.png",
            "тв центр" to "$RTRS_BASE/tvc.png",
            "твц" to "$RTRS_BASE/tvc.png",
            "звезда" to "$RTRS_BASE/zvezda.png",
            "отр" to "$RTRS_BASE/otr.png",
            "мир" to "$RTRS_BASE/mir.png",
            "мир 24" to "$RTRS_BASE/mir24.png",
            "че" to "$RTRS_BASE/che.png",
            "че!" to "$RTRS_BASE/che.png",
            "суббота" to "$RTRS_BASE/subbota.png",
            "суббота!" to "$RTRS_BASE/subbota.png",
            "2х2" to "$RTRS_BASE/2x2.png",
            "солнце" to "$RTRS_BASE/solnce.png",
            "ю" to "$RTRS_BASE/u.png",
            "канал ю" to "$RTRS_BASE/u.png",
            "спас" to "$RTRS_BASE/spas.png",

            // ── Спорт ──
            "матч тв" to "$RTRS_BASE/match.png",
            "матч" to "$RTRS_BASE/match.png",
            "матч премьер" to "$IT999_BASE/matchpremier.png",
            "матч футбол 1" to "$IT999_BASE/matchfootball1.png",
            "матч футбол 2" to "$IT999_BASE/matchfootball2.png",
            "матч футбол 3" to "$IT999_BASE/matchfootball3.png",
            "матч боец" to "$IT999_BASE/matchboec.png",
            "матч игра" to "$IT999_BASE/matchigra.png",
            "матч арена" to "$IT999_BASE/matcharena.png",
            "кхл тв" to "$IT999_BASE/khltv.png",
            "кхл prime" to "$IT999_BASE/khlprime.png",
            "кхл" to "$IT999_BASE/khltv.png",
            "eurosport 1" to "$IT999_BASE/eurosport1.png",
            "eurosport 2" to "$IT999_BASE/eurosport2.png",
            "евроспорт 1" to "$IT999_BASE/eurosport1.png",
            "евроспорт 2" to "$IT999_BASE/eurosport2.png",
            "setanta sports" to "$IT999_BASE/setantasports.png",
            "setanta sports 1" to "$IT999_BASE/setantasports1.png",
            "setanta sports 2" to "$IT999_BASE/setantasports2.png",
            "старт" to "$IT999_BASE/starttv.png",
            "старт триумф" to "$IT999_BASE/starttriumph.png",

            // ── Кино и сериалы ──
            "кинопоказ" to "$IT999_BASE/kinopokaz.png",
            "кинопремьера" to "$IT999_BASE/kinopremiera.png",
            "кинохит" to "$IT999_BASE/kinohit.png",
            "киносемья" to "$IT999_BASE/kinosemya.png",
            "киномикс" to "$IT999_BASE/kinomix.png",
            "киносвидание" to "$IT999_BASE/kinosvidanie.png",
            "родное кино" to "$IT999_BASE/rodnoekino.png",
            "мужское кино" to "$IT999_BASE/muzhskoekino.png",
            "индийское кино" to "$IT999_BASE/indiyskoekino.png",
            "дом кино" to "$IT999_BASE/domkino.png",
            "дом кино премиум" to "$IT999_BASE/domkinopremium.png",
            "мосфильм" to "$IT999_BASE/mosfilm.png",
            "золотая коллекция" to "$IT999_BASE/mosfilmzolotayakollekciya.png",
            "мосфильм золотая коллекция" to "$IT999_BASE/mosfilmzolotayakollekciya.png",
            "cinema" to "$IT999_BASE/cinema.png",
            "синема" to "$IT999_BASE/cinema.png",
            "tv1000" to "$IT999_BASE/tv1000.png",
            "tv1000 русское кино" to "$IT999_BASE/tv1000action.png",
            "tv1000 action" to "$IT999_BASE/tv1000action.png",
            "viju tv1000" to "$IT999_BASE/tv1000.png",
            "viju tv1000 русское" to "$IT999_BASE/tv1000action.png",
            "viju tv1000 action" to "$IT999_BASE/tv1000action.png",
            "vip premiere" to "$IT999_BASE/vippremiere.png",
            "vip megahit" to "$IT999_BASE/vipmegahit.png",
            "vip comedy" to "$IT999_BASE/vipcomedy.png",
            "viju premiere" to "$IT999_BASE/vippremiere.png",
            "viju megahit" to "$IT999_BASE/vipmegahit.png",
            "viju comedy" to "$IT999_BASE/vipcomedy.png",
            "amedia 1" to "$IT999_BASE/amedia1.png",
            "amedia 2" to "$IT999_BASE/amedia2.png",
            "amedia premium" to "$IT999_BASE/amediapremium.png",
            "амедиа 1" to "$IT999_BASE/amedia1.png",
            "амедиа 2" to "$IT999_BASE/amedia2.png",
            "амедиа премиум" to "$IT999_BASE/amediapremium.png",
            "fox" to "$IT999_BASE/fox.png",
            "fox life" to "$IT999_BASE/foxlife.png",
            "иллюзион+" to "$IT999_BASE/illusionplus.png",
            "русский иллюзион" to "$IT999_BASE/russkiyillusion.png",
            "еврокино" to "$IT999_BASE/eurokino.png",
            "шокирующее" to "$IT999_BASE/shokiruyuschee.png",
            "комедийное" to "$IT999_BASE/komediynoe.png",

            // ── Познавательные и документальные ──
            "discovery" to "$IT999_BASE/discovery.png",
            "discovery channel" to "$IT999_BASE/discovery.png",
            "animal planet" to "$IT999_BASE/animalplanet.png",
            "national geographic" to "$IT999_BASE/natgeo.png",
            "nat geo" to "$IT999_BASE/natgeo.png",
            "nat geo wild" to "$IT999_BASE/natgeowild.png",
            "viasat history" to "$IT999_BASE/viasathistory.png",
            "viasat explore" to "$IT999_BASE/viasatexplore.png",
            "viasat nature" to "$IT999_BASE/viasatnature.png",
            "viju history" to "$IT999_BASE/viasathistory.png",
            "viju explore" to "$IT999_BASE/viasatexplore.png",
            "viju nature" to "$IT999_BASE/viasatnature.png",
            "моя планета" to "$IT999_BASE/moyaplaneta.png",
            "наука" to "$IT999_BASE/nauka.png",
            "наука 2.0" to "$IT999_BASE/nauka.png",
            "живая планета" to "$IT999_BASE/zhivayaplaneta.png",
            "history" to "$IT999_BASE/history.png",
            "travel channel" to "$IT999_BASE/travelchannel.png",
            "оружие" to "$IT999_BASE/oruzhie.png",
            "охота и рыбалка" to "$IT999_BASE/ohotarybalka.png",
            "диалоги о рыбалке" to "$IT999_BASE/dialogiorybalke.png",
            "т24" to "$IT999_BASE/t24.png",
            "техно 24" to "$IT999_BASE/t24.png",
            "доктор" to "$IT999_BASE/doktor.png",

            // ── Детские ──
            "карусель" to "$RTRS_BASE/karusel.png",
            "мульт" to "$RTRS_BASE/mult.png",
            "мультимузыка" to "$RTRS_BASE/multimusic.png",
            "мульт и музыка" to "$RTRS_BASE/multimusic.png",
            "о!" to "$RTRS_BASE/o.png",
            "о" to "$RTRS_BASE/o.png",
            "ani" to "$IT999_BASE/ani.png",
            "ани" to "$IT999_BASE/ani.png",
            "рыжий" to "$IT999_BASE/ryzhiy.png",
            "в гостях у сказки" to "$IT999_BASE/vskazke.png",
            "nickelodeon" to "$IT999_BASE/nickelodeon.png",
            "nick jr" to "$IT999_BASE/nickjr.png",
            "cartoon network" to "$IT999_BASE/cartoonnetwork.png",
            "tiji" to "$IT999_BASE/tiji.png",
            "gulli girl" to "$IT999_BASE/gulligirl.png",
            "детский мир" to "$IT999_BASE/detskiymir.png",
            "радость моя" to "$IT999_BASE/radostmoya.png",

            // ── Музыка ──
            "муз-тв" to "$RTRS_BASE/muztv.png",
            "муз тв" to "$RTRS_BASE/muztv.png",
            "музтв" to "$RTRS_BASE/muztv.png",
            "ru tv" to "$IT999_BASE/rutv.png",
            "ru.tv" to "$IT999_BASE/rutv.png",
            "ру тв" to "$IT999_BASE/rutv.png",
            "bridge tv" to "$IT999_BASE/bridgetv.png",
            "bridge tv русский хит" to "$IT999_BASE/bridgetvrussianhit.png",
            "bridge tv classic" to "$IT999_BASE/bridgetvclassic.png",
            "bridge tv hits" to "$IT999_BASE/bridgetvhits.png",
            "europa plus tv" to "$IT999_BASE/europaplustv.png",
            "европа плюс тв" to "$IT999_BASE/europaplustv.png",
            "mtv" to "$IT999_BASE/mtv.png",
            "mtv live" to "$IT999_BASE/mtvlive.png",
            "жара" to "$IT999_BASE/zhara.png",
            "жара тв" to "$IT999_BASE/zhara.png",
            "тнт music" to "$IT999_BASE/tntmusic.png",
            "тнт мьюзик" to "$IT999_BASE/tntmusic.png",
            "шансон тв" to "$IT999_BASE/shansontv.png",

            // ── Новости и региональные ──
            "рбк" to "$IT999_BASE/rbk.png",
            "рбк тв" to "$IT999_BASE/rbk.png",
            "известия" to "$IT999_BASE/izvestia.png",
            "москва 24" to "$IT999_BASE/moskva24.png",
            "москва доверие" to "$IT999_BASE/doverie.png",
            "360" to "$IT999_BASE/360.png",
            "360°" to "$IT999_BASE/360.png",
            "санкт-петербург" to "$IT999_BASE/spbtv.png",
            "78" to "$IT999_BASE/78.png",
            "euronews" to "$IT999_BASE/euronews.png",
            "евроньюс" to "$IT999_BASE/euronews.png",
            "rt" to "$IT999_BASE/rt.png",
            "rt рус" to "$IT999_BASE/rtrus.png",
            "cgtn" to "$IT999_BASE/cgtn.png",
        )
    }
}
