package br.com.luma.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** A local, explicitly enabled browser voice panel. Does not access Minecraft or a microphone. */
public final class VoiceBridge implements AutoCloseable {
    private static final int MAX_TEXT = 500;
    private static final int MAX_BODY = 4096;
    private static final int MAX_MESSAGES = 80;
    private static final int MAX_COMMANDS_PER_MINUTE = 40;
    private final Consumer<String> onText;
    private final ArrayDeque<SpokenMessage> messages = new ArrayDeque<>();
    private HttpServer server;
    private ThreadPoolExecutor executor;
    private String url;
    private long sequence;
    private long commandWindowStart;
    private int commandsInWindow;

    public VoiceBridge(Consumer<String> onText) {
        this.onText = java.util.Objects.requireNonNull(onText, "onText");
    }

    /** Start only after the owning local player explicitly requests voice. URL contains a secret fragment. */
    public synchronized String start() throws IOException {
        if (server != null) return url;
        Map<String, Asset> assets = Map.of(
                "/", asset("index.html", "text/html; charset=utf-8"),
                "/index.html", asset("index.html", "text/html; charset=utf-8"),
                "/app.js", asset("app.js", "text/javascript; charset=utf-8"),
                "/style.css", asset("style.css", "text/css; charset=utf-8"));
        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16);
        String host = "127.0.0.1:" + created.getAddress().getPort();
        String origin = "http://" + host;
        ThreadPoolExecutor workers = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16), runnable -> {
                    Thread thread = new Thread(runnable, "Luma-local-voice");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        created.setExecutor(workers);
        created.createContext("/", exchange -> handle(exchange, host, origin, token, assets));
        server = created;
        executor = workers;
        url = origin + "/#token=" + token;
        commandWindowStart = System.nanoTime();
        commandsInWindow = 0;
        try {
            created.start();
        } catch (RuntimeException failure) {
            close();
            throw new IOException("Não foi possível iniciar o painel de voz local.", failure);
        }
        return url;
    }

    /** Enqueue a line for speech; old lines are discarded after the bounded history fills. */
    public synchronized void say(String text) {
        if (server == null || text == null || text.isBlank()) return;
        String clean = text.strip();
        if (clean.length() > MAX_TEXT) clean = clean.substring(0, MAX_TEXT);
        messages.addLast(new SpokenMessage(++sequence, clean));
        while (messages.size() > MAX_MESSAGES) messages.removeFirst();
    }

    @Override
    public synchronized void close() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
        url = null;
        messages.clear();
    }

    private void handle(HttpExchange exchange, String host, String origin, String token,
                        Map<String, Asset> assets) throws IOException {
        try {
            securityHeaders(exchange);
            // Host and Origin checks prevent DNS rebinding and requests initiated by other websites.
            if (!singleHeaderEquals(exchange, "Host", host)) {
                error(exchange, 403, "Host não permitido.");
                return;
            }
            var origins = exchange.getRequestHeaders().get("Origin");
            if (origins != null && (origins.size() != 1 || !origin.equals(origins.getFirst()))) {
                error(exchange, 403, "Origem não permitida.");
                return;
            }
            String fetchSite = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
            if (fetchSite != null && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) {
                error(exchange, 403, "Abra o painel pelo link no jogo.");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            if (!path.startsWith("/api/")) {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    error(exchange, 405, "Método não permitido.");
                    return;
                }
                Asset asset = assets.get(path);
                if (asset == null) error(exchange, 404, "Página não encontrada.");
                else send(exchange, 200, asset.contentType(), asset.bytes());
                return;
            }
            if (!authorized(exchange, token)) {
                error(exchange, 401, "Abra o link completo gerado por /luma voz.");
                return;
            }
            switch (path) {
                case "/api/status" -> {
                    if (!requireMethod(exchange, "GET")) return;
                    JsonObject status = new JsonObject();
                    synchronized (this) {
                        status.addProperty("running", server != null);
                        status.addProperty("cursor", sequence);
                        status.addProperty("queuedMessages", messages.size());
                    }
                    status.addProperty("maxTextLength", MAX_TEXT);
                    status.addProperty("language", "pt-BR");
                    json(exchange, 200, status);
                }
                case "/api/messages" -> {
                    if (!requireMethod(exchange, "GET")) return;
                    String query = exchange.getRequestURI().getRawQuery();
                    if (query == null || !query.matches("cursor=[0-9]{1,19}")) {
                        error(exchange, 400, "Cursor inválido.");
                        return;
                    }
                    long cursor;
                    try {
                        cursor = Long.parseLong(query.substring(7));
                    } catch (NumberFormatException failure) {
                        error(exchange, 400, "Cursor inválido.");
                        return;
                    }
                    JsonObject response = new JsonObject();
                    JsonArray lines = new JsonArray();
                    synchronized (this) {
                        for (SpokenMessage message : messages) {
                            if (message.sequence() <= cursor) continue;
                            JsonObject line = new JsonObject();
                            line.addProperty("sequence", message.sequence());
                            line.addProperty("text", message.text());
                            lines.add(line);
                        }
                        response.addProperty("cursor", sequence);
                    }
                    response.add("messages", lines);
                    json(exchange, 200, response);
                }
                case "/api/text" -> {
                    if (!requireMethod(exchange, "POST")) return;
                    if (!singleHeaderEquals(exchange, "Origin", origin)) {
                        error(exchange, 403, "Origem obrigatória para enviar comandos.");
                        return;
                    }
                    String type = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/json")) {
                        error(exchange, 415, "Use application/json.");
                        return;
                    }
                    byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                    if (body.length > MAX_BODY) {
                        error(exchange, 413, "Mensagem muito grande.");
                        return;
                    }
                    String text;
                    try {
                        var parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
                        if (!parsed.isJsonObject()) throw new IllegalArgumentException();
                        var value = parsed.getAsJsonObject().get("text");
                        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                            throw new IllegalArgumentException();
                        }
                        text = value.getAsString().strip();
                        if (text.isEmpty() || text.length() > MAX_TEXT || text.indexOf('\0') >= 0) {
                            throw new IllegalArgumentException();
                        }
                    } catch (RuntimeException failure) {
                        error(exchange, 400, "Envie um texto de 1 a 500 caracteres.");
                        return;
                    }
                    if (!allowCommand()) {
                        error(exchange, 429, "Muitos comandos. Aguarde um minuto.");
                        return;
                    }
                    try {
                        onText.accept(text);
                    } catch (RuntimeException failure) {
                        error(exchange, 503, "O jogo não conseguiu receber o comando.");
                        return;
                    }
                    JsonObject accepted = new JsonObject();
                    accepted.addProperty("accepted", true);
                    json(exchange, 202, accepted);
                }
                default -> error(exchange, 404, "Recurso não encontrado.");
            }
        } finally {
            exchange.close();
        }
    }

    private synchronized boolean allowCommand() {
        long now = System.nanoTime();
        if (now - commandWindowStart >= TimeUnit.MINUTES.toNanos(1)) {
            commandWindowStart = now;
            commandsInWindow = 0;
        }
        return commandsInWindow++ < MAX_COMMANDS_PER_MINUTE;
    }

    private static boolean authorized(HttpExchange exchange, String token) {
        var headers = exchange.getRequestHeaders().get("Authorization");
        if (headers == null || headers.size() != 1) return false;
        byte[] actual = headers.getFirst().getBytes(StandardCharsets.UTF_8);
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private static boolean singleHeaderEquals(HttpExchange exchange, String key, String expected) {
        var values = exchange.getRequestHeaders().get(key);
        return values != null && values.size() == 1 && values.getFirst().equals(expected);
    }

    private static boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (method.equals(exchange.getRequestMethod())) return true;
        exchange.getResponseHeaders().set("Allow", method);
        error(exchange, 405, "Método não permitido.");
        return false;
    }

    private static Asset asset(String name, String type) throws IOException {
        try (InputStream input = VoiceBridge.class.getResourceAsStream("/voice/" + name)) {
            if (input == null) throw new IOException("Recurso do painel ausente: " + name);
            return new Asset(input.readAllBytes(), type);
        }
    }

    private static void securityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; "
                + "connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        headers.set("Permissions-Policy", "camera=(), geolocation=(), microphone=(self)");
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", message);
        json(exchange, status, body);
    }

    private static void json(HttpExchange exchange, int status, JsonObject body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private record Asset(byte[] bytes, String contentType) { }
    private record SpokenMessage(long sequence, String text) { }
}
