package br.com.celestialdragon.sound;

import br.com.celestialdragon.CelestialDragonMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Original sounds bundled with the mod, with bounded positional audibility. */
public final class DragonSounds {
    public static final SoundEvent ROAR = create("dragon.roar", 64.0F);
    public static final SoundEvent BITE = create("dragon.bite", 48.0F);
    public static final SoundEvent BEAM = create("dragon.beam", 64.0F);
    public static final SoundEvent HURT = create("dragon.hurt", 48.0F);
    public static final SoundEvent DEATH = create("dragon.death", 64.0F);
    public static final SoundEvent AMBIENCE = create("biome.ambience", 24.0F);
    public static final SoundEvent BREATH = create("dragon.breath", 48.0F);
    public static final SoundEvent FLIGHT = create("dragon.flight", 48.0F);
    public static final SoundEvent BEAM_CHARGE = create("dragon.beam_charge", 64.0F);
    public static final SoundEvent SPIRIT_CHARGE = create("dragon.spirit_charge", 192.0F);
    public static final SoundEvent SPIRIT_LAUNCH = create("dragon.spirit_launch", 192.0F);
    public static final SoundEvent SPIRIT_IMPACT = create("dragon.spirit_impact", 192.0F);

    private DragonSounds() {
    }

    private static SoundEvent create(String path, float range) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, CelestialDragonMod.id(path),
                SoundEvent.createFixedRangeEvent(CelestialDragonMod.id(path), range));
    }

    public static void register() {
        // Static fields register each event exactly once during common initialization.
    }
}
