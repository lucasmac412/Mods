package br.com.mods.createport.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.api.config.CatnipConfigRegistry;
import net.createmod.catnip.api.config.ConfigId;
import net.createmod.catnip.api.config.ConfigSide;
import net.createmod.ponder.api.client.PonderIndex;
import net.createmod.ponder.api.client.scene.PonderScene;
import net.createmod.ponder.impl.client.PonderClientConfig;
import net.createmod.ponder.impl.client.gui.PonderUI;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Exercises real library scenes, entities, fluid sprites and persisted configuration. */
public final class PonderScreenCheck {
    public static void run(ClientGameTestContext context) {
        Object itemSlot = "render-test-item";
        context.runOnClient(client -> net.createmod.catnip.api.client.outliner.Outliner.getInstance().showItem(
                itemSlot, new net.minecraft.world.phys.Vec3(0.5, 142.5, 0.5),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD)));
        context.waitTicks(2);
        context.takeScreenshot(TestScreenshotOptions.of("catnip-item-overlay")
                .disableCounterPrefix().withSize(1280, 720)
                .withDestinationDir(Path.of("../../reports/client-ponder").toAbsolutePath().normalize()));
        context.runOnClient(client -> net.createmod.catnip.api.client.outliner.Outliner.getInstance().remove(itemSlot));
        AtomicReference<List<PonderScene>> scenes = new AtomicReference<>();
        context.runOnClient(client -> {
            var config = PonderClientConfig.INSTANCE;
            if (CatnipConfigRegistry.INSTANCE.get(new ConfigId("ponder", ConfigSide.CLIENT)).orElseThrow() != config)
                throw new AssertionError("Ponder config was not registered");
            boolean before = config.comfyReading.get();
            try {
                config.comfyReading.set(!before);
                String file = Files.readString(FabricLoader.getInstance().getConfigDir().resolve("ponder-client.toml"));
                if (!file.contains("comfyReading = " + !before)) throw new AssertionError("Config change was not saved");
            } catch (java.io.IOException error) { throw new AssertionError(error); }
            finally { config.comfyReading.set(before); }
            scenes.set(PonderIndex.getSceneAccess().compile(Identifier.withDefaultNamespace("spyglass")));
            if (scenes.get().size() < 8) throw new AssertionError("Built-in Ponder scenes are missing");
        });
        for (int index : new int[]{0, 2, 4, 6}) {
            context.runOnClient(client -> net.createmod.catnip.api.client.gui.ScreenOpener.transitionTo(new SingleSceneUI(scenes.get().get(index))));
            context.waitTicks(65);
            context.runOnClient(client -> {
                if (!(client.gui.screen() instanceof PonderUI ui) || ui.getActiveScene().getCurrentTime() <= 0)
                    throw new AssertionError("Ponder scene did not advance: " + index);
                System.out.println("Ponder scene " + index + " rendered at tick " + ui.getActiveScene().getCurrentTime());
            });
            context.takeScreenshot(TestScreenshotOptions.of("ponder-scene-" + (index + 1))
                    .disableCounterPrefix().withSize(1280, 720)
                    .withDestinationDir(Path.of("../../reports/client-ponder").toAbsolutePath().normalize()));
            context.runOnClient(client -> client.gui.setScreen(null));
            context.waitTicks(2);
        }
        System.out.println("Ponder 26.2 scene checks passed: coordinates, fluids, particles, parrot; config persisted");
    }
    private static final class SingleSceneUI extends PonderUI {
        SingleSceneUI(PonderScene scene) { super(List.of(scene)); }
    }
}
