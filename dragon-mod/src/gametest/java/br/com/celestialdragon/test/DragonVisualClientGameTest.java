package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.client.DragonModel;
import br.com.celestialdragon.client.DragonRenderState;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual game views of the mane, hooked claws, pointed scales and combat poses. */
public final class DragonVisualClientGameTest implements FabricClientGameTest {
    private static final Path SHOTS = Path.of("../../reports/client-visual-v7").toAbsolutePath().normalize();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.fov().set(50);
            client.options.pauseOnLostFocus = false;
            if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
            assertAnimationPoses();
        });
        try (var world = context.worldBuilder().adjustSettings(settings ->
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:send_command_feedback false");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            UUID id = server.computeOnServer(ignored -> scene(world));
            world.getConnection().waitForChunksDownload();
            world.getConnection().waitForClientboundEntityUpdates(CelestialDragonMod.DRAGON);
            world.getConnection().waitForChunksRender();
            context.getInput().lookAt(new BlockPos(0, 144, -10));
            context.waitTicks(10);
            screenshot(context, "07-perfil-serpentino");
            // Separate frames let reviewers see the lateral wave travelling along the body.
            for (int i = 0; i < 26; i++) {
                context.waitTicks(2);
                screenshot(context, String.format("onda-%02d", i));
            }
            view(context, world, 6.5, 147, 9, new BlockPos(0, 144, 3), 45);
            screenshot(context, "12-cabeca-tres-quartos");
            view(context, world, 10, 147, 3, new BlockPos(0, 144, 3), 45);
            screenshot(context, "13-perfil-cabeca");
            view(context, world, 0, 147, 13, new BlockPos(0, 144, 2), 45);
            screenshot(context, "14-juba-frontal");
            view(context, world, 8, 145.5, -2, new BlockPos(0, 143, -2), 40);
            screenshot(context, "15-garras-ombro");
            view(context, world, 8, 149, -9, new BlockPos(0, 144, -9), 45);
            screenshot(context, "16-escamas-costas");
            // The profile makes the jaw opening and the forward snap directly readable.
            view(context, world, 10, 147, 3, new BlockPos(0, 144, 3), 50);
            server.runOnServer(ignored -> {
                if (!dragon(world, id).tryBite()) throw new AssertionError("Bite must start for the visual check");
            });
            context.waitTicks(2);
            screenshot(context, "08-mordida-preparo");
            context.waitTicks(3);
            screenshot(context, "09-mordida-impacto");
            server.waitFor(ignored -> dragon(world, id).biteTicks() == 0);
            context.waitTicks(CelestialDragonEntity.BITE_COOLDOWN);
            // Step the actual world one tick per frame: GPU readback must not consume
            // the short windup before the first screenshot has reached the disk.
            server.runCommand("tick freeze");
            try {
                server.runOnServer(ignored -> {
                    if (!dragon(world, id).tryBite()) throw new AssertionError("Bite must restart after cooldown");
                });
                for (int frame = 0; frame <= CelestialDragonEntity.BITE_DURATION; frame++) {
                    context.waitTicks(2);
                    screenshot(context, String.format("mordida-%02d", frame));
                    if (frame < CelestialDragonEntity.BITE_DURATION) {
                        int expected = CelestialDragonEntity.BITE_DURATION - frame - 1;
                        server.runCommand("tick step 1");
                        server.waitFor(ignored -> dragon(world, id).biteTicks() == expected);
                    }
                }
            } finally {
                server.runCommand("tick unfreeze");
            }
            server.waitFor(ignored -> dragon(world, id).biteTicks() == 0);
            view(context, world, 6.5, 147, 9, new BlockPos(0, 144, 3), 50);
            server.runOnServer(ignored -> {
                if (!dragon(world, id).tryBeam()) throw new AssertionError("Beam must start after the bite recovery");
            });
            context.waitTicks(4);
            screenshot(context, "10-carga-raio");
            server.waitFor(ignored -> dragon(world, id).beamTicks() == 0);
            // A separate cast avoids teleport/render latency consuming the active beam phase.
            view(context, world, 38, 166, 7, new BlockPos(0, 144, 7), 60);
            context.waitTicks(CelestialDragonEntity.BEAM_COOLDOWN);
            server.runOnServer(ignored -> {
                if (!dragon(world, id).tryBeam()) throw new AssertionError("Beam cooldown must have elapsed");
            });
            server.waitFor(ignored -> dragon(world, id).beamTicks() <=
                    CelestialDragonEntity.BEAM_DURATION - CelestialDragonEntity.BEAM_CHARGE_TICKS - 2);
            screenshot(context, "11-raio-disparado");
            server.waitFor(ignored -> dragon(world, id).beamTicks() == 0);
            server.runOnServer(ignored -> {
                var level = world.getConnection().getServerLevel();
                level.setBlockAndUpdate(new BlockPos(12, 140, 14), DragonContent.ARENA_LANTERN.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(14, 140, 14), DragonContent.ARENA_LANTERN.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(14, 141, 14), DragonContent.ARENA_LANTERN.defaultBlockState());
            });
            view(context, world, 17, 143, 19, new BlockPos(13, 141, 14), 45);
            screenshot(context, "24-lanterna-runica");
            server.runCommand("time set midnight");
            context.waitTicks(8);
            screenshot(context, "25-lanterna-runica-noite");
        } finally {
            context.runOnClient(client -> { if (client.gui.hud.isHidden()) client.gui.hud.toggle(); });
        }
    }

    private static UUID scene(TestSingleplayerContext world) {
        var level = world.getConnection().getServerLevel();
        for (int x = -18; x <= 18; x++) for (int z = -38; z <= 42; z++)
            level.setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.GRASS_BLOCK.defaultBlockState());
        CelestialDragonEntity dragon = CelestialDragonMod.DRAGON.create(level, EntitySpawnReason.COMMAND);
        if (dragon == null) throw new AssertionError("Dragon factory returned null");
        dragon.tame(world.getConnection().getServerPlayer().getUUID());
        dragon.setNoAi(true);
        dragon.snapTo(0, 141, 0, 0, 0);
        dragon.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dragon);
        var player = world.getConnection().getServerPlayer();
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, 20, 154, -10, Set.of(), 90, 10, false);
        return dragon.getUUID();
    }

    private static void view(ClientGameTestContext context, TestSingleplayerContext world,
                             double x, double y, double z, BlockPos target, int fov) {
        world.getServer().runOnServer(ignored -> {
            var player = world.getConnection().getServerPlayer();
            player.teleportTo(world.getConnection().getServerLevel(), x, y, z, Set.of(), 0, 0, false);
        });
        context.runOnClient(client -> client.options.fov().set(fov));
        context.waitTicks(4);
        world.getConnection().waitForChunksDownload();
        context.getInput().lookAt(target);
        world.getConnection().waitForChunksRender();
        context.waitTicks(4);
    }

    private static CelestialDragonEntity dragon(TestSingleplayerContext world, UUID id) {
        if (!(world.getConnection().getServerLevel().getEntity(id) instanceof CelestialDragonEntity dragon))
            throw new AssertionError("Visual check dragon disappeared");
        return dragon;
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(1280, 720)
                .withDestinationDir(SHOTS));
    }

    private static void assertAnimationPoses() {
        DragonModel model = new DragonModel(DragonModel.createBodyLayer().bakeRoot());
        model.root().visit(new PoseStack(), (pose, path, index, cube) -> {
            for (var polygon : cube.polygons) {
                float length = polygon.normal().lengthSquared();
                if (!Float.isFinite(length) || Math.abs(length - 1) > 0.001F)
                    throw new AssertionError("Pointed model faces must retain finite unit lighting normals: " + path);
                for (var vertex : polygon.vertices()) {
                    if (!Float.isFinite(vertex.x()) || !Float.isFinite(vertex.y()) || !Float.isFinite(vertex.z())
                            || !Float.isFinite(vertex.u()) || !Float.isFinite(vertex.v()))
                        throw new AssertionError("Pointed model mesh must have finite vertices and UVs: " + path);
                }
            }
        });
        DragonRenderState state = new DragonRenderState();
        state.tamed = true;
        state.movementAmount = 1;
        state.ageInTicks = 24;
        state.flightPhase = 2.4F;
        model.setupAnim(state);
        ModelPart segment = model.root().getChild("body");
        float yaw = 0, minimum = 0, maximum = 0;
        for (int i = 0; i < DragonModel.SEGMENT_COUNT; i++) {
            segment = segment.getChild("segment_" + i);
            if (i > 2 && segment.hasChild("talon_arm_left")) throw new AssertionError("The dragon must not retain hind limbs");
            yaw += segment.yRot;
            minimum = Math.min(minimum, yaw);
            maximum = Math.max(maximum, yaw);
        }
        if (!(minimum < -0.2F && maximum > 0.2F && minimum > -1.2F && maximum < 1.2F))
            throw new AssertionError("The spine must form a bounded lateral S, not remain rigid or curl indefinitely");
        float[] original = transforms(model);
        state.ageInTicks = 48;
        state.flightPhase = 4.8F;
        model.setupAnim(state);
        float[] later = transforms(model);
        boolean changed = false;
        for (int i = 0; i < original.length; i++) changed |= Math.abs(original[i] - later[i]) > 0.01F;
        if (!changed) throw new AssertionError("The serpent wave must move over time");
        state.ageInTicks = 24;
        state.flightPhase = 2.4F;
        model.setupAnim(state);
        float[] repeated = transforms(model);
        for (int i = 0; i < original.length; i++)
            if (Math.abs(original[i] - repeated[i]) > 0.00001F)
                throw new AssertionError("Repeating the same pose must not accumulate transforms");
        state.dormant=true;state.sleepAmount=1;model.setupAnim(state);
        ModelPart coil=model.root().getChild("body");float turns=0;
        for(int i=0;i<DragonModel.SEGMENT_COUNT;i++) {coil=coil.getChild("segment_"+i);turns+=coil.yRot;}
        if(turns<8 || turns>12)throw new AssertionError("Sleeping serpent must form nested coils, not a single open C");
        state.dormant=false;state.sleepAmount=0;
        ModelPart jaw = model.root().getChild("body").getChild("neck").getChild("head").getChild("jaw");
        state.biteTicks = CelestialDragonEntity.BITE_DURATION - 4;
        model.setupAnim(state);
        float opened = jaw.xRot;
        state.biteTicks = CelestialDragonEntity.BITE_DURATION - 6;
        model.setupAnim(state);
        if (opened < jaw.xRot + 0.3F) throw new AssertionError("Bite must open before snapping its jaws shut");
        state.biteTicks = 0;
        for (int remaining = CelestialDragonEntity.BEAM_DURATION; remaining >= 0; remaining--) {
            state.beamTicks = remaining;
            model.setupAnim(state);
            transforms(model); // Every charging, firing and recovery transform must remain finite.
        }
    }

    private static float[] transforms(DragonModel model) {
        var parts = model.allParts();
        float[] values = new float[parts.size() * 9];
        int index = 0;
        for (ModelPart part : parts) {
            for (float value : new float[]{part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale}) {
                if (!Float.isFinite(value)) throw new AssertionError("Dragon animation produced a non-finite transform");
                values[index++] = value;
            }
        }
        return values;
    }
}
