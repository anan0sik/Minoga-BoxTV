package com.minogatv.box.feature.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class IptvMediaSourceHelperTest {

    @Test
    fun testCleanStreamUrl_simple() {
        val raw = "  http://example.com/live/ch1.m3u8 \n"
        assertEquals("http://example.com/live/ch1.m3u8", IptvMediaSourceHelper.cleanStreamUrl(raw))
    }

    @Test
    fun testCleanStreamUrl_withPipeHeaders() {
        val raw = "http://example.com/stream.m3u8|User-Agent=Mozilla/5.0&Referer=http://ref.com"
        assertEquals("http://example.com/stream.m3u8", IptvMediaSourceHelper.cleanStreamUrl(raw))
    }

    @Test
    fun testExtractHeaders() {
        val raw = "http://example.com/stream.m3u8|User-Agent=MyIptvAgent&Referer=http://ref.com&Auth=Bearer123"
        val headers = IptvMediaSourceHelper.extractHeaders(raw)
        assertEquals("MyIptvAgent", headers["User-Agent"])
        assertEquals("http://ref.com", headers["Referer"])
        assertEquals("Bearer123", headers["Auth"])
    }

    @Test
    fun testExtractHeaders_emptyWhenNoPipe() {
        val raw = "http://example.com/stream.m3u8"
        assertTrue(IptvMediaSourceHelper.extractHeaders(raw).isEmpty())
    }

    @Test
    fun testDetectMimeType() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            IptvMediaSourceHelper.detectMimeType("http://server.com/live/stream.m3u8"),
        )
        assertNull(
            IptvMediaSourceHelper.detectMimeType("http://server.com/hls/channel_1080"),
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            IptvMediaSourceHelper.detectMimeType("http://server.com/stream?format=m3u8"),
        )
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            IptvMediaSourceHelper.detectMimeType("http://server.com/live/user/pass/123.m3u8?token=xyz"),
        )
        assertEquals(
            MimeTypes.VIDEO_MP2T,
            IptvMediaSourceHelper.detectMimeType("http://server.com/live/stream.ts"),
        )
        assertEquals(
            MimeTypes.VIDEO_MP2T,
            IptvMediaSourceHelper.detectMimeType("http://server.com/live/stream.ts?token=123"),
        )
        assertEquals(
            MimeTypes.APPLICATION_MPD,
            IptvMediaSourceHelper.detectMimeType("http://server.com/live/manifest.mpd"),
        )
        assertEquals(
            MimeTypes.VIDEO_MP4,
            IptvMediaSourceHelper.detectMimeType("http://server.com/vod/movie.mp4"),
        )
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            IptvMediaSourceHelper.detectMimeType("http://server.com/radio.mp3"),
        )
    }

    @Test
    fun testParseHttpErrorCode() {
        val msg401 = IptvMediaSourceHelper.parseHttpErrorCode(401)
        assertTrue(msg401.contains("401") && msg401.contains("авторизация"))

        val msg403 = IptvMediaSourceHelper.parseHttpErrorCode(403)
        assertTrue(msg403.contains("403") && msg403.contains("Доступ запрещен"))

        val msg404 = IptvMediaSourceHelper.parseHttpErrorCode(404)
        assertTrue(msg404.contains("404") && msg404.contains("Поток не найден"))

        val msg500 = IptvMediaSourceHelper.parseHttpErrorCode(500)
        assertTrue(msg500.contains("500") && msg500.contains("Ошибка сервера IPTV"))

        val msg502 = IptvMediaSourceHelper.parseHttpErrorCode(502)
        assertTrue(msg502.contains("502") && msg502.contains("Ошибка сервера IPTV"))
    }

    @Test
    fun testParsePlaybackError_dns() {
        val dnsEx = UnknownHostException("stream.invalid.host")
        val ex = PlaybackException(
            "Source error",
            dnsEx,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
        val message = IptvMediaSourceHelper.parsePlaybackError(ex)
        assertTrue(message.contains("DNS") || message.contains("сети"))
    }

    @Test
    fun testParsePlaybackError_timeout() {
        val timeoutEx = SocketTimeoutException("Read timed out")
        val ex = PlaybackException(
            "Source error",
            timeoutEx,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        )
        val message = IptvMediaSourceHelper.parsePlaybackError(ex)
        assertTrue(message.contains("ожидания") || message.contains("Таймаут"))
    }
}
