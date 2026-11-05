package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * # IptvMockServer
 *
 * Lightweight, zero-dependency Web Pairing & IPTV Server for "Minoga TV Box"
 * (hosted on minogatvbox.duckdns.org).
 *
 * Pairing Code Flow:
 * 1. TV App opens "Загрузить с ВЕБ-страницы", generates a 4-digit code, and calls:
 *    - POST /api/code/register  {"code": "4829"}
 *    - GET  /api/code/poll?code=4829 (every 2s)
 * 2. User opens http://minogatvbox.duckdns.org, enters code "4829":
 *    - POST /api/code/verify    {"code": "4829"} -> unlocks Name, Playlist, EPG form
 * 3. User fills Name, Playlist URL, EPG URL and submits:
 *    - POST /api/code/submit    {"code": "4829", "name": "...", "playlistUrl": "...", "epgUrl": "..."}
 * 4. Next poll from TV App receives status="READY" with the submitted links and installs them.
 */
public class IptvMockServer {

    private static final int DEFAULT_PORT = 8080;
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L; // 30 minutes
    private static final File WEB_DIR = new File("web");
    private static final File INDEX_HTML_FILE = new File(WEB_DIR, "index.html");
    private static final File CONFIG_JSON_FILE = new File(WEB_DIR, "config.json");

    static class PairingSession {
        final String code;
        volatile String status; // "WAITING", "VERIFIED", "READY"
        volatile String name = "";
        volatile String playlistUrl = "";
        volatile String epgUrl = "";
        volatile long updatedAtMs = System.currentTimeMillis();

        PairingSession(String code) {
            this.code = code;
            this.status = "WAITING";
        }
    }

    private static final Map<String, PairingSession> CODE_SESSIONS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", new WebPortalHandler());
        server.createContext("/index.html", new WebPortalHandler());
        server.createContext("/web", new WebPortalHandler());
        server.createContext("/api/code/register", new CodeRegisterHandler());
        server.createContext("/api/code/verify", new CodeVerifyHandler());
        server.createContext("/api/code/submit", new CodeSubmitHandler());
        server.createContext("/api/code/poll", new CodePollHandler());
        server.createContext("/api/config", new ConfigApiHandler());
        server.createContext("/config.json", new ConfigApiHandler());
        server.createContext("/playlist.m3u", new M3uHandler());
        server.createContext("/get.php", new M3uHandler());
        server.createContext("/epg.xml", new EpgHandler());
        server.createContext("/xmltv.php", new EpgHandler());
        server.createContext("/health", new HealthHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================================");
        System.out.println("  Minoga TV Box - Web Pairing & IPTV Server STARTED!");
        System.out.println("==================================================================");
        System.out.println("  Port: " + port + " (Bound to 0.0.0.0)");
        System.out.println("  Web Page:     http://localhost:" + port + "/");
        System.out.println("  Code Pairing: /api/code/register | /verify | /submit | /poll");
        System.out.println("==================================================================");
    }

    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        CODE_SESSIONS.entrySet().removeIf(e -> (now - e.getValue().updatedAtMs) > SESSION_TTL_MS);
    }

    // ─── 1. Register Code from TV App (POST /api/code/register) ─────────────────

    static class CodeRegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            cleanupExpiredSessions();

            String body = readBody(exchange);
            String code = extractJsonString(body, "code", "").replaceAll("\\D", "");
            if (code.length() != 4) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Код должен состоять из 4 цифр\"}");
                return;
            }

            PairingSession session = new PairingSession(code);
            CODE_SESSIONS.put(code, session);

            String resp = "{\"status\":\"WAITING\",\"code\":\"" + code + "\"}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
        }
    }

    // ─── 2. Verify Code from Web Page (POST /api/code/verify) ───────────────────

    static class CodeVerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            cleanupExpiredSessions();

            String body = readBody(exchange);
            String code = extractJsonString(body, "code", "").replaceAll("\\D", "");
            if (code.length() != 4) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Введите 4-значный цифровой код\"}");
                return;
            }

            PairingSession session = CODE_SESSIONS.get(code);
            if (session == null) {
                sendResponse(exchange, 404, "application/json; charset=utf-8",
                        "{\"error\":\"Код " + code + " не найден. Откройте окно «Загрузить с Веб-страницы» в приложении на ТВ.\"}");
                return;
            }

            if ("WAITING".equals(session.status)) {
                session.status = "VERIFIED";
            }
            session.updatedAtMs = System.currentTimeMillis();

            String resp = "{\"status\":\"" + session.status + "\",\"code\":\"" + code + "\"}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
        }
    }

    // ─── 3. Submit Name, Playlist & EPG from Web Page (POST /api/code/submit) ──

    static class CodeSubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            cleanupExpiredSessions();

            String body = readBody(exchange);
            String code = extractJsonString(body, "code", "").replaceAll("\\D", "");
            String name = extractJsonString(body, "name", "Minoga TV").trim();
            String playlistUrl = extractJsonString(body, "playlistUrl", "").trim();
            String epgUrl = extractJsonString(body, "epgUrl", "http://epg.one/epg2.xml.gz").trim();

            if (code.length() != 4) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Некорректный код привязки\"}");
                return;
            }
            if (playlistUrl.isEmpty()) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Укажите ссылку на плейлист\"}");
                return;
            }

            PairingSession session = CODE_SESSIONS.computeIfAbsent(code, PairingSession::new);
            session.name = name.isEmpty() ? "Minoga TV" : name;
            session.playlistUrl = playlistUrl;
            session.epgUrl = epgUrl;
            session.status = "READY";
            session.updatedAtMs = System.currentTimeMillis();

            // Also persist latest config to web/config.json as backup
            try {
                if (!WEB_DIR.exists()) WEB_DIR.mkdirs();
                String savedJson = "{\n"
                        + "  \"name\": \"" + escapeJson(session.name) + "\",\n"
                        + "  \"playlistUrl\": \"" + escapeJson(session.playlistUrl) + "\",\n"
                        + "  \"epgUrl\": \"" + escapeJson(session.epgUrl) + "\",\n"
                        + "  \"updatedAt\": \"" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "\"\n"
                        + "}\n";
                Files.writeString(CONFIG_JSON_FILE.toPath(), savedJson, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}

            String resp = "{\n"
                    + "  \"status\": \"READY\",\n"
                    + "  \"code\": \"" + escapeJson(code) + "\",\n"
                    + "  \"name\": \"" + escapeJson(session.name) + "\",\n"
                    + "  \"playlistUrl\": \"" + escapeJson(session.playlistUrl) + "\",\n"
                    + "  \"epgUrl\": \"" + escapeJson(session.epgUrl) + "\"\n"
                    + "}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
        }
    }

    // ─── 4. Poll Code Status from TV App (GET /api/code/poll?code=XXXX) ─────────

    static class CodePollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            cleanupExpiredSessions();

            String code = getQueryParam(exchange.getRequestURI(), "code").replaceAll("\\D", "");
            if (code.length() != 4) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Invalid code parameter\"}");
                return;
            }

            // Auto-register if TV polls directly
            PairingSession session = CODE_SESSIONS.computeIfAbsent(code, PairingSession::new);
            session.updatedAtMs = System.currentTimeMillis();

            if ("READY".equals(session.status)) {
                String resp = "{\n"
                        + "  \"status\": \"READY\",\n"
                        + "  \"code\": \"" + escapeJson(code) + "\",\n"
                        + "  \"name\": \"" + escapeJson(session.name) + "\",\n"
                        + "  \"playlistUrl\": \"" + escapeJson(session.playlistUrl) + "\",\n"
                        + "  \"epgUrl\": \"" + escapeJson(session.epgUrl) + "\"\n"
                        + "}";
                // Remove consumed session so it's one-time use
                CODE_SESSIONS.remove(code);
                sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
            } else {
                String resp = "{\"status\":\"" + session.status + "\",\"code\":\"" + escapeJson(code) + "\"}";
                sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
            }
        }
    }

    // ─── Web Portal Page Handler (GET / and /index.html) ────────────────────────

    static class WebPortalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if (INDEX_HTML_FILE.exists()) {
                String html = Files.readString(INDEX_HTML_FILE.toPath(), StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "text/html; charset=utf-8", html);
            } else {
                String fallback = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head>"
                        + "<body><h1>Minoga TV Box Web Pairing</h1></body></html>";
                sendResponse(exchange, 200, "text/html; charset=utf-8", fallback);
            }
        }
    }

    // ─── Legacy Config API Handler (GET/POST /api/config & /config.json) ────────

    static class ConfigApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if (CONFIG_JSON_FILE.exists()) {
                String json = Files.readString(CONFIG_JSON_FILE.toPath(), StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "application/json; charset=utf-8", json);
            } else {
                String defaultJson = "{\n"
                        + "  \"name\": \"Minoga TV\",\n"
                        + "  \"playlistUrl\": \"http://minogatvbox.duckdns.org/playlist.m3u\",\n"
                        + "  \"epgUrl\": \"http://epg.one/epg2.xml.gz\"\n"
                        + "}";
                sendResponse(exchange, 200, "application/json; charset=utf-8", defaultJson);
            }
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String getQueryParam(URI uri, String key) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = pair.substring(0, idx);
                String v = pair.substring(idx + 1);
                if (key.equalsIgnoreCase(k)) {
                    return v;
                }
            }
        }
        return "";
    }

    private static String extractJsonString(String json, String key, String fallback) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();
        }
        return fallback;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

            sendResponse(exchange, 200, "application/x-mpegurl; charset=utf-8", m3u);
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

            sendResponse(exchange, 200, "application/xml; charset=utf-8", xml);
        }
    }

    // ─── Health Handler ─────────────────────────────────────────────────────────

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            String json = "{\"status\":\"ok\",\"channels\":8,\"uptime\":" + System.currentTimeMillis() + "}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", json);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static void sendCorsPreflight(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.sendResponseHeaders(statusCode, bytes.length);
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
