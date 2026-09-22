package com.minogatv.box.core.parser

import com.minogatv.box.core.model.enums.CatchupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupUrlBuilderTest {

    @Test
    fun testFlussonicUrl() {
        val streamUrl = "http://iptv.example.com:8080/channel1/index.m3u8"
        val startMs = 1700000000000L
        val endMs = 1700003600000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.FLUSSONIC,
            catchupSource = null,
            programStartMs = startMs,
            programEndMs = endMs,
        )

        assertNotNull(url)
        assertEquals("http://iptv.example.com:8080/channel1/timeshift_abs/1700000000/3600/index.m3u8", url)
    }

    @Test
    fun testFlussonicUrlNoIndexM3u8() {
        val streamUrl = "http://iptv.example.com:8080/channel1"
        val startMs = 1700000000000L
        val endMs = 1700003600000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.FLUSSONIC,
            catchupSource = null,
            programStartMs = startMs,
            programEndMs = endMs,
        )

        assertNotNull(url)
        assertEquals("http://iptv.example.com:8080/channel1/timeshift_abs/1700000000/3600/index.m3u8", url)
    }

    @Test
    fun testXtreamUrl() {
        val streamUrl = "http://iptv.provider.org:8080/live/myuser/mypass/12345.ts"
        val startMs = 1700000000000L
        val endMs = 1700003600000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.XTREAM,
            catchupSource = null,
            programStartMs = startMs,
            programEndMs = endMs,
        )

        assertNotNull(url)
        assertTrue(url!!.contains("timeshift/myuser/mypass/60/"))
        assertTrue(url.endsWith("/12345.ts"))
    }

    @Test
    fun testShiftUrl() {
        val streamUrl = "http://stream.example.com/hls/live.m3u8"
        val startMs = 1700000000000L
        val endMs = 1700003600000L
        val nowMs = 1700005000000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.SHIFT,
            catchupSource = null,
            programStartMs = startMs,
            programEndMs = endMs,
            nowMs = nowMs,
        )

        assertNotNull(url)
        assertEquals("http://stream.example.com/hls/live.m3u8?utc=1700000000&lutc=1700005000", url)
    }

    @Test
    fun testAppendUrl() {
        val streamUrl = "http://stream.example.com/hls/live.m3u8"
        val startMs = 1700000000000L
        val endMs = 1700003600000L
        val nowMs = 1700001000000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.APPEND,
            catchupSource = null,
            programStartMs = startMs,
            programEndMs = endMs,
            nowMs = nowMs,
        )

        assertNotNull(url)
        assertEquals("http://stream.example.com/hls/live.m3u8?catchup-back=1000", url)
    }

    @Test
    fun testTemplateSubstitution() {
        val streamUrl = "http://stream.example.com/live.m3u8"
        val template = "?utc={utc}&lutc={lutc}"
        val startMs = 1700000000000L
        val endMs = 1700003600000L
        val nowMs = 1700002000000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.AUTO,
            catchupSource = template,
            programStartMs = startMs,
            programEndMs = endMs,
            nowMs = nowMs,
        )

        assertNotNull(url)
        assertEquals("http://stream.example.com/live.m3u8?utc=1700000000&lutc=1700002000", url)
    }

    @Test
    fun testRussianProviderNotFalselyDetectedAsXtream() {
        val streamUrl = "http://cdn.edemtv.me/iptv/SECRETKEY/105/index.m3u8"
        val startMs = 1700000000000L
        val endMs = 1700003600000L
        val nowMs = 1700001000000L

        val url = CatchupUrlBuilder.build(
            streamUrl = streamUrl,
            catchupType = CatchupType.AUTO,
            catchupSource = null,
            programStartMs = startMs,
            programEndMs = endMs,
            nowMs = nowMs,
        )

        assertNotNull(url)
        assertTrue("Expected SHIFT url but got: $url", url!!.contains("utc="))
        assertTrue("Should not be Xtream timeshift URL: $url", !url.contains("/timeshift/"))
    }
}
