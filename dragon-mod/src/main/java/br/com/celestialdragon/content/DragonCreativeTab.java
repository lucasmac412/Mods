package br.com.celestialdragon.content;

import br.com.celestialdragon.CelestialDragonMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** A dedicated, searchable creative tab containing every item registered by this mod. */
public final class DragonCreativeTab {
    public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            CelestialDragonMod.id("celestial"));

    private DragonCreativeTab() { }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.celestial_dragon.celestial"))
                .icon(() -> new ItemStack(DragonContent.TAMED_DRAGON_SPAWN_EGG))
                .displayItems((parameters, output) -> DragonContent.creativeEntries().forEach(output::accept))
                .build());
    }
}
