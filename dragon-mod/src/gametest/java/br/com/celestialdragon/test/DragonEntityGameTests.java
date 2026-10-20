package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.config.DragonConfig;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.network.DragonControlNetworking;
import br.com.celestialdragon.network.DragonControlPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Integration tests exercise real server entities, collision, ownership and authenticated control. */
public final class DragonEntityGameTests {
    private static final String ARENA = "celestial_dragon_gametest:arena";

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void ownerAndPetsAreSafeFromBiteAndBeam(GameTestHelper helper) {
        var dragon = dragon(helper);
        ServerPlayer owner = player(helper, new Vec3(16, 3, 18.4));
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("dragon_" + UUID.randomUUID().toString().substring(0, 8));
        try {
            dragon.tame(owner.getUUID());
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            var teammate = target(helper, new Vec3(16, 3, 18.2));
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            helper.assertTrue(CelestialDragonEntity.isProtectedAlly(dragon, teammate),
                    "A member of the owner's team must be protected from retaliation too");
            var wolf = helper.spawn(EntityTypes.WOLF, new Vec3(16, 3, 18.7));
            freeze(wolf);
            wolf.tame(owner);
            var sibling = helper.spawn(CelestialDragonMod.DRAGON, new Vec3(16, 2, 19));
            freeze(sibling);
            sibling.tame(owner.getUUID());
            var bitten = target(helper, new Vec3(16, 3, 18.9));
            float ownerHealth = owner.getHealth();
            float wolfHealth = wolf.getHealth();
            float siblingHealth = sibling.getHealth();
            helper.assertTrue(dragon.tryBite(), "Bite must activate");
            helper.assertTrue(bitten.getHealth() < 100, "A nearby enemy must take bite damage");
            sameHealth(helper, ownerHealth, owner.getHealth(), "owner after bite");
            sameHealth(helper, wolfHealth, wolf.getHealth(), "pet after bite");
            sameHealth(helper, siblingHealth, sibling.getHealth(), "same owner dragon after bite");
            sameHealth(helper, 100, teammate.getHealth(), "owner's teammate after bite");

            owner.snapTo(helper.absoluteVec(new Vec3(16, 3, 24)));
            wolf.snapTo(helper.absoluteVec(new Vec3(16, 3, 25)));
            sibling.snapTo(helper.absoluteVec(new Vec3(16, 2, 28)));
            teammate.snapTo(helper.absoluteVec(new Vec3(16, 3, 26)));
            var beamed = target(helper, new Vec3(16, 3, 31));
            helper.assertTrue(dragon.tryBeam(), "Beam must activate");
            helper.assertTrue(beamed.getHealth() < 100, "Beam must reach an enemy beyond protected allies");
            sameHealth(helper, ownerHealth, owner.getHealth(), "owner after beam");
            sameHealth(helper, wolfHealth, wolf.getHealth(), "pet after beam");
            sameHealth(helper, siblingHealth, sibling.getHealth(), "same owner dragon after beam");
            sameHealth(helper, 100, teammate.getHealth(), "owner's teammate after beam");
            float dragonHealth = dragon.getHealth();
            helper.assertFalse(dragon.hurtServer(helper.getLevel(),
                    owner.damageSources().playerAttack(owner), 20), "Owner cannot hurt their dragon");
            sameHealth(helper, dragonHealth, dragon.getHealth(), "dragon after friendly damage");
            helper.succeed();
        } finally {
            scoreboard.removePlayerTeam(team);
            removePlayer(helper, owner);
        }
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void wallsBlockBeamAndRemainIntact(GameTestHelper helper) {
        var dragon = dragon(helper);
        var enemy = target(helper, new Vec3(16, 3, 27));
        for (int x = 14; x <= 18; x++) {
            for (int y = 2; y <= 7; y++) helper.setBlock(x, y, 22, Blocks.STONE);
        }
        helper.assertTrue(dragon.tryBeam(), "Beam activates in front of a wall");
        sameHealth(helper, 100, enemy.getHealth(), "enemy protected by wall");
        helper.assertTrue(helper.relativePos(BlockPos.containing(dragon.beamEnd())).getZ() <= 22,
                "Beam endpoint must stop at the wall");
        helper.runAfterDelay(26, () -> {
            sameHealth(helper, 100, enemy.getHealth(), "enemy after full beam channel");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(16, 3, 22));
            helper.succeed();
        });
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void beamStopsAt35AndDamagesEachTargetOnce(GameTestHelper helper) {
        var dragon = dragon(helper);
        var near = target(helper, new Vec3(16, 3, 50.5));
        var far = target(helper, new Vec3(16, 3, 52.5));
        helper.assertTrue(dragon.tryBeam(), "First beam activates");
        float expected = (float) Math.max(0, 100 - DragonConfig.current().beamDamage());
        sameHealth(helper, expected, near.getHealth(), "target within 35 blocks");
        sameHealth(helper, 100, far.getHealth(), "target beyond 35 blocks");
        helper.assertTrue(dragon.getEyePosition().distanceTo(dragon.beamEnd()) <= 35.01,
                "Beam endpoint cannot exceed 35 blocks");
        helper.runAfterDelay(26, () -> {
            sameHealth(helper, expected, near.getHealth(), "one configured hit per beam channel");
            sameHealth(helper, 100, far.getHealth(), "far target remains safe");
            helper.succeed();
        });
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 120)
    public void attacksRespectTheirOwnCooldowns(GameTestHelper helper) {
        var dragon = dragon(helper);
        var close = target(helper, new Vec3(16, 3, 18.5));
        var distant = target(helper, new Vec3(16, 3, 27));
        helper.assertTrue(dragon.tryBite(), "First bite activates");
        float biteHealth = close.getHealth();
        helper.assertFalse(dragon.tryBite(), "A second immediate bite must be rejected");
        sameHealth(helper, biteHealth, close.getHealth(), "rejected bite adds no damage");
        helper.assertTrue(dragon.tryBeam(), "Bite does not consume beam cooldown");
        float beamHealth = distant.getHealth();
        helper.assertFalse(dragon.tryBeam(), "A second immediate beam must be rejected");
        sameHealth(helper, beamHealth, distant.getHealth(), "rejected beam adds no damage");
        helper.runAfterDelay(19, () -> helper.assertTrue(dragon.tryBite(), "Bite returns after 18 ticks"));
        helper.runAfterDelay(95, () -> helper.assertFalse(dragon.tryBeam(), "Beam stays locked before 100 ticks"));
        helper.runAfterDelay(102, () -> {
            helper.assertTrue(dragon.tryBeam(), "Beam returns after 100 ticks");
            helper.succeed();
        });
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 100)
    public void passiveCowCanRetaliateWithoutAttackAttribute(GameTestHelper helper) {
        for (int x = 14; x <= 18; x++) {
            for (int z = 14; z <= 18; z++) helper.setBlock(x, 1, z, Blocks.BEDROCK);
        }
        var dragon = dragon(helper);
        var cow = helper.spawn(EntityTypes.COW, new Vec3(16, 2, 17.7));
        helper.assertFalse(cow.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE),
                "This test must exercise an animal with no attack attribute");
        float initialHealth = dragon.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.isAlive(), "Cow remains alive while confronting the dragon");
            helper.assertTrue(dragon.getHealth() < initialHealth, "Passive cow must be able to damage dragon");
        });
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void ownerNestHealthAndCooldownSurviveSaving(GameTestHelper helper) {
        var dragon = dragon(helper);
        helper.assertFalse(dragon.isTamed(), "Fresh dragon defaults to wild");
        UUID owner = UUID.randomUUID();
        BlockPos nest = helper.absolutePos(new BlockPos(8, 2, 8));
        dragon.tame(owner);
        dragon.setNest(nest);
        float health = dragon.getMaxHealth() * 0.37F;
        dragon.setHealth(health);
        dragon.tryBite();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        dragon.saveWithoutId(output);
        var restored = CelestialDragonMod.DRAGON.create(helper.getLevel(), EntitySpawnReason.LOAD);
        helper.assertTrue(restored != null, "Entity type can create a dragon for loading");
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), output.buildResult()));
        helper.assertTrue(restored.isTamed() && owner.equals(restored.getOwnerId()), "Owner and tamed mode persist");
        helper.assertValueEqual(restored.getNest(), nest, "Nest position persists");
        sameHealth(helper, health, restored.getHealth(), "health survives save and load");
        helper.assertTrue(restored.isSaddled(), "A restored tamed dragon has its automatic saddle");
        helper.assertFalse(restored.tryBite(), "Loading cannot erase a bite cooldown");
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void onlyMountedOwnerCanSendAttackControls(GameTestHelper helper) {
        var dragon = dragon(helper);
        var close = target(helper, new Vec3(16, 3, 18.5));
        var distant = target(helper, new Vec3(16, 3, 27));
        ServerPlayer owner = player(helper, new Vec3(14, 2, 16));
        ServerPlayer intruder = player(helper, new Vec3(18, 2, 16));
        try {
            dragon.tame(owner.getUUID());
            var attack = new DragonControlPayload(0, true, true);
            DragonControlNetworking.handle(owner, attack);
            helper.assertTrue(dragon.biteTicks() == 0 && dragon.beamTicks() == 0,
                    "An owner on foot cannot send dragon attacks");
            dragon.interact(intruder, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertFalse(intruder.isPassenger(), "An intruder cannot mount");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            dragon.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertFalse(owner.isPassenger(), "Mounting requires an empty hand");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            dragon.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(dragon.getControllingPassenger() == owner, "Empty handed owner controls their dragon");
            owner.setYRot(0);
            owner.setXRot(0);
            DragonControlNetworking.handle(intruder, attack);
            DragonControlNetworking.handle(owner, new DragonControlPayload(Float.NaN, true, true));
            helper.assertTrue(dragon.biteTicks() == 0 && dragon.beamTicks() == 0,
                    "Intruder and malformed controls cannot activate attacks");
            sameHealth(helper, 100, close.getHealth(), "untrusted bite has no damage");
            sameHealth(helper, 100, distant.getHealth(), "untrusted beam has no damage");
            DragonControlNetworking.handle(owner, attack);
            helper.assertTrue(close.getHealth() < 100 && distant.getHealth() < 100,
                    "Authenticated mounted owner can activate both server attacks");
            helper.succeed();
        } finally {
            removePlayer(helper, owner);
            removePlayer(helper, intruder);
        }
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void healthSettingsApplyToExistingAndNewDragonsWithoutHealing(GameTestHelper helper) throws IOException {
        DragonConfig.Values original = DragonConfig.current();
        Path temporary = Files.createTempFile("celestial-dragon-health-gametest-", ".json");
        try {
            loadHealthSetting(temporary, 200, original);
            var existing = dragon(helper);
            existing.setHealth(120);
            loadHealthSetting(temporary, 350, original);
            for (int tick = 0; tick < 20; tick++) helper.getLevel().tickNonPassenger(existing);
            sameHealth(helper, 350, existing.getMaxHealth(), "existing dragon picks up an increased maximum");
            sameHealth(helper, 120, existing.getHealth(), "increasing maximum does not heal existing dragon");

            var newborn = CelestialDragonMod.DRAGON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            helper.assertTrue(newborn != null, "A new dragon can be created after a setting change");
            sameHealth(helper, 350, newborn.getMaxHealth(), "new dragon uses current configured maximum");
            sameHealth(helper, 350, newborn.getHealth(), "new dragon starts at its full configured health");

            loadHealthSetting(temporary, 80, original);
            for (int tick = 0; tick < 20; tick++) helper.getLevel().tickNonPassenger(existing);
            sameHealth(helper, 80, existing.getMaxHealth(), "existing dragon picks up a decreased maximum");
            sameHealth(helper, 80, existing.getHealth(), "decreasing maximum clamps current health");
        } finally {
            // Keep the actual server configuration path and its values intact for later tests.
            DragonConfig.load(FabricLoader.getInstance().getConfigDir().resolve("celestial-dragon.json"));
            Files.deleteIfExists(temporary);
        }
        helper.succeed();
    }

    private static void loadHealthSetting(Path path, double maximum, DragonConfig.Values original) throws IOException {
        Files.writeString(path, "{\"maxHealth\":" + maximum + ",\"biteDamage\":" + original.biteDamage()
                + ",\"beamDamage\":" + original.beamDamage() + "}");
        DragonConfig.load(path);
    }

    private static CelestialDragonEntity dragon(GameTestHelper helper) {
        var dragon = helper.spawn(CelestialDragonMod.DRAGON, new Vec3(16, 2, 16));
        freeze(dragon);
        dragon.setYRot(0);
        dragon.setXRot(0);
        return dragon;
    }

    private static Mob target(GameTestHelper helper, Vec3 position) {
        // A cow has no armor and can be frozen in the exact height of the beam.
        var target = helper.spawn(EntityTypes.COW, position);
        freeze(target);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        return target;
    }

    private static void freeze(Mob mob) {
        mob.setNoAi(true);
        mob.setNoGravity(true);
    }

    private static ServerPlayer player(GameTestHelper helper, Vec3 position) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.snapTo(helper.absoluteVec(position));
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        if (player.isPassenger()) player.stopRiding();
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void sameHealth(GameTestHelper helper, float expected, float actual, String description) {
        helper.assertTrue(Math.abs(expected - actual) < 0.01F, description + ": expected " + expected + ", got " + actual);
    }
}
