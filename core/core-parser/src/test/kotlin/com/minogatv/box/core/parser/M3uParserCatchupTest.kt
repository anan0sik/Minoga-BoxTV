package com.minogatv.box.core.parser

import com.minogatv.box.core.model.enums.CatchupType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class M3uParserCatchupTest {

    @Test
    fun testCatchupDetectionPerChannel() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch1" catchup="default" catchup-days="5",Channel 1 (Default 5d)
            http://example.com/ch1.m3u8
            #EXTINF:-1 tvg-id="ch2" catchup-days="3",Channel 2 (Days only)
            http://example.com/ch2.m3u8
            #EXTINF:-1 tvg-id="ch3" tvg-rec="4",Channel 3 (Tvg-rec 4)
            http://example.com/ch3.m3u8
            #EXTINF:-1 tvg-id="ch4" catchup-source="http://example.com/catchup/{utc}",Channel 4 (Source only)
            http://example.com/ch4.m3u8
            #EXTINF:-1 tvg-id="ch5" catchup="shift",Channel 5 (Shift type only)
            http://example.com/ch5.m3u8
            #EXTINF:-1 tvg-id="ch6",Channel 6 (No Archive)
            http://example.com/ch6.m3u8
            #EXTINF:-1 tvg-id="ch7" tvg-rec="0",Channel 7 (Tvg-rec 0 - Disabled)
            http://example.com/ch7.m3u8
            #EXTINF:-1 tvg-id="ch8" catchup="none",Channel 8 (Catchup none)
            http://example.com/ch8.m3u8
            #EXTINF:-1 tvg-id="ch9" catchup-days="0",Channel 9 (Days 0)
            http://example.com/ch9.m3u8
        """.trimIndent()

        val result = M3uParser.parse(ByteArrayInputStream(playlist.toByteArray()))
        val channels = result.channels

        // Channel 1: catchup="default" catchup-days="5"
        val ch1 = channels[0]
        assertTrue("Channel 1 should have archive", ch1.catchupType != CatchupType.NONE && ch1.catchupDays > 0)
        assertEquals(5, ch1.catchupDays)

        // Channel 2: catchup-days="3"
        val ch2 = channels[1]
        assertTrue("Channel 2 should have archive", ch2.catchupType != CatchupType.NONE && ch2.catchupDays > 0)
        assertEquals(3, ch2.catchupDays)

        // Channel 3: tvg-rec="4"
        val ch3 = channels[2]
        assertTrue("Channel 3 should have archive", ch3.catchupType != CatchupType.NONE && ch3.catchupDays > 0)
        assertEquals(4, ch3.catchupDays)

        // Channel 4: catchup-source template
        val ch4 = channels[3]
        assertTrue("Channel 4 should have archive", ch4.catchupType != CatchupType.NONE && ch4.catchupDays > 0)
        assertEquals(7, ch4.catchupDays) // default days when not specified

        // Channel 5: catchup="shift"
        val ch5 = channels[4]
        assertTrue("Channel 5 should have archive", ch5.catchupType == CatchupType.SHIFT && ch5.catchupDays > 0)

        // Channel 6: No Archive
        val ch6 = channels[5]
        assertEquals(CatchupType.NONE, ch6.catchupType)
        assertEquals(0, ch6.catchupDays)
        assertFalse("Channel 6 must not have archive", ch6.catchupType != CatchupType.NONE && ch6.catchupDays > 0)

        // Channel 7: tvg-rec="0" -> explicitly disabled
        val ch7 = channels[6]
        assertEquals(CatchupType.NONE, ch7.catchupType)
        assertEquals(0, ch7.catchupDays)

        // Channel 8: catchup="none" -> explicitly disabled
        val ch8 = channels[7]
        assertEquals(CatchupType.NONE, ch8.catchupType)
        assertEquals(0, ch8.catchupDays)

        // Channel 9: catchup-days="0" -> explicitly disabled
        val ch9 = channels[8]
        assertEquals(CatchupType.NONE, ch9.catchupType)
        assertEquals(0, ch9.catchupDays)
    }

    @Test
    fun testGlobalCatchupHeaderWithOverrides() = runBlocking {
        val playlist = """
            #EXTM3U catchup="default" catchup-days="3"
            #EXTINF:-1 tvg-id="ch1",Channel 1 (Inherits global)
            http://example.com/ch1.m3u8
            #EXTINF:-1 tvg-id="ch2" catchup-days="7",Channel 2 (Override to 7 days)
            http://example.com/ch2.m3u8
            #EXTINF:-1 tvg-id="ch3" tvg-rec="0",Channel 3 (Override disabled via tvg-rec=0)
            http://example.com/ch3.m3u8
            #EXTINF:-1 tvg-id="ch4" catchup="none",Channel 4 (Override disabled via catchup=none)
            http://example.com/ch4.m3u8
            #EXTINF:-1 tvg-id="ch5" catchup="shift",Channel 5 (Override type to shift)
            http://example.com/ch5.m3u8
        """.trimIndent()

        val result = M3uParser.parse(ByteArrayInputStream(playlist.toByteArray()))
        val channels = result.channels

        // Channel 1: inherits global catchup="default" catchup-days="3"
        val ch1 = channels[0]
        assertNotEquals(CatchupType.NONE, ch1.catchupType)
        assertEquals(3, ch1.catchupDays)

        // Channel 2: overrides days to 7
        val ch2 = channels[1]
        assertNotEquals(CatchupType.NONE, ch2.catchupType)
        assertEquals(7, ch2.catchupDays)

        // Channel 3: tvg-rec="0" overrides and disables archive
        val ch3 = channels[2]
        assertEquals(CatchupType.NONE, ch3.catchupType)
        assertEquals(0, ch3.catchupDays)

        // Channel 4: catchup="none" overrides and disables archive
        val ch4 = channels[3]
        assertEquals(CatchupType.NONE, ch4.catchupType)
        assertEquals(0, ch4.catchupDays)

        // Channel 5: catchup="shift", inherits global days (3)
        val ch5 = channels[4]
        assertEquals(CatchupType.SHIFT, ch5.catchupType)
        assertEquals(3, ch5.catchupDays)
    }

    @Test
    fun testRealWorldCatchupVariations() = runBlocking {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch1" tvg-rec="1",Channel 1 (Tvg-rec 1 as flag)
            http://example.com/ch1.m3u8
            #EXTINF:-1 tvg-id="ch2" tvg-rec="true",Channel 2 (Tvg-rec true)
            http://example.com/ch2.m3u8
            #EXTINF:-1 tvg-id="ch3" timeshift="4",Channel 3 (Timeshift 4)
            http://example.com/ch3.m3u8
            #EXTINF:-1 tvg-id="ch4" catchup-type="flussonic",Channel 4 (Catchup-type flussonic)
            http://example.com/ch4.m3u8
            #EXTINF:-1 tvg-id="ch5" catchup="archive",Channel 5 (Catchup archive)
            http://example.com/ch5.m3u8
            #EXTINF:-1 tvg-id="ch6",Channel 6 (EXTVLCOPT timeshift)
            #EXTVLCOPT:timeshift=5
            http://example.com/ch6.m3u8
            #EXTINF:-1 tvg-id="ch7",Channel 7 (EXTVLCOPT catchup shift)
            #EXTVLCOPT:catchup=shift
            http://example.com/ch7.m3u8
        """.trimIndent()

        val result = M3uParser.parse(ByteArrayInputStream(playlist.toByteArray()))
        val channels = result.channels

        // ch1: tvg-rec="1"
        val ch1 = channels[0]
        assertTrue("ch1 has archive", ch1.catchupType != CatchupType.NONE && ch1.catchupDays >= 7)

        // ch2: tvg-rec="true"
        val ch2 = channels[1]
        assertTrue("ch2 has archive", ch2.catchupType != CatchupType.NONE && ch2.catchupDays >= 7)

        // ch3: timeshift="4"
        val ch3 = channels[2]
        assertTrue("ch3 has archive", ch3.catchupType != CatchupType.NONE && ch3.catchupDays == 4)

        // ch4: catchup-type="flussonic"
        val ch4 = channels[3]
        assertEquals(CatchupType.FLUSSONIC, ch4.catchupType)
        assertTrue(ch4.catchupDays >= 7)

        // ch5: catchup="archive"
        val ch5 = channels[4]
        assertNotEquals(CatchupType.NONE, ch5.catchupType)
        assertTrue(ch5.catchupDays >= 7)

        // ch6: EXTVLCOPT:timeshift=5
        val ch6 = channels[5]
        assertNotEquals(CatchupType.NONE, ch6.catchupType)
        assertEquals(5, ch6.catchupDays)

        // ch7: EXTVLCOPT:catchup=shift
        val ch7 = channels[6]
        assertEquals(CatchupType.SHIFT, ch7.catchupType)
        assertTrue(ch7.catchupDays >= 7)
    }

    @Test
    fun testChannelDomainHasArchiveLogic() {
        val noArchive = com.minogatv.box.core.model.Channel(
            playlistId = 1,
            name = "Test",
            streamUrl = "http://example.com",
            catchupType = CatchupType.NONE,
            catchupDays = 0,
        )
        assertFalse(noArchive.hasArchive)

        // Channel with catchupType != NONE but catchupDays == 0 must have archive
        val shiftZeroDays = noArchive.copy(catchupType = CatchupType.SHIFT, catchupDays = 0)
        assertTrue("Channel with SHIFT but 0 days must have archive", shiftZeroDays.hasArchive)

        // Channel with catchupDays > 0 but catchupType == NONE must have archive
        val daysOnly = noArchive.copy(catchupDays = 3)
        assertTrue("Channel with days > 0 must have archive", daysOnly.hasArchive)

        // Channel with catchupSource must have archive
        val sourceOnly = noArchive.copy(catchupSource = "http://example.com/{utc}")
        assertTrue("Channel with catchupSource must have archive", sourceOnly.hasArchive)

        // Settings forced override: forced NONE disables archive
        assertFalse(shiftZeroDays.hasArchive(CatchupType.NONE))

        // Settings forced override: forced SHIFT enables archive even on noArchive channel
        assertTrue(noArchive.hasArchive(CatchupType.SHIFT))
        assertTrue(noArchive.hasArchive(CatchupType.FLUSSONIC))

        // AUTO override follows channel detection
        assertFalse(noArchive.hasArchive(CatchupType.AUTO))
        assertTrue(shiftZeroDays.hasArchive(CatchupType.AUTO))
    }
}
