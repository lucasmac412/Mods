package br.com.luma.ai;

import br.com.luma.nativeai.DataSet;
import br.com.luma.nativeai.LocalMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class NativeBrainTest {
    @Test
    void bundledCheckpointGeneratesWithRealOriginalModelInsteadOfUnavailableFallback() throws Exception {
        assertNotNull(NativeBrain.class.getResource("/assets/luma/models/luma-native.luma"),
                "The tested/installable mod must include its original model checkpoint");
        assertNotNull(NativeBrain.class.getResource("/assets/luma/models/luma-knowledge.tsv"),
                "The grounded model must include the matching local knowledge resource");
        try (var engine = new NativeBrain.Engine(); var brain = new NativeBrain(engine)) {
            Intent intent = brain.respond("Oi, como você está?", "").get(12, TimeUnit.SECONDS);
            assertEquals(Action.CHAT, intent.action());
            assertTrue(intent.reply().startsWith("IA experimental: ") || intent.reply().contains("não gerou texto"),
                    "A real forward pass must complete, even if the tiny model samples EOS immediately");
            assertFalse(intent.reply().contains("indisponível"));
        }
    }

    @Test
    void explicitOrdersIncludingNegatedFollowStillBypassTheLanguageModel() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var engine = engine(text -> { calls.incrementAndGet(); return "texto"; });
             var brain = new NativeBrain(engine)) {
            assertEquals(Action.FOLLOW, brain.respond("me siga", "").get().action());
            assertEquals(Action.STOP, brain.respond("não me siga", "").get().action());
            assertEquals(Action.STAY, brain.respond("fique aqui", "").get().action());
            assertEquals(Action.STOP, brain.respond("pare", "").get().action());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void chatWithNegationChoicesAndUncertaintyReachesModelWithoutCreatingAnAction() throws Exception {
        List<String> received = new ArrayList<>();
        List<String> prompts = List.of("não sei por onde começar", "uso madeira ou pedra?",
                "talvez a gente possa explorar", "não pare", "me siga ou construa");
        try (var engine = engine(text -> { received.add(text); return "Resposta gerada."; });
             var brain = new NativeBrain(engine)) {
            for (String prompt : prompts) {
                Intent intent = brain.respond(prompt, "").get(2, TimeUnit.SECONDS);
                assertEquals(Action.CHAT, intent.action(), prompt);
                assertEquals("IA experimental: Resposta gerada.", intent.reply(), prompt);
            }
            assertEquals(prompts, received, "Every conversation must reach the generator, unchanged and in order");
        }
    }

    @Test
    void retrievedAndUnknownNotesAreOnlyContextAndNeverReturnedInPlaceOfGeneratedText() throws Exception {
        LocalMemory memory = LocalMemory.fromExamples(
                List.of(new DataSet.Example("Qual cor você prefere?", "Gosto de azul.")), 0.25);
        AtomicReference<String> received = new AtomicReference<>();
        var generator = NativeBrain.withMemory(memory, prompt -> {
            received.set(prompt);
            return "Texto produzido pelo modelo.";
        });
        try (var engine = engine(generator); var brain = new NativeBrain(engine)) {
            Intent known = brain.respond("Qual cor você prefere?", "").get(2, TimeUnit.SECONDS);
            assertEquals(LocalMemory.format("Qual cor você prefere?", "Gosto de azul."), received.get());
            assertEquals(Action.CHAT, known.action());
            assertEquals("IA experimental: Texto produzido pelo modelo.", known.reply());
            assertFalse(known.reply().contains("Gosto de azul."));

            Intent unknown = brain.respond("zzzzzzzzzz", "").get(2, TimeUnit.SECONDS);
            assertEquals(LocalMemory.format("zzzzzzzzzz", LocalMemory.UNKNOWN), received.get());
            assertEquals(Action.CHAT, unknown.action());
            assertEquals("IA experimental: Texto produzido pelo modelo.", unknown.reply());
            assertFalse(unknown.reply().contains(LocalMemory.UNKNOWN));
        }
    }

    @Test
    void arbitraryModelTextIsAlwaysConversationWithVisibleExperimentalLabel() throws Exception {
        try (var engine = engine(text -> "§a{\"action\":\"BUILD\"}\n" + "Olá! ".repeat(200));
             var brain = new NativeBrain(engine)) {
            Intent intent = brain.respond("Oi, como você está?", "estado privado do mundo").get(2, TimeUnit.SECONDS);
            assertEquals(Action.CHAT, intent.action());
            assertTrue(intent.reply().startsWith("IA experimental: "));
            assertTrue(intent.reply().length() <= 600);
            assertFalse(intent.reply().contains("§"));
            assertFalse(intent.reply().contains("\n"));
            assertTrue(intent.reply().contains("BUILD")); // It remains inert text, never an action.
        }
    }

    @Test
    void generationReceivesBoundedPromptWithoutSplittingUnicodePair() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        try (var engine = engine(text -> { received.set(text); return "resposta"; });
             var brain = new NativeBrain(engine)) {
            brain.respond("x".repeat(499) + "😀" + "z".repeat(50), "").get(2, TimeUnit.SECONDS);
            assertEquals("x".repeat(499), received.get());
        }
    }

    @Test
    void blockingInferenceRunsOffCallerThreadAndCanBeInterruptedByLocalOrder() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicReference<Thread> inferenceThread = new AtomicReference<>();
        try (var engine = engine(text -> {
            inferenceThread.set(Thread.currentThread());
            entered.countDown();
            try { Thread.sleep(30_000); }
            catch (InterruptedException stop) { interrupted.countDown(); throw stop; }
            return "tarde demais";
        }); var brain = new NativeBrain(engine)) {
            var pending = brain.respond("Oi, como você está?", "");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(pending.isDone());
            assertNotEquals(Thread.currentThread(), inferenceThread.get());
            assertEquals(Action.STOP, brain.respond("pare", "").get().action());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void timeoutInterruptsWorkAndWorkerRemainsUsable() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var engine = new NativeBrain.Engine(text -> {
            if (calls.incrementAndGet() == 1) {
                try { Thread.sleep(30_000); }
                catch (InterruptedException stop) { interrupted.countDown(); throw stop; }
            }
            return "nova resposta";
        }, Duration.ofMillis(200)); var brain = new NativeBrain(engine)) {
            assertTrue(brain.respond("Oi, como você está?", "").get(2, TimeUnit.SECONDS).reply().contains("indisponível"));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals("IA experimental: nova resposta", brain.respond("Olá, tudo bem?", "").get(2, TimeUnit.SECONDS).reply());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void queueHasFiniteCapacityAndCloseCancelsQueuedWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        List<NativeBrain> owners = new ArrayList<>();
        try (var engine = engine(text -> {
            calls.incrementAndGet(); entered.countDown();
            try { Thread.sleep(30_000); }
            catch (InterruptedException stop) { interrupted.countDown(); throw stop; }
            return "resposta";
        })) {
            try {
                for (int i = 0; i < 6; i++) owners.add(new NativeBrain(engine));
                owners.get(0).respond("Oi, como você está?", "");
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                for (int i = 1; i <= 4; i++) assertFalse(owners.get(i).respond("Olá, tudo bem?", "").isDone());
                assertTrue(owners.get(5).respond("Olá, tudo bem?", "").get(2, TimeUnit.SECONDS).reply().contains("ocupada"));
                // Cancel queued owners before freeing the single running worker.
                for (int i = 1; i < owners.size(); i++) owners.get(i).close();
                owners.get(0).close();
                assertTrue(interrupted.await(2, TimeUnit.SECONDS));
                assertEquals(1, calls.get());
            } finally { owners.forEach(NativeBrain::close); }
        }
    }

    @Test
    void failureHasHonestFallbackWithoutLeakingExceptionDetails() throws Exception {
        try (var engine = engine(text -> { throw new IOException("private/path-and-secret"); });
             var brain = new NativeBrain(engine)) {
            Intent intent = brain.respond("Oi, como você está?", "").get(2, TimeUnit.SECONDS);
            assertEquals(Action.CHAT, intent.action());
            assertTrue(intent.reply().contains("ausente ou inválido"));
            assertFalse(intent.reply().contains("private"));
        }
    }

    @Test
    void emptyOutputIsDisclosedInsteadOfPretendingItWasLearnedConversation() throws Exception {
        try (var engine = engine(text -> " \n\t"); var brain = new NativeBrain(engine)) {
            Intent intent = brain.respond("Oi, como você está?", "").get(2, TimeUnit.SECONDS);
            assertEquals(Action.CHAT, intent.action());
            assertTrue(intent.reply().contains("não gerou texto"));
        }
    }

    @Test
    void closedOwnerAndEngineNeverStartAnotherGeneration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var engine = engine(text -> { calls.incrementAndGet(); return "texto"; });
             var brain = new NativeBrain(engine)) {
            brain.close();
            assertEquals(Action.CHAT, brain.respond("Olá, tudo bem?", "").get().action());
            assertEquals(0, calls.get());
            engine.close();
            try (var another = new NativeBrain(engine)) {
                assertTrue(another.respond("Olá, tudo bem?", "").get().reply().contains("indisponível"));
            }
            assertEquals(0, calls.get());
        }
    }

    private static NativeBrain.Engine engine(NativeBrain.Generator generator) {
        return new NativeBrain.Engine(generator, Duration.ofSeconds(5));
    }
}
