package br.com.celestialdragon;

import br.com.celestialdragon.combat.DragonHostility;
import br.com.celestialdragon.config.DragonConfig;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.network.DragonControlNetworking;
import br.com.celestialdragon.network.DragonSettingsNetworking;
import br.com.celestialdragon.sound.DragonSounds;
import br.com.celestialdragon.world.DragonWorld;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

public final class CelestialDragonMod implements ModInitializer {
    public static final String ID = "celestial_dragon";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    public static final EntityType<CelestialDragonEntity> DRAGON = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("celestial_dragon"), EntityType.Builder.of(CelestialDragonEntity::new, MobCategory.CREATURE)
                    .sized(2.5F, 2.7F).clientTrackingRange(16)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("celestial_dragon"))));

    @Override public void onInitialize() {
        DragonConfig.load(FabricLoader.getInstance().getConfigDir().resolve("celestial-dragon.json"));
        DragonSounds.register();
        DragonContent.register();
        FabricDefaultAttributeRegistry.register(DRAGON, CelestialDragonEntity.createAttributes());
        DragonWorld.register();
        DragonControlNetworking.register();
        DragonSettingsNetworking.register();
        DragonHostility.register();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("celestial"), FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.celestial_dragon.celestial"))
                .icon(() -> new ItemStack(DragonContent.CELESTIAL_GEM))
                .displayItems((parameters, output) -> DragonContent.creativeEntries().forEach(output::accept)).build());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, selection) -> dispatcher.register(
                Commands.literal("dragao").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Dragão Celestial: procure o Bosque Celestial e o ninho no centro. "
                            + "Recolha o ovo e use a incubadora. No seu dragão: WASD, Espaço sobe, Ctrl desce, Shift desmonta; "
                            + "botão esquerdo morde, direito dispara. ESC → Dragão Celestial ajusta vida/dano."), false);
                    return 1;
                })));
    }
}
