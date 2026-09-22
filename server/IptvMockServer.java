package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

/**
 * # IptvMockServer
 *
 * Lightweight, zero-dependency Mock IPTV Backend for "Minoga TV Box".
 *
 * Endpoints:
 * - M3U Playlist: http://<ip>:8080/playlist.m3u
 * - Xtream Codes API: http://<ip>:8080/get.php?username=admin&password=admin
 * - XMLTV EPG: http://<ip>:8080/epg.xml
 * - Health Check: http://<ip>:8080/health
 */
public class IptvMockServer {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);

        server.createContext("/", new M3uHandler());
        server.createContext("/playlist.m3u", new M3uHandler());
        server.createContext("/get.php", new M3uHandler());
        server.createContext("/epg.xml", new EpgHandler());
        server.createContext("/xmltv.php", new EpgHandler());
        server.createContext("/health", new HealthHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================================");
        System.out.println("  Minoga TV Box - Mock IPTV Backend Server STARTED!");
        System.out.println("==================================================================");
        System.out.println("  Port: " + PORT + " (Bound to 0.0.0.0)");
        System.out.println();
        System.out.println("  M3U Playlist URLs to enter in Minoga Settings:");
        System.out.println("  * Android Emulator:       http://10.0.2.2:" + PORT + "/playlist.m3u");
        System.out.println("  * Xiaomi TV Box / Device: http://192.168.112.104:" + PORT + "/playlist.m3u");
        System.out.println("  * Localhost / Browser:    http://localhost:" + PORT + "/playlist.m3u");
        System.out.println();
        System.out.println("  Xtream Codes Simulation:");
        System.out.println("  * Server: http://10.0.2.2:" + PORT + " | User: admin | Pass: admin");
        System.out.println("==================================================================");
    }

    // ─── M3U Playlist Handler ───────────────────────────────────────────────────

    static class M3uHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);

            String m3u = "#EXTM3U url-tvg=\"http://10.0.2.2:8080/epg.xml\"\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"bb_bunny\" tvg-name=\"Big Buck Bunny HD\" tvg-logo=\"https://peach.blender.org/wp-content/uploads/title_sq.jpg\" group-title=\"Cinema\",Big Buck Bunny HD\n"
                    + "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"tears_steel\" tvg-name=\"Tears of Steel\" tvg-logo=\"https://mango.blender.org/wp-content/uploads/2013/05/01_thom_celia_b.jpg\" group-title=\"Cinema\",Tears of Steel 4K UHD\n"
                    + "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"sintel\" tvg-name=\"Sintel Movie\" tvg-logo=\"https://durian.blender.org/wp-content/uploads/2010/06/07.dragon_fight1.jpg\" group-title=\"Cinema\",Sintel Animation\n"
                    + "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"nasa_tv\" tvg-name=\"NASA TV Live\" tvg-logo=\"https://www.nasa.gov/sites/default/files/thumbnails/image/nasa-logo-web-rgb.png\" group-title=\"Science\",NASA TV Live\n"
                    + "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"euronews\" tvg-name=\"Euronews English\" tvg-logo=\"https://upload.wikimedia.org/wikipedia/commons/4/47/Euronews_2016_logo.svg\" group-title=\"News\",Euronews HD\n"
                    + "https://euronews-euronews-world-1-au.samsung.wurl.tv/playlist.m3u8\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"france24\" tvg-name=\"France 24\" tvg-logo=\"https://upload.wikimedia.org/wikipedia/commons/f/fb/France_24_logo.svg\" group-title=\"News\",France 24 English\n"
                    + "https://france24-hls-live-en.akamaized.net/hls/live/2034076/F24_EN_LO_HLS/master.m3u8\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"redbull\" tvg-name=\"Red Bull TV\" tvg-logo=\"https://upload.wikimedia.org/wikipedia/en/thumb/f/f5/Red_Bull_TV_logo.svg/320px-Red_Bull_TV_logo.svg.png\" group-title=\"Sports\",Red Bull Action Sports\n"
                    + "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8\n"
                    + "\n"
                    + "#EXTINF:-1 tvg-id=\"dw_news\" tvg-name=\"DW English\" tvg-logo=\"https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Deutsche_Welle_logo.svg/320px-Deutsche_Welle_logo.svg.png\" group-title=\"News\",Deutsche Welle HD\n"
                    + "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8\n";

            sendResponse(exchange, "application/x-mpegurl; charset=utf-8", m3u);
        }
    }

    // ─── EPG XMLTV Handler ──────────────────────────────────────────────────────

    static class EpgHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);

            Instant now = Instant.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss +0000").withZone(ZoneOffset.UTC);

            String start1 = fmt.format(now.minusSeconds(3600));
            String end1   = fmt.format(now.plusSeconds(3600));
            String start2 = end1;
            String end2   = fmt.format(now.plusSeconds(7200));

            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<tv generator-info-name=\"MinogaMockEPG\">\n"
                    + "  <channel id=\"bb_bunny\"><display-name>Big Buck Bunny HD</display-name></channel>\n"
                    + "  <channel id=\"tears_steel\"><display-name>Tears of Steel 4K UHD</display-name></channel>\n"
                    + "  <channel id=\"sintel\"><display-name>Sintel Animation</display-name></channel>\n"
                    + "  <channel id=\"nasa_tv\"><display-name>NASA TV Live</display-name></channel>\n"
                    + "  <channel id=\"euronews\"><display-name>Euronews HD</display-name></channel>\n"
                    + "  <channel id=\"france24\"><display-name>France 24 English</display-name></channel>\n"
                    + "  <channel id=\"redbull\"><display-name>Red Bull Action Sports</display-name></channel>\n"
                    + "  <channel id=\"dw_news\"><display-name>Deutsche Welle HD</display-name></channel>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"bb_bunny\">\n"
                    + "    <title lang=\"ru\">Big Buck Bunny: Special Edition</title>\n"
                    + "    <desc lang=\"ru\">Приключения гигантского кролика в формате высокой четкости Full HD.</desc>\n"
                    + "    <category lang=\"ru\">Мультфильм</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start2 + "\" stop=\"" + end2 + "\" channel=\"bb_bunny\">\n"
                    + "    <title lang=\"ru\">Blender Open Movie Showcase</title>\n"
                    + "    <desc lang=\"ru\">Шедевры трехмерной анимации от Blender Foundation.</desc>\n"
                    + "    <category lang=\"ru\">Кино</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"tears_steel\">\n"
                    + "    <title lang=\"ru\">Tears of Steel 4K UHD</title>\n"
                    + "    <desc lang=\"ru\">Фантастический постапокалиптический боевик в сверхвысоком разрешении 4K.</desc>\n"
                    + "    <category lang=\"ru\">Фантастика</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"sintel\">\n"
                    + "    <title lang=\"ru\">Sintel: Путь Воина</title>\n"
                    + "    <desc lang=\"ru\">История девушки, ищущей своего украденного дракона.</desc>\n"
                    + "    <category lang=\"ru\">Анимация</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"nasa_tv\">\n"
                    + "    <title lang=\"ru\">Прямой эфир с МКС (NASA TV)</title>\n"
                    + "    <desc lang=\"ru\">Трансляция из открытого космоса в прямом эфире.</desc>\n"
                    + "    <category lang=\"ru\">Наука</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"euronews\">\n"
                    + "    <title lang=\"ru\">Euronews: Главные мировые события</title>\n"
                    + "    <desc lang=\"ru\">Круглосуточный информационный канал.</desc>\n"
                    + "    <category lang=\"ru\">Новости</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"france24\">\n"
                    + "    <title lang=\"ru\">France 24: Международный дайджест</title>\n"
                    + "    <desc lang=\"ru\">Аналитика и репортажи из Европы и мира.</desc>\n"
                    + "    <category lang=\"ru\">Новости</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"redbull\">\n"
                    + "    <title lang=\"ru\">Red Bull Extreme Games</title>\n"
                    + "    <desc lang=\"ru\">Самые зрелищные экстремальные виды спорта.</desc>\n"
                    + "    <category lang=\"ru\">Спорт</category>\n"
                    + "  </programme>\n"
                    + "  <programme start=\"" + start1 + "\" stop=\"" + end1 + "\" channel=\"dw_news\">\n"
                    + "    <title lang=\"ru\">Deutsche Welle: Focus on Europe</title>\n"
                    + "    <desc lang=\"ru\">Европейская культура, технологии и политика.</desc>\n"
                    + "    <category lang=\"ru\">Новости</category>\n"
                    + "  </programme>\n"
                    + "</tv>\n";

            sendResponse(exchange, "application/xml; charset=utf-8", xml);
        }
    }

    // ─── Health Handler ─────────────────────────────────────────────────────────

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            String json = "{\"status\":\"ok\",\"channels\":8,\"uptime\":" + System.currentTimeMillis() + "}";
            sendResponse(exchange, "application/json; charset=utf-8", json);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static void sendResponse(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void log(HttpExchange exchange) {
        String remote = exchange.getRemoteAddress().getAddress().getHostAddress();
        System.out.println("[" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "] "
                + exchange.getRequestMethod() + " " + exchange.getRequestURI() + " from " + remote);
    }
}
