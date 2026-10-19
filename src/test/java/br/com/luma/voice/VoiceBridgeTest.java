package br.com.luma.voice;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class VoiceBridgeTest {
    private VoiceBridge bridge;
    private HttpClient client;
    private URI origin;
    private String token;
    private final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void startLocalService() throws Exception {
        bridge = new VoiceBridge(received::add);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        URI link = URI.create(bridge.start());
        origin = URI.create("http://" + link.getRawAuthority());
        token = link.getRawFragment().substring("token=".length());
    }

    @AfterEach
    void closeLocalService() {
        if (bridge != null) bridge.close();
        if (client != null) client.close();
    }

    @Test
    void onlyAuthenticatedSameOriginRequestsCanSubmitCommands() throws Exception {
        assertEquals("127.0.0.1", origin.getHost());
        assertEquals(401, request("GET", "/api/status", null, null, null, null).statusCode());
        assertEquals(401, request("GET", "/api/status", "wrong-key", null, null, null).statusCode());
        assertEquals(403, request("GET", "/api/status", token, "https://example.com", null, null).statusCode());
        assertEquals(403, post("{\"text\":\"me siga\"}", null, "application/json").statusCode());
        assertEquals(403, post("{\"text\":\"me siga\"}", "https://example.com", "application/json").statusCode());
        assertTrue(received.isEmpty(), "Rejected browser requests must not reach the game callback");

        var response = post("{\"text\":\"  Luma, me siga em português!  \"}", origin.toString(), "application/json; charset=UTF-8");
        assertEquals(202, response.statusCode());
        assertEquals(List.of("Luma, me siga em português!"), List.copyOf(received));
    }

    @Test
    void rejectsForgedHostEvenWithAValidToken() throws Exception {
        // A raw request avoids globally enabling restricted Host headers in HttpClient.
        try (Socket socket = new Socket(origin.getHost(), origin.getPort())) {
            socket.setSoTimeout(3000);
            String request = "GET /api/status HTTP/1.1\r\nHost: attacker.example:" + origin.getPort()
                    + "\r\nAuthorization: Bearer " + token + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertEquals("HTTP/1.1 403 Forbidden", reader.readLine());
        }
        assertTrue(received.isEmpty());
    }

    @Test
    void rejectsOversizedOrInvalidMessagesWithoutDispatchingThem() throws Exception {
        assertEquals(400, post("{\"text\":\"" + "a".repeat(501) + "\"}", origin.toString(), "application/json").statusCode());
        assertEquals(413, post("a".repeat(4097), origin.toString(), "application/json").statusCode());
        assertEquals(415, post("me siga", origin.toString(), "text/plain").statusCode());
        assertEquals(400, post("{\"text\":42}", origin.toString(), "application/json").statusCode());
        assertEquals(400, post("{\"text\":\"   \"}", origin.toString(), "application/json").statusCode());
        assertTrue(received.isEmpty());
        assertEquals(202, post("{\"text\":\"" + "a".repeat(500) + "\"}", origin.toString(), "application/json").statusCode());
        assertEquals(1, received.size(), "A valid message must still work after rejected requests");
    }

    @Test
    void cursorPreventsRepeatedSpeechAndKeepsLaterMessagesOrdered() throws Exception {
        bridge.say("Olá, explorador!");
        bridge.say("Vamos construir?");
        var firstPoll = request("GET", "/api/messages?cursor=0", token, null, null, null);
        assertEquals(200, firstPoll.statusCode());
        var first = JsonParser.parseString(firstPoll.body()).getAsJsonObject();
        var messages = first.getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("Olá, explorador!", messages.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("Vamos construir?", messages.get(1).getAsJsonObject().get("text").getAsString());
        long cursor = first.get("cursor").getAsLong();
        var repeated = request("GET", "/api/messages?cursor=" + cursor, token, null, null, null);
        assertTrue(JsonParser.parseString(repeated.body()).getAsJsonObject().getAsJsonArray("messages").isEmpty());
        bridge.say("Estou pronta.");
        var later = request("GET", "/api/messages?cursor=" + cursor, token, null, null, null);
        var next = JsonParser.parseString(later.body()).getAsJsonObject();
        assertEquals(1, next.getAsJsonArray("messages").size());
        assertEquals("Estou pronta.", next.getAsJsonArray("messages").get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(next.get("cursor").getAsLong() > cursor);
    }

    @Test
    void restartingServiceRevokesOldLinksAndDoesNotExposeSecretInAssetsOrStatus() throws Exception {
        var status = request("GET", "/api/status", token, null, null, null);
        assertEquals(200, status.statusCode());
        assertFalse(status.body().contains(token));
        var page = request("GET", "/", null, null, null, null);
        assertEquals(200, page.statusCode());
        assertFalse(page.body().contains(token));
        assertEquals("no-store", page.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(page.headers().firstValue("Access-Control-Allow-Origin").isEmpty());

        String oldToken = token;
        bridge.close();
        bridge.close();
        URI restarted = URI.create(bridge.start());
        origin = URI.create("http://" + restarted.getRawAuthority());
        token = restarted.getRawFragment().substring("token=".length());
        assertNotEquals(oldToken, token);
        assertEquals(401, request("GET", "/api/status", oldToken, null, null, null).statusCode());
        assertEquals(200, request("GET", "/api/status", token, null, null, null).statusCode());
    }

    private HttpResponse<String> post(String body, String requestOrigin, String type) throws Exception {
        return request("POST", "/api/text", token, requestOrigin, body, type);
    }

    private HttpResponse<String> request(String method, String path, String key, String requestOrigin,
                                         String body, String contentType) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5));
        if (key != null) request.header("Authorization", "Bearer " + key);
        if (requestOrigin != null) request.header("Origin", requestOrigin);
        if (contentType != null) request.header("Content-Type", contentType);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
