package br.com.celestialdragon.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Access to the biome holder owned by this world's dynamic registry. */
public interface CelestialBiomeAccess {
    Holder<Biome> celestialDragon$grove();
}
