package com.minogatv.box.core.network.xtream

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Moshi models for the **Xtream Codes** API.
 *
 * Xtream Codes is the most common IPTV panel API. It exposes:
 *  - Account info + server info
 *  - Live streams, VOD streams, Series
 *  - EPG data (JSON format)
 *  - Stream categories
 */

// ─── Server / account info ────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class XtreamUserInfoResponse(
    @Json(name = "user_info")   val userInfo: XtreamUserInfo,
    @Json(name = "server_info") val serverInfo: XtreamServerInfo,
)

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    @Json(name = "username")       val username: String = "",
    @Json(name = "password")       val password: String = "",
    @Json(name = "message")        val message: String = "",
    @Json(name = "auth")           val auth: Int = 0,        // 0 = error, 1 = ok
    @Json(name = "status")         val status: String = "",  // "Active", "Expired", etc.
    @Json(name = "exp_date")       val expDate: String? = null,
    @Json(name = "is_trial")       val isTrial: String = "0",
    @Json(name = "active_cons")    val activeCons: String = "0",
    @Json(name = "created_at")     val createdAt: String = "",
    @Json(name = "max_connections") val maxConnections: String = "1",
    @Json(name = "allowed_output_formats") val allowedFormats: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class XtreamServerInfo(
    @Json(name = "url")            val url: String = "",
    @Json(name = "port")           val port: String = "",
    @Json(name = "https_port")     val httpsPort: String = "",
    @Json(name = "server_protocol") val serverProtocol: String = "http",
    @Json(name = "rtmp_port")      val rtmpPort: String = "",
    @Json(name = "timezone")       val timezone: String = "",
    @Json(name = "timestamp_now")  val timestampNow: Long = 0,
    @Json(name = "time_now")       val timeNow: String = "",
)

// ─── Categories ───────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id")   val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id")     val parentId: Int = 0,
)

// ─── Live streams ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class XtreamLiveStream(
    @Json(name = "num")             val num: Int = 0,
    @Json(name = "name")            val name: String,
    @Json(name = "stream_type")     val streamType: String = "live",
    @Json(name = "stream_id")       val streamId: Long,
    @Json(name = "stream_icon")     val streamIcon: String = "",
    @Json(name = "epg_channel_id")  val epgChannelId: String = "",
    @Json(name = "added")           val added: String = "",
    @Json(name = "category_id")     val categoryId: String = "",
    @Json(name = "custom_sid")      val customSid: String = "",
    @Json(name = "tv_archive")      val tvArchive: Int = 0,         // 1 = has archive
    @Json(name = "direct_source")   val directSource: String = "",
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int = 0,
)

// ─── VOD streams ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class XtreamVodStream(
    @Json(name = "num")             val num: Int = 0,
    @Json(name = "name")            val name: String,
    @Json(name = "stream_type")     val streamType: String = "movie",
    @Json(name = "stream_id")       val streamId: Long,
    @Json(name = "stream_icon")     val streamIcon: String = "",
    @Json(name = "rating")          val rating: String = "",
    @Json(name = "rating_5based")   val rating5based: Float = 0f,
    @Json(name = "added")           val added: String = "",
    @Json(name = "category_id")     val categoryId: String = "",
    @Json(name = "container_extension") val containerExtension: String = "mkv",
    @Json(name = "custom_sid")      val customSid: String = "",
    @Json(name = "direct_source")   val directSource: String = "",
)

// ─── EPG listing ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class XtreamEpgResponse(
    @Json(name = "epg_listings") val epgListings: List<XtreamEpgEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class XtreamEpgEntry(
    @Json(name = "id")               val id: String = "",
    @Json(name = "epg_id")           val epgId: String = "",
    @Json(name = "title")            val titleBase64: String = "",  // Base64 encoded
    @Json(name = "lang")             val lang: String = "",
    @Json(name = "start")            val start: String = "",        // "YYYY-MM-DD HH:MM:SS"
    @Json(name = "end")              val end: String = "",
    @Json(name = "description")      val descriptionBase64: String = "", // Base64 encoded
    @Json(name = "channel_id")       val channelId: String = "",
    @Json(name = "start_timestamp")  val startTimestamp: Long = 0,
    @Json(name = "stop_timestamp")   val stopTimestamp: Long = 0,
)
