package com.minogatv.box.core.network.stalker

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Moshi models for the Stalker Portal (Ministra) JSON API.
 *
 * The Stalker API returns a generic envelope:
 * ```json
 * { "js": { ... actual data ... } }
 * ```
 *
 * All request params are sent as query parameters on GET requests.
 */

// ─── Handshake ────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StalkerHandshakeResponse(
    @Json(name = "js") val js: StalkerToken,
)

@JsonClass(generateAdapter = true)
data class StalkerToken(
    @Json(name = "token") val token: String,
)

// ─── Profile / Account info ───────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StalkerProfileResponse(
    @Json(name = "js") val js: StalkerProfile,
)

@JsonClass(generateAdapter = true)
data class StalkerProfile(
    @Json(name = "id")              val id: String = "",
    @Json(name = "name")            val name: String = "",
    @Json(name = "fname")           val fname: String = "",
    @Json(name = "phone")           val phone: String = "",
    @Json(name = "status")          val status: String = "",
    @Json(name = "tariff_expired_date") val tariffExpiredDate: String = "",
)

// ─── Channel list ─────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StalkerChannelListResponse(
    @Json(name = "js") val js: StalkerChannelListData,
)

@JsonClass(generateAdapter = true)
data class StalkerChannelListData(
    @Json(name = "data")  val data: List<StalkerChannel> = emptyList(),
    @Json(name = "total_items") val totalItems: Int = 0,
)

@JsonClass(generateAdapter = true)
data class StalkerChannel(
    @Json(name = "id")          val id: String,
    @Json(name = "name")        val name: String,
    @Json(name = "number")      val number: Int = 0,
    @Json(name = "tv_genre_id") val genreId: String = "",
    @Json(name = "logo")        val logo: String = "",
    @Json(name = "cmd")         val cmd: String,          // stream URL template
    @Json(name = "xmltv_id")    val xmltvId: String = "", // EPG channel ID
    @Json(name = "use_http_tmp_link") val useHttpTmpLink: Int = 0,
    @Json(name = "archive")     val archive: Int = 0,     // 1 = has archive
    @Json(name = "archive_days") val archiveDays: Int = 0,
)

// ─── Stream URL creation ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StalkerCreateLinkResponse(
    @Json(name = "js") val js: StalkerStreamLink,
)

@JsonClass(generateAdapter = true)
data class StalkerStreamLink(
    @Json(name = "cmd") val cmd: String,   // resolved HLS/TS URL
)

// ─── Genre list ───────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class StalkerGenreListResponse(
    @Json(name = "js") val js: List<StalkerGenre>,
)

@JsonClass(generateAdapter = true)
data class StalkerGenre(
    @Json(name = "id")   val id: String,
    @Json(name = "title") val title: String,
)
