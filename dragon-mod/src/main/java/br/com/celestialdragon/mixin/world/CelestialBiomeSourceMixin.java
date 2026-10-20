package br.com.celestialdragon.mixin.world;

import br.com.celestialdragon.world.CelestialBiomeAccess;
import br.com.celestialdragon.world.DragonWorld;
import br.com.celestialdragon.world.GroveLayout;

import com.mojang.datafixers.util.Either;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overlay only vanilla's Overworld preset; its original codec remains unchanged. */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class CelestialBiomeSourceMixin {
    @Shadow @Final private Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;

    @Unique private Holder<Biome> celestialDragon$grove() {
        return parameters.right().map(Holder::value)
                .map(p -> ((CelestialBiomeAccess) p).celestialDragon$grove()).orElse(null);
    }

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"), cancellable = true)
    private void celestialDragon$sample(int x, int y, int z, Climate.Sampler sampler,
                                        CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> grove = celestialDragon$grove();
        if (grove != null && y >= 8 && GroveLayout.contains(x * 4, z * 4)) cir.setReturnValue(grove);
    }

    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
    private void celestialDragon$include(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
        Holder<Biome> grove = celestialDragon$grove();
        if (grove != null) cir.setReturnValue(Stream.concat(cir.getReturnValue(), Stream.of(grove)).distinct());
    }
}
