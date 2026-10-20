package br.com.celestialdragon.client;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.sound.DragonSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.biome.Biome;

/** Sparse firefly motes and a quiet, original wind-chime bed inside the grove only. */
public final class BiomeAtmosphere {
    private static final ResourceKey<Biome> GROVE = ResourceKey.create(Registries.BIOME,
            CelestialDragonMod.id("celestial_grove"));
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xF8C957, 0.8F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x66EEFF, 0.65F);
    private static int ticks;
    private static int ambienceCooldown;

    private BiomeAtmosphere() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (minecraft.level == null || minecraft.player == null) {
                ticks = 0;
                ambienceCooldown = 0;
                return;
            }
            if (minecraft.isPaused()) {
                return;
            }
            if (++ticks % 4 != 0) {
                return;
            }
            if (!minecraft.level.getBiome(minecraft.player.blockPosition()).is(GROVE)) {
                ambienceCooldown = Math.max(0, ambienceCooldown - 4);
                return;
            }
            var random = minecraft.level.getRandom();
            for (int i = 0; i < 2; i++) {
                double x = minecraft.player.getX() + (random.nextDouble() - 0.5) * 22;
                double y = minecraft.player.getY() + random.nextDouble() * 8;
                double z = minecraft.player.getZ() + (random.nextDouble() - 0.5) * 22;
                minecraft.level.addParticle(i == 0 ? GOLD : CYAN, x, y, z,
                        (random.nextDouble() - 0.5) * 0.018, 0.012, (random.nextDouble() - 0.5) * 0.018);
            }
            ambienceCooldown -= 4;
            if (ambienceCooldown <= 0) {
                minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                        DragonSounds.AMBIENCE, SoundSource.AMBIENT, 0.20F, 0.96F + random.nextFloat() * 0.08F, false);
                ambienceCooldown = 280 + random.nextInt(160);
            }
        });
    }
}
