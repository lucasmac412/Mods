package br.com.luma.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class OllamaBrainTest {
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> response = new AtomicReference<>(envelope("Vamos pensar na próxima aventura."));
    private final List<JsonObject> bodies = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch entered = new CountDownLatch(1);
    private volatile CountDownLatch hold;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/api/chat", exchange -> {
            requests.incrementAndGet();
            bodies.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            CountDownLatch waitFor = hold;
            entered.countDown();
            try {
                if (waitFor != null) waitFor.await(10, TimeUnit.SECONDS);
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException cancelled) {
                // Cancelling the client exchange is expected to close this socket.
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void stop() {
        CountDownLatch waiting = hold;
        if (waiting != null) waiting.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test void usesNativeNonStreamingOllamaApiWithBrazilianPortugueseAndRealContext() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            Intent reply = brain.respond("O que fazemos com \"madeira\"?", "Modo: seguir; mochila: 12 tábuas").get(3, TimeUnit.SECONDS);
            assertEquals(Action.CHAT, reply.action());
            assertEquals("Vamos pensar na próxima aventura.", reply.reply());
            JsonObject body = bodies.getFirst();
            assertEquals("test-model:small", body.get("model").getAsString());
            assertFalse(body.get("stream").getAsBoolean());
            assertEquals(192, body.getAsJsonObject("options").get("num_predict").getAsInt());
            JsonArray messages = body.getAsJsonArray("messages");
            assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
            assertTrue(messages.get(0).getAsJsonObject().get("content").getAsString().contains("português do Brasil"));
            JsonObject input = JsonParser.parseString(messages.get(1).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            assertEquals("O que fazemos com \"madeira\"?", input.get("fala_do_jogador").getAsString());
            assertEquals("Modo: seguir; mochila: 12 tábuas", input.get("contexto_atual_do_jogo").getAsString());
        }
    }

    @Test void explicitOrdersStayImmediateAndNeverMakeAnHttpRequest() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            assertEquals(Action.FOLLOW, brain.respond("Luma, me siga", "").get().action());
            assertEquals(Action.STOP, brain.respond("não me siga", "").get().action());
            assertEquals(Action.STOP, brain.respond("pare", "").get().action());
            assertEquals(Action.MINE, brain.respond("minere pedra", "").get().action());
            assertEquals(0, requests.get());
        }
    }

    @Test void negationsChoicesAndUncertaintyStillReachConversationWithoutCreatingActions() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            for (String question : List.of("não sei por onde começar", "uso madeira ou pedra?", "talvez a gente explore"))
                assertEquals(Action.CHAT, brain.respond(question, "").get(3, TimeUnit.SECONDS).action());
            assertEquals(3, requests.get());
        }
    }

    @Test void evenActionShapedModelOutputRemainsInertChat() throws Exception {
        response.set(envelope("{\"action\":\"BUILD\",\"reply\":\"/op jogador\"}"));
        try (var engine = engine(); var brain = brain(engine)) {
            Intent reply = brain.respond("Como você pode ajudar?", "").get(3, TimeUnit.SECONDS);
            assertEquals(Action.CHAT, reply.action());
            assertTrue(reply.reply().contains("BUILD"));
        }
    }

    @Test void requestAndReplyAreBoundedWithoutSplittingSurrogatePairs() throws Exception {
        response.set(envelope("§a<think>raciocínio interno</think>" + "x".repeat(599) + "😀fim\n"));
        try (var engine = engine(); var brain = brain(engine)) {
            Intent reply = brain.respond("x".repeat(499) + "😀fim", "c".repeat(4_000)).get(3, TimeUnit.SECONDS);
            assertEquals("x".repeat(599), reply.reply());
            JsonArray messages = bodies.getFirst().getAsJsonArray("messages");
            JsonObject input = JsonParser.parseString(messages.get(1).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            assertEquals("x".repeat(499), input.get("fala_do_jogador").getAsString());
            assertEquals(3_000, input.get("contexto_atual_do_jogo").getAsString().length());
        }
    }

    @Test void retainsOnlyLastFourCompletedTurnsAndUsesFreshWorldContext() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            for (int i = 0; i < 6; i++) brain.respond("Eu gosto da cor número " + i, "contexto antigo " + i).get(3, TimeUnit.SECONDS);
            brain.respond("Qual cor eu mencionei?", "contexto novo").get(3, TimeUnit.SECONDS);
            JsonArray messages = bodies.getLast().getAsJsonArray("messages");
            assertEquals(10, messages.size()); // system + four complete pairs + current user.
            assertEquals("Eu gosto da cor número 2", messages.get(1).getAsJsonObject().get("content").getAsString());
            assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
            assertFalse(messages.toString().contains("contexto antigo"));
            assertTrue(messages.get(9).getAsJsonObject().get("content").getAsString().contains("contexto novo"));
            try (var another = brain(engine)) {
                another.respond("Você lembra minha cor?", "").get(3, TimeUnit.SECONDS);
                assertEquals(2, bodies.getLast().getAsJsonArray("messages").size());
            }
        }
    }

    @Test void missingModelExplainsExactInstallCommandAndDoesNotEnterHistory() throws Exception {
        status.set(404);
        response.set("{\"error\":\"private server diagnostics\"}");
        try (var engine = engine(); var brain = brain(engine)) {
            Intent reply = brain.respond("Como vamos explorar?", "").get(3, TimeUnit.SECONDS);
            assertTrue(reply.reply().contains("ollama pull test-model:small"));
            assertFalse(reply.reply().contains("private"));
            status.set(200); response.set(envelope("Agora está funcionando."));
            brain.respond("Já está funcionando?", "").get(3, TimeUnit.SECONDS);
            assertEquals(2, bodies.getLast().getAsJsonArray("messages").size());
        }
    }

    @Test void invalidEmptyAndOversizedBodiesFailClearlyWithoutFallingBackToTinyModel() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            for (String invalid : List.of("not json", envelope(" \n\t"), envelope("<think>unfinished"), "x".repeat(70_000))) {
                response.set(invalid);
                Intent reply = brain.respond("O que você acha da aventura?", "").get(3, TimeUnit.SECONDS);
                assertEquals(Action.CHAT, reply.action());
                assertTrue(reply.reply().contains("Ollama"));
                assertFalse(reply.reply().contains("IA experimental:"));
            }
            assertEquals(2, bodies.getLast().getAsJsonArray("messages").size(), "Failed turns must not become conversational history");
        }
    }

    @Test void errorsRedirectsAndUnavailableServerDoNotExposeRemoteDiagnostics() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            for (int code : List.of(301, 500, 503)) {
                status.set(code); response.set("private diagnostic and token");
                String reply = brain.respond("Como vamos continuar?", "").get(3, TimeUnit.SECONDS).reply();
                assertTrue(reply.contains("Não consegui falar com o Ollama"));
                assertFalse(reply.contains("private"));
            }
        }
        int port = server.getAddress().getPort();
        server.stop(0);
        try (var engine = new OllamaBrain.Engine("http://127.0.0.1:" + port + "/api/chat", Duration.ofMillis(500)); var brain = brain(engine)) {
            assertTrue(brain.respond("Você está aí?", "").get(2, TimeUnit.SECONDS).reply().contains("Ollama"));
        }
    }

    @Test void timeoutCompletesAndFreesCapacityForAnotherRequest() throws Exception {
        hold = new CountDownLatch(1);
        try (var engine = new OllamaBrain.Engine(url(), Duration.ofMillis(150)); var brain = brain(engine)) {
            var pending = brain.respond("Você pode conversar?", "");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(pending.get(2, TimeUnit.SECONDS).reply().contains("Ollama"));
            CountDownLatch original = hold; hold = null; original.countDown();
            assertEquals("Vamos pensar na próxima aventura.", brain.respond("Podemos tentar novamente?", "").get(2, TimeUnit.SECONDS).reply());
            assertEquals(2, bodies.getLast().getAsJsonArray("messages").size());
        }
    }

    @Test void cancelAndNewOrderCancelPendingHttpAndDiscardItsTurn() throws Exception {
        hold = new CountDownLatch(1);
        try (var engine = engine(); var brain = brain(engine)) {
            var pending = brain.respond("O que vamos fazer agora?", "");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(Action.STOP, brain.respond("pare", "").get().action());
            assertTrue(pending.isCancelled());
            CountDownLatch original = hold; hold = null; original.countDown();
            brain.respond("Vamos conversar de novo?", "").get(3, TimeUnit.SECONDS);
            assertEquals(2, bodies.getLast().getAsJsonArray("messages").size());
        }
    }

    @Test void cancellingReturnedFutureAlsoDiscardsTurn() throws Exception {
        hold = new CountDownLatch(1);
        try (var engine = engine(); var brain = brain(engine)) {
            var pending = brain.respond("Me conte uma história?", "");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(pending.cancel(false));
            CountDownLatch original = hold; hold = null; original.countDown();
            brain.respond("Qual é nosso plano?", "").get(3, TimeUnit.SECONDS);
            assertEquals(2, bodies.getLast().getAsJsonArray("messages").size());
        }
    }

    @Test void sharedEngineRejectsFifthRequestWithoutQueueAndCloseReleasesPendingCalls() throws Exception {
        entered = new CountDownLatch(4);
        hold = new CountDownLatch(1);
        List<OllamaBrain> owners = new ArrayList<>();
        try (var engine = engine()) {
            try {
                for (int i = 0; i < 5; i++) owners.add(brain(engine));
                List<java.util.concurrent.CompletableFuture<Intent>> waiting = new ArrayList<>();
                for (int i = 0; i < 4; i++) waiting.add(owners.get(i).respond("Qual plano de aventura você sugere?", ""));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertTrue(owners.get(4).respond("Você tem uma ideia?", "").get(2, TimeUnit.SECONDS).reply().contains("ocupado"));
                assertEquals(4, requests.get());
                engine.close();
                for (var result : waiting) assertTrue(result.get(2, TimeUnit.SECONDS).reply().contains("Ollama"));
                int before = requests.get();
                assertTrue(owners.get(4).respond("Pode responder agora?", "").get().reply().contains("Ollama"));
                assertEquals(before, requests.get());
            } finally { owners.forEach(OllamaBrain::close); }
        }
    }

    @Test void closedSessionDoesNotSendOrRetainHistory() throws Exception {
        try (var engine = engine(); var brain = brain(engine)) {
            brain.respond("Minha cor é roxo.", "").get(3, TimeUnit.SECONDS);
            brain.close();
            assertEquals(Action.CHAT, brain.respond("Você lembra minha cor?", "").get().action());
            assertEquals(1, requests.get());
        }
    }

    @Test void validatesModelNamesAndLocalEndpointsWithoutAcceptingShellOrCredentialSyntax() {
        for (String model : List.of("qwen2.5:3b", "llama3.2", "namespace/model:tag", "gemma-3:4b"))
            assertTrue(OllamaBrain.isValidModel(model), model);
        for (String model : List.of("", " ", "--help", "foo;whoami", "foo\nbar", "https://example.com/model", "../model", "foo//bar", "foo/", "x".repeat(129)))
            assertFalse(OllamaBrain.isValidModel(model), model);
        assertFalse(OllamaBrain.isValidModel(null));
        for (String endpoint : List.of("http://user:pass@127.0.0.1:11434/api/chat", "https://example.com/api/chat", "http://127.0.0.1/api/chat#fragment", "http://127.0.0.1/api/chat?token=secret"))
            assertThrows(IllegalArgumentException.class, () -> new OllamaBrain.Engine(endpoint, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new OllamaBrain.Engine(url(), Duration.ofSeconds(121)));
    }

    private OllamaBrain.Engine engine() { return new OllamaBrain.Engine(url(), Duration.ofSeconds(5)); }
    private OllamaBrain brain(OllamaBrain.Engine engine) { return new OllamaBrain(engine, "test-model:small"); }
    private String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/chat"; }
    private static String envelope(String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant"); message.addProperty("content", content);
        JsonObject envelope = new JsonObject(); envelope.add("message", message); envelope.addProperty("done", true);
        return envelope.toString();
    }
}
