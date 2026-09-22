package com.minogatv.box.core.network.stalker

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit interface for the **Stalker Portal / Ministra** JSON API.
 *
 * ## Authentication flow
 * 1. Call [handshake] → receive a `token`
 * 2. Use that token as the `Authorization: Bearer <token>` header in all subsequent calls
 * 3. Call [getProfile] to verify the MAC address is authorised
 * 4. Call [getGenres] + [getChannels] to fetch the channel list
 * 5. Before playing, call [createLink] to resolve the actual stream URL
 *
 * ## Base URL
 * `http://<portal_host>/portal.php` — configured per-playlist in [PlaylistEntity.url].
 *
 * ## MAC address
 * The portal identifies the subscriber by MAC address, sent via the
 * `Cookie: mac=<MAC>` header. Some portals also require `stb_lang` and
 * `timezone` cookies.
 */
interface StalkerPortalService {

    /**
     * Step 1 — Handshake.
     * Returns a session token needed for all subsequent requests.
     *
     * @param mac         Device MAC address (format `AA:BB:CC:DD:EE:FF`).
     * @param type        Always `"stb"` for set-top-box mode.
     * @param token       Send empty string on first call; use the received token after.
     */
    @GET(".")
    suspend fun handshake(
        @Header("Cookie") mac: String,
        @Query("action") action: String = "handshake",
        @Query("type") type: String = "stb",
        @Query("token") token: String = "",
    ): StalkerHandshakeResponse

    /**
     * Step 3 — Get account profile to verify authorisation.
     *
     * @param authorization  `"Bearer <token>"` from [handshake].
     * @param mac            MAC address cookie.
     */
    @GET(".")
    suspend fun getProfile(
        @Header("Authorization") authorization: String,
        @Header("Cookie") mac: String,
        @Query("action") action: String = "get_profile",
        @Query("type") type: String = "stb",
    ): StalkerProfileResponse

    /**
     * Fetch the list of channel genre categories.
     */
    @GET(".")
    suspend fun getGenres(
        @Header("Authorization") authorization: String,
        @Header("Cookie") mac: String,
        @Query("action") action: String = "get_genres",
        @Query("type") type: String = "itv",
    ): StalkerGenreListResponse

    /**
     * Fetch a page of channels.
     *
     * @param genreId  Filter by genre ID, or `"*"` for all genres.
     * @param p        Page number (1-based).
     */
    @GET(".")
    suspend fun getChannels(
        @Header("Authorization") authorization: String,
        @Header("Cookie") mac: String,
        @Query("action") action: String = "get_ordered_list",
        @Query("type") type: String = "itv",
        @Query("genre") genreId: String = "*",
        @Query("force_ch_link_check") forceCheck: Int = 0,
        @Query("fav") fav: Int = 0,
        @Query("sortby") sortBy: String = "number",
        @Query("p") page: Int = 1,
    ): StalkerChannelListResponse

    /**
     * Resolve the actual playback URL for a channel.
     * The [StalkerChannel.cmd] field contains a template like `ffmpeg http://...`
     * that must be resolved via this endpoint before playing.
     *
     * @param cmd  The raw `cmd` value from [StalkerChannel.cmd].
     */
    @GET(".")
    suspend fun createLink(
        @Header("Authorization") authorization: String,
        @Header("Cookie") mac: String,
        @Query("action") action: String = "create_link",
        @Query("type") type: String = "itv",
        @Query("cmd") cmd: String,
        @Query("series") series: Int = 0,
        @Query("forced_storage") forcedStorage: String = "undefined",
        @Query("disable_ad") disableAd: Int = 0,
        @Query("download") download: Int = 0,
        @Query("JsHttpRequest") jsHttpRequest: String = "1-xml",
    ): StalkerCreateLinkResponse
}
