package com.minogatv.box.core.network

import android.content.Context
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Smart DNS-over-HTTPS (DoH) resolver for bypassing ISP DNS poisoning and spoofing.
 *
 * Queries Cloudflare (1.1.1.1 / 1.0.0.1) and Google (8.8.8.8 / 8.8.4.4) directly by IP
 * over HTTPS (JSON API) so no system UDP port 53 lookup is required to reach the DoH servers.
 * All video traffic still flows 100% directly from the IPTV/VOD CDN to the Android TV device
 * with zero load on Server1.
 */
object SmartDohDns : Dns {

    const val PREF_KEY_DOH_MODE = "dns_doh_mode"
    const val MODE_AUTO = "AUTO"       // Smart DoH (Cloudflare + Google with system fallback)
    const val MODE_ALWAYS = "ALWAYS"   // Strictly prefer DoH for all external domains
    const val MODE_OFF = "OFF"         // Standard system DNS only

    private const val CACHE_TTL_MS = 20 * 60 * 1000L // 20 minutes

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long,
    )

    private val dnsCache = ConcurrentHashMap<String, CacheEntry>()

    @Volatile
    private var appContext: Context? = null

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .writeTimeout(2500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val sharedStreamingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(this)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getCurrentMode(context: Context? = appContext): String {
        val ctx = context ?: appContext ?: return MODE_AUTO
        return try {
            val prefs = ctx.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            prefs.getString(PREF_KEY_DOH_MODE, MODE_AUTO) ?: MODE_AUTO
        } catch (_: Exception) {
            MODE_AUTO
        }
    }

    fun setCurrentMode(context: Context, mode: String) {
        val normalized = when (mode.uppercase()) {
            MODE_ALWAYS -> MODE_ALWAYS
            MODE_OFF -> MODE_OFF
            else -> MODE_AUTO
        }
        dnsCache.clear()
        context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_DOH_MODE, normalized)
            .apply()
    }

    fun getStreamingOkHttpClient(context: Context? = null, userAgent: String? = null): OkHttpClient {
        if (context != null && appContext == null) {
            appContext = context.applicationContext
        }
        val customUa = userAgent?.trim()?.takeIf { it.isNotEmpty() }
        if (customUa == null) {
            return sharedStreamingClient
        }
        return sharedStreamingClient.newBuilder()
            .addInterceptor(UserAgentInterceptor(customUa))
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim().trimEnd('.').lowercase()
        if (cleanHost.isEmpty()) return Dns.SYSTEM.lookup(hostname)

        // Direct IP literals or local LAN hostnames do not need DoH
        if (isIpLiteral(cleanHost) || isLocalHostname(cleanHost)) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val mode = getCurrentMode()
        if (mode == MODE_OFF) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val now = System.currentTimeMillis()
        dnsCache[cleanHost]?.let { cached ->
            if (now < cached.expiresAtMs && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        // Resolve via encrypted DoH (Cloudflare 1.1.1.1 / Google 8.8.8.8) first so ISP DNS spoofing
        // (returning an ISP block page IP or NXDOMAIN) is completely bypassed.
        val dohAddresses = queryDohEndpoints(cleanHost)
        if (dohAddresses.isNotEmpty()) {
            dnsCache[cleanHost] = CacheEntry(
                addresses = dohAddresses,
                expiresAtMs = now + CACHE_TTL_MS,
            )
            return dohAddresses
        }

        // Fallback to system DNS if DoH is unreachable on the local network
        val sysAddresses = Dns.SYSTEM.lookup(hostname).filterNot { isBogusStubAddress(it) }
        if (sysAddresses.isNotEmpty()) {
            dnsCache[cleanHost] = CacheEntry(
                addresses = sysAddresses,
                expiresAtMs = now + 5 * 60 * 1000L,
            )
            return sysAddresses
        }

        return Dns.SYSTEM.lookup(hostname)
    }

    private fun queryDohEndpoints(hostname: String): List<InetAddress> {
        val endpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://8.8.8.8/resolve?name=$hostname&type=1",
            "https://1.0.0.1/dns-query?name=$hostname&type=A",
            "https://8.8.4.4/resolve?name=$hostname&type=1",
        )
        for (url in endpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .header("User-Agent", "MinogaTVBox/1.1.6")
                    .get()
                    .build()
                bootstrapClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val parsed = parseDohJsonResponse(hostname, body)
                        if (parsed.isNotEmpty()) {
                            return parsed
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next DoH provider
            }
        }
        return emptyList()
    }

    private fun parseDohJsonResponse(hostname: String, jsonText: String): List<InetAddress> {
        if (!jsonText.trimStart().startsWith("{")) return emptyList()
        return try {
            val root = JSONObject(jsonText)
            val answers = root.optJSONArray("Answer") ?: return emptyList()
            val result = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val ans = answers.optJSONObject(i) ?: continue
                val type = ans.optInt("type", 0)
                val data = ans.optString("data", "").trim()
                if (type == 1 && data.isNotEmpty()) {
                    val bytes = parseIpv4Bytes(data) ?: continue
                    val addr = InetAddress.getByAddress(hostname, bytes)
                    if (!isBogusStubAddress(addr)) {
                        result.add(addr)
                    }
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseIpv4Bytes(ip: String): ByteArray? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for (i in 0..3) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            out[i] = v.toByte()
        }
        return out
    }

    private fun isBogusStubAddress(addr: InetAddress): Boolean {
        return addr.isLoopbackAddress ||
            addr.isAnyLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            addr.hostAddress == "0.0.0.0" ||
            addr.hostAddress?.startsWith("127.") == true
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.contains(":")) return true // IPv6 literal
        return parseIpv4Bytes(host) != null
    }

    private fun isLocalHostname(host: String): Boolean {
        return host == "localhost" ||
            host.endsWith(".local") ||
            host.endsWith(".lan") ||
            host.endsWith(".home") ||
            !host.contains(".")
    }
}

