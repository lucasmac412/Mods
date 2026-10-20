package br.com.luma.voiceinput;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoiceSettings {
    private final Path file;
    private boolean speakReplies = true;

    public VoiceSettings(Path file) {
        this.file = file;
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 4096) {
                JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (saved.has("speakReplies")) speakReplies = saved.get("speakReplies").getAsBoolean();
            }
        } catch (IOException | RuntimeException ignored) { /* safe, documented default */ }
    }
    public boolean speakReplies() { return speakReplies; }
    public boolean toggle() {
        speakReplies = !speakReplies;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\n  \"speakReplies\": " + speakReplies + "\n}\n");
            return true;
        } catch (IOException ignored) { return false; }
    }
}
