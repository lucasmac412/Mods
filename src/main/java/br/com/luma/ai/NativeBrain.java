package br.com.luma.ai;

import br.com.luma.nativeai.ModelBundle;
import br.com.luma.nativeai.LocalMemory;
import br.com.luma.nativeai.TextGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optional, original, CPU-only language experiment. Generated text never selects game actions. */
public final class NativeBrain implements ConversationBrain {
    private static final String LABEL = "IA experimental: ";
    private final Engine engine;
    private CompletableFuture<String> pending;
    private boolean closed;

    public NativeBrain(Engine engine) { this.engine = Objects.requireNonNull(engine); }

    /** Local orders remain immediate; only ordinary conversation reaches the learned model. */
    public synchronized CompletableFuture<Intent> respond(String text, String unusedWorldContext) {
        cancelPending();
        Intent local = IntentParser.parse(text);
        // Negation/choices also occur in ordinary questions. The parser still owns every
        // real order, while any generated answer below remains inert CHAT text.
        if (closed || text == null || text.isBlank() || local.action() != Action.CHAT) {
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
                + "O modelo ou o arquivo de memória local pode estar ausente ou inválido. As ordens básicas continuam funcionando.");
    }

    private static String limit(String text, int maximum) {
        if (text.length() <= maximum) return text;
        int end = maximum;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    @FunctionalInterface interface Generator { String generate(String text) throws Exception; }

    /** Retrieval supplies context only: the generator remains responsible for every returned reply. */
    static Generator withMemory(LocalMemory memory, Generator generator) {
        Objects.requireNonNull(memory);
        Objects.requireNonNull(generator);
        return text -> generator.generate(LocalMemory.format(text, memory.lookup(text).information()));
    }

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
        private static final int MAX_MEMORY_BYTES = 6 * 1024 * 1024;
        private static final double MEMORY_THRESHOLD = 0.15;
        private static final double MEMORY_ANSWER_WEIGHT = 0.7;
        private Generator grounded;

        @Override public String generate(String text) throws Exception {
            if (grounded == null) {
                ModelBundle bundle = loadResource("/assets/luma/models/luma-native.luma", ".luma",
                        MAX_MODEL_BYTES, ModelBundle::load);
                LocalMemory memory = loadResource("/assets/luma/models/luma-knowledge.tsv", ".tsv",
                        MAX_MEMORY_BYTES, path -> LocalMemory.load(path, MEMORY_THRESHOLD, MEMORY_ANSWER_WEIGHT));
                // Cache the complete pair only after both resources have been parsed successfully.
                grounded = withMemory(memory, prompt -> TextGenerator.generate(bundle, prompt, 64, 0, 20, 412));
            }
            return grounded.generate(text);
        }

        @FunctionalInterface private interface ResourceReader<T> { T read(Path file) throws IOException; }

        private static <T> T loadResource(String resource, String suffix, int maximumBytes,
                                           ResourceReader<T> reader) throws IOException {
            try (InputStream source = NativeBrain.class.getResourceAsStream(resource)) {
                if (source == null) throw new IOException("Bundled AI resource missing");
                Path temporary = Files.createTempFile("luma-native-", suffix);
                try {
                    try (OutputStream output = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[8192];
                        int count, total = 0;
                        while ((count = source.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted())
                                throw new java.util.concurrent.CancellationException("AI resource loading cancelled");
                            if (count > maximumBytes - total) throw new IOException("Bundled AI resource exceeds size limit");
                            total += count;
                            output.write(buffer, 0, count);
                        }
                    }
                    // Each reader validates its own format; no resource is used before parsing succeeds.
                    return reader.read(temporary);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }
}
