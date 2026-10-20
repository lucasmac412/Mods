package br.com.luma.ai;

import br.com.luma.nativeai.ModelBundle;
import br.com.luma.nativeai.TextGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional, original, CPU-only language experiment. Generated text never selects game actions. */
public final class NativeBrain implements AutoCloseable {
    private static final String LABEL = "IA experimental: ";
    private final Engine engine;
    private CompletableFuture<String> pending;
    private boolean closed;

    public NativeBrain(Engine engine) { this.engine = Objects.requireNonNull(engine); }

    /** Local orders remain immediate; only ordinary conversation reaches the learned model. */
    public synchronized CompletableFuture<Intent> respond(String text, String unusedWorldContext) {
        cancelPending();
        Intent local = IntentParser.parse(text);
        if (closed || text == null || text.isBlank() || local.action() != Action.CHAT
                || IntentParser.hasNegation(text) || IntentParser.isAmbiguous(text)) {
            return CompletableFuture.completedFuture(local);
        }
        CompletableFuture<String> generation = engine.submit(limit(text, 500));
        pending = generation;
        return generation.handle((reply, failure) -> {
            if (failure != null) return unavailable();
            String clean = reply == null ? "" : reply.replaceAll("§.", "")
                    .replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
            if (clean.isBlank()) return new Intent(Action.CHAT,
                    "O modelo experimental não gerou texto. Ele ainda está em desenvolvimento.");
            return new Intent(Action.CHAT, LABEL + limit(clean, 600 - LABEL.length()));
        });
    }

    public synchronized void cancelPending() {
        if (pending != null) pending.cancel(false);
        pending = null;
    }

    @Override public synchronized void close() {
        closed = true;
        cancelPending();
    }

    private static Intent unavailable() {
        return new Intent(Action.CHAT, "A IA original está indisponível ou ocupada. "
                + "O modelo incluído pode estar ausente ou inválido. As ordens básicas continuam funcionando.");
    }

    private static String limit(String text, int maximum) {
        if (text.length() <= maximum) return text;
        int end = maximum;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    @FunctionalInterface interface Generator { String generate(String text) throws Exception; }

    /** One bounded worker and one lazily loaded model per world; no network or credentials. */
    public static final class Engine implements AutoCloseable {
        private final ThreadPoolExecutor worker;
        private final Set<CompletableFuture<String>> active = ConcurrentHashMap.newKeySet();
        private final Generator generator;
        private final long timeoutMillis;
        private volatile boolean closed;

        public Engine() { this(new BundledGenerator(), Duration.ofSeconds(10)); }

        Engine(Generator generator, Duration timeout) {
            this.generator = Objects.requireNonNull(generator);
            if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0)
                throw new IllegalArgumentException("Generation timeout must be within one minute");
            timeoutMillis = Math.max(1, timeout.toMillis());
            worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(4), runnable -> {
                        Thread thread = new Thread(runnable, "luma-native-ai");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
        }

        private CompletableFuture<String> submit(String text) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Model engine closed"));
            CompletableFuture<String> result = new CompletableFuture<>();
            FutureTask<Void> task = new FutureTask<>(() -> {
                try {
                    if (!result.isDone()) result.complete(generator.generate(text));
                } catch (Exception failure) {
                    // No exception details enter chat: model output and system paths are not diagnostics.
                    result.completeExceptionally(failure);
                }
                return null;
            });
            active.add(result);
            result.whenComplete((reply, failure) -> {
                active.remove(result);
                if (failure != null) {
                    task.cancel(true);
                    worker.remove(task);
                }
            });
            if (closed) result.cancel(false);
            else {
                try { worker.execute(task); }
                catch (RejectedExecutionException busy) { result.completeExceptionally(busy); }
            }
            return result.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override public void close() {
            closed = true;
            active.forEach(future -> future.cancel(false));
            worker.shutdownNow();
        }
    }

    private static final class BundledGenerator implements Generator {
        private static final int MAX_MODEL_BYTES = 16 * 1024 * 1024;
        private final SplittableRandom sampling = new SplittableRandom();
        private ModelBundle bundle;

        @Override public String generate(String text) throws Exception {
            if (bundle == null) bundle = loadBundled();
            return TextGenerator.generate(bundle, text, 64, 0.7, 20, sampling.nextLong());
        }

        private static ModelBundle loadBundled() throws IOException {
            try (InputStream source = NativeBrain.class.getResourceAsStream("/assets/luma/models/luma-native.luma")) {
                if (source == null) throw new IOException("Bundled model missing");
                Path temporary = Files.createTempFile("luma-native-", ".luma");
                try {
                    try (OutputStream output = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[8192];
                        int count, total = 0;
                        while ((count = source.read(buffer)) != -1) {
                            total += count;
                            if (total > MAX_MODEL_BYTES) throw new IOException("Bundled model exceeds size limit");
                            output.write(buffer, 0, count);
                        }
                    }
                    // Own format, checksum, tensor sizes and finite weights are checked before use.
                    return ModelBundle.load(temporary);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }
}
