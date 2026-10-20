package br.com.celestialdragon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Server-owned combat settings. Client screens never write this singleton. */
public final class DragonConfig {
    public record Values(double maxHealth, double biteDamage, double beamDamage) {
        public Values {
            if (!valid(maxHealth, 1, 1024) || !valid(biteDamage, 0, 1000) || !valid(beamDamage, 0, 1000))
                throw new IllegalArgumentException("Vida deve ficar entre 1 e 1024; danos entre 0 e 1000.");
        }
        private static boolean valid(double value, double min, double max) {
            return Double.isFinite(value) && value >= min && value <= max;
        }
    }
    public static final Values DEFAULT = new Values(200, 12, 30);
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Values values = DEFAULT;
    private static Path file;

    private DragonConfig() {}
    public static Values current() { return values; }

    public static synchronized void load(Path path) {
        file = path;
        values = DEFAULT;
        try {
            if (Files.isRegularFile(path) && Files.size(path) <= 4096) {
                var object = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                values = new Values(object.get("maxHealth").getAsDouble(), object.get("biteDamage").getAsDouble(),
                        object.get("beamDamage").getAsDouble());
            }
        } catch (IOException | RuntimeException invalid) {
            // Keep valid defaults, without overwriting a file the owner may want to repair.
        }
    }

    /** Commit only after an atomic save succeeds. */
    public static synchronized boolean save(Values next) {
        if (file == null) return false;
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, JSON.toJson(next) + "\n");
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            values = next;
            return true;
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            return false;
        }
    }
}
