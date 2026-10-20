package br.com.luma.voiceinput;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One bounded worker owns the microphone and recognizer; ticks only change flags. */
public final class VoiceEngine implements AutoCloseable {
    public static final int SAMPLE_RATE = 16_000;
    public static final int MAX_SECONDS = 15;
    static final int MAX_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS;
    private static final int MIN_BYTES = SAMPLE_RATE * 2 / 5;

    public interface AudioSource extends AutoCloseable {
        int available() throws Exception;
        int read(byte[] buffer, int length) throws Exception;
        @Override void close();
    }
    public interface Recognition extends AutoCloseable {
        void accept(byte[] bytes, int count);
        String finish();
        @Override void close();
    }
    public interface Backend extends AutoCloseable {
        Recognition recognition() throws Exception;
        AudioSource microphone() throws Exception;
        @Override void close();
    }
    public record Outcome(String text, String error) {
        static Outcome text(String text) { return new Outcome(text, ""); }
        static Outcome error(String error) { return new Outcome("", error); }
    }

    private final Backend backend;
    private final LongSupplier clock;
    private final ThreadPoolExecutor worker;
    private volatile boolean requested;
    private volatile boolean closed;
    private volatile boolean busy;
    private volatile long generation;

    public VoiceEngine(Backend backend) { this(backend, System::nanoTime); }
    VoiceEngine(Backend backend, LongSupplier clock) {
        this.backend = backend;
        this.clock = clock;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, "luma-voice-microphone");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized boolean start(Consumer<Outcome> callback) {
        return start(callback, () -> {});
    }

    public synchronized boolean start(Consumer<Outcome> callback, Runnable ready) {
        if (closed || busy) return false;
        requested = true;
        busy = true;
        long token = ++generation;
        worker.execute(() -> capture(token, callback, ready));
        return true;
    }

    /** Release finishes this utterance; it does not invalidate the eventual transcript. */
    public void release() { requested = false; }

    /** Menus, focus loss, logout and new worlds invalidate both capture and late results. */
    public synchronized void cancel() { requested = false; generation++; }
    public boolean busy() { return busy; }

    private boolean current(long token) { return !closed && generation == token; }
    private void capture(long token, Consumer<Outcome> callback, Runnable ready) {
        Outcome outcome = null;
        try (Recognition recognition = backend.recognition()) {
            if (!current(token) || !requested) return;
            try (AudioSource microphone = backend.microphone()) {
                if (!current(token) || !requested) return;
                ready.run();
                byte[] bytes = new byte[4096];
                long start = clock.getAsLong();
                int total = 0;
                long energy = 0;
                while (current(token) && requested && total < MAX_BYTES
                        && clock.getAsLong() - start < TimeUnit.SECONDS.toNanos(MAX_SECONDS)) {
                    int available = Math.min(bytes.length, Math.min(MAX_BYTES - total, microphone.available())) & ~1;
                    if (available == 0) { Thread.sleep(10); continue; }
                    int count = microphone.read(bytes, available);
                    if (count <= 0) { Thread.sleep(10); continue; }
                    if (count > available || (count & 1) != 0) throw new IllegalStateException("Invalid audio buffer");
                    total += count;
                    for (int i = 0; i < count; i += 2) {
                        int sample = (short) ((bytes[i] & 255) | (bytes[i + 1] << 8));
                        energy += (long) sample * sample;
                    }
                    recognition.accept(bytes, count);
                }
                if (current(token)) {
                    String text = VoiceText.clean(recognition.finish());
                    boolean quiet = total < MIN_BYTES || Math.sqrt(energy / Math.max(1.0, total / 2.0)) < 75;
                    outcome = quiet || text.isBlank()
                            ? Outcome.error("Não entendi a fala. Segure V, fale perto do microfone e solte para enviar.")
                            : Outcome.text(text);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (MissingModelException missing) {
            outcome = Outcome.error("Falta o modelo de voz. Copie a pasta config do pacote para a pasta do jogo; ela deve conter config/luma-voice/model.");
        } catch (LinkageError unavailable) {
            outcome = Outcome.error("O reconhecedor de voz não carregou neste sistema. Use Windows/Linux x64 ou macOS compatível e consulte o guia.");
        } catch (Exception unavailable) {
            outcome = Outcome.error("Não consegui usar o microfone ou o modelo de voz. Confira o microfone padrão, a permissão do sistema e o guia.");
        } finally {
            busy = false;
        }
        if (outcome != null && current(token)) callback.accept(outcome);
    }

    @Override public synchronized void close() {
        if (closed) return;
        cancel();
        closed = true;
        // At most one active task and this one cleanup task can exist.
        worker.execute(backend::close);
        worker.shutdown();
    }

    public static final class MissingModelException extends Exception {
        public MissingModelException() { super("Speech model not installed"); }
    }
}
