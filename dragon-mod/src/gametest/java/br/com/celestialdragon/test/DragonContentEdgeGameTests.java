package br.com.celestialdragon.test;

import br.com.celestialdragon.content.CelestialIncubatorBlockEntity;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

/** Integration checks for non-player removals and a hatch that must wait for safe space. */
public final class DragonContentEdgeGameTests {
    private static final String ARENA = "celestial_dragon_gametest:arena";

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void removingEggSupportReturnsExactlyOneEgg(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(20, 3, 20));
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(DragonContent.EGG.placeAt(level, base, 3), "Place a complete egg on its support");
        level.destroyBlock(base.below(), true);
        assertEggGone(helper, base);
        helper.assertTrue(eggDrops(helper) == 1, "Losing support must produce one collectible egg");
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void creativeBreakOfAnySectionProducesNoEgg(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos base = helper.absolutePos(new BlockPos(20, 3, 20));
        level.setBlock(base.below(), Blocks.BEDROCK.defaultBlockState(), 3);
        for (int part = 0; part < 3; part++) {
            helper.assertTrue(DragonContent.EGG.placeAt(level, base, 3), "Place a complete egg for each creative removal");
            BlockPos target = base.above(part);
            // These are the two removal hooks used by the creative block-breaking path.
            DragonContent.EGG.playerWillDestroy(level, target, level.getBlockState(target), player);
            level.destroyBlock(target, false, player);
            assertEggGone(helper, base);
            helper.assertTrue(eggDrops(helper) == 0, "Creative removal must suppress anchor loot, section=" + part);
        }
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void explosionDestroysWholeEggWithoutDuplicatingLoot(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(32, 3, 32));
        level.setBlock(base.below(), Blocks.BEDROCK.defaultBlockState(), 3);
        helper.assertTrue(DragonContent.EGG.placeAt(level, base, 3), "Place a complete egg in the blast");
        level.explode(null, base.getX() + 0.5D, base.getY() + 1.5D, base.getZ() + 0.5D,
                6.0F, Level.ExplosionInteraction.TNT);
        assertEggGone(helper, base);
        helper.assertTrue(eggDrops(helper) == 1, "An explosion that hits multiple sections must drop exactly one egg");
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 80, skyAccess = true)
    public void blockedHatchSurvivesReloadAndResumesExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(28, 3, 28));
        UUID owner = UUID.randomUUID();
        level.setBlock(pos, DragonContent.INCUBATOR.defaultBlockState(), 3);
        var incubator = (CelestialIncubatorBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(incubator != null && incubator.insertEgg(owner), "Insert the owned egg before blocking its exit");
        fillExit(helper, pos, true);
        for (int tick = 0; tick < CelestialIncubatorBlockEntity.INCUBATION_TICKS; tick++) {
            CelestialIncubatorBlockEntity.tick(level, pos, level.getBlockState(pos), incubator);
        }
        helper.assertTrue(incubator.hasEgg() && incubator.progress() == CelestialIncubatorBlockEntity.INCUBATION_TICKS,
                "A blocked completed egg must stay in the incubator");
        helper.assertTrue(ownedDragons(helper, owner) == 0, "A blocked incubator cannot spawn a dragon into a wall");
        var waiting = reload(helper, pos, incubator);
        helper.assertTrue(waiting.hasEgg() && owner.equals(waiting.ownerId()), "A waiting egg keeps its owner after reload");
        fillExit(helper, pos, false);
        // Let the real world ticker retry, rather than advancing the implementation's retry clock by hand.
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(!waiting.hasEgg() && ownedDragons(helper, owner) == 1,
                    "Clearing the exit lets the saved egg hatch once");
            reload(helper, pos, waiting);
            helper.runAfterDelay(21, () -> {
                helper.assertTrue(ownedDragons(helper, owner) == 1, "Reloading the empty incubator cannot hatch a second dragon");
                helper.succeed();
            });
        });
    }

    private static CelestialIncubatorBlockEntity reload(GameTestHelper helper, BlockPos pos,
                                                        CelestialIncubatorBlockEntity original) {
        var level = helper.getLevel();
        var saved = original.saveWithoutMetadata(level.registryAccess());
        var reloaded = new CelestialIncubatorBlockEntity(pos, level.getBlockState(pos));
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        level.setBlockEntity(reloaded);
        return reloaded;
    }

    private static void fillExit(GameTestHelper helper, BlockPos pos, boolean blocked) {
        var state = blocked ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (int x = -6; x <= 6; x++) for (int y = 1; y <= 8; y++) for (int z = -6; z <= 6; z++) {
            helper.getLevel().setBlock(pos.offset(x, y, z), state, 2);
        }
    }

    private static int ownedDragons(GameTestHelper helper, UUID owner) {
        return helper.getLevel().getEntitiesOfClass(CelestialDragonEntity.class, helper.getBounds(),
                dragon -> owner.equals(dragon.getOwnerId())).size();
    }

    private static int eggDrops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                item -> item.getItem().is(DragonContent.EGG.asItem())).stream()
                .mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static void assertEggGone(GameTestHelper helper, BlockPos base) {
        for (int part = 0; part < 3; part++) {
            helper.assertTrue(helper.getLevel().getBlockState(base.above(part)).isAir(),
                    "Removing one section must also remove egg section " + part);
        }
    }
}
