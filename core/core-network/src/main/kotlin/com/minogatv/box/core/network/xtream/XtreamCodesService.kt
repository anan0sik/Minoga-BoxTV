package com.minogatv.box.core.network.xtream

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the **Xtream Codes** panel API.
 *
 * ## Base URL
 * `http://<server>:<port>/player_api.php` — configured per-playlist.
 *
 * ## Authentication
 * All requests pass `username` and `password` as query parameters.
 * There is no session/cookie mechanism — credentials are sent on every call.
 *
 * ## Stream URL format
 * After fetching stream IDs, construct playback URLs as:
 * - Live:  `{server}/{username}/{password}/{stream_id}.{ext}`
 * - VOD:   `{server}/movie/{username}/{password}/{stream_id}.{container_extension}`
 * - TS:    same as Live with `.ts` extension
 *
 * ## Catch-up / Timeshift
 * Xtream archive URLs follow:
 * `{server}/timeshift/{username}/{password}/{duration}/{YYYY-MM-DD:HH-MM}/{stream_id}.ts`
 */
interface XtreamCodesService {

    /**
     * Authenticate and fetch server + account info.
     * Call this first to validate credentials and get server timezone, timestamps, etc.
     *
     * A successful response has `user_info.auth == 1`.
     */
    @GET(".")
    suspend fun getUserInfo(
        @Query("username") username: String,
        @Query("password") password: String,
    ): XtreamUserInfoResponse

    /** Fetch all live stream categories. */
    @GET(".")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<XtreamCategory>

    /**
     * Fetch all live streams (optionally filtered by category).
     *
     * @param categoryId  Filter by category. Omit to get all streams.
     */
    @GET(".")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null,
    ): List<XtreamLiveStream>

    /** Fetch all VOD (movie) categories. */
    @GET(".")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<XtreamCategory>

    /** Fetch all VOD streams (movies). */
    @GET(".")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null,
    ): List<XtreamVodStream>

    /**
     * Fetch short EPG for a live stream (typically last 24 hours + next 24 hours).
     *
     * @param streamId  The [XtreamLiveStream.streamId].
     * @param limit     Max number of EPG entries to return.
     */
    @GET(".")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: Long,
        @Query("limit") limit: Int = 48,
    ): XtreamEpgResponse

    /**
     * Fetch the full EPG listing for a stream.
     * Response can be large for channels with long archive windows.
     */
    @GET(".")
    suspend fun getEpgForStream(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_simple_data_table",
        @Query("stream_id") streamId: Long,
    ): XtreamEpgResponse
}
