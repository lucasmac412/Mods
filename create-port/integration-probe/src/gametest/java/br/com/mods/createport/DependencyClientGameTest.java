package br.com.mods.createport;

import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.george.milk.MilkLib;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Checks that the server-safety fix preserves the actual six client particle factories. */
public final class DependencyClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.fov().set(60);
        });
        try (var world = context.worldBuilder().adjustSettings(s ->
                s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            world.getServer().runCommand("time set noon");
            world.getServer().runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x, 140, z),
                            Math.abs(x) == 2 || Math.abs(z) == 2 ? Blocks.STONE.defaultBlockState()
                                    : MilkLib.MILK_FLUID_BLOCK.defaultBlockState());
                }
                var player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, 6, 145, 6, Set.of(), 0, 35, false);
            });
            world.getConnection().waitForChunksDownload();
            context.getInput().lookAt(new BlockPos(0, 140, 0));
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                var types = new net.minecraft.core.particles.SimpleParticleType[]{
                        MilkLib.STILL_MILK_PARTICLES.hang(), MilkLib.STILL_MILK_PARTICLES.fall(),
                        MilkLib.STILL_MILK_PARTICLES.splash(), MilkLib.FLOWING_MILK_PARTICLES.hang(),
                        MilkLib.FLOWING_MILK_PARTICLES.fall(), MilkLib.FLOWING_MILK_PARTICLES.splash()};
                for (var type : types) {
                    if (client.particleEngine.createParticle(type, 0.5, 142, 0.5, 0, 0, 0) == null)
                        throw new AssertionError("A moved client particle factory is missing: " + type);
                }
            });
            context.waitTicks(3);
            context.takeScreenshot(TestScreenshotOptions.of("milk-26.2-client")
                    .disableCounterPrefix().withSize(1280, 720)
                    .withDestinationDir(Path.of("../../reports/client-milk").toAbsolutePath().normalize()));
            System.out.println("Create prerequisite client test passed: six real milk particle factories and fluid world view");
        }
    }
}
