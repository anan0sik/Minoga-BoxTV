package com.minogatv.box.core.parser

import com.minogatv.box.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

class EpgUrlNormalizationTest {

    @Test
    fun normalizeUrl_fixesTyposAndSchemes() {
        assertEquals(
            "http://epg.one/epg2.xml.gz",
            Playlist.normalizeUrl("hppt://epg.one/epg2.xml.gz")
        )
        assertEquals(
            "http://epg.one/epg2.xml.gz",
            Playlist.normalizeUrl("htttp://epg.one/epg2.xml.gz")
        )
        assertEquals(
            "http://epg.one/epg2.xml.gz",
            Playlist.normalizeUrl("http://epg.one/epg2.xml.gz")
        )
        assertEquals(
            Playlist.IT999_GITHUB_EPG_URL,
            Playlist.normalizeUrl("it999http://github.com/it999/it999.github.io")
        )
        assertEquals(
            Playlist.IT999_GITHUB_EPG_URL,
            Playlist.normalizeUrl("https://github.com/it999/it999.github.io")
        )
        assertEquals(
            "http://cdn.epg.one/epg2.xml.gz",
            Playlist.normalizeUrl(" cdn.epg.one/epg2.xml.gz ")
        )
    }
}
