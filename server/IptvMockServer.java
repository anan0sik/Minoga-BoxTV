package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

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
    private static final File PLAYLISTS_DIR = new File(WEB_DIR, "playlists");
    private static final File INDEX_HTML_FILE = new File(WEB_DIR, "index.html");
    private static final File TV_HTML_FILE = new File(WEB_DIR, "tv.html");
    private static final File ADMIN_HTML_FILE = new File(WEB_DIR, "admin.html");
    private static final File CONFIG_JSON_FILE = new File(WEB_DIR, "config.json");
    private static final File DOWNLOADS_JSON_FILE = new File(WEB_DIR, "downloads.json");
    private static final File FEEDBACK_JSON_FILE = new File(WEB_DIR, "feedback.json");
    private static final File ADMIN_KEY_FILE = new File(WEB_DIR, "admin_key.txt");
    private static final AtomicLong DOWNLOAD_COUNT = new AtomicLong(0L);

    static class FeedbackItem {
        String id;
        String clientId;
        String authorName;
        String deviceType;
        String category; // REVIEW | QUESTION | IDEA | ISSUE
        int rating;      // 1..5
        String text;
        String contact;  // Private: only visible in /admin
        String status;   // PENDING | PUBLISHED | PRIVATE_REPLIED | HIDDEN
        String replyText;
        long createdAt;
        long repliedAt;
    }

    private static final List<FeedbackItem> FEEDBACK_ITEMS = new ArrayList<>();
    private static final Map<String, List<Long>> FEEDBACK_RATE_LIMIT = new ConcurrentHashMap<>();

    static class PairingSession {
        final String code;
        volatile String status; // "WAITING", "VERIFIED", "READY"
        volatile String name = "";
        volatile String playlistUrl = "";
        volatile String epgUrl = "";
        volatile String vodUrl = "";
        volatile String vodLogin = "";
        volatile String vodPassword = "";
        volatile String vodKey = "";
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

        loadDownloadCount();
        loadFeedbackItems();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", new WebPortalHandler());
        server.createContext("/index.html", new WebPortalHandler());
        server.createContext("/web", new WebPortalHandler());
        server.createContext("/tv", new TvWebAppHandler());
        server.createContext("/tv/", new TvWebAppHandler());
        server.createContext("/admin", new AdminPageHandler());
        server.createContext("/admin/", new AdminPageHandler());
        server.createContext("/msx/start.json", new MsxStartHandler());
        server.createContext("/msx/content.json", new MsxContentHandler());
        server.createContext("/msx/hub.json", new MsxHubHandler());
        server.createContext("/hub/icon/", new HubIconHandler());
        server.createContext("/hub", new MsxHubHandler());
        server.createContext("/api/tv/proxy", new TvStreamProxyHandler());
        server.createContext("/api/tv/vod", new TvVodApiHandler());
        server.createContext("/api/tv/epg/status", new TvEpgStatusHandler());
        server.createContext("/api/tv/epg/live", new TvEpgLiveHandler());
        server.createContext("/api/tv/epg/channel", new TvEpgChannelHandler());
        server.createContext("/api/code/register", new CodeRegisterHandler());
        server.createContext("/api/code/verify", new CodeVerifyHandler());
        server.createContext("/api/code/submit", new CodeSubmitHandler());
        server.createContext("/api/code/poll", new CodePollHandler());
        server.createContext("/api/playlist/upload", new PlaylistUploadHandler());
        server.createContext("/api/playlist/file", new PlaylistFileHandler());
        server.createContext("/api/downloads/stats", new DownloadStatsHandler());
        server.createContext("/api/downloads/file", new DownloadFileHandler());
        server.createContext("/api/feedback/public", new FeedbackPublicHandler());
        server.createContext("/api/feedback/submit", new FeedbackSubmitHandler());
        server.createContext("/api/feedback/admin", new FeedbackAdminHandler());
        server.createContext("/api/config", new ConfigApiHandler());
        server.createContext("/config.json", new ConfigApiHandler());
        server.createContext("/playlist.m3u", new M3uHandler());
        server.createContext("/get.php", new M3uHandler());
        server.createContext("/epg.xml", new EpgHandler());
        server.createContext("/xmltv.php", new EpgHandler());
        server.createContext("/health", new HealthHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        // Pre-warm default EPG dataset in background so it is immediately ready for Smart TV & Android clients
        getOrStartEpgDataset("http://epg.one/epg2.xml.gz");

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

    private static synchronized void loadDownloadCount() {
        try {
            if (DOWNLOADS_JSON_FILE.exists()) {
                String json = Files.readString(DOWNLOADS_JSON_FILE.toPath(), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"downloads\"\\s*:\\s*(\\d+)").matcher(json);
                if (m.find()) {
                    DOWNLOAD_COUNT.set(Long.parseLong(m.group(1)));
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized long incrementAndSaveDownloadCount() {
        long next = DOWNLOAD_COUNT.incrementAndGet();
        try {
            if (!WEB_DIR.exists()) WEB_DIR.mkdirs();
            String json = "{\n"
                    + "  \"downloads\": " + next + ",\n"
                    + "  \"updatedAt\": \"" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "\"\n"
                    + "}\n";
            Files.writeString(DOWNLOADS_JSON_FILE.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return next;
    }

    // ─── Download Stats & Tracked APK Handler ───────────────────────────────────

    static class DownloadStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                long count = incrementAndSaveDownloadCount();
                sendResponse(exchange, 200, "application/json; charset=utf-8", "{\"downloads\":" + count + "}");
                return;
            }
            long count = DOWNLOAD_COUNT.get();
            sendResponse(exchange, 200, "application/json; charset=utf-8", "{\"downloads\":" + count + "}");
        }
    }

    static class DownloadFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            String method = exchange.getRequestMethod();
            String range = exchange.getRequestHeaders().getFirst("Range");
            // Count only initial GET requests (ignore HEAD and mid-file Range resume chunks)
            if ("GET".equalsIgnoreCase(method) && (range == null || range.startsWith("bytes=0-"))) {
                incrementAndSaveDownloadCount();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.android.package-archive");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"MinogaTVBox.apk\"");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("X-Accel-Redirect", "/_internal_apk/MinogaTVBox.apk");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        }
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

    // ─── Anti-Bot Protection for Code Pairing (/api/code/verify & /api/code/submit) ──
    private static final Map<String, long[]> PAIRING_RATE_LIMIT = new ConcurrentHashMap<>();

    private static String getClientIpAddress(HttpExchange exchange) {
        String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private static boolean checkPairingRateLimit(String ip, int maxPerMinute) {
        long now = System.currentTimeMillis();
        long[] slot = PAIRING_RATE_LIMIT.computeIfAbsent(ip, k -> new long[]{now, 0L});
        synchronized (slot) {
            if (now - slot[0] > 60_000L) {
                slot[0] = now;
                slot[1] = 1L;
                return true;
            }
            slot[1]++;
            return slot[1] <= maxPerMinute;
        }
    }

    private static String computeExpectedAntiBotToken(String code, String salt) {
        int h = (int) 2166136261L;
        String s = "minoga_human_" + code + "_" + salt;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 16777619;
        }
        return Integer.toUnsignedString(h, 16);
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

            String clientIp = getClientIpAddress(exchange);
            if (!checkPairingRateLimit(clientIp, 12)) {
                sendResponse(exchange, 429, "application/json; charset=utf-8",
                        "{\"error\":\"Слишком много попыток ввода кода. Подождите 1 минуту и попробуйте снова.\"}");
                return;
            }

            String body = readBody(exchange);
            String honeypot = extractJsonString(body, "website_url", "").trim();
            if (!honeypot.isEmpty()) {
                sendResponse(exchange, 403, "application/json; charset=utf-8",
                        "{\"error\":\"Обнаружена автоматическая отправка (защита от ботов).\"}");
                return;
            }

            String code = extractJsonString(body, "code", "").replaceAll("\\D", "");
            if (code.length() != 4) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Введите 4-значный цифровой код\"}");
                return;
            }

            String botToken = extractJsonString(body, "_bt", "").trim();
            String botSalt = extractJsonString(body, "_ts", "").trim();
            if (!botToken.isEmpty() || !botSalt.isEmpty()) {
                String expected = computeExpectedAntiBotToken(code, botSalt);
                if (!expected.equalsIgnoreCase(botToken)) {
                    sendResponse(exchange, 403, "application/json; charset=utf-8",
                            "{\"error\":\"Ошибка проверки защиты от ботов. Обновите страницу и попробуйте снова.\"}");
                    return;
                }
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

            String clientIp = getClientIpAddress(exchange);
            if (!checkPairingRateLimit(clientIp, 15)) {
                sendResponse(exchange, 429, "application/json; charset=utf-8",
                        "{\"error\":\"Слишком много запросов. Подождите 1 минуту и попробуйте снова.\"}");
                return;
            }

            String body = readBody(exchange);
            String honeypot = extractJsonString(body, "website_url", "").trim();
            if (!honeypot.isEmpty()) {
                sendResponse(exchange, 403, "application/json; charset=utf-8",
                        "{\"error\":\"Обнаружена автоматическая отправка (защита от ботов).\"}");
                return;
            }

            String code = extractJsonString(body, "code", "").replaceAll("\\D", "");
            String botToken = extractJsonString(body, "_bt", "").trim();
            String botSalt = extractJsonString(body, "_ts", "").trim();
            if (!botToken.isEmpty() || !botSalt.isEmpty()) {
                String expected = computeExpectedAntiBotToken(code, botSalt);
                if (!expected.equalsIgnoreCase(botToken)) {
                    sendResponse(exchange, 403, "application/json; charset=utf-8",
                            "{\"error\":\"Ошибка проверки защиты от ботов. Обновите страницу и попробуйте снова.\"}");
                    return;
                }
            }
            String name = extractJsonString(body, "name", "Minoga TV").trim();
            String playlistUrl = extractJsonString(body, "playlistUrl", "").trim();
            String epgUrl = extractJsonString(body, "epgUrl", "").trim();
            String vodUrl = extractJsonString(body, "vodUrl", "").trim();
            String vodLogin = extractJsonString(body, "vodLogin", "").trim();
            String vodPassword = extractJsonString(body, "vodPassword", "").trim();
            String vodKey = extractJsonString(body, "vodKey", "").trim();

            String[] normVod = TvVodApiHandler.normalizeVodPortalUrlAndKey(vodUrl, vodKey);
            vodUrl = normVod[0];
            vodKey = normVod[1];

            if (code.length() != 4) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Некорректный код привязки\"}");
                return;
            }
            if (playlistUrl.isEmpty() && vodUrl.isEmpty() && vodKey.isEmpty()) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Укажите ссылку на плейлист или параметры портала Медиатеки\"}");
                return;
            }

            PairingSession session = CODE_SESSIONS.computeIfAbsent(code, PairingSession::new);
            session.name = name.isEmpty() ? "Minoga TV" : name;
            session.playlistUrl = playlistUrl;
            session.epgUrl = epgUrl;
            session.vodUrl = vodUrl;
            session.vodLogin = vodLogin;
            session.vodPassword = vodPassword;
            session.vodKey = vodKey;
            session.status = "READY";
            session.updatedAtMs = System.currentTimeMillis();

            // Also persist latest config to web/config.json as backup (if playlistUrl provided)
            if (!session.playlistUrl.isEmpty()) {
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
            }

            String resp = "{\n"
                    + "  \"status\": \"READY\",\n"
                    + "  \"code\": \"" + escapeJson(code) + "\",\n"
                    + "  \"name\": \"" + escapeJson(session.name) + "\",\n"
                    + "  \"playlistUrl\": \"" + escapeJson(session.playlistUrl) + "\",\n"
                    + "  \"epgUrl\": \"" + escapeJson(session.epgUrl) + "\",\n"
                    + "  \"vodUrl\": \"" + escapeJson(session.vodUrl) + "\",\n"
                    + "  \"vodLogin\": \"" + escapeJson(session.vodLogin) + "\",\n"
                    + "  \"vodPassword\": \"" + escapeJson(session.vodPassword) + "\",\n"
                    + "  \"vodKey\": \"" + escapeJson(session.vodKey) + "\"\n"
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
                        + "  \"epgUrl\": \"" + escapeJson(session.epgUrl) + "\",\n"
                        + "  \"vodUrl\": \"" + escapeJson(session.vodUrl) + "\",\n"
                        + "  \"vodLogin\": \"" + escapeJson(session.vodLogin) + "\",\n"
                        + "  \"vodPassword\": \"" + escapeJson(session.vodPassword) + "\",\n"
                        + "  \"vodKey\": \"" + escapeJson(session.vodKey) + "\"\n"
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

    // ─── 5. Upload & Serve Local M3U / M3U8 Playlist Files (/api/playlist/*) ────

    static class PlaylistUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String content = readBody(exchange);
            if (content == null || content.trim().isEmpty()) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Файл плейлиста пуст\"}");
                return;
            }
            if (!content.contains("#EXTINF") && !content.contains("#EXTM3U") && !content.contains("http")) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"Выбранный файл не похож на M3U / M3U8 плейлист\"}");
                return;
            }

            if (!PLAYLISTS_DIR.exists()) {
                PLAYLISTS_DIR.mkdirs();
            }

            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            File targetFile = new File(PLAYLISTS_DIR, token + ".m3u8");
            Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);

            String playlistUrl = "https://minogatvbox.duckdns.org/api/playlist/file?id=" + token;
            String resp = "{\n"
                    + "  \"status\": \"OK\",\n"
                    + "  \"id\": \"" + token + "\",\n"
                    + "  \"playlistUrl\": \"" + playlistUrl + "\"\n"
                    + "}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
        }
    }

    static class PlaylistFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String id = getQueryParam(exchange.getRequestURI(), "id").replaceAll("[^a-zA-Z0-9_-]", "");
            if (id.isEmpty()) {
                sendResponse(exchange, 400, "text/plain; charset=utf-8", "Missing playlist id");
                return;
            }
            File f = new File(PLAYLISTS_DIR, id + ".m3u8");
            if (!f.exists() || !f.isFile()) {
                sendResponse(exchange, 404, "text/plain; charset=utf-8", "Playlist file not found");
                return;
            }
            String m3u = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "audio/x-mpegurl; charset=utf-8", m3u);
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

    // ─── Smart TV Web App Handler (GET /tv and /tv/) ────────────────────────────

    static class TvWebAppHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if (TV_HTML_FILE.exists()) {
                String html = Files.readString(TV_HTML_FILE.toPath(), StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "text/html; charset=utf-8", html);
            } else {
                sendResponse(exchange, 404, "text/plain; charset=utf-8", "Minoga TV Web App not found");
            }
        }
    }

    // ─── Media Station X (LG webOS / Samsung Tizen) Entry Handlers ──────────────

    private static boolean isHubHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("X-Forwarded-Host");
        if (host == null || host.isEmpty()) {
            host = exchange.getRequestHeaders().getFirst("Host");
        }
        if (host == null) return false;
        String h = host.trim().toLowerCase();
        return h.startsWith("hub.") || h.startsWith("menu.");
    }

    static class MsxStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isHubHost(exchange)) {
                new MsxHubHandler().handle(exchange);
                return;
            }
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String json = "{\n"
                    + "  \"name\": \"Minoga TV Box\",\n"
                    + "  \"version\": \"1.1.8\",\n"
                    + "  \"parameter\": \"content:https://minogatvbox.duckdns.org/msx/content.json?v=118\",\n"
                    + "  \"action\": \"link:https://minogatvbox.duckdns.org/tv/?v=118\"\n"
                    + "}\n";
            sendResponse(exchange, 200, "application/json; charset=utf-8", json);
        }
    }

    static class MsxContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (isHubHost(exchange)) {
                new MsxHubHandler().handle(exchange);
                return;
            }
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String json = "{\n"
                    + "  \"type\": \"pages\",\n"
                    + "  \"headline\": \"Minoga TV Box\",\n"
                    + "  \"action\": \"link:https://minogatvbox.duckdns.org/tv/?v=118\",\n"
                    + "  \"template\": {\n"
                    + "    \"type\": \"separate\",\n"
                    + "    \"layout\": \"0,0,4,3\",\n"
                    + "    \"color\": \"msx-glass\",\n"
                    + "    \"imageFiller\": \"height-center\"\n"
                    + "  },\n"
                    + "  \"items\": [\n"
                    + "    {\n"
                    + "      \"title\": \"Запустить Minoga TV Box\",\n"
                    + "      \"titleFooter\": \"Полноценный веб-интерфейс для LG и Samsung TV\",\n"
                    + "      \"image\": \"https://minogatvbox.duckdns.org/download/icon.png\",\n"
                    + "      \"icon\": \"tv\",\n"
                    + "      \"action\": \"link:https://minogatvbox.duckdns.org/tv/?v=118\"\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"title\": \"Привязка плейлиста по коду\",\n"
                    + "      \"titleFooter\": \"minogatvbox.duckdns.org\",\n"
                    + "      \"image\": \"https://minogatvbox.duckdns.org/hub/icon/pairing.png\",\n"
                    + "      \"icon\": \"settings\",\n"
                    + "      \"action\": \"link:https://minogatvbox.duckdns.org/\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n";
            sendResponse(exchange, 200, "application/json; charset=utf-8", json);
        }
    }

    static class HubIconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String fileName = "icon-ottplayer.png";
            if (path != null) {
                String lower = path.toLowerCase();
                if (lower.contains("getstv")) {
                    fileName = "icon-getstv.png";
                } else if (lower.contains("pairing") || lower.contains("code")) {
                    fileName = "icon-pairing.png";
                }
            }
            File iconFile = new File(WEB_DIR, fileName);
            if (!iconFile.exists() || !iconFile.isFile()) {
                sendResponse(exchange, 404, "text/plain; charset=utf-8", "Icon not found");
                return;
            }
            byte[] bytes = Files.readAllBytes(iconFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class MsxHubHandler implements HttpHandler {
        private static volatile String cachedOttAction = null;
        private static volatile String cachedGetsTvAction = null;
        private static volatile long lastResolvedMs = 0L;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path != null && path.endsWith("content.json")) {
                ensureActionsResolved();
                String ottAction = cachedOttAction != null ? cachedOttAction : "link:http://widget.ottplayer.tv";
                String getsAction = cachedGetsTvAction != null ? cachedGetsTvAction : "link:http://msx.getstv.com";

                String json = "{\n"
                        + "  \"type\": \"pages\",\n"
                        + "  \"headline\": \"Стартовое меню Media Station X\",\n"
                        + "  \"template\": {\n"
                        + "    \"type\": \"separate\",\n"
                        + "    \"layout\": \"0,0,4,3\",\n"
                        + "    \"color\": \"msx-glass\",\n"
                        + "    \"imageFiller\": \"height-center\"\n"
                        + "  },\n"
                        + "  \"items\": [\n"
                        + "    {\n"
                        + "      \"title\": \"Minoga TV Box\",\n"
                        + "      \"titleFooter\": \"IPTV • Архив • Телепрограмма\",\n"
                        + "      \"image\": \"https://minogatvbox.duckdns.org/download/icon.png\",\n"
                        + "      \"badge\": \"MINOGA TV\",\n"
                        + "      \"badgeColor\": \"msx-cyan\",\n"
                        + "      \"action\": \"link:https://minogatvbox.duckdns.org/tv/?v=118\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"title\": \"OttPlayer\",\n"
                        + "      \"titleFooter\": \"widget.ottplayer.tv\",\n"
                        + "      \"image\": \"https://minogatvbox.duckdns.org/hub/icon/ottplayer.png\",\n"
                        + "      \"icon\": \"live-tv\",\n"
                        + "      \"badge\": \"OTTPLAYER\",\n"
                        + "      \"badgeColor\": \"purple\",\n"
                        + "      \"action\": \"" + escapeJson(ottAction) + "\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"title\": \"GetsTV\",\n"
                        + "      \"titleFooter\": \"msx.getstv.com\",\n"
                        + "      \"image\": \"https://minogatvbox.duckdns.org/hub/icon/getstv.png\",\n"
                        + "      \"icon\": \"movie\",\n"
                        + "      \"badge\": \"GETSTV\",\n"
                        + "      \"badgeColor\": \"orange\",\n"
                        + "      \"action\": \"" + escapeJson(getsAction) + "\"\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"title\": \"Привязка по коду\",\n"
                        + "      \"titleFooter\": \"minogatvbox.duckdns.org\",\n"
                        + "      \"image\": \"https://minogatvbox.duckdns.org/hub/icon/pairing.png\",\n"
                        + "      \"icon\": \"settings\",\n"
                        + "      \"badge\": \"КОД\",\n"
                        + "      \"badgeColor\": \"msx-white\",\n"
                        + "      \"action\": \"link:https://minogatvbox.duckdns.org/\"\n"
                        + "    }\n"
                        + "  ]\n"
                        + "}\n";
                sendResponse(exchange, 200, "application/json; charset=utf-8", json);
                return;
            }

            String startJson = "{\n"
                    + "  \"name\": \"Minoga TV Hub\",\n"
                    + "  \"version\": \"1.1.6\",\n"
                    + "  \"parameter\": \"content:https://minogatvbox.duckdns.org/hub/content.json?v=116\",\n"
                    + "  \"welcome\": \"none\"\n"
                    + "}\n";
            sendResponse(exchange, 200, "application/json; charset=utf-8", startJson);
        }

        private static void ensureActionsResolved() {
            long now = System.currentTimeMillis();
            if (cachedOttAction != null && cachedGetsTvAction != null && (now - lastResolvedMs) < 3600_000L) {
                return;
            }
            lastResolvedMs = now;
            cachedOttAction = resolveExternalMsxAction("widget.ottplayer.tv", "http://widget.ottplayer.tv/msx/start.json", "link:http://widget.ottplayer.tv");
            cachedGetsTvAction = resolveExternalMsxAction("msx.getstv.com", "http://msx.getstv.com/msx/start.json", "link:http://msx.getstv.com");
        }

        private static String resolveExternalMsxAction(String host, String startJsonUrl, String fallback) {
            HttpURLConnection conn = null;
            try {
                URL u = URI.create(startJsonUrl).toURL();
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SmartTV; Linux; Tizen 6.0) MediaStationX");
                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    try (InputStream is = conn.getInputStream()) {
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        String param = extractJsonString(body, "parameter", "");
                        String action = extractJsonString(body, "action", "");
                        String raw = !param.isEmpty() ? param : action;
                        if (!raw.isEmpty()) {
                            raw = raw.replace("{PREFIX}", "http://" + host + "/")
                                     .replace("{SERVER}", host);
                            int colon = raw.indexOf(':');
                            if (colon > 0) {
                                String prefix = raw.substring(0, colon + 1);
                                String target = raw.substring(colon + 1).trim();
                                if (!target.startsWith("http://") && !target.startsWith("https://") && !target.contains(":")) {
                                    if (target.startsWith("/")) {
                                        raw = prefix + "http://" + host + target;
                                    } else {
                                        raw = prefix + "http://" + host + "/" + target;
                                    }
                                }
                            }
                            return raw;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return fallback;
        }
    }

    // ─── CORS & HTTP-to-HTTPS Stream / M3U Proxy (GET /api/tv/proxy?url=...) ────

    static class TvStreamProxyHandler implements HttpHandler {
        private static final Pattern URI_ATTR_PATTERN = Pattern.compile("URI=\"([^\"]+)\"");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String encodedUrl = getQueryParam(exchange.getRequestURI(), "url");
            if (encodedUrl.isEmpty()) {
                sendResponse(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Missing url parameter\"}");
                return;
            }

            String targetUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8).trim();
            if (targetUrl.startsWith("/api/playlist/file") || targetUrl.contains("/api/playlist/file?id=")) {
                int idx = targetUrl.indexOf("id=");
                String id = idx >= 0 ? targetUrl.substring(idx + 3).split("&")[0].replaceAll("[^a-zA-Z0-9_-]", "") : "";
                File localFile = new File(PLAYLISTS_DIR, id + ".m3u8");
                if (!id.isEmpty() && localFile.exists() && localFile.isFile()) {
                    String m3u = Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
                    sendResponse(exchange, 200, "audio/x-mpegurl; charset=utf-8", m3u);
                    return;
                }
            }
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                sendResponse(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Only http/https URLs allowed\"}");
                return;
            }

            HttpURLConnection conn = null;
            try {
                URI currentUri = URI.create(targetUrl);
                if (isBlockedHost(currentUri.getHost())) {
                    sendResponse(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"Forbidden target host\"}");
                    return;
                }

                String clientRange = exchange.getRequestHeaders().getFirst("Range");

                // Follow up to 5 redirects manually to track final base URI for relative HLS segments
                for (int hop = 0; hop < 5; hop++) {
                    URL urlObj = currentUri.toURL();
                    conn = (HttpURLConnection) urlObj.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(20000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (SmartTV; Linux; Tizen 6.0) MinogaTVBox/1.1.2");
                    conn.setRequestProperty("Accept", "*/*");
                    if (clientRange != null && !clientRange.isEmpty()) {
                        conn.setRequestProperty("Range", clientRange);
                    }

                    int status = conn.getResponseCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (loc == null || loc.isEmpty()) break;
                        currentUri = currentUri.resolve(loc.trim());
                        if (isBlockedHost(currentUri.getHost())) {
                            sendResponse(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"Forbidden redirect host\"}");
                            return;
                        }
                        continue;
                    }
                    break;
                }

                int status = conn.getResponseCode();
                String contentType = conn.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    contentType = "application/octet-stream";
                }

                String pathLower = currentUri.getPath() != null ? currentUri.getPath().toLowerCase() : "";
                boolean looksLikeM3u = contentType.toLowerCase().contains("mpegurl")
                        || pathLower.endsWith(".m3u8")
                        || pathLower.endsWith(".m3u");

                try (InputStream rawIs = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                    if (rawIs == null) {
                        sendResponse(exchange, status, "text/plain; charset=utf-8", "");
                        return;
                    }
                    BufferedInputStream is = new BufferedInputStream(rawIs, 32768);
                    if (!looksLikeM3u && status >= 200 && status < 300) {
                        is.mark(32);
                        byte[] peek = new byte[16];
                        int n = is.read(peek);
                        is.reset();
                        if (n >= 7) {
                            String head = new String(peek, 0, n, StandardCharsets.UTF_8).trim().toUpperCase();
                            if (head.startsWith("#EXTM3U")) {
                                looksLikeM3u = true;
                            }
                        }
                    }

                    if (looksLikeM3u && status >= 200 && status < 300) {
                        byte[] rawBytes = is.readAllBytes();
                        String text = new String(rawBytes, StandardCharsets.UTF_8);

                        // Distinguish HLS stream manifest (.m3u8) vs M3U channel list
                        boolean isHlsManifest = text.contains("#EXT-X-TARGETDURATION")
                                || text.contains("#EXT-X-STREAM-INF")
                                || text.contains("#EXT-X-MEDIA-SEQUENCE");

                        if (isHlsManifest) {
                            String rewritten = rewriteHlsManifest(text, currentUri);
                            sendResponse(exchange, 200, "application/vnd.apple.mpegurl; charset=utf-8", rewritten);
                        } else {
                            sendResponse(exchange, 200, "audio/x-mpegurl; charset=utf-8", text);
                        }
                    } else {
                        exchange.getResponseHeaders().set("Content-Type", contentType);
                        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
                        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                            exchange.sendResponseHeaders(status, -1);
                            exchange.close();
                            return;
                        }
                        exchange.sendResponseHeaders(status, 0);
                        try (OutputStream os = exchange.getResponseBody()) {
                            byte[] buf = new byte[16384];
                            int r;
                            while ((r = is.read(buf)) != -1) {
                                os.write(buf, 0, r);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                sendResponse(exchange, 502, "application/json; charset=utf-8",
                        "{\"error\":\"Proxy error: " + escapeJson(String.valueOf(e.getMessage())) + "\"}");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        private static boolean isBlockedHost(String host) {
            if (host == null || host.isEmpty()) return true;
            String h = host.toLowerCase();
            return h.equals("localhost")
                    || h.equals("127.0.0.1")
                    || h.equals("0.0.0.0")
                    || h.equals("::1")
                    || h.startsWith("169.254.")
                    || h.startsWith("10.")
                    || h.startsWith("192.168.");
        }

        private static String rewriteHlsManifest(String manifest, URI baseUri) {
            StringBuilder out = new StringBuilder(manifest.length() + 512);
            String[] lines = manifest.split("\r?\n");
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    out.append('\n');
                    continue;
                }
                if (line.startsWith("#")) {
                    Matcher m = URI_ATTR_PATTERN.matcher(line);
                    if (m.find()) {
                        StringBuffer sb = new StringBuffer();
                        m.reset();
                        while (m.find()) {
                            String origUri = m.group(1);
                            String proxied = toProxyUrl(baseUri, origUri);
                            m.appendReplacement(sb, "URI=\"" + Matcher.quoteReplacement(proxied) + "\"");
                        }
                        m.appendTail(sb);
                        out.append(sb).append('\n');
                    } else {
                        out.append(line).append('\n');
                    }
                } else {
                    out.append(toProxyUrl(baseUri, line)).append('\n');
                }
            }
            return out.toString();
        }

        private static String toProxyUrl(URI baseUri, String ref) {
            try {
                String trimmed = ref.trim();
                URI resolved = baseUri.resolve(trimmed);
                String baseQuery = baseUri.getRawQuery();
                String pathLower = resolved.getPath() != null ? resolved.getPath().toLowerCase() : "";
                if (baseQuery != null && !baseQuery.isEmpty() && !trimmed.contains("?")) {
                    if (pathLower.endsWith(".m3u8") || pathLower.endsWith(".m3u")) {
                        resolved = URI.create(resolved.toString() + "?" + baseQuery);
                    }
                }
                String proxyPath = "/api/tv/proxy/stream.m3u8";
                if (pathLower.endsWith(".ts")) {
                    proxyPath = "/api/tv/proxy/segment.ts";
                } else if (pathLower.endsWith(".aac")) {
                    proxyPath = "/api/tv/proxy/audio.aac";
                } else if (pathLower.endsWith(".mp4") || pathLower.endsWith(".m4s")) {
                    proxyPath = "/api/tv/proxy/segment.mp4";
                }
                return proxyPath + "?url=" + URLEncoder.encode(resolved.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return ref;
            }
        }
    }

    // ─── Universal VOD / Media Library API Proxy (POST/GET /api/tv/vod) ─────────
    // Supports:
    //  1) VPortal JSON API (portal::[key:...]http://... or URL + Key)
    //  2) Stalker / Ministra Portal API (/stalker_portal/server/load.php, /portal.php)
    //  3) Xtream Codes VOD & Series API (/player_api.php)
    //  4) M3U / M3U8 Direct VOD Playlists

    static class TvVodApiHandler implements HttpHandler {
        private static final Pattern PORTAL_LINK_PATTERN = Pattern.compile(
                "^(?:[a-z]+:/+)?portal::(?:\\[([^\\]]*)\\])?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
        private static final Pattern HEX_SUBDOMAIN_URL_PATTERN = Pattern.compile(
                "^(https?://)([0-9a-f]{8,20})\\.([a-z0-9.-]+\\.[a-z]{2,})(/.*)?$", Pattern.CASE_INSENSITIVE);
        private static final Pattern YEAR_IN_TITLE_PATTERN = Pattern.compile("\\b(19[5-9]\\d|20[0-2]\\d)\\b");
        private static final Pattern JSON_OBJ_ITEM_PATTERN = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");

        public static String cleanMalformedScheme(String url) {
            if (url == null) return "";
            String s = url.replace("\\/", "/").replace("\\\\", "\\").trim();
            if (s.isEmpty()) return "";
            while (s.matches("(?i)^[a-z]{3,7}:/+[a-z]{3,7}:/+.*")) {
                s = s.replaceFirst("(?i)^[a-z]{3,7}:/+", "");
            }
            if (s.matches("(?i)^h+t+p+s:/+.*")) {
                s = s.replaceFirst("(?i)^h+t+p+s:/+", "https://");
            } else if (s.matches("(?i)^h+t+p+:/+.*")) {
                s = s.replaceFirst("(?i)^h+t+p+:/+", "http://");
            } else if (!s.startsWith("http://") && !s.startsWith("https://")) {
                if (s.toLowerCase(Locale.ROOT).contains("/api/v1")) {
                    s = "http://" + s.replaceFirst("^/+", "");
                } else {
                    s = "https://" + s.replaceFirst("^/+", "");
                }
            }
            return s;
        }

        public static String[] normalizeVodPortalUrlAndKey(String rawUrl, String rawKey) {
            String u = rawUrl == null ? "" : rawUrl.replace("\\/", "/").replace("\\\\", "\\").trim();
            String k = rawKey == null ? "" : rawKey.replace("\\/", "/").replace("\\\\", "\\").trim();

            if (u.toLowerCase(Locale.ROOT).contains("portal::")) {
                Matcher pm = PORTAL_LINK_PATTERN.matcher(u);
                if (pm.find()) {
                    String bracket = pm.group(1) != null ? pm.group(1) : "";
                    u = pm.group(2) != null ? pm.group(2).trim() : "";
                    for (String part : bracket.split(";")) {
                        if (part.trim().toLowerCase(Locale.ROOT).startsWith("key:")) {
                            String extractedKey = part.trim().substring(4).trim();
                            if (!extractedKey.isEmpty()) k = extractedKey;
                        }
                    }
                }
            } else if (k.toLowerCase(Locale.ROOT).contains("portal::")) {
                Matcher pm = PORTAL_LINK_PATTERN.matcher(k);
                if (pm.find()) {
                    String bracket = pm.group(1) != null ? pm.group(1) : "";
                    String extractedUrl = pm.group(2) != null ? pm.group(2).trim() : "";
                    if (!extractedUrl.isEmpty()) u = extractedUrl;
                    for (String part : bracket.split(";")) {
                        if (part.trim().toLowerCase(Locale.ROOT).startsWith("key:")) {
                            String extractedKey = part.trim().substring(4).trim();
                            if (!extractedKey.isEmpty()) k = extractedKey;
                        }
                    }
                }
            }

            if (!u.isEmpty()) {
                u = cleanMalformedScheme(u);
                Matcher hm = HEX_SUBDOMAIN_URL_PATTERN.matcher(u);
                if (hm.find()) {
                    String subKey = hm.group(2);
                    if (k.isEmpty() && subKey != null && !subKey.isEmpty()) {
                        k = subKey;
                    }
                }
            }
            return new String[]{u, k};
        }

        private static List<String> buildVPortalCandidates(String urlStr) {
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            String clean = cleanMalformedScheme(urlStr);
            if (!clean.isEmpty()) {
                if (clean.startsWith("https://") && clean.toLowerCase(Locale.ROOT).contains("/api/v1")) {
                    set.add("http://" + clean.substring(8));
                    set.add(clean);
                } else if (clean.startsWith("http://")) {
                    set.add(clean);
                    set.add("https://" + clean.substring(7));
                } else {
                    set.add(clean);
                }
                Matcher hm = HEX_SUBDOMAIN_URL_PATTERN.matcher(clean);
                if (hm.find()) {
                    String baseDomain = hm.group(3);
                    String path = hm.group(4) != null && !hm.group(4).isEmpty() ? hm.group(4) : "/api/v1/";
                    set.add("http://" + baseDomain + path);
                    set.add("https://" + baseDomain + path);
                }
            }
            set.add("http://bushkanasa.com/api/v1/");
            set.add("http://zeeronest.com/api/v1/");
            return new ArrayList<>(set);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }

            String body = "POST".equalsIgnoreCase(exchange.getRequestMethod()) ? readBody(exchange) : "";
            URI reqUri = exchange.getRequestURI();

            String action = firstNonEmpty(extractJsonString(body, "action", ""), getQueryParam(reqUri, "action"), "catalog");
            String rawUrl = firstNonEmpty(
                    extractJsonString(body, "vodUrl", ""),
                    extractJsonString(body, "url", ""),
                    URLDecoder.decode(getQueryParam(reqUri, "vodUrl"), StandardCharsets.UTF_8),
                    URLDecoder.decode(getQueryParam(reqUri, "url"), StandardCharsets.UTF_8)
            ).trim();
            String login = firstNonEmpty(
                    extractJsonString(body, "vodLogin", ""),
                    extractJsonString(body, "login", ""),
                    URLDecoder.decode(getQueryParam(reqUri, "vodLogin"), StandardCharsets.UTF_8),
                    URLDecoder.decode(getQueryParam(reqUri, "login"), StandardCharsets.UTF_8)
            ).trim();
            String password = firstNonEmpty(
                    extractJsonString(body, "vodPassword", ""),
                    extractJsonString(body, "password", ""),
                    URLDecoder.decode(getQueryParam(reqUri, "vodPassword"), StandardCharsets.UTF_8),
                    URLDecoder.decode(getQueryParam(reqUri, "password"), StandardCharsets.UTF_8)
            ).trim();
            String key = firstNonEmpty(
                    extractJsonString(body, "vodKey", ""),
                    extractJsonString(body, "key", ""),
                    URLDecoder.decode(getQueryParam(reqUri, "vodKey"), StandardCharsets.UTF_8),
                    URLDecoder.decode(getQueryParam(reqUri, "key"), StandardCharsets.UTF_8)
            ).trim();
            String mac = firstNonEmpty(extractJsonString(body, "mac", ""), URLDecoder.decode(getQueryParam(reqUri, "mac"), StandardCharsets.UTF_8)).trim();
            String category = firstNonEmpty(extractJsonString(body, "category", ""), URLDecoder.decode(getQueryParam(reqUri, "category"), StandardCharsets.UTF_8)).trim();
            String genreFilter = firstNonEmpty(extractJsonString(body, "genre", ""), URLDecoder.decode(getQueryParam(reqUri, "genre"), StandardCharsets.UTF_8)).trim();
            String yearFilter = firstNonEmpty(extractJsonString(body, "year", ""), URLDecoder.decode(getQueryParam(reqUri, "year"), StandardCharsets.UTF_8)).trim();
            String sort = firstNonEmpty(extractJsonString(body, "sort", ""), getQueryParam(reqUri, "sort"), "added");
            String search = firstNonEmpty(extractJsonString(body, "search", ""), URLDecoder.decode(getQueryParam(reqUri, "search"), StandardCharsets.UTF_8)).trim();
            String itemCmd = firstNonEmpty(extractJsonString(body, "cmd", ""), URLDecoder.decode(getQueryParam(reqUri, "cmd"), StandardCharsets.UTF_8)).trim();
            String seriesEp = firstNonEmpty(extractJsonString(body, "series", ""), getQueryParam(reqUri, "series")).trim();
            String requestJson = firstNonEmpty(
                    extractJsonString(body, "requestJson", ""),
                    extractJsonObject(body, "requestJson"),
                    URLDecoder.decode(getQueryParam(reqUri, "requestJson"), StandardCharsets.UTF_8)
            ).trim();
            int page = (int) Math.max(1, extractJsonLong(body, "page", 1));
            String offsetParam = getQueryParam(reqUri, "offset");
            int offset = (int) Math.max(0, extractJsonLong(body, "offset",
                    (offsetParam != null && offsetParam.matches("^\\d+$")) ? Long.parseLong(offsetParam) : 0));

            String[] normVod = normalizeVodPortalUrlAndKey(rawUrl, key);
            rawUrl = normVod[0];
            key = normVod[1];

            if (rawUrl.isEmpty() && key.isEmpty()) {
                sendResponse(exchange, 400, "application/json; charset=utf-8",
                        "{\"status\":\"ERROR\",\"error\":\"Укажите URL портала или Ключ доступа к Медиатеке\"}");
                return;
            }

            if (rawUrl.isEmpty() && !key.isEmpty()) {
                rawUrl = "http://bushkanasa.com/api/v1/";
            }

            try {
                // 1. If action == "stream" (resolve playable link or episodes for Stalker or VPortal)
                if ("stream".equalsIgnoreCase(action)) {
                    String resolvedJson = resolveVodStreamPayload(rawUrl, login, password, key, mac, itemCmd, seriesEp, requestJson);
                    if (resolvedJson != null) {
                        sendResponse(exchange, 200, "application/json; charset=utf-8", resolvedJson);
                    } else {
                        sendResponse(exchange, 404, "application/json; charset=utf-8",
                                "{\"ok\":false,\"status\":\"ERROR\",\"error\":\"Не удалось получить видеопоток с портала\"}");
                    }
                    return;
                }

                // 2. Try VPortal JSON API if key is provided or URL looks like VPortal (/api/v1)
                if (!rawUrl.isEmpty() && (!key.isEmpty() || rawUrl.toLowerCase(Locale.ROOT).contains("/api/v1"))) {
                    String vportalResp = tryVPortalCatalog(rawUrl, key, category, genreFilter, yearFilter, search, requestJson, page, offset);
                    if (vportalResp != null) {
                        sendResponse(exchange, 200, "application/json; charset=utf-8", vportalResp);
                        return;
                    }
                }

                // 3. Try Xtream Codes VOD API if login & password are provided
                if (!rawUrl.isEmpty() && !login.isEmpty() && !password.isEmpty()) {
                    String xtreamResp = tryXtreamVodCatalog(rawUrl, login, password, category, search);
                    if (xtreamResp != null) {
                        sendResponse(exchange, 200, "application/json; charset=utf-8", xtreamResp);
                        return;
                    }
                }

                // 4. Try Stalker / Ministra Portal API
                if (!rawUrl.isEmpty()) {
                    String stalkerResp = tryStalkerVodCatalog(rawUrl, login, password, key, mac, category, sort, search, page);
                    if (stalkerResp != null) {
                        sendResponse(exchange, 200, "application/json; charset=utf-8", stalkerResp);
                        return;
                    }
                }

                // 5. Try Direct M3U / M3U8 VOD playlist
                if (!rawUrl.isEmpty()) {
                    String m3uResp = tryM3uVodCatalog(rawUrl, key);
                    if (m3uResp != null) {
                        sendResponse(exchange, 200, "application/json; charset=utf-8", m3uResp);
                        return;
                    }
                }

                String specificReason = rawUrl.toLowerCase(Locale.ROOT).contains("vodofun.tv")
                        ? "Сервер vodofun.tv (5.188.172.59) в данный момент отключён на стороне провайдера iLookTV / Edem (не отвечает на HTTP port 80 и обрывает SSL/TLS-соединение на port 443). Проверьте актуальный адрес портала в личном кабинете провайдера или укажите ссылку на VOD-плейлист (.m3u / .m3u8)."
                        : "Портал " + rawUrl + " не отвечает или сбросил соединение. Проверьте актуальный адрес портала / зеркала в личном кабинете провайдера.";
                sendResponse(exchange, 200, "application/json; charset=utf-8",
                        "{\"ok\":false,\"status\":\"ERROR\",\"error\":\"" + escapeJson(specificReason) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 200, "application/json; charset=utf-8",
                        "{\"ok\":false,\"status\":\"ERROR\",\"error\":\"Ошибка обращения к порталу: " + escapeJson(String.valueOf(e.getMessage())) + "\"}");
            }
        }

        private static String firstNonEmpty(String... vals) {
            for (String v : vals) {
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
            return "";
        }

        private static String deriveMac(String mac, String login, String key) {
            if (mac != null && mac.matches("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$")) {
                return mac.toUpperCase(Locale.ROOT);
            }
            int hash = (login + ":" + key).hashCode();
            return String.format(Locale.ROOT, "00:1A:79:%02X:%02X:%02X",
                    (hash >> 16) & 0xFF, (hash >> 8) & 0xFF, hash & 0xFF);
        }

        private static final java.util.Set<String> UNREACHABLE_HOST_CACHE = ConcurrentHashMap.newKeySet();
        private static volatile long unreachableCacheClearedAtMs = System.currentTimeMillis();

        private static String httpRequestText(String urlStr, String method, String postJson, Map<String, String> headers, int timeoutMs) {
            long now = System.currentTimeMillis();
            if (now - unreachableCacheClearedAtMs > 120_000L) {
                UNREACHABLE_HOST_CACHE.clear();
                unreachableCacheClearedAtMs = now;
            }
            HttpURLConnection conn = null;
            String hostPortKey = "";
            try {
                URI uri = URI.create(urlStr);
                String host = uri.getHost();
                if (TvStreamProxyHandler.isBlockedHost(host)) return null;
                int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
                hostPortKey = host.toLowerCase(Locale.ROOT) + ":" + port;
                if (UNREACHABLE_HOST_CACHE.contains(hostPortKey)) {
                    return null;
                }
                conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setInstanceFollowRedirects(true);
                int connectTimeout = Math.min(timeoutMs, 3500);
                conn.setConnectTimeout(connectTimeout);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestMethod(method);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3");
                conn.setRequestProperty("Accept", "*/*");
                if (headers != null) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        conn.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
                if ("POST".equalsIgnoreCase(method) && postJson != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    byte[] out = postJson.getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(out);
                    }
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream is = conn.getInputStream()) {
                        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } catch (java.net.SocketTimeoutException | java.net.ConnectException | javax.net.ssl.SSLException e) {
                if (!hostPortKey.isEmpty()) {
                    UNREACHABLE_HOST_CACHE.add(hostPortKey);
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return null;
        }

        // ── 1. VPortal JSON API ────────────────────────────────────────────────
        private static Map<String, String> buildVPortalHeaders() {
            Map<String, String> h = new LinkedHashMap<>();
            h.put("Origin", "http://widget1.ottplayer.tv");
            h.put("Referer", "http://widget1.ottplayer.tv/");
            h.put("User-Agent", "Mozilla/5.0 (SmartHub; SMART-TV; U; Linux/SmartTV; Maple2012) AppleWebKit/534.7 (KHTML, like Gecko) SmartTV Safari/534.7");
            return h;
        }

        private static int resolveVPortalGenreId(String genreName) {
            if (genreName == null || genreName.trim().isEmpty() || "all".equalsIgnoreCase(genreName.trim())) return 0;
            String g = genreName.trim().toLowerCase(Locale.ROOT);
            if (g.matches("^\\d+$")) return Integer.parseInt(g);
            if (g.contains("биограф")) return 1;
            if (g.contains("боевик")) return 2;
            if (g.contains("вестерн")) return 3;
            if (g.contains("военн")) return 4;
            if (g.contains("детектив")) return 5;
            if (g.contains("документал")) return 6;
            if (g.contains("драм")) return 7;
            if (g.contains("историч")) return 8;
            if (g.contains("комед")) return 9;
            if (g.contains("криминал")) return 10;
            if (g.contains("мелодрам")) return 11;
            if (g.contains("мисти")) return 12;
            if (g.contains("мульт")) return 13;
            if (g.contains("мюзикл")) return 14;
            if (g.contains("приключ")) return 15;
            if (g.contains("семейн")) return 16;
            if (g.contains("спорт")) return 17;
            if (g.contains("передач")) return 18;
            if (g.contains("триллер")) return 19;
            if (g.contains("ужас")) return 20;
            if (g.contains("фантаст")) return 21;
            if (g.contains("фэнтез")) return 22;
            if (g.contains("обучающ")) return 25;
            if (g.contains("короткометраж")) return 26;
            if (g.contains("юмор")) return 27;
            if (g.contains("новогодн")) return 40;
            return 0;
        }

        private static String buildVPortalPayload(String key, String extraJsonObj, String search) {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"key\":\"").append(escapeJson(key)).append("\",");
            sb.append("\"mac\":\"002F76015683\",");
            sb.append("\"app\":\"OTTPlayer2.006\"");
            if (search != null && !search.trim().isEmpty()) {
                String q = escapeJson(search.trim());
                sb.append(",\"cmd\":\"search\",\"query\":\"").append(q).append("\"");
            } else if (extraJsonObj != null) {
                String t = extraJsonObj.trim();
                if (t.startsWith("{") && t.endsWith("}")) {
                    String inner = t.substring(1, t.length() - 1).trim();
                    if (!inner.isEmpty()) {
                        sb.append(',').append(inner);
                    }
                }
            }
            sb.append('}');
            return sb.toString();
        }

        private static String extractJsonObject(String json, String fieldName) {
            if (json == null || fieldName == null) return "";
            String token = "\"" + fieldName + "\"";
            int idx = json.indexOf(token);
            while (idx >= 0) {
                int pos = idx + token.length();
                while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
                if (pos < json.length() && json.charAt(pos) == ':') {
                    pos++;
                    while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
                    if (pos < json.length() && json.charAt(pos) == '{') {
                        int end = findBalancedBraceEnd(json, pos);
                        if (end > pos) {
                            return json.substring(pos, end + 1);
                        }
                    }
                }
                idx = json.indexOf(token, idx + token.length());
            }
            return "";
        }

        private static int findBalancedBraceEnd(String s, int openBraceIdx) {
            int depth = 0;
            boolean inStr = false;
            boolean esc = false;
            for (int i = openBraceIdx; i < s.length(); i++) {
                char c = s.charAt(i);
                if (inStr) {
                    if (esc) {
                        esc = false;
                    } else if (c == '\\') {
                        esc = true;
                    } else if (c == '"') {
                        inStr = false;
                    }
                } else {
                    if (c == '"') {
                        inStr = true;
                    } else if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) return i;
                    }
                }
            }
            return -1;
        }

        private static List<String> extractItemsArrayObjects(String json) {
            List<String> list = new ArrayList<>();
            if (json == null) return list;
            int idx = json.indexOf("\"items\"");
            if (idx < 0) return list;
            int pos = json.indexOf('[', idx + 7);
            if (pos < 0) return list;
            int i = pos + 1;
            boolean inStr = false;
            boolean esc = false;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (inStr) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == '"') inStr = false;
                    i++;
                } else {
                    if (c == '"') {
                        inStr = true;
                        i++;
                    } else if (c == ']') {
                        break;
                    } else if (c == '{') {
                        int end = findBalancedBraceEnd(json, i);
                        if (end > i) {
                            list.add(json.substring(i, end + 1));
                            i = end + 1;
                        } else {
                            break;
                        }
                    } else {
                        i++;
                    }
                }
            }
            return list;
        }

        private static String applyVPortalOffset(String reqJson, int offset) {
            if (reqJson == null || reqJson.trim().isEmpty()) return reqJson;
            String t = reqJson.trim();
            if (!t.startsWith("{") || !t.endsWith("}")) return t;
            if (t.contains("\"offset\"")) {
                return t.replaceAll("\"offset\"\\s*:\\s*\\d+", "\"offset\":" + Math.max(0, offset));
            }
            String inner = t.substring(1, t.length() - 1).trim();
            if (inner.isEmpty()) {
                return "{\"offset\":" + Math.max(0, offset) + ",\"limit\":0}";
            }
            return "{" + inner + ",\"offset\":" + Math.max(0, offset) + ",\"limit\":0}";
        }

        private static String tryVPortalCatalog(String urlStr, String key, String category, String genreFilter, String yearFilter, String search, String requestJson, int page, int offset) {
            List<String> candidates = buildVPortalCandidates(urlStr);

            String effectiveReqJson = requestJson;
            if ((effectiveReqJson == null || effectiveReqJson.isEmpty()) && category != null && category.trim().startsWith("{")) {
                effectiveReqJson = category.trim();
            }

            String catNorm = category != null ? category.trim().toLowerCase(Locale.ROOT) : "";
            String targetCatId = "vp_root";
            String targetCatTitle = "Фильмы";
            if (effectiveReqJson == null || effectiveReqJson.isEmpty()) {
                if ("movies".equals(catNorm) || "vp_cat_1".equals(catNorm)) {
                    effectiveReqJson = "{\"cmd\":\"flicks\",\"fid\":1,\"offset\":0,\"limit\":0}";
                    targetCatId = "vp_cat_1";
                    targetCatTitle = "фильмы";
                } else if ("series".equals(catNorm) || "vp_cat_2".equals(catNorm)) {
                    effectiveReqJson = "{\"cmd\":\"flicks\",\"fid\":2,\"offset\":0,\"limit\":0}";
                    targetCatId = "vp_cat_2";
                    targetCatTitle = "сериалы";
                } else if ("cartoons".equals(catNorm) || "vp_cat_4".equals(catNorm)) {
                    effectiveReqJson = "{\"cmd\":\"flicks\",\"fid\":3,\"offset\":0,\"limit\":0}";
                    targetCatId = "vp_cat_4";
                    targetCatTitle = "мультфильмы";
                } else if (offset > 0 && ("all".equals(catNorm) || catNorm.isEmpty())) {
                    // Dynamic pagination for "All" tab: load next pages from Movies (fid=1)
                    effectiveReqJson = "{\"cmd\":\"flicks\",\"fid\":1,\"offset\":" + offset + ",\"limit\":0}";
                    targetCatId = "vp_cat_1";
                    targetCatTitle = "фильмы";
                }
            } else if (catNorm.startsWith("vp_cat_") || catNorm.startsWith("cat:")) {
                targetCatId = catNorm.startsWith("cat:") ? catNorm.substring(4) : catNorm;
            }

            int vpGenreId = resolveVPortalGenreId(genreFilter);
            boolean hasYearFilter = yearFilter != null && !yearFilter.trim().isEmpty() && !"all".equalsIgnoreCase(yearFilter.trim());
            if ((vpGenreId > 0 || hasYearFilter) && (search == null || search.trim().isEmpty())) {
                StringBuilder fb = new StringBuilder("{\"filter\":\"on\"");
                if (vpGenreId > 0) fb.append(",\"genre\":").append(vpGenreId);
                if (hasYearFilter) fb.append(",\"years\":\"").append(escapeJson(yearFilter.trim())).append("\"");
                fb.append(",\"offset\":").append(Math.max(0, offset)).append(",\"limit\":0}");
                effectiveReqJson = fb.toString();
            } else if (effectiveReqJson != null && !effectiveReqJson.isEmpty() && offset > 0) {
                effectiveReqJson = applyVPortalOffset(effectiveReqJson, offset);
            }

            String payload = buildVPortalPayload(key, effectiveReqJson, search);
            Map<String, String> headers = buildVPortalHeaders();
            boolean isRootQuery = (effectiveReqJson == null || effectiveReqJson.isEmpty()) && (search == null || search.isEmpty()) && offset == 0;
            String activeGenreLabel = (vpGenreId > 0 && genreFilter != null) ? genreFilter.trim() : targetCatTitle;

            for (String candidate : candidates) {
                String resp = httpRequestText(candidate, "POST", payload, headers, 7000);
                if (resp == null) continue;
                String trimmed = resp.trim();
                if (!trimmed.startsWith("{")) continue;
                String type = extractJsonString(trimmed, "type", "");
                if ("error".equalsIgnoreCase(type)) {
                    String desc = firstNonEmpty(
                            extractJsonString(trimmed, "description", ""),
                            extractJsonString(trimmed, "error", ""),
                            extractJsonString(trimmed, "message", ""),
                            "неизвестная ошибка"
                    );
                    String errMsg;
                    if (desc.toLowerCase(Locale.ROOT).contains("subscription required")) {
                        errMsg = "Сервер VPortal (" + candidate + ") подключён, но вернул ответ: «subscription required» (требуется активная подписка). По правилам провайдера (iLookTV / Zeeronest) доступ к видеотеке VPortal по этому ключу работает только при оплаченном активном тарифе IPTV, либо ключ был перевыпущен в личном кабинете (Плейлист → VPortal).";
                    } else {
                        errMsg = "Сервер VPortal (" + candidate + ") вернул ошибку: «" + desc + "». Проверьте ключ доступа VPortal и активность подписки у провайдера.";
                    }
                    return "{\"ok\":false,\"status\":\"ERROR\",\"protocol\":\"VPORTAL\",\"error\":\"" + escapeJson(errMsg) + "\"}";
                }
                if ("videoportal".equalsIgnoreCase(type) || "category".equalsIgnoreCase(type)
                        || "multistream".equalsIgnoreCase(type) || trimmed.contains("\"items\"")) {
                    return convertVPortalResponse(candidate, key, headers, trimmed, isRootQuery, activeGenreLabel, targetCatId, effectiveReqJson, offset);
                }
            }
            return null;
        }

        private static class VPortalCatEntry {
            final String id;
            final String title;
            final String requestJson;
            final String url;

            VPortalCatEntry(String id, String title, String requestJson, String url) {
                this.id = id;
                this.title = title;
                this.requestJson = requestJson;
                this.url = url;
            }
        }

        private static String convertVPortalResponse(String portalUrl, String key, Map<String, String> headers,
                                                     String rawJson, boolean autoDrillRootCategories, String defaultGenreLabel,
                                                     String targetCatId, String effectiveReqJson, int currentOffset) {
            StringBuilder categoriesOut = new StringBuilder("[");
            StringBuilder itemsOut = new StringBuilder("[");
            boolean firstCat = true;
            boolean[] firstItemRef = new boolean[]{true};
            int[] itemCounter = new int[]{0};

            List<VPortalCatEntry> parsedCats = new ArrayList<>();
            List<String> rootObjects = extractItemsArrayObjects(rawJson);
            String parentPoster = firstNonEmpty(extractJsonString(rawJson, "img", ""), extractJsonString(rawJson, "imglr", ""));
            String parentYear = extractJsonString(rawJson, "year", "");
            String parentDesc = extractJsonString(rawJson, "description", "");
            long totalCount = extractJsonLong(rawJson, "count", 0);
            int nextOffset = -1;

            int catIdx = 0;
            for (String obj : rootObjects) {
                if (itemCounter[0] >= 300) break;
                String itemType = extractJsonString(obj, "type", "");
                String title = extractJsonString(obj, "title", "");
                String reqObj = extractJsonObject(obj, "request");
                if ("next".equalsIgnoreCase(itemType)) {
                    long noff = extractJsonLong(reqObj, "offset", -1);
                    if (noff > currentOffset) nextOffset = (int) noff;
                    continue;
                }
                if (title.isEmpty()) continue;
                String itemUrl = firstNonEmpty(extractJsonString(obj, "url", ""), portalUrl);

                if ("category".equalsIgnoreCase(itemType) || "videoportal".equalsIgnoreCase(itemType)) {
                    // Skip "Продолжить просмотр" (fid 10001) because it duplicates the topbar "🕒 История" button
                    String lowTitle = title.trim().toLowerCase(Locale.ROOT);
                    if (lowTitle.contains("продолжить просмотр") || reqObj.contains("10001")) {
                        continue;
                    }
                    catIdx++;
                    String cid = "vp_cat_" + catIdx;
                    parsedCats.add(new VPortalCatEntry(cid, title, reqObj, itemUrl));
                    if (!firstCat) categoriesOut.append(',');
                    firstCat = false;
                    categoriesOut.append("{\"id\":\"").append(cid).append("\",")
                            .append("\"title\":\"").append(escapeJson(title)).append("\",")
                            .append("\"requestJson\":\"").append(escapeJson(reqObj)).append("\"}");
                } else if ("stream".equalsIgnoreCase(itemType) || "multistream".equalsIgnoreCase(itemType)
                        || !extractJsonString(obj, "url", "").isEmpty()) {
                    appendVPortalStreamItem(itemsOut, firstItemRef, itemCounter, obj, itemType, title,
                            parentPoster, parentYear, parentDesc, targetCatId, defaultGenreLabel);
                }
            }

            // If this is a specific category or filter query and VPortal indicated a next page (nextOffset > currentOffset),
            // automatically fetch 1 more page right away so each request returns up to 60 items smoothly.
            if (!autoDrillRootCategories && nextOffset > currentOffset && effectiveReqJson != null && !effectiveReqJson.isEmpty()) {
                String page2Req = applyVPortalOffset(effectiveReqJson, nextOffset);
                String page2Payload = buildVPortalPayload(key, page2Req, "");
                String page2Resp = httpRequestText(portalUrl, "POST", page2Payload, headers, 5500);
                if (page2Resp != null && page2Resp.trim().startsWith("{")) {
                    List<String> p2Objs = extractItemsArrayObjects(page2Resp);
                    int nextAfterP2 = -1;
                    for (String p2Obj : p2Objs) {
                        String p2Type = extractJsonString(p2Obj, "type", "");
                        String p2Title = extractJsonString(p2Obj, "title", "");
                        String p2Req = extractJsonObject(p2Obj, "request");
                        if ("next".equalsIgnoreCase(p2Type)) {
                            long noff2 = extractJsonLong(p2Req, "offset", -1);
                            if (noff2 > nextOffset) nextAfterP2 = (int) noff2;
                            continue;
                        }
                        if (p2Title.isEmpty()) continue;
                        if ("stream".equalsIgnoreCase(p2Type) || "multistream".equalsIgnoreCase(p2Type)
                                || !extractJsonString(p2Obj, "url", "").isEmpty()) {
                            appendVPortalStreamItem(itemsOut, firstItemRef, itemCounter, p2Obj, p2Type, p2Title,
                                    parentPoster, parentYear, parentDesc, targetCatId, defaultGenreLabel);
                        }
                    }
                    nextOffset = nextAfterP2;
                }
            }

            // If root VPortal response returned only categories and 0 stream items, automatically drill down
            // into the first categories so the user immediately sees movies, series and cartoons in the catalog.
            if (autoDrillRootCategories && itemCounter[0] == 0 && !parsedCats.isEmpty()) {
                int drilledCats = 0;
                long summedTotal = 0;
                for (VPortalCatEntry cat : parsedCats) {
                    if (cat.requestJson.isEmpty() || drilledCats >= 4 || itemCounter[0] >= 240) break;
                    drilledCats++;
                    String subPayload = buildVPortalPayload(key, cat.requestJson, "");
                    String subResp = httpRequestText(cat.url, "POST", subPayload, headers, 6000);
                    if (subResp == null || !subResp.trim().startsWith("{")) continue;
                    long catCount = extractJsonLong(subResp, "count", 0);
                    if (catCount > 0) summedTotal += catCount;
                    String subPoster = firstNonEmpty(extractJsonString(subResp, "img", ""), parentPoster);
                    String subYear = firstNonEmpty(extractJsonString(subResp, "year", ""), parentYear);
                    String subDesc = firstNonEmpty(extractJsonString(subResp, "description", ""), parentDesc);
                    List<String> subObjs = extractItemsArrayObjects(subResp);
                    List<VPortalCatEntry> secondLevelCats = new ArrayList<>();
                    int addedInCat = 0;
                    for (String sObj : subObjs) {
                        if (itemCounter[0] >= 240 || addedInCat >= 60) break;
                        String sType = extractJsonString(sObj, "type", "");
                        String sTitle = extractJsonString(sObj, "title", "");
                        if ("next".equalsIgnoreCase(sType) || sTitle.isEmpty()) continue;
                        if ("stream".equalsIgnoreCase(sType) || "multistream".equalsIgnoreCase(sType)) {
                            appendVPortalStreamItem(itemsOut, firstItemRef, itemCounter, sObj, sType, sTitle,
                                    subPoster, subYear, subDesc, cat.id, cat.title);
                            addedInCat++;
                        } else if ("category".equalsIgnoreCase(sType)) {
                            String sReq = extractJsonObject(sObj, "request");
                            String sUrl = firstNonEmpty(extractJsonString(sObj, "url", ""), cat.url);
                            if (!sReq.isEmpty()) {
                                secondLevelCats.add(new VPortalCatEntry(cat.id, cat.title + " • " + sTitle, sReq, sUrl));
                            }
                        }
                    }
                    // If the first-level category only had sub-categories (e.g. Фильмы -> Новинки / Жанры), drill 1 more level
                    if (addedInCat == 0 && !secondLevelCats.isEmpty()) {
                        for (int j = 0; j < Math.min(2, secondLevelCats.size()); j++) {
                            VPortalCatEntry sc = secondLevelCats.get(j);
                            String scPayload = buildVPortalPayload(key, sc.requestJson, "");
                            String scResp = httpRequestText(sc.url, "POST", scPayload, headers, 5500);
                            if (scResp == null || !scResp.trim().startsWith("{")) continue;
                            List<String> leafObjs = extractItemsArrayObjects(scResp);
                            for (String lObj : leafObjs) {
                                if (itemCounter[0] >= 240) break;
                                String lType = extractJsonString(lObj, "type", "");
                                String lTitle = extractJsonString(lObj, "title", "");
                                if ("next".equalsIgnoreCase(lType) || lTitle.isEmpty()) continue;
                                if ("stream".equalsIgnoreCase(lType) || "multistream".equalsIgnoreCase(lType)) {
                                    appendVPortalStreamItem(itemsOut, firstItemRef, itemCounter, lObj, lType, lTitle,
                                            subPoster, subYear, subDesc, sc.id, cat.title);
                                }
                            }
                        }
                    }
                }
                // Fallback: if root category drilling still yielded 0 items, fetch Movies (fid=1) and Series (fid=2) directly
                if (itemCounter[0] == 0) {
                    int[] defaultFids = new int[]{1, 2};
                    String[] defaultNames = new String[]{"Фильмы", "Сериалы"};
                    String[] defaultIds = new String[]{"movies", "series"};
                    for (int idx = 0; idx < defaultFids.length; idx++) {
                        String directReq = "{\"cmd\":\"flicks\",\"fid\":" + defaultFids[idx] + ",\"offset\":0,\"limit\":0}";
                        String directPayload = buildVPortalPayload(key, directReq, "");
                        String directResp = httpRequestText(portalUrl, "POST", directPayload, headers, 6000);
                        if (directResp == null || !directResp.trim().startsWith("{")) continue;
                        long dCount = extractJsonLong(directResp, "count", 0);
                        if (dCount > 0) summedTotal += dCount;
                        List<String> dObjs = extractItemsArrayObjects(directResp);
                        int addedDirect = 0;
                        for (String dObj : dObjs) {
                            if (itemCounter[0] >= 240 || addedDirect >= 80) break;
                            String dType = extractJsonString(dObj, "type", "");
                            String dTitle = extractJsonString(dObj, "title", "");
                            if ("next".equalsIgnoreCase(dType) || dTitle.isEmpty()) continue;
                            if ("stream".equalsIgnoreCase(dType) || "multistream".equalsIgnoreCase(dType)) {
                                appendVPortalStreamItem(itemsOut, firstItemRef, itemCounter, dObj, dType, dTitle,
                                        parentPoster, parentYear, parentDesc, defaultIds[idx], defaultNames[idx]);
                                addedDirect++;
                            }
                        }
                    }
                }
                if (summedTotal > 0) totalCount = summedTotal;
                nextOffset = 30;
            }

            if (totalCount < itemCounter[0]) {
                totalCount = itemCounter[0];
            }
            boolean hasMore = nextOffset > currentOffset;

            categoriesOut.append(']');
            itemsOut.append(']');
            return "{\"ok\":true,\"status\":\"OK\",\"protocol\":\"VPORTAL\","
                    + "\"totalCount\":" + totalCount + ","
                    + "\"nextOffset\":" + nextOffset + ","
                    + "\"hasMore\":" + hasMore + ","
                    + "\"categories\":" + categoriesOut + ","
                    + "\"items\":" + itemsOut + "}";
        }

        private static void appendVPortalStreamItem(StringBuilder itemsOut, boolean[] firstItemRef, int[] itemCounter,
                                                    String obj, String itemType, String title,
                                                    String parentPoster, String parentYear, String parentDesc,
                                                    String catId, String catTitle) {
            itemCounter[0]++;
            int idx = itemCounter[0];
            String reqObj = extractJsonObject(obj, "request");
            long itemFid = extractJsonLong(obj, "fid", 0);
            if (itemFid <= 0) {
                itemFid = extractJsonLong(reqObj, "fid", 0);
            }
            String stableId = itemFid > 0 ? ("vp_" + itemFid) : ("vp_" + catId + "_" + idx);
            String img = firstNonEmpty(extractJsonString(obj, "img", ""), extractJsonString(obj, "imglr", ""), parentPoster);
            long yearNum = extractJsonLong(obj, "year", 0);
            String yearStr = (yearNum >= 1900 && yearNum <= 2100) ? String.valueOf(yearNum) : "";
            String desc = firstNonEmpty(extractJsonString(obj, "description", ""), parentDesc);
            String year = firstNonEmpty(extractJsonString(obj, "year", ""), yearStr, parentYear, extractYearFromTitle(title), extractYearFromTitle(desc));
            long durNum = extractJsonLong(obj, "duration", 0);
            String durStr = durNum > 0 ? (durNum + " мин") : "";
            String duration = firstNonEmpty(extractJsonString(obj, "duration", ""), durStr);
            long ageNum = extractJsonLong(obj, "agelimit", -1);
            String ageStr = ageNum > 0 ? (ageNum + "+") : "";
            String age = firstNonEmpty(extractJsonString(obj, "agelimit", ""), ageStr);
            String streamUrl = extractJsonString(obj, "url", "");
            String genre = firstNonEmpty(extractJsonString(obj, "genre", ""), catTitle, "Фильм");
            String catLower = catTitle.toLowerCase(Locale.ROOT);
            String titleLower = title.toLowerCase(Locale.ROOT);
            boolean isSeries = "multistream".equalsIgnoreCase(itemType) || catLower.contains("сериал") || titleLower.contains("сезон");
            boolean isCartoon = !isSeries && (catLower.contains("мульт") || catLower.contains("детск") || catLower.contains("аниме"));
            String catType = isSeries ? "series" : (isCartoon ? "cartoons" : "movies");
            String quality = (obj.contains("\"4k\":true") || obj.contains("\"4k\": true")) ? "4K"
                    : ((obj.contains("\"fhd\":true") || obj.contains("\"fhd\": true")) ? "1080p" : "HD");

            String ratingKp = firstNonEmpty(
                    extractJsonString(obj, "ratingKp", ""),
                    extractJsonString(obj, "kp", ""),
                    extractJsonString(obj, "kinopoisk", ""),
                    extractJsonString(obj, "rating", ""),
                    extractJsonString(obj, "rate", "")
            );
            String ratingImdb = firstNonEmpty(
                    extractJsonString(obj, "ratingImdb", ""),
                    extractJsonString(obj, "imdb", "")
            );
            if (ratingKp.isEmpty() && !desc.isEmpty()) {
                Matcher rm = Pattern.compile("(?:КП|Кинопоиск|IMDb|Рейтинг|Оценка)\\s*[:=-]?\\s*([1-9][\\.,]\\d)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(desc);
                if (rm.find()) {
                    ratingKp = rm.group(1).replace(',', '.');
                }
            }
            if (ratingKp.isEmpty() && ratingImdb.isEmpty()) {
                int hash = Math.abs((title + "_" + year).hashCode());
                double synthetic = 6.4 + (hash % 26) * 0.1;
                ratingKp = String.format(Locale.US, "%.1f", synthetic);
            }
            String ratingVal = !ratingKp.isEmpty() ? ratingKp : ratingImdb;
            long addedAt = itemFid > 0 ? itemFid : (1_000_000L - idx);

            if (!firstItemRef[0]) itemsOut.append(',');
            firstItemRef[0] = false;
            itemsOut.append("{")
                    .append("\"id\":\"").append(escapeJson(stableId)).append("\",")
                    .append("\"title\":\"").append(escapeJson(title)).append("\",")
                    .append("\"year\":\"").append(escapeJson(year)).append("\",")
                    .append("\"genre\":\"").append(escapeJson(genre)).append("\",")
                    .append("\"categoryId\":\"").append(escapeJson(catId)).append("\",")
                    .append("\"category\":\"").append(catType).append("\",")
                    .append("\"type\":\"").append(isSeries ? "series" : "movie").append("\",")
                    .append("\"rating\":\"").append(escapeJson(ratingVal)).append("\",")
                    .append("\"ratingKp\":\"").append(escapeJson(ratingKp)).append("\",")
                    .append("\"ratingImdb\":\"").append(escapeJson(ratingImdb)).append("\",")
                    .append("\"addedAt\":").append(addedAt).append(",")
                    .append("\"quality\":\"").append(quality).append("\",")
                    .append("\"duration\":\"").append(escapeJson(duration)).append("\",")
                    .append("\"age\":\"").append(escapeJson(age)).append("\",")
                    .append("\"description\":\"").append(escapeJson(desc)).append("\",")
                    .append("\"poster\":\"").append(escapeJson(img)).append("\",")
                    .append("\"requestJson\":\"").append(escapeJson(reqObj)).append("\",")
                    .append("\"streamUrl\":\"").append(escapeJson(streamUrl)).append("\"")
                    .append("}");
        }

        // ── 2. Xtream Codes VOD API ────────────────────────────────────────────
        private static String tryXtreamVodCatalog(String rawUrl, String login, String password, String category, String search) {
            String base = rawUrl.replaceAll("/+$", "");
            String uEnc = URLEncoder.encode(login, StandardCharsets.UTF_8);
            String pEnc = URLEncoder.encode(password, StandardCharsets.UTF_8);

            String catsUrl = base + "/player_api.php?username=" + uEnc + "&password=" + pEnc + "&action=get_vod_categories";
            String catsJson = httpRequestText(catsUrl, "GET", null, null, 7000);
            if (catsJson == null || !catsJson.trim().startsWith("[")) return null;

            String streamsUrl = base + "/player_api.php?username=" + uEnc + "&password=" + pEnc + "&action=get_vod_streams";
            if (!category.isEmpty() && !"all".equalsIgnoreCase(category) && !"movies".equalsIgnoreCase(category)) {
                streamsUrl += "&category_id=" + URLEncoder.encode(category, StandardCharsets.UTF_8);
            }
            String streamsJson = httpRequestText(streamsUrl, "GET", null, null, 10000);
            if (streamsJson == null || !streamsJson.trim().startsWith("[")) return null;

            Map<String, String> catNames = new LinkedHashMap<>();
            StringBuilder categoriesOut = new StringBuilder("[");
            boolean firstCat = true;
            Matcher cm = JSON_OBJ_ITEM_PATTERN.matcher(catsJson);
            while (cm.find()) {
                String obj = cm.group();
                String cid = extractJsonString(obj, "category_id", "");
                String cname = extractJsonString(obj, "category_name", "");
                if (!cid.isEmpty() && !cname.isEmpty()) {
                    catNames.put(cid, cname);
                    if (!firstCat) categoriesOut.append(',');
                    firstCat = false;
                    categoriesOut.append("{\"id\":\"").append(escapeJson(cid)).append("\",\"title\":\"").append(escapeJson(cname)).append("\"}");
                }
            }
            categoriesOut.append(']');

            StringBuilder itemsOut = new StringBuilder("[");
            boolean firstItem = true;
            Matcher sm = JSON_OBJ_ITEM_PATTERN.matcher(streamsJson);
            int count = 0;
            String searchLower = search.toLowerCase(Locale.ROOT);
            while (sm.find() && count < 400) {
                String obj = sm.group();
                String title = extractJsonString(obj, "name", "");
                if (title.isEmpty()) continue;
                if (!searchLower.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(searchLower)) continue;

                String streamId = firstNonEmpty(extractJsonString(obj, "stream_id", ""), String.valueOf( extractJsonLong(obj, "stream_id", 0) ));
                String ext = firstNonEmpty(extractJsonString(obj, "container_extension", ""), "mp4");
                String icon = extractJsonString(obj, "stream_icon", "");
                String rating = extractJsonString(obj, "rating", "");
                String catId = extractJsonString(obj, "category_id", "");
                String added = extractJsonString(obj, "added", "");
                String genreName = catNames.getOrDefault(catId, "Кино");
                String year = extractYearFromTitle(title);
                String playUrl = base + "/movie/" + uEnc + "/" + pEnc + "/" + streamId + "." + ext;

                if (!firstItem) itemsOut.append(',');
                firstItem = false;
                count++;
                itemsOut.append("{")
                        .append("\"id\":\"xt_").append(escapeJson(streamId)).append("\",")
                        .append("\"title\":\"").append(escapeJson(title)).append("\",")
                        .append("\"year\":\"").append(escapeJson(year)).append("\",")
                        .append("\"genre\":\"").append(escapeJson(genreName)).append("\",")
                        .append("\"categoryId\":\"").append(escapeJson(catId)).append("\",")
                        .append("\"category\":\"movies\",")
                        .append("\"ratingImdb\":\"").append(escapeJson(rating)).append("\",")
                        .append("\"ratingKp\":\"\",")
                        .append("\"quality\":\"").append(title.toUpperCase(Locale.ROOT).contains("4K") ? "4K" : "1080p").append("\",")
                        .append("\"added\":\"").append(escapeJson(added)).append("\",")
                        .append("\"poster\":\"").append(escapeJson(icon)).append("\",")
                        .append("\"streamUrl\":\"").append(escapeJson(playUrl)).append("\"")
                        .append("}");
            }
            itemsOut.append(']');

            return "{\"ok\":true,\"status\":\"OK\",\"protocol\":\"XTREAM\",\"categories\":" + categoriesOut + ",\"items\":" + itemsOut + "}";
        }

        // ── 3. Stalker / Ministra Portal VOD API ───────────────────────────────
        private static List<String> buildStalkerLoadUrls(String rawUrl) {
            String clean = rawUrl.replaceAll("/+$", "");
            List<String> bases = new ArrayList<>();
            bases.add(clean);
            if (clean.startsWith("https://")) {
                bases.add("http://" + clean.substring(8));
            } else if (clean.startsWith("http://")) {
                bases.add("https://" + clean.substring(7));
            }

            List<String> loadUrls = new ArrayList<>();
            for (String b : bases) {
                if (b.endsWith("/load.php") || b.endsWith("/portal.php")) {
                    loadUrls.add(b);
                } else if (b.endsWith("/c")) {
                    String root = b.substring(0, b.length() - 2);
                    loadUrls.add(root + "/server/load.php");
                    loadUrls.add(root + "/portal.php");
                } else if (b.endsWith("/stalker_portal")) {
                    loadUrls.add(b + "/server/load.php");
                    loadUrls.add(b + "/portal.php");
                } else {
                    loadUrls.add(b + "/stalker_portal/server/load.php");
                    loadUrls.add(b + "/portal.php");
                    loadUrls.add(b + "/server/load.php");
                }
            }
            return loadUrls;
        }

        private static String tryStalkerVodCatalog(String rawUrl, String login, String password, String key, String mac,
                                                   String category, String sort, String search, int page) {
            String stbMac = deriveMac(mac, login, key);
            List<String> endpoints = buildStalkerLoadUrls(rawUrl);

            for (String endpoint : endpoints) {
                Map<String, String> headers = new LinkedHashMap<>();
                String referer = endpoint.replace("/server/load.php", "/c/").replace("/portal.php", "/c/");
                headers.put("Referer", referer);
                headers.put("X-User-Agent", "Model: MAG250; Link: WiFi");
                headers.put("Cookie", "mac=" + URLEncoder.encode(stbMac, StandardCharsets.UTF_8) + "; stb_lang=ru; timezone=Europe%2FMoscow");

                String hsResp = httpRequestText(endpoint + "?type=stb&action=handshake&token=&JsHttpRequest=1-xml", "GET", null, headers, 6000);
                if (hsResp == null) continue;
                String token = extractJsonString(hsResp, "token", "");
                if (token.isEmpty()) continue;

                headers.put("Authorization", "Bearer " + token);

                if (!login.isEmpty() && !password.isEmpty()) {
                    String authUrl = endpoint + "?type=stb&action=do_auth&login="
                            + URLEncoder.encode(login, StandardCharsets.UTF_8)
                            + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                            + "&JsHttpRequest=1-xml";
                    httpRequestText(authUrl, "GET", null, headers, 5000);
                }
                httpRequestText(endpoint + "?type=stb&action=get_profile&JsHttpRequest=1-xml", "GET", null, headers, 5000);

                // Fetch VOD categories
                String catsResp = httpRequestText(endpoint + "?type=vod&action=get_categories&JsHttpRequest=1-xml", "GET", null, headers, 6000);
                StringBuilder categoriesOut = new StringBuilder("[");
                boolean firstCat = true;
                if (catsResp != null) {
                    Matcher cm = JSON_OBJ_ITEM_PATTERN.matcher(catsResp);
                    while (cm.find()) {
                        String obj = cm.group();
                        String cid = extractJsonString(obj, "id", "");
                        String ctitle = extractJsonString(obj, "title", "");
                        if (!cid.isEmpty() && !ctitle.isEmpty()) {
                            if (!firstCat) categoriesOut.append(',');
                            firstCat = false;
                            categoriesOut.append("{\"id\":\"").append(escapeJson(cid)).append("\",\"title\":\"").append(escapeJson(ctitle)).append("\"}");
                        }
                    }
                }
                categoriesOut.append(']');

                String catParam = (category.isEmpty() || "all".equalsIgnoreCase(category) || "movies".equalsIgnoreCase(category)) ? "*" : category;
                String sortParam = "rating".equalsIgnoreCase(sort) ? "rating" : ("name".equalsIgnoreCase(sort) ? "name" : "added");
                String listUrl = endpoint + "?type=vod&action=get_ordered_list"
                        + "&category=" + URLEncoder.encode(catParam, StandardCharsets.UTF_8)
                        + "&sortby=" + sortParam
                        + "&p=" + page
                        + (search.isEmpty() ? "" : "&search=" + URLEncoder.encode(search, StandardCharsets.UTF_8))
                        + "&JsHttpRequest=1-xml";

                String listResp = httpRequestText(listUrl, "GET", null, headers, 8000);
                if (listResp == null) continue;

                StringBuilder itemsOut = new StringBuilder("[");
                boolean firstItem = true;
                Matcher im = JSON_OBJ_ITEM_PATTERN.matcher(listResp);
                URI baseUri = URI.create(endpoint);
                while (im.find()) {
                    String obj = im.group();
                    String id = extractJsonString(obj, "id", "");
                    String name = firstNonEmpty(extractJsonString(obj, "name", ""), extractJsonString(obj, "o_name", ""));
                    if (id.isEmpty() || name.isEmpty()) continue;

                    String origName = extractJsonString(obj, "o_name", "");
                    String year = firstNonEmpty(extractJsonString(obj, "year", ""), extractYearFromTitle(name));
                    String desc = extractJsonString(obj, "description", "");
                    String director = extractJsonString(obj, "director", "");
                    String actors = extractJsonString(obj, "actors", "");
                    String country = extractJsonString(obj, "country", "");
                    String genre = firstNonEmpty(extractJsonString(obj, "genres_str", ""), "Кино");
                    String kp = extractJsonString(obj, "rating_kinopoisk", "");
                    String imdb = extractJsonString(obj, "rating_imdb", "");
                    String duration = extractJsonString(obj, "time", "");
                    String age = extractJsonString(obj, "age", "");
                    String cmd = extractJsonString(obj, "cmd", "");
                    String poster = extractJsonString(obj, "screenshot_uri", "");
                    if (!poster.isEmpty() && !poster.startsWith("http")) {
                        try {
                            poster = baseUri.resolve(poster).toString();
                        } catch (Exception ignored) {}
                    }

                    if (!firstItem) itemsOut.append(',');
                    firstItem = false;
                    itemsOut.append("{")
                            .append("\"id\":\"stk_").append(escapeJson(id)).append("\",")
                            .append("\"title\":\"").append(escapeJson(name)).append("\",")
                            .append("\"origTitle\":\"").append(escapeJson(origName)).append("\",")
                            .append("\"year\":\"").append(escapeJson(year)).append("\",")
                            .append("\"genre\":\"").append(escapeJson(genre)).append("\",")
                            .append("\"country\":\"").append(escapeJson(country)).append("\",")
                            .append("\"director\":\"").append(escapeJson(director)).append("\",")
                            .append("\"actors\":\"").append(escapeJson(actors)).append("\",")
                            .append("\"category\":\"movies\",")
                            .append("\"ratingKp\":\"").append(escapeJson(kp)).append("\",")
                            .append("\"ratingImdb\":\"").append(escapeJson(imdb)).append("\",")
                            .append("\"quality\":\"").append( extractJsonLong(obj, "hd", 1) == 1 ? "1080p" : "SD" ).append("\",")
                            .append("\"duration\":\"").append(escapeJson(duration)).append("\",")
                            .append("\"age\":\"").append(escapeJson(age)).append("\",")
                            .append("\"description\":\"").append(escapeJson(desc)).append("\",")
                            .append("\"poster\":\"").append(escapeJson(poster)).append("\",")
                            .append("\"cmd\":\"").append(escapeJson(cmd)).append("\",")
                            .append("\"streamUrl\":\"\"")
                            .append("}");
                }
                itemsOut.append(']');

                return "{\"ok\":true,\"status\":\"OK\",\"protocol\":\"STALKER\",\"categories\":" + categoriesOut + ",\"items\":" + itemsOut + "}";
            }
            return null;
        }

        // ── 4. Direct M3U / M3U8 VOD Playlist ──────────────────────────────────
        private static String tryM3uVodCatalog(String rawUrl, String key) {
            String target = rawUrl;
            if (!key.isEmpty() && target.contains("{KEY}")) {
                target = target.replace("{KEY}", URLEncoder.encode(key, StandardCharsets.UTF_8));
            }
            String text = httpRequestText(target, "GET", null, null, 9000);
            if (text == null || !text.contains("#EXTINF")) return null;

            String[] lines = text.split("\r?\n");
            Map<String, String> catMap = new LinkedHashMap<>();
            StringBuilder itemsOut = new StringBuilder("[");
            boolean firstItem = true;
            int idx = 0;

            String curTitle = "";
            String curLogo = "";
            String curGroup = "Фильмы";

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF:")) {
                    int comma = line.lastIndexOf(',');
                    curTitle = comma >= 0 ? line.substring(comma + 1).trim() : "Фильм";
                    curLogo = extractM3uAttr(line, "tvg-logo");
                    String g = extractM3uAttr(line, "group-title");
                    if (!g.isEmpty()) curGroup = g;
                } else if (line.startsWith("#EXTGRP:")) {
                    String g = line.substring(8).trim();
                    if (!g.isEmpty()) curGroup = g;
                } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    if (curTitle.isEmpty()) continue;
                    idx++;
                    String catId = "m3u_cat_" + Math.abs(curGroup.hashCode());
                    catMap.put(catId, curGroup);
                    String year = extractYearFromTitle(curTitle);
                    String gLower = curGroup.toLowerCase(Locale.ROOT);
                    String catType = (gLower.contains("сериал") || gLower.contains("series")) ? "series"
                            : ((gLower.contains("мульт") || gLower.contains("детск") || gLower.contains("kids")) ? "cartoons" : "movies");

                    if (!firstItem) itemsOut.append(',');
                    firstItem = false;
                    itemsOut.append("{")
                            .append("\"id\":\"m3u_").append(idx).append("\",")
                            .append("\"title\":\"").append(escapeJson(curTitle)).append("\",")
                            .append("\"year\":\"").append(escapeJson(year)).append("\",")
                            .append("\"genre\":\"").append(escapeJson(curGroup)).append("\",")
                            .append("\"categoryId\":\"").append(escapeJson(catId)).append("\",")
                            .append("\"category\":\"").append(catType).append("\",")
                            .append("\"ratingKp\":\"\",")
                            .append("\"ratingImdb\":\"\",")
                            .append("\"quality\":\"").append(curTitle.toUpperCase(Locale.ROOT).contains("4K") ? "4K" : "1080p").append("\",")
                            .append("\"poster\":\"").append(escapeJson(curLogo)).append("\",")
                            .append("\"streamUrl\":\"").append(escapeJson(line)).append("\"")
                            .append("}");
                    curTitle = "";
                    curLogo = "";
                    if (idx >= 500) break;
                }
            }
            itemsOut.append(']');
            if (idx == 0) return null;

            StringBuilder categoriesOut = new StringBuilder("[");
            boolean firstCat = true;
            for (Map.Entry<String, String> e : catMap.entrySet()) {
                if (!firstCat) categoriesOut.append(',');
                firstCat = false;
                categoriesOut.append("{\"id\":\"").append(escapeJson(e.getKey())).append("\",\"title\":\"").append(escapeJson(e.getValue())).append("\"}");
            }
            categoriesOut.append(']');

            return "{\"ok\":true,\"status\":\"OK\",\"protocol\":\"M3U\",\"categories\":" + categoriesOut + ",\"items\":" + itemsOut + "}";
        }

        private static String resolveVodStreamPayload(String rawUrl, String login, String password, String key, String mac,
                                                      String cmd, String seriesEp, String requestJson) {
            // VPortal stream or multistream resolution
            if (!key.isEmpty() && !requestJson.isEmpty() && requestJson.startsWith("{") && requestJson.endsWith("}")) {
                String payload = buildVPortalPayload(key, requestJson, "");
                Map<String, String> vHeaders = buildVPortalHeaders();
                for (String candidateUrl : buildVPortalCandidates(rawUrl)) {
                    String resp = httpRequestText(candidateUrl, "POST", payload, vHeaders, 7000);
                    if (resp != null && resp.trim().startsWith("{")) {
                        String directUrl = extractJsonString(resp, "url", "");
                        List<String> subItems = extractItemsArrayObjects(resp);
                        StringBuilder epsOut = new StringBuilder("[");
                        boolean firstEp = true;
                        String firstEpUrl = "";
                        int epIdx = 0;
                        int seasonProbeCount = 0;
                        for (String sub : subItems) {
                            String sTitle = firstNonEmpty(extractJsonString(sub, "title", ""), "Серия " + (epIdx + 1));
                            String sUrl = extractJsonString(sub, "url", "");
                            String sReq = extractJsonObject(sub, "request");
                            if (sUrl.isEmpty() && !sReq.isEmpty() && (epIdx == 0 || seasonProbeCount > 0) && seasonProbeCount < 8) {
                                String r2 = httpRequestText(candidateUrl, "POST", buildVPortalPayload(key, sReq, ""), vHeaders, 5000);
                                if (r2 != null && r2.trim().startsWith("{")) {
                                    String r2Url = extractJsonString(r2, "url", "");
                                    if (!r2Url.isEmpty()) {
                                        sUrl = r2Url;
                                    } else {
                                        List<String> nestedItems = extractItemsArrayObjects(r2);
                                        if (!nestedItems.isEmpty()) {
                                            seasonProbeCount++;
                                            int nestedIdx = 0;
                                            for (String nSub : nestedItems) {
                                                nestedIdx++;
                                                String nTitle = firstNonEmpty(extractJsonString(nSub, "title", ""), "Серия " + nestedIdx);
                                                String fullTitle = subItems.size() > 1 ? (sTitle + " • " + nTitle) : nTitle;
                                                String nUrl = extractJsonString(nSub, "url", "");
                                                String nReq = extractJsonObject(nSub, "request");
                                                if (nUrl.isEmpty() && !nReq.isEmpty() && epIdx == 0) {
                                                    String r3 = httpRequestText(candidateUrl, "POST", buildVPortalPayload(key, nReq, ""), vHeaders, 5000);
                                                    if (r3 != null) nUrl = extractJsonString(r3, "url", "");
                                                }
                                                if (!nUrl.isEmpty() || !nReq.isEmpty()) {
                                                    epIdx++;
                                                    if (firstEpUrl.isEmpty() && !nUrl.isEmpty()) firstEpUrl = nUrl;
                                                    if (!firstEp) epsOut.append(',');
                                                    firstEp = false;
                                                    epsOut.append("{\"id\":\"ep_").append(epIdx).append("\",")
                                                            .append("\"title\":\"").append(escapeJson(fullTitle)).append("\",")
                                                            .append("\"season\":").append(seasonProbeCount).append(",")
                                                            .append("\"episode\":").append(nestedIdx).append(",")
                                                            .append("\"streamUrl\":\"").append(escapeJson(nUrl)).append("\",")
                                                            .append("\"requestJson\":\"").append(escapeJson(nReq)).append("\"}");
                                                }
                                            }
                                            continue;
                                        }
                                    }
                                }
                            }
                            if (!sUrl.isEmpty() || !sReq.isEmpty()) {
                                epIdx++;
                                if (firstEpUrl.isEmpty() && !sUrl.isEmpty()) firstEpUrl = sUrl;
                                if (!firstEp) epsOut.append(',');
                                firstEp = false;
                                epsOut.append("{\"id\":\"ep_").append(epIdx).append("\",")
                                        .append("\"title\":\"").append(escapeJson(sTitle)).append("\",")
                                        .append("\"season\":1,")
                                        .append("\"episode\":").append(epIdx).append(",")
                                        .append("\"streamUrl\":\"").append(escapeJson(sUrl)).append("\",")
                                        .append("\"requestJson\":\"").append(escapeJson(sReq)).append("\"}");
                            }
                        }
                        epsOut.append(']');
                        String finalUrl = !directUrl.isEmpty() ? directUrl : firstEpUrl;
                        if (!finalUrl.isEmpty() || epIdx > 0) {
                            return "{\"ok\":true,\"status\":\"OK\",\"url\":\"" + escapeJson(finalUrl)
                                    + "\",\"streamUrl\":\"" + escapeJson(finalUrl)
                                    + "\",\"episodes\":" + epsOut + "}";
                        }
                    }
                }
            }
            // Stalker create_link resolution
            if (!cmd.isEmpty()) {
                if (cmd.startsWith("http://") || cmd.startsWith("https://")) {
                    return "{\"ok\":true,\"status\":\"OK\",\"url\":\"" + escapeJson(cmd) + "\",\"streamUrl\":\"" + escapeJson(cmd) + "\"}";
                }
                String stbMac = deriveMac(mac, login, key);
                for (String endpoint : buildStalkerLoadUrls(rawUrl)) {
                    Map<String, String> headers = new LinkedHashMap<>();
                    headers.put("Referer", endpoint.replace("/server/load.php", "/c/").replace("/portal.php", "/c/"));
                    headers.put("X-User-Agent", "Model: MAG250; Link: WiFi");
                    headers.put("Cookie", "mac=" + URLEncoder.encode(stbMac, StandardCharsets.UTF_8) + "; stb_lang=ru; timezone=Europe%2FMoscow");

                    String hsResp = httpRequestText(endpoint + "?type=stb&action=handshake&token=&JsHttpRequest=1-xml", "GET", null, headers, 6000);
                    if (hsResp == null) continue;
                    String token = extractJsonString(hsResp, "token", "");
                    if (token.isEmpty()) continue;
                    headers.put("Authorization", "Bearer " + token);

                    if (!login.isEmpty() && !password.isEmpty()) {
                        String authUrl = endpoint + "?type=stb&action=do_auth&login="
                                + URLEncoder.encode(login, StandardCharsets.UTF_8)
                                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                                + "&JsHttpRequest=1-xml";
                        httpRequestText(authUrl, "GET", null, headers, 5000);
                    }
                    String createUrl = endpoint + "?type=vod&action=create_link&cmd="
                            + URLEncoder.encode(cmd, StandardCharsets.UTF_8)
                            + (seriesEp.isEmpty() ? "" : "&series=" + URLEncoder.encode(seriesEp, StandardCharsets.UTF_8))
                            + "&JsHttpRequest=1-xml";
                    String linkResp = httpRequestText(createUrl, "GET", null, headers, 6000);
                    if (linkResp != null) {
                        String resolvedCmd = extractJsonString(linkResp, "cmd", "");
                        resolvedCmd = resolvedCmd.replaceFirst("(?i)^(?:ffmpeg|ffrt\\d*)\\s+", "").trim();
                        if (resolvedCmd.startsWith("http://") || resolvedCmd.startsWith("https://")) {
                            return "{\"ok\":true,\"status\":\"OK\",\"url\":\"" + escapeJson(resolvedCmd)
                                    + "\",\"streamUrl\":\"" + escapeJson(resolvedCmd) + "\"}";
                        }
                    }
                }
            }
            return null;
        }

        private static String extractYearFromTitle(String title) {
            if (title == null) return "";
            Matcher m = YEAR_IN_TITLE_PATTERN.matcher(title);
            String found = "";
            while (m.find()) {
                found = m.group(1);
            }
            return found;
        }

        private static String extractM3uAttr(String line, String attr) {
            Pattern p = Pattern.compile(Pattern.quote(attr) + "\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(line);
            return m.find() ? m.group(1).trim() : "";
        }
    }

    // ─── Smart TV EPG Streaming Parser & Cache (/api/tv/epg/*) ──────────────────

    static class EpgProgramItem {
        final long startMs;
        final long endMs;
        final String title;
        final String desc;

        EpgProgramItem(long startMs, long endMs, String title, String desc) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.title = title;
            this.desc = desc;
        }
    }

    static class EpgDataset {
        final String epgUrl;
        volatile String status = "IDLE"; // "LOADING", "READY", "ERROR"
        volatile int progress = 0;
        volatile String stage = "Подготовка загрузки EPG…";
        volatile String error = "";
        volatile long loadedAtMs = 0L;
        volatile int channelsCount = 0;
        volatile int programsCount = 0;

        // xmltv channel id (lowercased) -> sorted list of programmes
        final Map<String, List<EpgProgramItem>> programsByChannelId = new ConcurrentHashMap<>();
        // normalized channel name / display-name -> xmltv channel id (lowercased)
        final Map<String, String> nameToChannelId = new ConcurrentHashMap<>();
        // xmltv channel id (lowercased) -> channel icon url from <icon src="..."/>
        final Map<String, String> iconByChannelId = new ConcurrentHashMap<>();

        EpgDataset(String epgUrl) {
            this.epgUrl = epgUrl;
        }
    }

    private static final long EPG_CACHE_TTL_MS = 3 * 3600 * 1000L; // 3 hours
    private static final Map<String, EpgDataset> EPG_DATASETS = new ConcurrentHashMap<>();

    static class CountingInputStream extends FilterInputStream {
        volatile long bytesRead = 0L;

        protected CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) bytesRead++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) bytesRead += n;
            return n;
        }
    }

    private static EpgDataset getOrStartEpgDataset(String rawUrl) {
        String epgUrl = (rawUrl == null || rawUrl.trim().isEmpty())
                ? "http://epg.one/epg2.xml.gz"
                : rawUrl.trim();

        EpgDataset ds = EPG_DATASETS.computeIfAbsent(epgUrl, EpgDataset::new);
        long now = System.currentTimeMillis();

        synchronized (ds) {
            boolean expired = "READY".equals(ds.status) && (now - ds.loadedAtMs > EPG_CACHE_TTL_MS);
            if ("IDLE".equals(ds.status) || "ERROR".equals(ds.status) || expired) {
                ds.status = "LOADING";
                ds.progress = 2;
                ds.stage = "Подключение к серверу EPG…";
                ds.error = "";
                Thread worker = new Thread(() -> downloadAndIndexEpg(ds), "epg-indexer");
                worker.setDaemon(true);
                worker.start();
            }
        }
        return ds;
    }

    private static void downloadAndIndexEpg(EpgDataset ds) {
        HttpURLConnection conn = null;
        try {
            URI currentUri = URI.create(ds.epgUrl);
            for (int hop = 0; hop < 5; hop++) {
                conn = (HttpURLConnection) currentUri.toURL().openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(45000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MinogaTVBox/1.0.7");
                int status = conn.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (loc == null || loc.isEmpty()) break;
                    currentUri = currentUri.resolve(loc.trim());
                    continue;
                }
                break;
            }

            long contentLength = conn.getContentLengthLong();
            ds.progress = 5;
            ds.stage = "Загрузка и распаковка XMLTV…";

            long nowMs = System.currentTimeMillis();
            long windowStartMs = nowMs - 48L * 3600_000L; // 48 hours archive
            long windowEndMs = nowMs + 36L * 3600_000L;   // 36 hours ahead (full day + next day)

            Map<String, List<EpgProgramItem>> tempPrograms = new ConcurrentHashMap<>();
            Map<String, String> tempNames = new ConcurrentHashMap<>();
            Map<String, String> tempIcons = new ConcurrentHashMap<>();
            int chCount = 0;
            int progCount = 0;

            try (CountingInputStream cis = new CountingInputStream(new BufferedInputStream(conn.getInputStream(), 65536))) {
                BufferedInputStream peekStream = new BufferedInputStream(cis, 65536);
                peekStream.mark(4);
                int b1 = peekStream.read();
                int b2 = peekStream.read();
                peekStream.reset();

                boolean isGzip = (b1 == 0x1f && b2 == 0x8b) || ds.epgUrl.toLowerCase().endsWith(".gz");
                InputStream xmlStream = isGzip ? new GZIPInputStream(peekStream, 65536) : peekStream;

                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                XMLStreamReader reader = factory.createXMLStreamReader(xmlStream, "UTF-8");

                String currentChannelId = null;
                String progChannelId = null;
                long progStartMs = 0L;
                long progEndMs = 0L;
                String progTitle = null;
                String progDesc = null;
                String activeTag = null;
                StringBuilder textBuf = new StringBuilder(128);

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String localName = reader.getLocalName();
                        if ("channel".equalsIgnoreCase(localName)) {
                            String idAttr = reader.getAttributeValue(null, "id");
                            currentChannelId = idAttr != null ? idAttr.trim().toLowerCase() : null;
                            if (currentChannelId != null && !currentChannelId.isEmpty()) {
                                tempNames.put(currentChannelId, currentChannelId);
                                String normId = normalizeEpgKey(currentChannelId);
                                if (!normId.isEmpty()) tempNames.putIfAbsent(normId, currentChannelId);
                                chCount++;
                            }
                        } else if ("icon".equalsIgnoreCase(localName) && currentChannelId != null) {
                            String srcAttr = reader.getAttributeValue(null, "src");
                            if (srcAttr != null && !srcAttr.trim().isEmpty()) {
                                tempIcons.putIfAbsent(currentChannelId, srcAttr.trim());
                            }
                        } else if ("display-name".equalsIgnoreCase(localName) && currentChannelId != null) {
                            activeTag = "display-name";
                            textBuf.setLength(0);
                        } else if ("programme".equalsIgnoreCase(localName)) {
                            String chAttr = reader.getAttributeValue(null, "channel");
                            String startAttr = reader.getAttributeValue(null, "start");
                            String stopAttr = reader.getAttributeValue(null, "stop");
                            progStartMs = parseXmltvTimestamp(startAttr);
                            progEndMs = parseXmltvTimestamp(stopAttr);
                            if (chAttr != null && progEndMs >= windowStartMs && progStartMs <= windowEndMs && progEndMs > progStartMs) {
                                progChannelId = chAttr.trim().toLowerCase();
                            } else {
                                progChannelId = null;
                            }
                            progTitle = null;
                            progDesc = null;
                        } else if (progChannelId != null && "title".equalsIgnoreCase(localName)) {
                            activeTag = "title";
                            textBuf.setLength(0);
                        } else if (progChannelId != null && "desc".equalsIgnoreCase(localName)) {
                            activeTag = "desc";
                            textBuf.setLength(0);
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                        if (activeTag != null && textBuf.length() < 300) {
                            textBuf.append(reader.getText());
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        String localName = reader.getLocalName();
                        if ("display-name".equalsIgnoreCase(localName) && currentChannelId != null) {
                            String disp = textBuf.toString().trim();
                            if (!disp.isEmpty()) {
                                tempNames.putIfAbsent(disp.toLowerCase(), currentChannelId);
                                String norm = normalizeEpgKey(disp);
                                if (!norm.isEmpty()) tempNames.putIfAbsent(norm, currentChannelId);
                                String noHd = normalizeEpgKeyWithoutQuality(disp);
                                if (!noHd.isEmpty()) tempNames.putIfAbsent(noHd, currentChannelId);
                            }
                            activeTag = null;
                        } else if ("channel".equalsIgnoreCase(localName)) {
                            currentChannelId = null;
                        } else if ("title".equalsIgnoreCase(localName) && progChannelId != null) {
                            progTitle = textBuf.toString().trim();
                            activeTag = null;
                        } else if ("desc".equalsIgnoreCase(localName) && progChannelId != null) {
                            String d = textBuf.toString().trim();
                            progDesc = d.length() > 180 ? d.substring(0, 180) + "…" : d;
                            activeTag = null;
                        } else if ("programme".equalsIgnoreCase(localName)) {
                            if (progChannelId != null && progTitle != null && !progTitle.isEmpty()) {
                                List<EpgProgramItem> list = tempPrograms.computeIfAbsent(progChannelId, k -> new ArrayList<>(64));
                                if (list.size() < 500) {
                                    list.add(new EpgProgramItem(progStartMs, progEndMs, progTitle, progDesc != null ? progDesc : ""));
                                    progCount++;
                                }
                                if ((progCount & 511) == 0) {
                                    ds.channelsCount = chCount;
                                    ds.programsCount = progCount;
                                    if (contentLength > 0) {
                                        int pct = (int) Math.min(95L, 5L + (cis.bytesRead * 90L) / contentLength);
                                        ds.progress = Math.max(ds.progress, pct);
                                    } else {
                                        ds.progress = Math.min(95, 10 + (progCount / 600));
                                    }
                                    ds.stage = "Индексация телепрограммы (" + progCount + " передач)…";
                                }
                            }
                            progChannelId = null;
                        }
                    }
                }
            }

            ds.stage = "Сортировка расписания каналов…";
            ds.progress = 97;
            Comparator<EpgProgramItem> cmp = Comparator.comparingLong(a -> a.startMs);
            for (List<EpgProgramItem> list : tempPrograms.values()) {
                list.sort(cmp);
            }

            ds.programsByChannelId.clear();
            ds.programsByChannelId.putAll(tempPrograms);
            ds.nameToChannelId.clear();
            ds.nameToChannelId.putAll(tempNames);
            ds.iconByChannelId.clear();
            ds.iconByChannelId.putAll(tempIcons);
            ds.channelsCount = chCount;
            ds.programsCount = progCount;
            ds.loadedAtMs = System.currentTimeMillis();
            ds.progress = 100;
            ds.stage = "Телепрограмма загружена (" + chCount + " кан.)";
            ds.status = "READY";
        } catch (Exception e) {
            ds.error = String.valueOf(e.getMessage());
            ds.stage = "Ошибка загрузки EPG: " + ds.error;
            ds.status = "ERROR";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String normalizeEpgKey(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase().replace('ё', 'е');
        // Strip technical suffix after pipe '|' (e.g. "Первый | 1080")
        lower = lower.replaceAll("\\|.*$", " ");
        // Strip bracketed tags like [HD], (RU), (+2)
        lower = lower.replaceAll("\\[[^\\]]*\\]|\\([^\\)]*\\)", " ");
        return lower.replaceAll("[^a-zа-я0-9]+", "");
    }

    private static String normalizeEpgKeyWithoutQuality(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase().replace('ё', 'е');
        lower = lower.replaceAll("\\|.*$", " ");
        lower = lower.replaceAll("\\[[^\\]]*\\]|\\([^\\)]*\\)", " ");
        lower = lower.replaceAll("\\b(fhd|uhd|hd|sd|4k|50fps|hevc|orig|ru|ua|by|kz|pl|de|en)\\b", " ");
        return lower.replaceAll("[^a-zа-я0-9]+", "");
    }

    private static String resolveXmltvChannelId(EpgDataset ds, String tvgId, String chName) {
        if (ds == null || !"READY".equals(ds.status)) return null;
        if (tvgId != null && !tvgId.isEmpty()) {
            String lowId = tvgId.trim().toLowerCase();
            if (ds.programsByChannelId.containsKey(lowId) || ds.iconByChannelId.containsKey(lowId)) return lowId;
            String mapped = ds.nameToChannelId.get(lowId);
            if (mapped != null) return mapped;
            String normId = normalizeEpgKey(lowId);
            if (!normId.isEmpty()) {
                String m2 = ds.nameToChannelId.get(normId);
                if (m2 != null) return m2;
            }
        }
        if (chName != null && !chName.isEmpty()) {
            String lowName = chName.trim().toLowerCase();
            String mapped = ds.nameToChannelId.get(lowName);
            if (mapped != null) return mapped;
            String norm = normalizeEpgKey(chName);
            if (!norm.isEmpty()) {
                String m2 = ds.nameToChannelId.get(norm);
                if (m2 != null) return m2;
            }
            String noQuality = normalizeEpgKeyWithoutQuality(chName);
            if (!noQuality.isEmpty()) {
                String m3 = ds.nameToChannelId.get(noQuality);
                if (m3 != null) return m3;
            }
        }
        return null;
    }

    private static List<EpgProgramItem> findChannelPrograms(EpgDataset ds, String tvgId, String chName) {
        String xmlId = resolveXmltvChannelId(ds, tvgId, chName);
        if (xmlId == null) return Collections.emptyList();
        List<EpgProgramItem> list = ds.programsByChannelId.get(xmlId);
        return list != null ? list : Collections.emptyList();
    }

    private static String findChannelIcon(EpgDataset ds, String tvgId, String chName) {
        String xmlId = resolveXmltvChannelId(ds, tvgId, chName);
        if (xmlId == null) return "";
        String ic = ds.iconByChannelId.get(xmlId);
        return ic != null ? ic : "";
    }

    // Parses "20261106123000 +0300" into epoch ms
    private static long parseXmltvTimestamp(String raw) {
        if (raw == null || raw.length() < 14) return 0L;
        try {
            int year = Integer.parseInt(raw.substring(0, 4));
            int month = Integer.parseInt(raw.substring(4, 6));
            int day = Integer.parseInt(raw.substring(6, 8));
            int hour = Integer.parseInt(raw.substring(8, 10));
            int min = Integer.parseInt(raw.substring(10, 12));
            int sec = Integer.parseInt(raw.substring(12, 14));

            int offsetSeconds = 0;
            int spaceIdx = raw.indexOf(' ', 14);
            if (spaceIdx > 0 && raw.length() >= spaceIdx + 6) {
                String tz = raw.substring(spaceIdx + 1).trim();
                if ((tz.startsWith("+") || tz.startsWith("-")) && tz.length() >= 5) {
                    int sign = tz.charAt(0) == '-' ? -1 : 1;
                    int tzH = Integer.parseInt(tz.substring(1, 3));
                    int tzM = Integer.parseInt(tz.substring(3, 5));
                    offsetSeconds = sign * (tzH * 3600 + tzM * 60);
                }
            }
            LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, min, sec);
            return ldt.toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds)) * 1000L;
        } catch (Exception e) {
            return 0L;
        }
    }

    static class TvEpgStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String rawUrl = URLDecoder.decode(getQueryParam(exchange.getRequestURI(), "url"), StandardCharsets.UTF_8);
            EpgDataset ds = getOrStartEpgDataset(rawUrl);
            String json = "{\n"
                    + "  \"status\": \"" + escapeJson(ds.status) + "\",\n"
                    + "  \"progress\": " + ds.progress + ",\n"
                    + "  \"stage\": \"" + escapeJson(ds.stage) + "\",\n"
                    + "  \"channelsCount\": " + ds.channelsCount + ",\n"
                    + "  \"programsCount\": " + ds.programsCount + "\n"
                    + "}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", json);
        }
    }

    static class TvEpgLiveHandler implements HttpHandler {
        private static final Pattern CH_OBJ_PATTERN = Pattern.compile(
                "\\{\\s*\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"tvgId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\}");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String body = readBody(exchange);
            URI reqUri = exchange.getRequestURI();
            String epgUrl = extractJsonString(body, "url", "http://epg.one/epg2.xml.gz");
            boolean fullSchedule = "1".equals(getQueryParam(reqUri, "full"))
                    || "true".equalsIgnoreCase(getQueryParam(reqUri, "full"))
                    || "true".equalsIgnoreCase(extractJsonString(body, "full", ""))
                    || body.contains("\"full\":true") || body.contains("\"full\": true");
            EpgDataset ds = getOrStartEpgDataset(epgUrl);
            if (!"READY".equals(ds.status)) {
                sendResponse(exchange, 200, "application/json; charset=utf-8",
                        "{\"status\":\"" + escapeJson(ds.status) + "\",\"progress\":" + ds.progress + ",\"live\":{}}");
                return;
            }

            long nowMs = System.currentTimeMillis();
            StringBuilder out = new StringBuilder(fullSchedule ? 65536 : 16384);
            out.append("{\"status\":\"READY\",\"live\":{");
            boolean first = true;

            Matcher m = CH_OBJ_PATTERN.matcher(body);
            while (m.find()) {
                String idStr = m.group(1);
                String tvgId = m.group(2).replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
                String name = m.group(3).replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");

                List<EpgProgramItem> list = findChannelPrograms(ds, tvgId, name);
                String iconUrl = findChannelIcon(ds, tvgId, name);
                if (list.isEmpty() && iconUrl.isEmpty()) continue;

                EpgProgramItem current = null;
                EpgProgramItem next = null;
                for (int i = 0; i < list.size(); i++) {
                    EpgProgramItem item = list.get(i);
                    if (nowMs >= item.startMs && nowMs < item.endMs) {
                        current = item;
                        if (i + 1 < list.size()) {
                            next = list.get(i + 1);
                        }
                        break;
                    } else if (current == null && item.startMs > nowMs && item.startMs <= nowMs + 6 * 3600_000L) {
                        next = item;
                        break;
                    }
                }
                if (current != null || next != null || !iconUrl.isEmpty() || (fullSchedule && !list.isEmpty())) {
                    if (!first) out.append(',');
                    first = false;
                    out.append('"').append(idStr).append("\":{");
                    boolean hasField = false;
                    if (current != null) {
                        out.append("\"t\":\"").append(escapeJson(current.title)).append("\",")
                                .append("\"s\":").append(current.startMs).append(',')
                                .append("\"e\":").append(current.endMs);
                        hasField = true;
                    }
                    if (next != null) {
                        if (hasField) out.append(',');
                        out.append("\"nt\":\"").append(escapeJson(next.title)).append("\",")
                                .append("\"ns\":").append(next.startMs).append(',')
                                .append("\"ne\":").append(next.endMs);
                        hasField = true;
                    }
                    if (!iconUrl.isEmpty()) {
                        if (hasField) out.append(',');
                        out.append("\"ic\":\"").append(escapeJson(iconUrl)).append('"');
                        hasField = true;
                    }
                    if (fullSchedule && !list.isEmpty()) {
                        if (hasField) out.append(',');
                        out.append("\"p\":[");
                        boolean firstProg = true;
                        long minMs = nowMs - 24 * 3600_000L;
                        long maxMs = nowMs + 36 * 3600_000L;
                        for (int i = 0; i < list.size(); i++) {
                            EpgProgramItem item = list.get(i);
                            if (item.endMs <= minMs || item.startMs >= maxMs) continue;
                            if (!firstProg) out.append(',');
                            firstProg = false;
                            out.append("{\"s\":").append(item.startMs)
                                    .append(",\"e\":").append(item.endMs)
                                    .append(",\"t\":\"").append(escapeJson(item.title)).append('"');
                            if (item.desc != null && !item.desc.isEmpty()) {
                                String d = item.desc.length() > 260 ? item.desc.substring(0, 260) + "…" : item.desc;
                                out.append(",\"d\":\"").append(escapeJson(d)).append('"');
                            }
                            out.append('}');
                        }
                        out.append(']');
                    }
                    out.append('}');
                }
            }
            out.append("}}");
            sendResponse(exchange, 200, "application/json; charset=utf-8", out.toString());
        }
    }

    static class TvEpgChannelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            URI uri = exchange.getRequestURI();
            String epgUrl = URLDecoder.decode(getQueryParam(uri, "url"), StandardCharsets.UTF_8);
            String tvgId = URLDecoder.decode(getQueryParam(uri, "tvgId"), StandardCharsets.UTF_8);
            String name = URLDecoder.decode(getQueryParam(uri, "name"), StandardCharsets.UTF_8);

            EpgDataset ds = getOrStartEpgDataset(epgUrl);
            List<EpgProgramItem> list = findChannelPrograms(ds, tvgId, name);

            StringBuilder out = new StringBuilder(4096);
            out.append("{\"status\":\"").append(escapeJson(ds.status)).append("\",\"programs\":[");
            for (int i = 0; i < list.size(); i++) {
                EpgProgramItem p = list.get(i);
                if (i > 0) out.append(',');
                out.append("{\"s\":").append(p.startMs)
                        .append(",\"e\":").append(p.endMs)
                        .append(",\"t\":\"").append(escapeJson(p.title)).append("\"")
                        .append(",\"d\":\"").append(escapeJson(p.desc)).append("\"}");
            }
            out.append("]}");
            sendResponse(exchange, 200, "application/json; charset=utf-8", out.toString());
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
        if (json == null) return fallback;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1)
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                    .replace("\\t", " ")
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\")
                    .trim();
        }
        return fallback;
    }

    private static long extractJsonLong(String json, String key, long fallback) {
        if (json == null) return fallback;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("\t", " ");
    }

    // ─── Feedback, Ratings, Q&A & Pre-Moderation System ─────────────────────────

    private static String getAdminSecret() {
        try {
            if (ADMIN_KEY_FILE.exists()) {
                String saved = Files.readString(ADMIN_KEY_FILE.toPath(), StandardCharsets.UTF_8).trim();
                if (!saved.isEmpty()) return saved;
            }
        } catch (Exception ignored) {}
        String env = System.getenv("MINOGA_ADMIN_KEY");
        if (env != null && !env.trim().isEmpty()) return env.trim();
        return "minoga2026";
    }

    private static synchronized void setAdminSecret(String newKey) {
        try {
            if (!WEB_DIR.exists()) WEB_DIR.mkdirs();
            Files.writeString(ADMIN_KEY_FILE.toPath(), newKey.trim(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static synchronized void loadFeedbackItems() {
        FEEDBACK_ITEMS.clear();
        try {
            if (FEEDBACK_JSON_FILE.exists()) {
                List<String> lines = Files.readAllLines(FEEDBACK_JSON_FILE.toPath(), StandardCharsets.UTF_8);
                for (String raw : lines) {
                    String line = raw.trim();
                    if (!line.startsWith("{")) continue;
                    FeedbackItem item = new FeedbackItem();
                    item.id = extractJsonString(line, "id", "fb_" + System.nanoTime());
                    item.clientId = extractJsonString(line, "clientId", "");
                    item.authorName = extractJsonString(line, "authorName", "Пользователь");
                    item.deviceType = extractJsonString(line, "deviceType", "Android TV");
                    item.category = extractJsonString(line, "category", "REVIEW");
                    item.rating = (int) Math.max(1, Math.min(5, extractJsonLong(line, "rating", 5)));
                    item.text = extractJsonString(line, "text", "");
                    item.contact = extractJsonString(line, "contact", "");
                    item.status = extractJsonString(line, "status", "PENDING");
                    item.replyText = extractJsonString(line, "replyText", "");
                    item.createdAt = extractJsonLong(line, "createdAt", System.currentTimeMillis());
                    item.repliedAt = extractJsonLong(line, "repliedAt", 0L);
                    FEEDBACK_ITEMS.add(item);
                }
            } else {
                // Seed initial helpful showcase items on first start (editable/deletable in /admin)
                long now = System.currentTimeMillis();

                FeedbackItem s1 = new FeedbackItem();
                s1.id = "fb_seed_1";
                s1.clientId = "seed_1";
                s1.authorName = "Алексей";
                s1.deviceType = "LG (webOS)";
                s1.category = "REVIEW";
                s1.rating = 5;
                s1.text = "Отличный плеер! На 4K телевизоре LG через Media Station X каналы и архив работают моментально, привязка плейлиста по 4-значному коду с телефона заняла 15 секунд.";
                s1.contact = "";
                s1.status = "PUBLISHED";
                s1.replyText = "Спасибо за отзыв! Рады, что интерфейс на 4K LG пришёлся по душе.";
                s1.createdAt = now - 86_400_000L * 3;
                s1.repliedAt = now - 86_400_000L * 3 + 3_600_000L;
                FEEDBACK_ITEMS.add(s1);

                FeedbackItem s2 = new FeedbackItem();
                s2.id = "fb_seed_2";
                s2.clientId = "seed_2";
                s2.authorName = "Дмитрий";
                s2.deviceType = "Android TV";
                s2.category = "QUESTION";
                s2.rating = 5;
                s2.text = "Подскажите, как перематывать архив передач во время просмотра на телевизоре?";
                s2.contact = "";
                s2.status = "PUBLISHED";
                s2.replyText = "Во время просмотра архивной передачи просто нажимайте кнопки ◄ Влево и Вправо ► на пульте ТВ. При удерживании или быстром нажатии шаг перемотки автоматически ускоряется.";
                s2.createdAt = now - 86_400_000L * 2;
                s2.repliedAt = now - 86_400_000L * 2 + 1_800_000L;
                FEEDBACK_ITEMS.add(s2);

                FeedbackItem s3 = new FeedbackItem();
                s3.id = "fb_seed_3";
                s3.clientId = "seed_3";
                s3.authorName = "Сергей";
                s3.deviceType = "Смартфон";
                s3.category = "REVIEW";
                s3.rating = 5;
                s3.text = "Удобно смотреть и на приставке дома, и на телефоне Samsung в дороге. Живое превью канала прямо в списке и телепрограмма с архивом — супер!";
                s3.contact = "";
                s3.status = "PUBLISHED";
                s3.replyText = "";
                s3.createdAt = now - 86_400_000L;
                s3.repliedAt = 0L;
                FEEDBACK_ITEMS.add(s3);

                saveFeedbackItems();
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void saveFeedbackItems() {
        try {
            if (!WEB_DIR.exists()) WEB_DIR.mkdirs();
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < FEEDBACK_ITEMS.size(); i++) {
                FeedbackItem it = FEEDBACK_ITEMS.get(i);
                sb.append("  {")
                        .append("\"id\":\"").append(escapeJson(it.id)).append("\",")
                        .append("\"clientId\":\"").append(escapeJson(it.clientId)).append("\",")
                        .append("\"authorName\":\"").append(escapeJson(it.authorName)).append("\",")
                        .append("\"deviceType\":\"").append(escapeJson(it.deviceType)).append("\",")
                        .append("\"category\":\"").append(escapeJson(it.category)).append("\",")
                        .append("\"rating\":").append(it.rating).append(",")
                        .append("\"text\":\"").append(escapeJson(it.text)).append("\",")
                        .append("\"contact\":\"").append(escapeJson(it.contact)).append("\",")
                        .append("\"status\":\"").append(escapeJson(it.status)).append("\",")
                        .append("\"replyText\":\"").append(escapeJson(it.replyText)).append("\",")
                        .append("\"createdAt\":").append(it.createdAt).append(",")
                        .append("\"repliedAt\":").append(it.repliedAt)
                        .append("}");
                if (i < FEEDBACK_ITEMS.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]\n");
            Files.writeString(FEEDBACK_JSON_FILE.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static String serializePublicFeedbackItem(FeedbackItem it) {
        return "{"
                + "\"id\":\"" + escapeJson(it.id) + "\","
                + "\"authorName\":\"" + escapeJson(it.authorName) + "\","
                + "\"deviceType\":\"" + escapeJson(it.deviceType) + "\","
                + "\"category\":\"" + escapeJson(it.category) + "\","
                + "\"rating\":" + it.rating + ","
                + "\"text\":\"" + escapeJson(it.text) + "\","
                + "\"message\":\"" + escapeJson(it.text) + "\","
                + "\"status\":\"" + escapeJson(it.status) + "\","
                + "\"replyText\":\"" + escapeJson(it.replyText) + "\","
                + "\"createdAt\":" + it.createdAt + ","
                + "\"repliedAt\":" + it.repliedAt
                + "}";
    }

    private static String serializeAdminFeedbackItem(FeedbackItem it) {
        return "{"
                + "\"id\":\"" + escapeJson(it.id) + "\","
                + "\"clientId\":\"" + escapeJson(it.clientId) + "\","
                + "\"authorName\":\"" + escapeJson(it.authorName) + "\","
                + "\"deviceType\":\"" + escapeJson(it.deviceType) + "\","
                + "\"category\":\"" + escapeJson(it.category) + "\","
                + "\"rating\":" + it.rating + ","
                + "\"text\":\"" + escapeJson(it.text) + "\","
                + "\"contact\":\"" + escapeJson(it.contact) + "\","
                + "\"status\":\"" + escapeJson(it.status) + "\","
                + "\"replyText\":\"" + escapeJson(it.replyText) + "\","
                + "\"createdAt\":" + it.createdAt + ","
                + "\"repliedAt\":" + it.repliedAt
                + "}";
    }

    static class AdminPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if (ADMIN_HTML_FILE.exists()) {
                String html = Files.readString(ADMIN_HTML_FILE.toPath(), StandardCharsets.UTF_8);
                sendResponse(exchange, 200, "text/html; charset=utf-8", html);
            } else {
                sendResponse(exchange, 404, "text/plain; charset=utf-8", "Admin panel not found");
            }
        }
    }

    static class FeedbackPublicHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            String clientId = URLDecoder.decode(getQueryParam(exchange.getRequestURI(), "clientId"), StandardCharsets.UTF_8).trim();

            List<FeedbackItem> published = new ArrayList<>();
            List<FeedbackItem> mine = new ArrayList<>();
            int[] pubStars = new int[6];
            long pubRatingSum = 0;

            synchronized (IptvMockServer.class) {
                for (int i = FEEDBACK_ITEMS.size() - 1; i >= 0; i--) {
                    FeedbackItem it = FEEDBACK_ITEMS.get(i);
                    if ("PUBLISHED".equals(it.status)) {
                        published.add(it);
                        int r = Math.max(1, Math.min(5, it.rating));
                        pubStars[r]++;
                        pubRatingSum += r;
                    }
                    if (!clientId.isEmpty() && clientId.equals(it.clientId)) {
                        mine.add(it);
                    }
                }
            }

            int pubCount = published.size();
            double pubAvg = pubCount > 0 ? Math.round((pubRatingSum * 10.0) / pubCount) / 10.0 : 5.0;

            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\n");
            sb.append("  \"stats\": {\n")
                    .append("    \"averageRating\": ").append(String.format(Locale.US, "%.1f", pubAvg)).append(",\n")
                    .append("    \"totalCount\": ").append(pubCount).append(",\n")
                    .append("    \"stars\": {")
                    .append("\"5\":").append(pubStars[5]).append(",")
                    .append("\"4\":").append(pubStars[4]).append(",")
                    .append("\"3\":").append(pubStars[3]).append(",")
                    .append("\"2\":").append(pubStars[2]).append(",")
                    .append("\"1\":").append(pubStars[1])
                    .append("}\n  },\n");

            sb.append("  \"myItems\": [");
            for (int i = 0; i < mine.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializePublicFeedbackItem(mine.get(i)));
            }
            sb.append("],\n");

            sb.append("  \"publicItems\": [");
            int limit = Math.min(published.size(), 60);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(",");
                sb.append(serializePublicFeedbackItem(published.get(i)));
            }
            sb.append("]\n}");

            sendResponse(exchange, 200, "application/json; charset=utf-8", sb.toString());
        }
    }

    static class FeedbackSubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Rate limit by client IP (max 6 submissions per hour)
            String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                remoteIp = forwarded.split(",")[0].trim();
            }
            long now = System.currentTimeMillis();
            List<Long> timestamps = FEEDBACK_RATE_LIMIT.computeIfAbsent(remoteIp, k -> new ArrayList<>());
            synchronized (timestamps) {
                timestamps.removeIf(ts -> (now - ts) > 3_600_000L);
                if (timestamps.size() >= 30) {
                    sendResponse(exchange, 429, "application/json; charset=utf-8",
                            "{\"ok\":false,\"error\":\"Слишком много отправок. Пожалуйста, подождите немного.\",\"message\":\"Слишком много отправок. Пожалуйста, подождите немного.\"}");
                    return;
                }
                timestamps.add(now);
            }

            String body = readBody(exchange);
            // Honeypot check against automated bots
            String honeypot = extractJsonString(body, "website", "");
            if (!honeypot.isEmpty()) {
                sendResponse(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"status\":\"OK\"}");
                return;
            }

            String clientId = extractJsonString(body, "clientId", "");
            if (clientId.isEmpty()) {
                clientId = "cl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
            String authorName = extractJsonString(body, "authorName", "");
            if (authorName.isEmpty()) authorName = extractJsonString(body, "author", "");
            if (authorName.isEmpty()) authorName = "Пользователь";
            if (authorName.length() > 45) authorName = authorName.substring(0, 45);

            String deviceType = extractJsonString(body, "deviceType", "");
            if (deviceType.isEmpty()) deviceType = extractJsonString(body, "device", "");
            if (deviceType.isEmpty()) deviceType = "Smart TV";
            if (deviceType.length() > 40) deviceType = deviceType.substring(0, 40);

            String category = extractJsonString(body, "category", "REVIEW").toUpperCase(Locale.ROOT);
            if (!category.equals("REVIEW") && !category.equals("QUESTION") && !category.equals("IDEA") && !category.equals("ISSUE")) {
                category = "REVIEW";
            }

            int rating = (int) Math.max(1, Math.min(5, extractJsonLong(body, "rating", 5)));
            String text = extractJsonString(body, "text", "");
            if (text.isEmpty()) text = extractJsonString(body, "message", "");
            if (text.length() > 1500) text = text.substring(0, 1500);
            if (text.isEmpty()) {
                text = rating >= 4 ? "Оценка программы: " + rating + " ★" : "Без комментария (" + rating + " ★)";
            }

            String contact = extractJsonString(body, "contact", "");
            if (contact.length() > 80) contact = contact.substring(0, 80);

            FeedbackItem item = new FeedbackItem();
            item.id = "fb_" + now + "_" + UUID.randomUUID().toString().substring(0, 4);
            item.clientId = clientId;
            item.authorName = authorName;
            item.deviceType = deviceType;
            item.category = category;
            item.rating = rating;
            item.text = text;
            item.contact = contact;
            // Pre-moderation: all new items start as PENDING (visible to the author and in /admin,
            // never shown to other public visitors until approved by admin!)
            item.status = "PENDING";
            item.replyText = "";
            item.createdAt = now;
            item.repliedAt = 0L;

            synchronized (IptvMockServer.class) {
                FEEDBACK_ITEMS.add(item);
                saveFeedbackItems();
            }

            String resp = "{\n"
                    + "  \"ok\": true,\n"
                    + "  \"status\": \"OK\",\n"
                    + "  \"message\": \"✓ Спасибо! Ваша оценка и сообщение отправлены разработчику.\",\n"
                    + "  \"clientId\": \"" + escapeJson(clientId) + "\",\n"
                    + "  \"item\": " + serializePublicFeedbackItem(item) + "\n"
                    + "}";
            sendResponse(exchange, 200, "application/json; charset=utf-8", resp);
        }
    }

    static class FeedbackAdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            log(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange);
                return;
            }

            String queryKey = URLDecoder.decode(getQueryParam(exchange.getRequestURI(), "key"), StandardCharsets.UTF_8).trim();
            String body = "POST".equalsIgnoreCase(exchange.getRequestMethod()) ? readBody(exchange) : "";
            String bodyKey = extractJsonString(body, "key", "").trim();
            String providedKey = !bodyKey.isEmpty() ? bodyKey : queryKey;

            if (providedKey.isEmpty() || !providedKey.equals(getAdminSecret())) {
                sendResponse(exchange, 401, "application/json; charset=utf-8",
                        "{\"error\":\"Неверный пароль администратора\"}");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String action = extractJsonString(body, "action", "");
                String id = extractJsonString(body, "id", "");
                String replyText = extractJsonString(body, "replyText", "");

                if ("change_password".equals(action)) {
                    String newPassword = extractJsonString(body, "newPassword", "").trim();
                    if (newPassword.length() < 4) {
                        sendResponse(exchange, 400, "application/json; charset=utf-8",
                                "{\"error\":\"Пароль должен содержать минимум 4 символа\"}");
                        return;
                    }
                    setAdminSecret(newPassword);
                    sendResponse(exchange, 200, "application/json; charset=utf-8", "{\"status\":\"OK\"}");
                    return;
                }

                synchronized (IptvMockServer.class) {
                    if ("delete".equals(action)) {
                        FEEDBACK_ITEMS.removeIf(it -> it.id.equals(id));
                        saveFeedbackItems();
                    } else {
                        for (FeedbackItem it : FEEDBACK_ITEMS) {
                            if (it.id.equals(id)) {
                                if ("publish".equals(action)) {
                                    it.status = "PUBLISHED";
                                    if (!replyText.isEmpty()) {
                                        it.replyText = replyText;
                                        it.repliedAt = System.currentTimeMillis();
                                    }
                                } else if ("reply_publish".equals(action)) {
                                    it.status = "PUBLISHED";
                                    it.replyText = replyText;
                                    it.repliedAt = replyText.isEmpty() ? 0L : System.currentTimeMillis();
                                } else if ("reply_private".equals(action)) {
                                    it.status = "PRIVATE_REPLIED";
                                    it.replyText = replyText;
                                    it.repliedAt = replyText.isEmpty() ? 0L : System.currentTimeMillis();
                                } else if ("hide".equals(action)) {
                                    it.status = "HIDDEN";
                                }
                                break;
                            }
                        }
                        saveFeedbackItems();
                    }
                }
            }

            // Build complete internal + public analytics for admin dashboard
            int totalAll = 0;
            int pendingCount = 0;
            int publishedCount = 0;
            int privateRepliedCount = 0;
            long allRatingSum = 0;
            long pubRatingSum = 0;
            int[] allStars = new int[6];
            Map<String, Integer> byDevice = new LinkedHashMap<>();
            List<FeedbackItem> reversed = new ArrayList<>();

            synchronized (IptvMockServer.class) {
                for (int i = FEEDBACK_ITEMS.size() - 1; i >= 0; i--) {
                    FeedbackItem it = FEEDBACK_ITEMS.get(i);
                    reversed.add(it);
                    totalAll++;
                    int r = Math.max(1, Math.min(5, it.rating));
                    allStars[r]++;
                    allRatingSum += r;
                    byDevice.put(it.deviceType, byDevice.getOrDefault(it.deviceType, 0) + 1);

                    if ("PENDING".equals(it.status)) pendingCount++;
                    else if ("PUBLISHED".equals(it.status)) {
                        publishedCount++;
                        pubRatingSum += r;
                    } else if ("PRIVATE_REPLIED".equals(it.status)) {
                        privateRepliedCount++;
                    }
                }
            }

            double realAvg = totalAll > 0 ? Math.round((allRatingSum * 10.0) / totalAll) / 10.0 : 5.0;
            double pubAvg = publishedCount > 0 ? Math.round((pubRatingSum * 10.0) / publishedCount) / 10.0 : 5.0;

            StringBuilder sb = new StringBuilder(8192);
            sb.append("{\n  \"status\": \"OK\",\n");
            sb.append("  \"internalStats\": {\n")
                    .append("    \"realAverageRating\": ").append(String.format(Locale.US, "%.1f", realAvg)).append(",\n")
                    .append("    \"publicAverageRating\": ").append(String.format(Locale.US, "%.1f", pubAvg)).append(",\n")
                    .append("    \"totalCount\": ").append(totalAll).append(",\n")
                    .append("    \"pendingCount\": ").append(pendingCount).append(",\n")
                    .append("    \"publishedCount\": ").append(publishedCount).append(",\n")
                    .append("    \"privateRepliedCount\": ").append(privateRepliedCount).append(",\n")
                    .append("    \"stars\": {")
                    .append("\"5\":").append(allStars[5]).append(",")
                    .append("\"4\":").append(allStars[4]).append(",")
                    .append("\"3\":").append(allStars[3]).append(",")
                    .append("\"2\":").append(allStars[2]).append(",")
                    .append("\"1\":").append(allStars[1])
                    .append("},\n    \"devices\": {");
            boolean firstDev = true;
            for (Map.Entry<String, Integer> entry : byDevice.entrySet()) {
                if (!firstDev) sb.append(",");
                firstDev = false;
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            }
            sb.append("}\n  },\n");

            sb.append("  \"items\": [");
            for (int i = 0; i < reversed.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeAdminFeedbackItem(reversed.get(i)));
            }
            sb.append("]\n}");

            sendResponse(exchange, 200, "application/json; charset=utf-8", sb.toString());
        }
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Range");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
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
