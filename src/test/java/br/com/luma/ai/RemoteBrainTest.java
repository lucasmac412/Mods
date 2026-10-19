package br.com.luma.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RemoteBrainTest {
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> response = new AtomicReference<>(envelope("FOLLOW", "Vamos juntos!"));
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile long delayMillis;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/chat", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void disabledBrainUsesOnlyLocalParserAndNeverContactsConfiguredServer() throws Exception {
        RemoteBrain brain = brain(false, Duration.ofSeconds(2));
        assertEquals(Action.FOLLOW, brain.respond("me siga", "").get().action());
        assertEquals(Action.CHAT, brain.respond("vamos explorar?", "").get().action());
        assertEquals(0, requests.get());
    }

    @Test
    void explicitCommandsAndNegationsRemainLocalEvenWhenEnabled() throws Exception {
        RemoteBrain brain = brain(true, Duration.ofSeconds(2));
        assertEquals(Action.STOP, brain.respond("pare", "").get().action());
        assertEquals(Action.MINE, brain.respond("minere pedra", "").get().action());
        assertEquals(Action.STOP, brain.respond("não me siga", "").get().action());
        assertEquals(Action.CHAT, brain.respond("não pare", "").get().action());
        assertEquals(Action.CHAT, brain.respond("me siga ou construa", "").get().action());
        assertEquals(0, requests.get());
    }

    @Test
    void sendsEscapedContextAndAcceptsOnlyAnEnumeratedAction() throws Exception {
        Intent intent = brain(true, Duration.ofSeconds(2)).respond("vamos \"juntos\"?", "perto da casa").get(3, TimeUnit.SECONDS);
        assertEquals(Action.FOLLOW, intent.action());
        assertEquals("Vamos juntos!", intent.reply());
        JsonObject payload = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertEquals("test-model", payload.get("model").getAsString());
        String userInput = payload.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
        assertEquals("vamos \"juntos\"?", JsonParser.parseString(userInput).getAsJsonObject().get("fala_do_jogador").getAsString());
        assertEquals("Bearer example-only-not-a-real-secret", authorization.get());
    }

    @Test
    void unknownActionCannotBecomeAnExecutableCommand() throws Exception {
        response.set(envelope("RUN_SHELL", "Não posso executar isso."));
        assertEquals(Action.CHAT, ask().action());
    }

    @Test
    void stripsMinecraftFormattingAndBoundsReplies() throws Exception {
        response.set(envelope("CHAT", "§a" + "Olá! ".repeat(200)));
        String reply = ask().reply();
        assertTrue(reply.length() <= 600);
        assertFalse(reply.contains("§"));
    }

    @Test
    void invalidJsonAndHttpErrorsUseAnHonestLocalFallback() throws Exception {
        response.set("not json");
        assertTrue(ask().reply().contains("indisponível"));
        status.set(503);
        assertEquals(Action.CHAT, ask().action());
    }

    @Test
    void rejectsOversizedResponsesInsteadOfBufferingThemWithoutLimit() throws Exception {
        response.set("x".repeat(40_000));
        assertTrue(ask().reply().contains("indisponível"));
    }

    @Test
    void aStalledServerCannotBlockCompletionPastTheTimeout() throws Exception {
        delayMillis = 1_000;
        Intent intent = brain(true, Duration.ofMillis(100)).respond("vamos explorar?", "").get(2, TimeUnit.SECONDS);
        assertTrue(intent.reply().contains("indisponível"));
    }

    @Test
    void credentialBearingAndInvalidUrlsAreNeverRequested() throws Exception {
        RemoteBrain brain = new RemoteBrain(true, "http://user:pass@127.0.0.1:" + server.getAddress().getPort() + "/chat",
                "test-model", "", Duration.ofSeconds(1));
        assertTrue(brain.respond("vamos explorar?", "").get().reply().contains("indisponível"));
        assertEquals(0, requests.get());
    }

    private Intent ask() throws Exception {
        return brain(true, Duration.ofSeconds(2)).respond("vamos explorar?", "").get(3, TimeUnit.SECONDS);
    }

    private RemoteBrain brain(boolean enabled, Duration timeout) {
        return new RemoteBrain(enabled, "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                "test-model", "example-only-not-a-real-secret", timeout);
    }

    private static String envelope(String action, String reply) {
        JsonObject message = new JsonObject();
        JsonObject result = new JsonObject();
        result.addProperty("action", action);
        result.addProperty("reply", reply);
        message.addProperty("content", result.toString());
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
        choices.add(choice);
        JsonObject envelope = new JsonObject();
        envelope.add("choices", choices);
        return envelope.toString();
    }
}
