package com.minogatv.box.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that injects a custom `User-Agent` header on every request.
 *
 * IPTV providers often rate-limit or block requests with default OkHttp/Retrofit
 * user-agents. This interceptor replaces the user-agent with the one configured
 * per-playlist (stored in [com.minogatv.box.core.model.Playlist.userAgent]).
 *
 * Usage: instantiate with the desired user-agent string and pass to
 * [okhttp3.OkHttpClient.Builder.addInterceptor].
 *
 * @param userAgent  The value to set for the `User-Agent` header.
 *                   Example: `"MinogaTVBox/1.0 (Android; TV)"` or
 *                   `"Dalvik/2.1.0 (Linux; U; Android 9; Mi Box 4K)"`.
 */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val modifiedRequest = chain.request()
            .newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(modifiedRequest)
    }
}
