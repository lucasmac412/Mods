package br.com.celestialdragon.client;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.content.DragonCreativeTab;
import br.com.celestialdragon.network.DragonSettingsNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public final class CelestialDragonClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(DragonModel.MODEL_LAYER, DragonModel::createBodyLayer);
        EntityRendererRegistry.register(CelestialDragonMod.DRAGON, DragonRenderer::new);
        EntityRendererRegistry.register(CelestialDragonMod.SPIRIT_ORB, SpiritOrbRenderer::new);
        DragonControls.register();
        BiomeAtmosphere.register();
        ClientPlayNetworking.registerGlobalReceiver(DragonSettingsNetworking.Snapshot.TYPE,
                (snapshot, context) -> context.client().execute(() -> DragonSettingsScreen.receive(snapshot)));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof PauseScreen pause && pause.showsPauseMenu())
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Dragão Celestial"),
                        button -> client.gui.setScreen(new DragonSettingsScreen(screen))).bounds(8, 8, 146, 20).build());
            if (screen instanceof CreativeModeInventoryScreen creative) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Dragão Celestial"), button -> {
                    var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(DragonCreativeTab.KEY);
                    if (tab == null) return;
                    var navigation = (FabricCreativeModeInventoryScreen)creative;
                    int page = navigation.getPage(tab);
                    if (page >= 0) {
                        navigation.switchToPage(page);
                        navigation.setSelectedTab(tab);
                    }
                }).bounds(8, 8, 146, 20).build());
            }
        });
    }
}
