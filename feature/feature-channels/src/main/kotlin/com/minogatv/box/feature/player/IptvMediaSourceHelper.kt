package com.minogatv.box.feature.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Helper object providing robust IPTV media source creation, URL sanitization,
 * MIME-type detection, TS extractor flags, and human-readable error translation.
 */
@OptIn(UnstableApi::class)
object IptvMediaSourceHelper {

    /** Default fallback user agent accepted across IPTV servers and CDNs. */
    const val DEFAULT_IPTV_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Cleans raw stream URL:
     * - Removes leading/trailing whitespace and control characters.
     * - Strips IPTV pipe-separated parameters (`url|User-Agent=...&Referer=...`).
     */
    fun cleanStreamUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        val pipeIndex = trimmed.indexOf('|')
        return if (pipeIndex >= 0) {
            trimmed.substring(0, pipeIndex).trim()
        } else {
            trimmed
        }
    }

    /**
     * Extracts headers passed via IPTV pipe syntax, e.g.:
     * `http://host/stream.m3u8|User-Agent=CustomUA&Referer=http://host/`
     */
    fun extractHeaders(rawUrl: String): Map<String, String> {
        val pipeIndex = rawUrl.indexOf('|')
        if (pipeIndex < 0) return emptyMap()

        val paramsPart = rawUrl.substring(pipeIndex + 1).trim()
        if (paramsPart.isEmpty()) return emptyMap()

        val headers = mutableMapOf<String, String>()
        val pairs = paramsPart.split('&')
        for (pair in pairs) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    headers[key] = value
                }
            }
        }
        return headers
    }

    /**
     * Automatically detects container / streaming protocol MIME type from URL.
     * Crucial for IPTV servers returning `application/octet-stream` or `text/plain`.
     * If the type cannot be safely determined from the extension, returns null to let
     * ExoPlayer sniff the Content-Type header dynamically from the HTTP response.
     */
    fun detectMimeType(url: String): String? {
        val clean = cleanStreamUrl(url).lowercase()
        val path = try {
            Uri.parse(clean).path?.lowercase() ?: clean
        } catch (_: Exception) {
            clean
        }

        return when {
            path.endsWith(".m3u8") || clean.contains(".m3u8") || clean.contains("format=m3u8") -> {
                MimeTypes.APPLICATION_M3U8
            }
            path.endsWith(".mpd") || clean.contains(".mpd") -> {
                MimeTypes.APPLICATION_MPD
            }
            path.endsWith(".ts") || clean.contains(".ts?") -> {
                MimeTypes.VIDEO_MP2T
            }
            path.endsWith(".mp4") || clean.contains(".mp4?") -> {
                MimeTypes.VIDEO_MP4
            }
            path.endsWith(".mkv") || clean.contains(".mkv?") -> {
                MimeTypes.VIDEO_MATROSKA
            }
            path.endsWith(".mp3") || clean.contains(".mp3?") -> {
                MimeTypes.AUDIO_MPEG
            }
            path.endsWith(".aac") || clean.contains(".aac?") -> {
                MimeTypes.AUDIO_AAC
            }
            else -> null
        }
    }

    /**
     * Builds a [MediaItem] with proper URI and explicit MIME type if detected.
     */
    fun buildMediaItem(rawUrl: String): MediaItem {
        val cleanUrl = cleanStreamUrl(rawUrl)
        val uri = Uri.parse(cleanUrl)
        val mimeType = detectMimeType(cleanUrl)

        val builder = MediaItem.Builder().setUri(uri)
        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }
        return builder.build()
    }

    /**
     * Creates an [ExtractorsFactory] configured with essential flags for IPTV MPEG-TS streams:
     * - `FLAG_ALLOW_NON_IDR_KEYFRAMES`: playback starts even if stream does not begin with an IDR frame.
     * - `FLAG_DETECT_ACCESS_UNITS`: detects packet boundaries without Access Unit Delimiters.
     * - `FLAG_ENABLE_HDMV_DTS_AUDIOIZATION`: enables parsing of HDMV DTS audio in TS streams.
     * - `setConstantBitrateSeekingEnabled(true)`: enables seeking in CBR streams.
     */
    fun createExtractorsFactory(): DefaultExtractorsFactory {
        return DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            setConstantBitrateSeekingEnabled(true)
        }
    }

    /**
     * Creates a [DataSource.Factory] supporting HTTP, HTTPS, redirects, custom headers,
     * and wraps into [DefaultDataSource.Factory] for local/fallback schemes.
     */
    fun createDataSourceFactory(
        context: Context,
        userAgent: String? = null,
        customHeaders: Map<String, String> = emptyMap(),
    ): DataSource.Factory {
        val effectiveUa = userAgent?.trim()?.takeIf { it.isNotBlank() }
            ?: com.minogatv.box.core.model.Playlist.DEFAULT_USER_AGENT

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(effectiveUa)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        if (customHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(customHeaders)
        }

        return DefaultDataSource.Factory(context, httpFactory)
    }

    /**
     * High-performance [LoadControl] tuned for IPTV live and catch-up streams:
     * - Starts playback quickly (1 second buffer threshold).
     * - Recovers fast after stalls (2 seconds buffer threshold).
     * - Prioritizes time threshold over size so live streams NEVER stall waiting for large buffers.
     */
    fun createLoadControl(
        minBufferMs: Int = 15_000,
        maxBufferMs: Int = 50_000,
        bufferForPlaybackMs: Int = 1_000,
        bufferForPlaybackAfterRebufferMs: Int = 2_000,
    ): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Translates ExoPlayer [PlaybackException] into a descriptive Russian error message.
     */
    fun parsePlaybackError(error: PlaybackException): String {
        val errorCodeName = runCatching { error.errorCodeName }.getOrNull()
        val message = runCatching { error.localizedMessage }.getOrNull()
        return parsePlaybackError(
            cause = error.cause,
            errorCode = error.errorCode,
            errorCodeName = errorCodeName,
            errorMessage = message,
        )
    }

    /**
     * Translates error parameters into a descriptive Russian error message.
     */
    fun parsePlaybackError(
        cause: Throwable?,
        errorCode: Int = PlaybackException.ERROR_CODE_UNSPECIFIED,
        errorCodeName: String? = null,
        errorMessage: String? = null,
    ): String {
        // Check HTTP status code if available
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            return parseHttpErrorCode(cause.responseCode)
        }

        if (cause is UnknownHostException) {
            return "Не удалось найти сервер потока (ошибка DNS/сети)"
        }
        if (cause is ConnectException) {
            return "Не удалось подключиться к серверу потока"
        }
        if (cause is SocketTimeoutException) {
            return "Время ожидания ответа от сервера потока истекло"
        }
        if (cause is IOException && cause.message?.contains("Cleartext", ignoreCase = true) == true) {
            return "Незащищенный HTTP-трафик заблокирован системой"
        }

        val name = errorCodeName ?: "ERROR_$errorCode"

        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Ошибка сетевого соединения с потоком"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Таймаут подключения к потоку"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "Неподдерживаемый или поврежденный формат потока"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Аппаратный декодер устройства не поддерживает данный видеопоток"
            else ->
                errorMessage?.takeIf { it.isNotBlank() && it != "Source error" }
                    ?: "Ошибка воспроизведения ($name)"
        }
    }

    /**
     * Translates HTTP response codes into user-friendly Russian messages.
     */
    fun parseHttpErrorCode(code: Int): String {
        return when (code) {
            401 -> "Ошибка 401: Требуется авторизация (проверьте логин/пароль)"
            403 -> "Ошибка 403: Доступ запрещен (проверьте подписку или User-Agent)"
            404 -> "Ошибка 404: Поток не найден на сервере"
            in 500..599 -> "Ошибка сервера IPTV (HTTP $code)"
            else -> "Ошибка сервера: HTTP $code"
        }
    }
}
