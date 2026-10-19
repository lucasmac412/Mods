package br.com.luma.test;

import br.com.luma.LumaMod;
import br.com.luma.ai.Action;
import br.com.luma.entity.CompanionEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** These run inside a real headless Minecraft server, never in the JUnit JVM. */
public final class CompanionGameTests {
    private static final String ARENA = "luma_gametest:arena";

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void registrationAttributesAndInventoryAreReal(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(8.5, 1, 8.5), new Vec3(9.5, 1, 8.5));
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(f.npc.getType()).equals(LumaMod.id("companion")),
                "The spawned entity must use the registered luma:companion type");
        helper.assertTrue(f.npc.getMaxHealth() == 40.0F, "Companion must have 40 maximum health");
        helper.assertTrue(Math.abs(f.npc.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.29) < 0.0001,
                "Companion movement speed must be registered");
        helper.assertTrue(f.npc.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3.0,
                "Companion attack attributes must be registered");

        ServerPlayer stranger = (ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
        // This lightweight mock has no network connection; set its entity position directly.
        stranger.setPos(f.owner.position());
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 32));
        helper.assertFalse(f.npc.giveItem(stranger), "Another player must not exchange the owner's items");
        helper.assertTrue(stranger.getMainHandItem().getCount() == 32, "Rejected delivery must preserve the stack");

        give(helper, f, Items.IRON_AXE, 1);
        give(helper, f, Items.OAK_PLANKS, 64);
        give(helper, f, Items.OAK_PLANKS, 32);
        helper.assertTrue(f.npc.getMainHandItem().is(Items.IRON_AXE), "Supplied axe must be equipped");
        helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 0,
                "Delivered materials must leave the player's inventory");
        f.npc.returnItems(f.owner);
        f.npc.returnItems(f.owner);
        helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 96,
                "Returning twice must return exactly the 96 delivered planks, without duplication");
        helper.assertTrue(f.owner.getInventory().countItem(Items.IRON_AXE) == 1,
                "Returning twice must return exactly one axe");
        helper.assertTrue(f.npc.getMainHandItem().isEmpty(), "Returned equipment must leave the NPC");
        f.cleanup.run();
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 260, skyAccess = true)
    public void stopStaysStillAndFollowActuallyWalks(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(9.5, 1, 8.5), new Vec3(2.5, 1, 8.5));
        Vec3 stoppedAt = f.npc.position();
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(f.npc.position().distanceToSqr(stoppedAt) < 0.01,
                            "STOP must keep the NPC still for 40 real server ticks");
                    helper.assertTrue(f.npc.getTarget() == null, "Stopped NPC must not attack a target");
                    f.npc.command(Action.FOLLOW);
                })
                .thenWaitUntil(() -> helper.assertTrue(f.npc.distanceToSqr(f.owner) < 10.0,
                        "FOLLOW must navigate from seven blocks away to the owner"))
                .thenExecute(() -> {
                    helper.assertTrue(f.npc.position().distanceToSqr(stoppedAt) > 4.0,
                            "Following must move the entity through the world");
                    f.cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(structure = ARENA, maxTicks = 900, skyAccess = true)
    public void miningRespectsBudgetLootAndToolDurability(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(8.5, 1, 8.5), new Vec3(8.5, 1, 10.5));
        helper.assertTrue(helper.getLevel().getGameRules().get(GameRules.MOB_GRIEFING),
                "Mining fixture requires the default mobGriefing=true rule");
        give(helper, f, Items.IRON_AXE, 1);
        List<BlockPos> logs = new ArrayList<>();
        for (int x = 10; x <= 14 && logs.size() < 17; x++) {
            for (int z = 7; z <= 10 && logs.size() < 17; z++) {
                BlockPos log = new BlockPos(x, 1, z);
                helper.setBlock(log, Blocks.OAK_LOG);
                logs.add(log);
            }
        }
        f.npc.command(Action.MINE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(f.messages.stream().anyMatch(s -> s.contains("limite de 16")),
                        "NPC must complete its mining order at the 16-block budget"))
                .thenIdle(50)
                .thenExecute(() -> {
                    long remaining = logs.stream().filter(p -> helper.getBlockState(p).is(Blocks.OAK_LOG)).count();
                    helper.assertTrue(remaining == 1, "Exactly one of the 17 exposed logs must remain after the budget");
                    helper.assertTrue(f.npc.getMainHandItem().is(Items.IRON_AXE), "Mining must retain the actual axe");
                    helper.assertTrue(f.npc.getMainHandItem().getDamageValue() == 16,
                            "Breaking 16 logs must consume 16 axe durability");
                    moveOwnerNear(f);
                    f.npc.returnItems(f.owner);
                    f.npc.returnItems(f.owner);
                    helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_LOG) == 16,
                            "Normal block loot must be stored and returned exactly once");
                    helper.assertTrue(f.owner.getInventory().countItem(Items.IRON_AXE) == 1,
                            "Mining must not duplicate its supplied axe");
                    f.cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(structure = ARENA, maxTicks = 2400, skyAccess = true)
    public void shelterUses96BlocksAndPreservesExistingTerrain(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(8.5, 1, 8.5), new Vec3(7.5, 1, 9.5));
        helper.setBlock(11, 1, 11, Blocks.DIAMOND_BLOCK); // Occupies the first candidate site.
        give(helper, f, Items.OAK_PLANKS, 64);
        give(helper, f, Items.OAK_PLANKS, 36);
        BlockPos origin = new BlockPos(2, 1, 10); // The next supported, empty candidate site.
        f.npc.command(Action.BUILD);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(f.messages.stream().anyMatch(s -> s.contains("Abrigo pronto")),
                        "NPC must finish its shelter through real navigation and block placement"))
                .thenExecute(() -> {
                    int placed = 0;
                    for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
                        BlockPos pos = origin.offset(x, y, z);
                        boolean doorway = x == 2 && z == 0 && (y == 1 || y == 2);
                        boolean shell = y == 0 || y == 4 || x == 0 || x == 4 || z == 0 || z == 4;
                        if (shell && !doorway) {
                            helper.assertBlockPresent(Blocks.OAK_PLANKS, pos);
                            placed++;
                        } else helper.assertTrue(helper.getBlockState(pos).isAir(),
                                "Shelter must preserve a two-block doorway and a hollow interior at " + pos);
                    }
                    helper.assertTrue(placed == 96, "Shelter must contain a 25-block floor, 46 walls and a 25-block roof");
                    helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, 11, 1, 11);
                    int totalPlanks = 0;
                    for (int x = 0; x < 16; x++) for (int y = 1; y < 7; y++) for (int z = 0; z < 16; z++) {
                        if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_PLANKS)) totalPlanks++;
                    }
                    helper.assertTrue(totalPlanks == 96, "No materials may be placed outside the selected shelter");
                    moveOwnerNear(f);
                    f.npc.returnItems(f.owner);
                    f.npc.returnItems(f.owner);
                    helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 4,
                            "A 96-block shelter must consume exactly 96 of the 100 supplied blocks");
                    f.cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void saveLoadPreservesOwnerModeEquipmentAndSupplies(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(8.5, 1, 8.5), new Vec3(9.5, 1, 8.5));
        give(helper, f, Items.IRON_AXE, 1);
        give(helper, f, Items.OAK_PLANKS, 64);
        give(helper, f, Items.OAK_PLANKS, 32);
        f.npc.getMainHandItem().setDamageValue(7);
        f.npc.setHealth(31.0F);
        f.npc.command(Action.AUTO);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        f.npc.saveWithoutId(output);
        CompanionEntity loaded = new CompanionEntity(LumaMod.COMPANION, helper.getLevel());
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), output.buildResult()));
        helper.assertTrue(f.owner.getUUID().equals(loaded.getOwnerId()), "Owner must survive serialization");
        helper.assertTrue(loaded.describe().contains("em modo autônomo"), "Autonomous mode must survive serialization");
        helper.assertTrue(loaded.getHealth() == 31.0F, "Health must survive serialization");
        helper.assertTrue(loaded.getMainHandItem().is(Items.IRON_AXE)
                        && loaded.getMainHandItem().getDamageValue() == 7,
                "Equipment and its durability must survive serialization");
        // The unloaded original is discarded, exactly as during an entity reload, without death drops.
        f.npc.discard();
        loaded.returnItems(f.owner);
        loaded.returnItems(f.owner);
        helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 96,
                "Reloaded supplies must return exactly once");
        helper.assertTrue(f.owner.getInventory().countItem(Items.IRON_AXE) == 1,
                "Reloaded equipment must return exactly once");
        loaded.discard();
        f.cleanup.run();
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 180, skyAccess = true)
    public void defendDamagesHostileWithSuppliedSwordAndProtectsOwner(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(8.5, 1, 8.5), new Vec3(9.5, 1, 8.5));
        give(helper, f, Items.IRON_SWORD, 1);
        Husk hostile = helper.spawn(EntityTypes.HUSK, new Vec3(10.5, 1, 8.5));
        hostile.setNoAi(true); // The damage must come from the companion, with no retaliatory attacks.
        hostile.setPersistenceRequired(); // Husks also do not burn in daylight.
        float hostileHealth = hostile.getHealth();
        float ownerHealth = f.owner.getHealth();
        helper.assertFalse(f.npc.canAttack(f.owner), "The owner's entity must never be an attack candidate");
        helper.assertFalse(f.npc.doHurtTarget(helper.getLevel(), f.owner),
                "Even a direct attack request against the owner must be rejected");
        helper.assertTrue(f.owner.getHealth() == ownerHealth, "Rejected friendly fire must leave the owner unharmed");
        f.npc.command(Action.DEFEND);
        helper.runBeforeTestEnd(hostile::discard);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(hostile.getHealth() < hostileHealth,
                            "DEFEND must actually damage the nearby hostile entity");
                    helper.assertTrue(f.npc.getMainHandItem().is(Items.IRON_SWORD)
                                    && f.npc.getMainHandItem().getDamageValue() > 0,
                            "Successful melee attacks must use the delivered sword and consume its durability");
                })
                .thenExecute(() -> {
                    helper.assertTrue(f.owner.getHealth() == ownerHealth,
                            "Defending against a monster must preserve the owner's health");
                    helper.assertFalse(f.npc.canAttack(f.owner), "Defense must continue to exclude the owner");
                    hostile.discard();
                    f.cleanup.run();
                })
                .thenSucceed();
    }

    @GameTest(structure = ARENA, maxTicks = 60, skyAccess = true)
    public void registeredCommandsCreateOnceDeliverRemoveAndAllowRecreation(GameTestHelper helper) {
        Fixture f = fixture(helper, new Vec3(8.5, 1, 8.5), new Vec3(9.5, 1, 8.5));
        // This scenario creates its NPC through the registered player command, rather than the fixture.
        f.npc.discard();
        command(helper, f.owner, "luma criar");
        List<CompanionEntity> firstSpawn = ownedCompanions(helper, f.owner);
        helper.assertTrue(firstSpawn.size() == 1, "The registered create command must spawn exactly one companion");
        CompanionEntity created = firstSpawn.getFirst();
        command(helper, f.owner, "luma criar");
        List<CompanionEntity> afterSecondCreate = ownedCompanions(helper, f.owner);
        helper.assertTrue(afterSecondCreate.size() == 1
                        && afterSecondCreate.getFirst().getUUID().equals(created.getUUID()),
                "A second create command must retain the original NPC without duplicating it");

        f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 64));
        command(helper, f.owner, "luma entregar");
        helper.assertTrue(f.owner.getMainHandItem().isEmpty(),
                "The registered deliver command must transfer the player's held stack");
        command(helper, f.owner, "luma remover");
        helper.assertTrue(created.isRemoved() && ownedCompanions(helper, f.owner).isEmpty(),
                "The registered remove command must remove the owned entity");
        helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 64,
                "Removing the companion must return all 64 delivered blocks");
        command(helper, f.owner, "luma remover");
        helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 64,
                "Repeating remove must not duplicate returned supplies");

        command(helper, f.owner, "luma criar");
        List<CompanionEntity> recreated = ownedCompanions(helper, f.owner);
        helper.assertTrue(recreated.size() == 1
                        && !recreated.getFirst().getUUID().equals(created.getUUID()),
                "Removing must clear the persistent owner binding and permit a fresh NPC");
        command(helper, f.owner, "luma remover");
        helper.assertTrue(ownedCompanions(helper, f.owner).isEmpty(), "Final removal must leave no owned NPC");
        helper.assertTrue(f.owner.getInventory().countItem(Items.OAK_PLANKS) == 64,
                "Recreating and removing an empty companion must preserve the returned materials");
        f.cleanup.run();
        helper.succeed();
    }

    private static void command(GameTestHelper helper, ServerPlayer player, String command) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static List<CompanionEntity> ownedCompanions(GameTestHelper helper, ServerPlayer owner) {
        return helper.getLevel().getEntitiesOfClass(CompanionEntity.class, helper.getBounds(),
                npc -> npc.isAlive() && owner.getUUID().equals(npc.getOwnerId()));
    }

    private static Fixture fixture(GameTestHelper helper, Vec3 playerPosition, Vec3 npcPosition) {
        // Use non-mineable support material so mining tests can only touch their explicit logs.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) helper.setBlock(x, 0, z, Blocks.BEDROCK);
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.snapTo(helper.absoluteVec(playerPosition));
        owner.getInventory().clearContent();
        CompanionEntity npc = helper.spawn(LumaMod.COMPANION, npcPosition);
        npc.setOwner(owner.getUUID());
        npc.command(Action.STOP);
        List<String> messages = new ArrayList<>();
        npc.setSpeech(messages::add);
        Runnable cleanup = () -> {
            npc.discard();
            if (helper.getLevel().getServer().getPlayerList().getPlayer(owner.getUUID()) == owner) {
                helper.getLevel().getServer().getPlayerList().remove(owner);
            }
        };
        helper.runBeforeTestEnd(cleanup); // Also release the mock if an asynchronous test times out.
        return new Fixture(owner, npc, messages, cleanup);
    }

    private static void give(GameTestHelper helper, Fixture f, Item item, int count) {
        f.owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        helper.assertTrue(f.npc.giveItem(f.owner), "Owner delivery must succeed for " + item);
        helper.assertTrue(f.owner.getMainHandItem().isEmpty(), "Delivery must remove the entire supplied stack");
    }

    private static void moveOwnerNear(Fixture f) {
        // Only move the test player after work finishes, to exercise the normal nearby exchange API.
        f.owner.snapTo(f.npc.position().add(0, 0, -1));
    }

    private record Fixture(ServerPlayer owner, CompanionEntity npc, List<String> messages, Runnable cleanup) { }
}
