package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.client.DragonSettingsScreen;
import br.com.celestialdragon.config.DragonConfig;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.network.DragonSettingsNetworking;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** A real OpenGL/client/network smoke test. Its worlds and configuration are disposable. */
public final class DragonClientGameTest implements FabricClientGameTest {
    private static final Path SCREENSHOTS = Path.of("../../reports/client-smoke").toAbsolutePath().normalize();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.fov().set(70);
            client.options.pauseOnLostFocus = false;
        });
        try (var world = context.worldBuilder().adjustSettings(settings ->
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            var server = world.getServer();
            var connection = world.getConnection();
            DragonConfig.Values original = server.computeOnServer(ignored -> DragonConfig.current());
            try {
                server.runCommand("time set noon");
                server.runCommand("weather clear");
                UUID dragonId = server.computeOnServer(ignored -> setupScene(world));
                connection.waitForChunksDownload();
                connection.waitForClientboundEntityUpdates(CelestialDragonMod.DRAGON);
                connection.waitForChunksRender();
                context.getInput().lookAt(new BlockPos(-3, 144, 0));
                context.waitTicks(10);
                screenshot(context, "01-dragons-wild-and-owned");
                testSettings(context, world, original);
                testRiding(context, world, dragonId);
            } finally {
                server.runOnServer(ignored -> {
                    if (!DragonConfig.save(original)) throw new AssertionError("Could not restore disposable test configuration");
                });
                context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            }
        }
    }

    private static UUID setupScene(TestSingleplayerContext world) {
        ServerLevel level = world.getConnection().getServerLevel();
        var player = world.getConnection().getServerPlayer();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, 20, 149, 22, Set.of(), 135, 10, false);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        CelestialDragonEntity owned = spawnDragon(level, new Vec3(0, 141, 0), -35);
        owned.tame(player.getUUID());
        spawnDragon(level, new Vec3(-15, 141, 7), 20);
        if (!DragonSettingsNetworking.canEdit(player)) throw new AssertionError("Singleplayer owner must be able to edit settings");
        return owned.getUUID();
    }

    private static CelestialDragonEntity spawnDragon(ServerLevel level, Vec3 position, float yaw) {
        CelestialDragonEntity dragon = CelestialDragonMod.DRAGON.create(level, EntitySpawnReason.COMMAND);
        if (dragon == null) throw new AssertionError("Dragon factory returned null");
        dragon.snapTo(position, yaw, 0);
        dragon.setNoAi(true);
        dragon.setNoGravity(true);
        level.addFreshEntity(dragon);
        return dragon;
    }

    private static void testSettings(ClientGameTestContext context, TestSingleplayerContext world,
                                     DragonConfig.Values original) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(PauseScreen.class);
        context.clickScreenButton("Dragão Celestial");
        context.waitForScreen(DragonSettingsScreen.class);
        context.waitFor(client -> saveButton(client.gui.screen()).active);
        screenshot(context, "02-settings-authorized-owner");
        context.runOnClient(client -> fields(client.gui.screen()).getFirst().setValue("NaN"));
        context.clickScreenButton("Salvar no servidor");
        context.waitTicks(2);
        assertValues(world, original, "UI must reject invalid numeric values");
        screenshot(context, "03-settings-invalid-input");

        context.runOnClient(client -> ClientPlayNetworking.send(
                new DragonSettingsNetworking.Request(true, Double.NaN, 1, 1)));
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        assertValues(world, original, "Server must independently reject malformed values");

        DragonConfig.Values edited = new DragonConfig.Values(240, 14, 35);
        context.runOnClient(client -> {
            List<EditBox> fields = fields(client.gui.screen());
            fields.get(0).setValue("240");
            fields.get(1).setValue("14");
            fields.get(2).setValue("35");
        });
        context.clickScreenButton("Salvar no servidor");
        world.getServer().waitFor(ignored -> DragonConfig.current().equals(edited));
        world.getConnection().waitForClientboundPackets();
        context.waitFor(client -> saveButton(client.gui.screen()).active);
        assertValues(world, edited, "Authorized UI save must reach the server");
        screenshot(context, "04-settings-saved-on-server");
        context.clickScreenButton("Voltar");
        context.waitForScreen(PauseScreen.class);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static void testRiding(ClientGameTestContext context, TestSingleplayerContext world, UUID dragonId) {
        var server = world.getServer();
        var connection = world.getConnection();
        server.runOnServer(ignored -> {
            var dragon = dragon(world, dragonId);
            var player = connection.getServerPlayer();
            dragon.snapTo(0, 145, 0, 0, 0);
            dragon.setDeltaMovement(Vec3.ZERO);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.teleportTo(connection.getServerLevel(), 0, 145, 2, Set.of(), 0, 0, false);
            dragon.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            dragon.setNoAi(false);
            if (dragon.getControllingPassenger() != player) throw new AssertionError("Owner failed to mount");
        });
        connection.waitForClientboundPackets();
        context.waitFor(client -> client.player != null && client.player.getVehicle() instanceof CelestialDragonEntity);
        context.getInput().lookAt(0, 0);
        Vec3 before = server.computeOnServer(ignored -> dragon(world, dragonId).position());
        context.getInput().holdKeyFor(options -> options.keyUp, 12);
        connection.waitForServerboundPackets();
        Vec3 after = server.computeOnServer(ignored -> dragon(world, dragonId).position());
        if (after.distanceToSqr(before) < 0.1) throw new AssertionError("W must steer the mounted dragon");
        double initialY = after.y;
        context.getInput().holdKeyFor(options -> options.keyJump, 12);
        connection.waitForServerboundPackets();
        double upY = server.computeOnServer(ignored -> dragon(world, dragonId).getY());
        if (upY <= initialY + 0.1) throw new AssertionError("Space must raise the dragon");
        context.getInput().holdKeyFor(options -> options.keySprint, 24);
        connection.waitForServerboundPackets();
        double downY = server.computeOnServer(ignored -> dragon(world, dragonId).getY());
        if (downY >= upY - 0.1) throw new AssertionError("Ctrl/sprint must lower the dragon");
        context.waitTicks(10);
        context.runOnClient(client -> {
            if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
                throw new AssertionError("Mount must select the rear chase camera automatically");
            if (client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()) < 15)
                throw new AssertionError("Mounted camera must clear the mane and body in open air");
        });
        context.getInput().holdKeyFor(options -> options.keyShift, 18);
        connection.waitForServerboundPackets();
        if (!server.computeOnServer(ignored -> connection.getServerPlayer().isPassenger()))
            throw new AssertionError("Holding Shift boosts and must not dismount");
        context.getInput().lookAt(0, -5);
        context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 2);
        server.waitFor(ignored -> dragon(world, dragonId).beamTicks() > 0);
        context.waitTicks(3);
        screenshot(context, "05-mounted-cyan-beam");
        server.waitFor(ignored -> dragon(world, dragonId).beamTicks() == 0);
        context.getInput().holdKeyFor(options -> options.keyJump, 20);
        connection.waitForServerboundPackets();
        // Aim steeply down: after actual ascent, a 60-degree ray passes beyond this small pad.
        context.getInput().lookAt(0, 80);
        context.getInput().pressKey(GLFW.GLFW_KEY_R);
        server.waitFor(ignored -> dragon(world, dragonId).spiritTicks() > 0);
        var liveOrb = server.computeOnServer(ignored -> connection.getServerLevel().getEntities(CelestialDragonMod.SPIRIT_ORB, orb -> dragonId.equals(orb.dragonId())).getFirst());
        context.waitTicks(26);
        screenshot(context, "19-spirit-charging");
        context.waitTicks(32);
        screenshot(context, "20-spirit-slow-flight");
        String flightDiagnostic = server.computeOnServer(ignored -> "Spirit flight check: age="+liveOrb.chargeAge()+" pos="+liveOrb.position()+" rider pitch="+connection.getServerPlayer().getXRot()+" mount="+dragon(world,dragonId).position());
        System.out.println(flightDiagnostic);
        server.waitFor(ignored -> connection.getServerLevel().getEntities(CelestialDragonMod.SPIRIT_ORB,
                orb -> dragonId.equals(orb.dragonId())).isEmpty(), 550);
        int cleared = server.computeOnServer(ignored -> {
            int count=0;
            for(int x=-30;x<=30;x++) for(int z=-30;z<=30;z++)
                if(connection.getServerLevel().getBlockState(new BlockPos(x,139,z)).isAir()) count++;
            return count;
        });
        if(cleared < 100) throw new AssertionError("The real R-key cast must destroy the visible test platform, not expire in flight; removed="+cleared+server.computeOnServer(ignored -> "; orb age="+liveOrb.chargeAge()+" pos="+liveOrb.position()+" dragon="+dragon(world,dragonId).position()));
        connection.waitForClientboundPackets();
        connection.waitForChunksRender();
        context.waitTicks(5);
        screenshot(context, "21-spirit-impact-crater");
        context.getInput().lookAt(0,0);
        Vec3 plainStart=server.computeOnServer(ignored -> dragon(world,dragonId).position());
        context.getInput().holdKeyFor(options -> options.keyUp,16);
        connection.waitForServerboundPackets();
        Vec3 plainEnd=server.computeOnServer(ignored -> dragon(world,dragonId).position());
        context.getInput().holdKey(options -> options.keyShift);
        Vec3 boostedEnd;
        try {
            context.getInput().holdKeyFor(options -> options.keyUp,16);
            connection.waitForServerboundPackets();
            boostedEnd=server.computeOnServer(ignored -> dragon(world,dragonId).position());
            if(!server.computeOnServer(ignored -> dragon(world,dragonId).isBoosting() && connection.getServerPlayer().isPassenger()))
                throw new AssertionError("Real W+Shift must boost without dismounting");
            context.getInput().lookAt(0, 22);
            context.waitTicks(3);
            screenshot(context,"22-mounted-lightning-boost");
        } finally { context.getInput().releaseKey(options -> options.keyShift); }
        double normalDistance=plainEnd.subtract(plainStart).horizontalDistance();
        double boostedDistance=boostedEnd.subtract(plainEnd).horizontalDistance();
        if(boostedDistance < normalDistance*1.2)
            throw new AssertionError("Shift must increase actual flight distance, not slow it through crouching: normal="+normalDistance+", boosted="+boostedDistance);
        System.out.println("Real mounted controls passed: camera clear; flight normal="+normalDistance+", W+Shift="+boostedDistance+"; spirit crater blocks="+cleared);
        context.getInput().holdKeyFor(options -> options.keySprint, 1);
        context.waitTicks(1);
        context.getInput().holdKeyFor(options -> options.keySprint, 1);
        connection.waitForServerboundPackets();
        server.waitFor(ignored -> !connection.getServerPlayer().isPassenger());
        context.waitTicks(3);
        context.runOnClient(client -> {
            if(client.options.getCameraType()!=CameraType.FIRST_PERSON)throw new AssertionError("Dismount must restore the previous perspective");
        });
        server.runOnServer(ignored -> connection.getServerPlayer().teleportTo(connection.getServerLevel(), 24, 163, 42, Set.of(), 135, 40, false));
        connection.waitForClientboundPackets();
        context.getInput().lookAt(new BlockPos(0, 139, 22));
        context.waitTicks(8);
        connection.waitForChunksRender();
        screenshot(context, "21-spirit-impact-crater");
    }

    private static CelestialDragonEntity dragon(TestSingleplayerContext world, UUID id) {
        var entity = world.getConnection().getServerLevel().getEntity(id);
        if (!(entity instanceof CelestialDragonEntity dragon)) throw new AssertionError("Test dragon disappeared");
        return dragon;
    }

    private static Button saveButton(net.minecraft.client.gui.screens.Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Salvar no servidor"))
                .findFirst().orElseThrow(() -> new AssertionError("Settings save button missing"));
    }

    private static List<EditBox> fields(net.minecraft.client.gui.screens.Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .sorted(Comparator.comparingInt(EditBox::getY)).toList();
    }

    private static void assertValues(TestSingleplayerContext world, DragonConfig.Values expected, String message) {
        if (!world.getServer().computeOnServer(ignored -> DragonConfig.current().equals(expected)))
            throw new AssertionError(message);
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(1280, 720)
                .withDestinationDir(SCREENSHOTS));
    }
}
