package com.minogatv.box.core.parser

import com.minogatv.box.core.model.WebPairingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebConfigParserTest {

    @Test
    fun `parses JSON config from minogatvbox duckdns org`() {
        val json = """
            {
              "name": "Minoga TV",
              "playlistUrl": "http://provider.example.com/list.m3u8",
              "epgUrl": "http://epg.one/epg2.xml.gz"
            }
        """.trimIndent()

        val result = WebConfigParser.parse(json, "http://minogatvbox.duckdns.org")
        assertNotNull(result)
        assertEquals("Minoga TV", result!!.name)
        assertEquals("http://provider.example.com/list.m3u8", result.playlistUrl)
        assertEquals("http://epg.one/epg2.xml.gz", result.epgUrl)
    }

    @Test
    fun `parses HTML page with embedded minoga-config script and data attributes`() {
        val html = """
            <!DOCTYPE html>
            <html lang="ru"
                  data-minoga-name="Minoga Home"
                  data-minoga-playlist="https://iptv.example.org/channels.m3u"
                  data-minoga-epg="https://epg.it999.ru/edem.xml.gz">
            <head>
              <script id="minoga-config" type="application/json">
              {
                "name": "Minoga Home",
                "playlistUrl": "https://iptv.example.org/channels.m3u",
                "epgUrl": "https://epg.it999.ru/edem.xml.gz"
              }
              </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val result = WebConfigParser.parse(html, "http://minogatvbox.duckdns.org")
        assertNotNull(result)
        assertEquals("Minoga Home", result!!.name)
        assertEquals("https://iptv.example.org/channels.m3u", result.playlistUrl)
        assertEquals("https://epg.it999.ru/edem.xml.gz", result.epgUrl)
    }

    @Test
    fun `parses generic HTML page with m3u and xml gz links`() {
        val html = """
            <html>
              <body>
                <p>Плейлист: <a href="http://myserver.net/tv/playlist.m3u8">Скачать</a></p>
                <p>EPG: <a href="http://epg.one/epg2.xml.gz">Телепрограмма</a></p>
              </body>
            </html>
        """.trimIndent()

        val result = WebConfigParser.parse(html, "http://minogatvbox.duckdns.org")
        assertNotNull(result)
        assertEquals("http://myserver.net/tv/playlist.m3u8", result!!.playlistUrl)
        assertEquals("http://epg.one/epg2.xml.gz", result.epgUrl)
    }

    @Test
    fun `resolves relative URLs against source page URL`() {
        val json = """{"name": "Local", "playlistUrl": "/playlist.m3u", "epgUrl": "/epg.xml"}"""
        val result = WebConfigParser.parse(json, "http://minogatvbox.duckdns.org")
        assertNotNull(result)
        assertEquals("http://minogatvbox.duckdns.org/playlist.m3u", result!!.playlistUrl)
        assertEquals("http://minogatvbox.duckdns.org/epg.xml", result.epgUrl)
    }

    @Test
    fun `parses poll responses for WAITING VERIFIED and READY`() {
        val waiting = WebConfigParser.parsePollResponse("""{"status":"WAITING","code":"4829"}""", "4829")
        assertNotNull(waiting)
        assertEquals(WebPairingStatus.WAITING, waiting!!.status)
        assertNull(waiting.config)

        val verified = WebConfigParser.parsePollResponse("""{"status":"VERIFIED","code":"4829"}""", "4829")
        assertNotNull(verified)
        assertEquals(WebPairingStatus.VERIFIED, verified!!.status)
        assertNull(verified.config)

        val readyJson = """
            {
              "status": "READY",
              "code": "4829",
              "name": "Мой Плейлист",
              "playlistUrl": "http://example.com/tv.m3u8",
              "epgUrl": "http://epg.one/epg2.xml.gz"
            }
        """.trimIndent()
        val ready = WebConfigParser.parsePollResponse(readyJson, "4829")
        assertNotNull(ready)
        assertEquals(WebPairingStatus.READY, ready!!.status)
        assertNotNull(ready.config)
        assertEquals("Мой Плейлист", ready.config!!.name)
        assertEquals("http://example.com/tv.m3u8", ready.config!!.playlistUrl)
        assertEquals("http://epg.one/epg2.xml.gz", ready.config!!.epgUrl)
    }
}

