package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.client.DragonControls;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.content.DragonCreativeTab;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Real mouse clicks must reach vanilla item use, even while controlling the dragon. */
public final class DragonVanillaEggClientGameTest implements FabricClientGameTest {
    private static final Path SCREENSHOTS = Path.of("../../reports/client-eggs").toAbsolutePath().normalize();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.pauseOnLostFocus = false;
        });
        try (var world = context.worldBuilder().adjustSettings(settings ->
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
            var server = world.getServer();
            server.runCommand("difficulty normal");
            server.runCommand("time set midnight");
            server.runCommand("gamerule minecraft:spawn_mobs false");
            server.runOnServer(ignored -> setupGround(world));
            world.getConnection().waitForChunksDownload();
            world.getConnection().waitForChunksRender();
            awaitPlayerAt(context, 0.5, 140, 0.5);

            checkCreativeTab(context);

            clickVanillaEgg(context, world, Items.COW_SPAWN_EGG, EntityTypes.COW,
                    InteractionHand.MAIN_HAND, new BlockPos(0, 139, 3), null);
            clickVanillaEgg(context, world, Items.ZOMBIE_SPAWN_EGG, EntityTypes.ZOMBIE,
                    InteractionHand.MAIN_HAND, new BlockPos(-3, 139, 3), null);

            UUID dragonId = server.computeOnServer(ignored -> mountDragon(world));
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForClientboundEntityUpdates(CelestialDragonMod.DRAGON);
            context.waitFor(client -> client.player != null && client.player.getVehicle() instanceof CelestialDragonEntity);
            context.waitTicks(3);

            clickVanillaEgg(context, world, Items.COW_SPAWN_EGG, EntityTypes.COW,
                    InteractionHand.MAIN_HAND, new BlockPos(12, 144, 3), dragonId);
            clickVanillaEgg(context, world, Items.ZOMBIE_SPAWN_EGG, EntityTypes.ZOMBIE,
                    InteractionHand.OFF_HAND, new BlockPos(14, 144, 3), dragonId);

            // The exception for held items must not disable the empty-handed energy attack.
            equip(context, world, InteractionHand.MAIN_HAND, Items.AIR);
            context.runOnClient(client -> {
                if (!DragonControls.shouldUseBeamControls(client))
                    throw new AssertionError("An empty-handed owner must retain mounted beam controls");
            });
            context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 2);
            server.waitFor(ignored -> dragon(world, dragonId).beamTicks() > 0);
            context.getInput().holdKeyFor(options -> options.keyShift, 2);
            server.waitFor(ignored -> !world.getConnection().getServerPlayer().isPassenger());
            server.runOnServer(ignored -> world.getConnection().getServerPlayer().teleportTo(
                    world.getConnection().getServerLevel(), 24.5, 140, 0.5, Set.of(), 0, 0, false));
            world.getConnection().waitForClientboundPackets();
            awaitPlayerAt(context, 24.5, 140, 0.5);
            clickVanillaEgg(context, world, Items.COW_SPAWN_EGG, EntityTypes.COW,
                    InteractionHand.MAIN_HAND, new BlockPos(24, 139, 3), null);
            clickVanillaEgg(context, world, Items.ZOMBIE_SPAWN_EGG, EntityTypes.ZOMBIE,
                    InteractionHand.MAIN_HAND, new BlockPos(27, 139, 3), null);
            System.out.println("Vanilla egg mouse regression passed: cow/zombie on foot, mounted main/offhand, after dismount; empty-hand beam preserved.");
        }
    }

    private static void checkCreativeTab(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_E);
        context.waitForScreen(CreativeModeInventoryScreen.class);
        context.clickScreenButton("Dragão Celestial");
        context.waitFor(client -> client.gui.screen() instanceof CreativeModeInventoryScreen creative
                && ((FabricCreativeModeInventoryScreen)creative).getSelectedTab()
                        == BuiltInRegistries.CREATIVE_MODE_TAB.getValue(DragonCreativeTab.KEY));
        context.runOnClient(client -> {
            var creative = (FabricCreativeModeInventoryScreen)client.gui.screen();
            var tab = creative.getSelectedTab();
            Set<Item> registered = BuiltInRegistries.ITEM.stream()
                    .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CelestialDragonMod.ID))
                    .collect(java.util.stream.Collectors.toSet());
            Set<Item> displayed = tab.getDisplayItems().stream().map(ItemStack::getItem)
                    .collect(java.util.stream.Collectors.toSet());
            if (registered.size() != 12 || !displayed.equals(registered)
                    || tab.getDisplayItems().size() != displayed.size()
                    || !displayed.contains(DragonContent.WILD_DRAGON_SPAWN_EGG)
                    || !displayed.contains(DragonContent.TAMED_DRAGON_SPAWN_EGG))
                throw new AssertionError("The visible dragon tab must contain all twelve mod items, including both spawn eggs");
            if (creative.getCurrentPage() != creative.getPage(tab))
                throw new AssertionError("The shortcut must switch to the page actually containing the dragon tab");
        });
        context.waitTicks(3);
        context.takeScreenshot(TestScreenshotOptions.of("12-creative-tab-all-items").disableCounterPrefix()
                .withSize(1280, 720).withDestinationDir(SCREENSHOTS));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static void setupGround(TestSingleplayerContext world) {
        ServerLevel level = world.getConnection().getServerLevel();
        for (int x = -6; x <= 32; x++) for (int z = -6; z <= 8; z++)
            level.setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.GRASS_BLOCK.defaultBlockState());
        // A rider's eye is higher than ordinary ground reach; these targets stay within vanilla reach.
        for (int x = 12; x <= 14; x++) for (int z = 3; z <= 4; z++)
            level.setBlockAndUpdate(new BlockPos(x, 144, z), Blocks.STONE.defaultBlockState());
        var player = world.getConnection().getServerPlayer();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, 0.5, 140, 0.5, Set.of(), 0, 0, false);
    }

    private static UUID mountDragon(TestSingleplayerContext world) {
        var level = world.getConnection().getServerLevel();
        var player = world.getConnection().getServerPlayer();
        CelestialDragonEntity dragon = CelestialDragonMod.DRAGON.create(level, EntitySpawnReason.COMMAND);
        if (dragon == null) throw new AssertionError("Dragon factory returned null");
        dragon.tame(player.getUUID());
        dragon.setNoAi(true); // Fresh vanilla mobs must not be mistaken for failed eggs because of combat.
        dragon.snapTo(12.5, 140, 0.5, 0, 0);
        dragon.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dragon);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.teleportTo(level, 12.5, 140, 2.5, Set.of(), 0, 0, false);
        dragon.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (dragon.getControllingPassenger() != player) throw new AssertionError("Owner failed to mount for item regression");
        return dragon.getUUID();
    }

    private static <T extends Mob> void clickVanillaEgg(ClientGameTestContext context, TestSingleplayerContext world,
                                                       Item egg, EntityType<T> type, InteractionHand hand,
                                                       BlockPos target, UUID dragonId) {
        equip(context, world, hand, egg);
        context.waitTicks(5); // Respect vanilla's right-click delay between separate item uses.
        Set<UUID> before = world.getServer().computeOnServer(ignored -> world.getConnection().getServerLevel()
                .getEntities(type, mob -> true).stream().map(Mob::getUUID).collect(java.util.stream.Collectors.toSet()));
        lookAtTopFace(context, target);
        context.runOnClient(client -> {
            if (!(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(target))
                throw new AssertionError("Egg click must point at its reachable block, got " + client.hitResult);
        });
        context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 1);
        world.getConnection().waitForServerboundPackets();
        if (dragonId != null) world.getServer().runOnServer(ignored -> {
            if (dragon(world, dragonId).beamTicks() != 0)
                throw new AssertionError("Using a held vanilla spawn egg must not trigger the dragon beam");
        });
        world.getServer().waitFor(ignored -> !newMobs(world, type, before).isEmpty());
        world.getServer().runOnServer(ignored -> {
            var spawned = newMobs(world, type, before);
            if (spawned.size() != 1) throw new AssertionError("One right click must spawn exactly one " + type + ", got " + spawned.size());
            for (Mob mob : spawned) {
                mob.setNoAi(true);
                mob.setInvulnerable(true);
                mob.setPersistenceRequired();
            }
        });
    }

    private static void equip(ClientGameTestContext context, TestSingleplayerContext world, InteractionHand hand, Item item) {
        world.getServer().runOnServer(ignored -> {
            var player = world.getConnection().getServerPlayer();
            player.getInventory().setSelectedSlot(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(hand, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
            player.connection.send(player.getInventory().createInventoryUpdatePacket(0));
            player.connection.send(player.getInventory().createInventoryUpdatePacket(Inventory.SLOT_OFFHAND));
        });
        world.getConnection().waitForClientboundPackets();
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
        context.waitFor(client -> item == Items.AIR
                ? client.player.getMainHandItem().isEmpty() && client.player.getOffhandItem().isEmpty()
                : client.player.getItemInHand(hand).is(item));
    }

    private static void lookAtTopFace(ClientGameTestContext context, BlockPos target) {
        Vec3 eye = context.computeOnClient(client -> client.player.getEyePosition());
        Vec3 delta = new Vec3(target.getX() + 0.5, target.getY() + 0.9999, target.getZ() + 0.5).subtract(eye);
        float yaw = (float)(Math.atan2(-delta.x, delta.z) * 180 / Math.PI);
        float pitch = (float)(-Math.atan2(delta.y, delta.horizontalDistance()) * 180 / Math.PI);
        context.getInput().lookAt(yaw, pitch);
        context.waitTicks(2);
    }

    private static <T extends Mob> List<? extends T> newMobs(TestSingleplayerContext world, EntityType<T> type, Set<UUID> before) {
        return world.getConnection().getServerLevel().getEntities(type, mob -> !before.contains(mob.getUUID()));
    }

    private static void awaitPlayerAt(ClientGameTestContext context, double x, double y, double z) {
        context.waitFor(client -> client.player != null && client.player.position().distanceToSqr(new Vec3(x, y, z)) < 0.1);
    }

    private static CelestialDragonEntity dragon(TestSingleplayerContext world, UUID id) {
        if (!(world.getConnection().getServerLevel().getEntity(id) instanceof CelestialDragonEntity dragon))
            throw new AssertionError("Mounted regression dragon disappeared");
        return dragon;
    }
}
