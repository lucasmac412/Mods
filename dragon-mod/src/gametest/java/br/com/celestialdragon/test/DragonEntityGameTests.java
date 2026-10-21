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
        ServerPlayer owner = player(helper, new Vec3(16, 4, 19.4));
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("dragon_" + UUID.randomUUID().toString().substring(0, 8));
        try {
            dragon.tame(owner.getUUID());
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            var teammate = target(helper, new Vec3(16, 4, 19.7));
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            helper.assertTrue(CelestialDragonEntity.isProtectedAlly(dragon, teammate),
                    "A member of the owner's team must be protected from retaliation too");
            var wolf = helper.spawn(EntityTypes.WOLF, new Vec3(16, 4, 20.7));
            freeze(wolf);
            wolf.tame(owner);
            var sibling = helper.spawn(CelestialDragonMod.DRAGON, new Vec3(16, 2, 21.4));
            freeze(sibling);
            sibling.tame(owner.getUUID());
            var bitten = target(helper, new Vec3(16, 4, 20.2));
            float ownerHealth = owner.getHealth();
            float wolfHealth = wolf.getHealth();
            float siblingHealth = sibling.getHealth();
            helper.assertTrue(dragon.tryBite(), "Bite must activate");
            advance(helper, dragon, CelestialDragonEntity.BITE_HIT_DELAY - 1);
            sameHealth(helper, 100, bitten.getHealth(), "bite wind-up cannot damage early");
            advance(helper, dragon, 1);
            helper.assertTrue(bitten.getHealth() < 100, "A nearby enemy must take bite damage");
            sameHealth(helper, ownerHealth, owner.getHealth(), "owner after bite");
            sameHealth(helper, wolfHealth, wolf.getHealth(), "pet after bite");
            sameHealth(helper, siblingHealth, sibling.getHealth(), "same owner dragon after bite");
            sameHealth(helper, 100, teammate.getHealth(), "owner's teammate after bite");

            advance(helper, dragon, CelestialDragonEntity.BITE_DURATION - CelestialDragonEntity.BITE_HIT_DELAY);
            owner.snapTo(helper.absoluteVec(new Vec3(16, 4, 24)));
            wolf.snapTo(helper.absoluteVec(new Vec3(16, 4, 25)));
            sibling.snapTo(helper.absoluteVec(new Vec3(16, 2, 28)));
            teammate.snapTo(helper.absoluteVec(new Vec3(16, 4, 26)));
            var beamed = target(helper, new Vec3(16, 4, 31));
            helper.assertTrue(dragon.tryBeam(), "Beam must activate");
            advance(helper, dragon, CelestialDragonEntity.BEAM_CHARGE_TICKS - 1);
            sameHealth(helper, 100, beamed.getHealth(), "beam charge cannot damage early");
            advance(helper, dragon, 1);
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
        var enemy = target(helper, new Vec3(16, 4, 27));
        for (int x = 14; x <= 18; x++) {
            for (int y = 2; y <= 7; y++) helper.setBlock(x, y, 22, Blocks.STONE);
        }
        helper.assertTrue(dragon.tryBeam(), "Beam activates in front of a wall");
        sameHealth(helper, 100, enemy.getHealth(), "enemy protected by wall");
        helper.assertTrue(helper.relativePos(BlockPos.containing(dragon.beamEnd())).getZ() <= 22,
                "Beam endpoint must stop at the wall");
        advance(helper, dragon, CelestialDragonEntity.BEAM_DURATION);
        sameHealth(helper, 100, enemy.getHealth(), "enemy after full beam channel");
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(16, 3, 22));
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void beamStopsAt35AndDamagesEachTargetOnce(GameTestHelper helper) {
        var dragon = dragon(helper);
        var near = target(helper, new Vec3(16, 4, 50.5));
        var far = target(helper, new Vec3(16, 4, 52.5));
        helper.assertTrue(dragon.tryBeam(), "First beam activates");
        float expected = (float) Math.max(0, 100 - DragonConfig.current().beamDamage());
        advance(helper, dragon, CelestialDragonEntity.BEAM_CHARGE_TICKS - 1);
        sameHealth(helper, 100, near.getHealth(), "target remains unharmed throughout beam charge");
        advance(helper, dragon, 1);
        sameHealth(helper, expected, near.getHealth(), "target within 35 blocks");
        sameHealth(helper, 100, far.getHealth(), "target beyond 35 blocks");
        helper.assertTrue(dragon.getEyePosition().distanceTo(dragon.beamEnd()) <= 35.01,
                "Beam endpoint cannot exceed 35 blocks");
        advance(helper, dragon, CelestialDragonEntity.BEAM_DURATION
                - CelestialDragonEntity.BEAM_CHARGE_TICKS - CelestialDragonEntity.BEAM_RECOVERY_TICKS);
        sameHealth(helper, expected, near.getHealth(), "one configured hit per beam channel");
        var late = target(helper, new Vec3(16, 4, 30));
        advance(helper, dragon, CelestialDragonEntity.BEAM_RECOVERY_TICKS);
        sameHealth(helper, 100, late.getHealth(), "a target entering during recovery cannot be damaged");
        sameHealth(helper, expected, near.getHealth(), "completed channel cannot hit a target twice");
        sameHealth(helper, 100, far.getHealth(), "far target remains safe");
        helper.assertTrue(dragon.beamTicks() == 0, "Beam animation finishes after its recovery");
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 120)
    public void attacksRespectTheirOwnCooldowns(GameTestHelper helper) {
        var dragon = dragon(helper);
        var close = target(helper, new Vec3(16, 4, 20));
        var distant = target(helper, new Vec3(16, 4, 27));
        helper.assertTrue(dragon.tryBite(), "First bite activates");
        float biteHealth = close.getHealth();
        helper.assertFalse(dragon.tryBite(), "A second immediate bite must be rejected");
        sameHealth(helper, biteHealth, close.getHealth(), "rejected bite adds no damage");
        helper.assertFalse(dragon.tryBeam(), "Beam cannot overlap the bite animation");
        advance(helper, dragon, CelestialDragonEntity.BITE_DURATION);
        helper.assertFalse(dragon.tryBite(), "Finishing the bite animation does not erase its 18-tick cooldown");
        helper.assertTrue(close.getHealth() < biteHealth, "First bite deals damage only after its preparation");
        helper.assertTrue(dragon.tryBeam(), "After the animation, bite has not consumed the independent beam cooldown");
        float beamHealth = distant.getHealth();
        helper.assertFalse(dragon.tryBeam(), "A second immediate beam must be rejected");
        sameHealth(helper, beamHealth, distant.getHealth(), "rejected beam adds no damage");
        advance(helper, dragon, CelestialDragonEntity.BEAM_DURATION);
        helper.assertTrue(dragon.tryBite(), "An independently ready bite can activate after the beam animation");
        helper.assertFalse(dragon.tryBeam(), "Beam remains locked after its 36-tick animation");
        advance(helper, dragon, CelestialDragonEntity.BEAM_COOLDOWN - CelestialDragonEntity.BEAM_DURATION - 1);
        helper.assertFalse(dragon.tryBeam(), "Beam stays locked through tick 99 after its activation");
        advance(helper, dragon, 1);
        helper.assertTrue(dragon.tryBeam(), "Beam returns exactly at tick 100 after its activation");
        helper.succeed();
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
        var close = target(helper, new Vec3(16, 4, 20));
        var distant = target(helper, new Vec3(16, 4, 27));
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
            helper.assertTrue(dragon.biteTicks() == CelestialDragonEntity.BITE_DURATION && dragon.beamTicks() == 0,
                    "Authenticated owner starts one attack, without overlapping bite and beam");
            sameHealth(helper, 100, close.getHealth(), "authenticated bite still respects preparation");
            advance(helper, dragon, CelestialDragonEntity.BITE_HIT_DELAY);
            helper.assertTrue(close.getHealth() < 100, "Authenticated mounted owner can land the prepared bite");
            advance(helper, dragon, CelestialDragonEntity.BITE_DURATION - CelestialDragonEntity.BITE_HIT_DELAY);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COW_SPAWN_EGG));
            DragonControlNetworking.handle(owner, new DragonControlPayload(0, false, true));
            helper.assertTrue(dragon.beamTicks() == 0, "The server preserves offhand item use instead of firing a beam");
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            DragonControlNetworking.handle(owner, new DragonControlPayload(0, false, true));
            helper.assertTrue(dragon.beamTicks() == CelestialDragonEntity.BEAM_DURATION,
                    "Authenticated mounted owner can start a separate beam");
            advance(helper, dragon, CelestialDragonEntity.BEAM_CHARGE_TICKS - 1);
            sameHealth(helper, 100, distant.getHealth(), "authenticated beam cannot bypass charge");
            advance(helper, dragon, 1);
            helper.assertTrue(distant.getHealth() < 100, "Authenticated mounted owner's beam fires after charge");
            helper.succeed();
        } finally {
            removePlayer(helper, owner);
            removePlayer(helper, intruder);
        }
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void dyingDuringPreparationCancelsPendingAttacks(GameTestHelper helper) {
        var biter = dragon(helper);
        var close = target(helper, new Vec3(16, 4, 20));
        helper.assertTrue(biter.tryBite(), "Start a bite before the lethal hit");
        advance(helper, biter, CelestialDragonEntity.BITE_HIT_DELAY - 1);
        sameHealth(helper, 100, close.getHealth(), "Bite is still preparing before death");
        biter.setHealth(0);
        advance(helper, biter, 2);
        sameHealth(helper, 100, close.getHealth(), "Dead dragon cannot land its pending bite");
        helper.assertTrue(biter.biteTicks() == 0, "Death clears the bite animation");

        var beamer = dragon(helper);
        beamer.snapTo(helper.absoluteVec(new Vec3(32, 2, 16)));
        var distant = target(helper, new Vec3(32, 4, 28));
        helper.assertTrue(beamer.tryBeam(), "Start a beam charge before the lethal hit");
        advance(helper, beamer, CelestialDragonEntity.BEAM_CHARGE_TICKS - 1);
        sameHealth(helper, 100, distant.getHealth(), "Beam is still charging before death");
        beamer.setHealth(0);
        advance(helper, beamer, 2);
        sameHealth(helper, 100, distant.getHealth(), "Dead dragon cannot release its pending beam");
        helper.assertTrue(beamer.beamTicks() == 0, "Death clears the beam animation");
        helper.succeed();
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

    private static void advance(GameTestHelper helper, CelestialDragonEntity dragon, int ticks) {
        // Exercise the real entity ticker while keeping the isolated targets at their deliberate positions.
        for (int tick = 0; tick < ticks; tick++) helper.getLevel().tickNonPassenger(dragon);
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
