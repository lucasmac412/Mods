package br.com.celestialdragon.mixin.world;

import br.com.celestialdragon.world.CelestialBiomeAccess;
import br.com.celestialdragon.world.DragonWorld;
import br.com.celestialdragon.world.GroveLayout;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class CelestialParameterListMixin implements CelestialBiomeAccess {
    @Unique private Holder<Biome> celestialDragon$grove;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void celestialDragon$capture(MultiNoiseBiomeSourceParameterList.Preset preset,
                                         HolderGetter<Biome> biomes, CallbackInfo ci) {
        // The vanilla data bootstrap has a lookup that creates missing holders. Asking it
        // for a datapack biome would create an unbound entry before datapacks are loaded.
        if (biomes.getClass().getName().contains("RegistrySetBuilder$")) return;
        if (preset.equals(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD)) {
            celestialDragon$grove = biomes.get(DragonWorld.CELESTIAL_GROVE).orElse(null);
        }
    }

    @Override public Holder<Biome> celestialDragon$grove() { return celestialDragon$grove; }
}
