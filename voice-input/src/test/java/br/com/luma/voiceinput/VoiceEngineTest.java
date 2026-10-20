package br.com.luma.voiceinput;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceEngineTest {
    @Test void cancellationDuringModelLoadNeverOpensMicrophoneOrDeliversLateText() throws Exception {
        var loaded = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        FakeBackend backend = new FakeBackend();
        backend.loading = () -> { loaded.countDown(); proceed.await(); };
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            assertTrue(engine.start(result::complete));
            assertTrue(loaded.await(2, TimeUnit.SECONDS));
            engine.cancel();
            proceed.countDown();
        }
        assertTrue(backend.closed.await(2, TimeUnit.SECONDS));
        assertEquals(0, backend.opened.get());
        assertFalse(result.isDone());
    }

    @Test void captureStopsAtHardByteLimitAndClosesAllResources() throws Exception {
        FakeBackend backend = new FakeBackend();
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            assertTrue(engine.start(result::complete));
            assertEquals("me siga", result.get(2, TimeUnit.SECONDS).text());
            assertEquals(VoiceEngine.MAX_BYTES, backend.bytes.get());
            assertEquals(1, backend.micClosed.get());
            assertEquals(1, backend.recognitionClosed.get());
        }
        assertTrue(backend.closed.await(2, TimeUnit.SECONDS));
    }

    @Test void wallClockLimitStopsCaptureEvenWhenOnlySmallChunksArrive() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.chunk = 8000;
        AtomicLong clock = new AtomicLong();
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend, () -> clock.getAndAdd(TimeUnit.SECONDS.toNanos(1)))) {
            engine.start(result::complete);
            assertEquals("me siga", result.get(2, TimeUnit.SECONDS).text());
            assertTrue(backend.bytes.get() < VoiceEngine.MAX_BYTES);
        }
    }

    @Test void releaseDuringModelPreparationDoesNotOpenMicrophoneAfterKeyHasBeenReleased() throws Exception {
        var loaded = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        FakeBackend backend = new FakeBackend();
        backend.loading = () -> { loaded.countDown(); proceed.await(); };
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(ignored -> fail("No recording should have happened"));
            assertTrue(loaded.await(2, TimeUnit.SECONDS));
            engine.release();
            proceed.countDown();
        }
        assertTrue(backend.closed.await(2, TimeUnit.SECONDS));
        assertEquals(0, backend.opened.get());
    }

    @Test void aSecondStartIsRejectedInsteadOfCreatingAQueuedRecording() throws Exception {
        var loaded = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        FakeBackend backend = new FakeBackend();
        backend.loading = () -> { loaded.countDown(); proceed.await(); };
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(ignored -> {});
            assertTrue(loaded.await(2, TimeUnit.SECONDS));
            assertFalse(engine.start(ignored -> fail("Queued utterance")));
            engine.cancel();
            proceed.countDown();
        }
    }

    @Test void releaseStopsAnActiveRecordingAndSendsItsCompletedTranscript() throws Exception {
        var captured = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        FakeBackend backend = new FakeBackend();
        backend.accepted = count -> {
            if (count >= 8192) { captured.countDown(); proceed.await(); }
        };
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(result::complete);
            assertTrue(captured.await(2, TimeUnit.SECONDS));
            engine.release();
            proceed.countDown();
            assertEquals("me siga", result.get(2, TimeUnit.SECONDS).text());
            assertEquals(8192, backend.bytes.get());
            assertEquals(1, backend.micClosed.get());
        }
    }

    @Test void cancellationWhileRecognitionFinishesSuppressesTheLateTranscript() throws Exception {
        var finishing = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        FakeBackend backend = new FakeBackend();
        backend.finishing = () -> { finishing.countDown(); proceed.await(); };
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(result::complete);
            assertTrue(finishing.await(2, TimeUnit.SECONDS));
            engine.cancel();
            proceed.countDown();
        }
        assertTrue(backend.closed.await(2, TimeUnit.SECONDS));
        assertFalse(result.isDone());
    }

    @Test void silenceIsNotSentAsAnInventedTranscript() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.amplitude = 0;
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(result::complete);
            var outcome = result.get(2, TimeUnit.SECONDS);
            assertTrue(outcome.text().isBlank());
            assertTrue(outcome.error().contains("Não entendi"));
        }
    }

    @Test void missingModelReportsInstallationWithoutOpeningMicrophone() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.loading = () -> { throw new VoiceEngine.MissingModelException(); };
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(result::complete);
            assertTrue(result.get(2, TimeUnit.SECONDS).error().contains("config/luma-voice/model"));
            assertEquals(0, backend.opened.get());
        }
    }

    @Test void resourceFailureDoesNotLeakDiagnosticsOrLeaveWorkerBusy() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.loading = () -> { throw new IllegalStateException("private-path-should-not-appear"); };
        var result = new CompletableFuture<VoiceEngine.Outcome>();
        try (VoiceEngine engine = new VoiceEngine(backend)) {
            engine.start(result::complete);
            var outcome = result.get(2, TimeUnit.SECONDS);
            assertFalse(outcome.error().contains("private-path"));
            assertFalse(engine.busy());
        }
    }

    interface Loading { void run() throws Exception; }
    interface Accepted { void run(int count) throws Exception; }
    static class FakeBackend implements VoiceEngine.Backend {
        Loading loading = () -> {};
        Loading finishing = () -> {};
        Accepted accepted = count -> {};
        final AtomicInteger opened = new AtomicInteger(), bytes = new AtomicInteger();
        final AtomicInteger micClosed = new AtomicInteger(), recognitionClosed = new AtomicInteger();
        final CountDownLatch closed = new CountDownLatch(1);
        int chunk = 4096, amplitude = 256;
        @Override public VoiceEngine.Recognition recognition() throws Exception {
            loading.run();
            return new VoiceEngine.Recognition() {
                @Override public void accept(byte[] value, int count) {
                    int total = bytes.addAndGet(count);
                    try { accepted.run(total); } catch (Exception failure) { throw new IllegalStateException(failure); }
                }
                @Override public String finish() {
                    try { finishing.run(); } catch (Exception failure) { throw new IllegalStateException(failure); }
                    return "me siga";
                }
                @Override public void close() { recognitionClosed.incrementAndGet(); }
            };
        }
        @Override public VoiceEngine.AudioSource microphone() {
            opened.incrementAndGet();
            return new VoiceEngine.AudioSource() {
                @Override public int available() { return chunk; }
                @Override public int read(byte[] buffer, int length) {
                    for (int i = 0; i < length; i += 2) {
                        buffer[i] = (byte) amplitude; buffer[i + 1] = (byte) (amplitude >> 8);
                    }
                    return length;
                }
                @Override public void close() { micClosed.incrementAndGet(); }
            };
        }
        @Override public void close() { closed.countDown(); }
    }
}
