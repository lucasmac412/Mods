package br.com.mods.createport.render;

import java.nio.file.Path;
import java.util.Set;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.backend.NoiseTextures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Requires a real Flywheel instance and animation; opening vanilla alone cannot pass. */
public final class FlywheelRenderGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.fov().set(60);
            if (NoiseTextures.BLUE_NOISE == null || NoiseTextures.BLUE_NOISE.getTexture().isClosed())
                throw new AssertionError("Flywheel resources failed to upload");
            if (!BackendManager.isBackendOn()) throw new AssertionError("Flywheel fell back to the OFF backend");
        });
        try (var world = context.worldBuilder().adjustSettings(s ->
                s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            world.getServer().runCommand("time set noon");
            world.getServer().runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.QUARTZ_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(0, 140, 0), Blocks.CHEST.defaultBlockState());
                var player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, 6, 145, 6, Set.of(), 0, 25, false);
            });
            world.getConnection().waitForChunksDownload();
            context.getInput().lookAt(new BlockPos(0, 141, 0));
            world.getConnection().waitForChunksRender();
            for (String backendName : new String[]{"indirect", "instancing"}) {
                int before = FlywheelProbeClient.CREATED.get();
                context.runOnClient(client -> {
                    var target = dev.engine_room.flywheel.api.backend.Backend.REGISTRY
                            .get(net.minecraft.resources.Identifier.fromNamespaceAndPath("flywheel", backendName));
                    if (target == null || !target.isSupported()) throw new AssertionError("Test backend unsupported: " + backendName);
                    dev.engine_room.flywheel.impl.FabricFlwConfig.INSTANCE.backend = target;
                    dev.engine_room.flywheel.impl.FabricFlwConfig.INSTANCE.useDefaultBackend = false;
                    client.levelExtractor.allChanged();
                });
                context.waitTicks(15);
                context.runOnClient(client -> {
                    if (FlywheelProbeClient.CREATED.get() <= before || FlywheelProbeClient.FRAMES.get() < 2)
                        throw new AssertionError("Flywheel visual was not reconstructed and animated");
                    String actual = dev.engine_room.flywheel.impl.BackendManagerImpl.getBackendString();
                    if (!actual.equals("flywheel:" + backendName)) throw new AssertionError("Backend fell back: " + actual);
                    if (org.lwjgl.opengl.GL11.glGetError() != org.lwjgl.opengl.GL11.GL_NO_ERROR)
                        throw new AssertionError("OpenGL error during " + backendName + " rendering");
                });
                context.takeScreenshot(TestScreenshotOptions.of("flywheel-" + backendName + "-26.2")
                        .disableCounterPrefix().withSize(1280, 720)
                        .withDestinationDir(Path.of("../../reports/client-flywheel").toAbsolutePath().normalize()));
                System.out.println("Flywheel rendered opaque and transparent instances with " + backendName);
            }
            world.getServer().runOnServer(ignored -> world.getConnection().getServerLevel()
                    .setBlockAndUpdate(new BlockPos(0, 140, 0), Blocks.AIR.defaultBlockState()));
            context.waitTicks(10);
            if (FlywheelProbeClient.DELETED.get() < 1) throw new AssertionError("Removed visual leaked its GPU instance");
            System.out.println("Flywheel 26.2 real instance test passed: created=" + FlywheelProbeClient.CREATED.get()
                    + ", animated=" + FlywheelProbeClient.FRAMES.get() + ", deleted=" + FlywheelProbeClient.DELETED.get());
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ponder")) {
                try {
                    Class.forName("br.com.mods.createport.render.PonderScreenCheck")
                            .getMethod("run", ClientGameTestContext.class).invoke(null, context);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Ponder client checks failed", error);
                }
            }
        }
    }
}
