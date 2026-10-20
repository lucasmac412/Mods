package br.com.celestialdragon.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DragonConfigTest {
    @TempDir Path directory;
    @Test void rejectsNonFiniteAndOutOfRangeCombatValues() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, -1, 1025})
            assertThrows(IllegalArgumentException.class, () -> new DragonConfig.Values(invalid, 12, 30));
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, -1, 1001}) {
            assertThrows(IllegalArgumentException.class, () -> new DragonConfig.Values(200, invalid, 30));
            assertThrows(IllegalArgumentException.class, () -> new DragonConfig.Values(200, 12, invalid));
        }
        assertDoesNotThrow(() -> new DragonConfig.Values(1, 0, 0));
        assertDoesNotThrow(() -> new DragonConfig.Values(1024, 1000, 1000));
    }
    @Test void savedValuesSurviveReloadWithoutLeavingTemporaryFile() throws Exception {
        Path path = directory.resolve("settings.json");
        DragonConfig.load(path);
        var chosen = new DragonConfig.Values(350, 17.5, 48);
        assertTrue(DragonConfig.save(chosen));
        DragonConfig.load(path);
        assertEquals(chosen, DragonConfig.current());
        assertFalse(Files.exists(directory.resolve("settings.json.tmp")));
    }
    @Test void malformedAndInvalidFilesKeepDefaultsWithoutDestroyingOriginal() throws Exception {
        Path path = directory.resolve("settings.json");
        for (String bad : new String[]{"not-json", "{}", "{\"maxHealth\":-1,\"biteDamage\":12,\"beamDamage\":30}"}) {
            Files.writeString(path, bad);
            DragonConfig.load(path);
            assertEquals(DragonConfig.DEFAULT, DragonConfig.current());
            assertEquals(bad, Files.readString(path));
        }
    }
    @Test void failedSaveDoesNotApplyValuesThatWereNeverPersisted() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "occupied");
        DragonConfig.load(parent.resolve("settings.json"));
        assertFalse(DragonConfig.save(new DragonConfig.Values(500, 40, 80)));
        assertEquals(DragonConfig.DEFAULT, DragonConfig.current());
        assertEquals("occupied", Files.readString(parent));
    }
}
