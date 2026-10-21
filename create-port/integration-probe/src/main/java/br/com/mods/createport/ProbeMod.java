package br.com.mods.createport;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import io.github.tropheusj.milk.Milk;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Isolated test harness. It deliberately does not register the Create mod ID. */
public final class ProbeMod implements ModInitializer {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_SPEED;
    static {
        var builder = new ModConfigSpec.Builder();
        MAX_SPEED = builder.defineInRange("kinetics.maxRotationSpeed", 256, 16, 1024);
        SPEC = builder.build();
    }
    @Override public void onInitialize() {
        Milk.enableMilkFluid();
        ConfigRegistry.INSTANCE.register("create_dependency_probe", ModConfig.Type.SERVER, SPEC);
    }
}
