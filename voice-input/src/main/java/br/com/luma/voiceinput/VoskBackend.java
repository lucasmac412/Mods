package br.com.luma.voiceinput;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Audio and text remain on the player's computer; the model is installed explicitly. */
public final class VoskBackend implements VoiceEngine.Backend {
    private final Path directory;
    private Model model;

    public VoskBackend(Path directory) { this.directory = directory; }

    @Override public VoiceEngine.Recognition recognition() throws Exception {
        if (model == null) {
            // The official small Portuguese 0.3 model has the older, flat layout.
            boolean flat = Files.isRegularFile(directory.resolve("final.mdl"))
                    && Files.isRegularFile(directory.resolve("mfcc.conf"));
            boolean modern = Files.isRegularFile(directory.resolve("am/final.mdl"))
                    && Files.isRegularFile(directory.resolve("conf/mfcc.conf"));
            if (!flat && !modern) throw new VoiceEngine.MissingModelException();
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            model = new Model(directory.toAbsolutePath().toString());
        }
        Recognizer recognizer = new Recognizer(model, VoiceEngine.SAMPLE_RATE);
        return new VoiceEngine.Recognition() {
            private final StringBuilder words = new StringBuilder();
            private void append(String json) {
                if (words.length() >= 500) return;
                String text = JsonParser.parseString(json).getAsJsonObject().get("text").getAsString();
                if (!text.isBlank()) words.append(' ').append(VoiceText.clean(text));
            }
            @Override public void accept(byte[] bytes, int count) {
                if (recognizer.acceptWaveForm(bytes, count)) append(recognizer.getResult());
            }
            @Override public String finish() {
                append(recognizer.getFinalResult());
                return VoiceText.clean(words.toString());
            }
            @Override public void close() { recognizer.close(); }
        };
    }

    @Override public VoiceEngine.AudioSource microphone() throws Exception {
        AudioFormat format = new AudioFormat(VoiceEngine.SAMPLE_RATE, 16, 1, true, false);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
        try {
            line.open(format, VoiceEngine.SAMPLE_RATE * 2 / 2);
            line.start();
        } catch (Exception failure) { line.close(); throw failure; }
        return new VoiceEngine.AudioSource() {
            @Override public int available() { return line.available(); }
            @Override public int read(byte[] buffer, int length) { return line.read(buffer, 0, length); }
            @Override public void close() { line.stop(); line.flush(); line.close(); }
        };
    }

    @Override public void close() { if (model != null) { model.close(); model = null; } }
}
