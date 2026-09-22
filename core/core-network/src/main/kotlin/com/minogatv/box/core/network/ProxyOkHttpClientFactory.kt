package com.minogatv.box.core.network

import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.enums.ProxyType
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory that creates a fully configured [OkHttpClient] for a specific [Playlist].
 *
 * Each playlist can have its own:
 * - Custom `User-Agent` header (via [UserAgentInterceptor])
 * - HTTP or SOCKS5 proxy (with optional authentication)
 * - Separate connect/read/write timeouts
 *
 * Clients are **not** cached here — caching is done at the call site via a
 * `@Qualifier`-scoped Hilt binding or an LRU map keyed by `playlistId`.
 *
 * ## SOCKS5 proxy notes
 * OkHttp uses `java.net.Proxy` for SOCKS5. DNS resolution occurs through the
 * proxy (SOCKS5 remote DNS) when the host is specified as a hostname (not an IP).
 * This naturally bypasses ISP DNS blocks.
 */
@Singleton
class ProxyOkHttpClientFactory @Inject constructor() {

    /**
     * Build an [OkHttpClient] configured for the given [playlist].
     *
     * @param playlist     The playlist whose network settings should be applied.
     * @param enableLogging Set to true only in debug builds.
     */
    fun create(playlist: Playlist, enableLogging: Boolean = false): OkHttpClient {
        return OkHttpClient.Builder().apply {
            // ── Timeouts & Redirects ──────────────────────────────────────────
            connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            followRedirects(true)
            followSslRedirects(true)

            // ── User-Agent ────────────────────────────────────────────────────
            addInterceptor(UserAgentInterceptor(playlist.userAgent))

            // ── Proxy ─────────────────────────────────────────────────────────
            when (playlist.proxyType) {
                ProxyType.NONE -> { /* direct connection */ }

                ProxyType.HTTP -> {
                    val host = playlist.proxyHost ?: return@apply
                    val port = playlist.proxyPort ?: return@apply
                    proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
                    applyProxyAuth(playlist)
                }

                ProxyType.SOCKS5 -> {
                    val host = playlist.proxyHost ?: return@apply
                    val port = playlist.proxyPort ?: return@apply
                    // SOCKS5: Java automatically routes DNS through the proxy
                    proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port)))
                    applyProxyAuth(playlist)
                }
            }

            // ── Logging (debug only) ──────────────────────────────────────────
            if (enableLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    },
                )
            }
        }.build()
    }

    /**
     * Attach proxy Basic-auth credentials when the playlist has them configured.
     */
    private fun OkHttpClient.Builder.applyProxyAuth(playlist: Playlist) {
        val username = playlist.proxyUsername ?: return
        val password = playlist.proxyPassword ?: return
        proxyAuthenticator { _, response ->
            val credential = Credentials.basic(username, password)
            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SEC = 15L
        private const val READ_TIMEOUT_SEC    = 30L
        private const val WRITE_TIMEOUT_SEC   = 30L
    }
}
