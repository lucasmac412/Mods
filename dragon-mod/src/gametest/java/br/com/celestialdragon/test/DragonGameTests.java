package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.content.CelestialIncubatorBlockEntity;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.world.DragonWorld;
import br.com.celestialdragon.world.GroveLayout;
import br.com.celestialdragon.world.NestGenerator;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

public final class DragonGameTests {
    private static final String ARENA = "celestial_dragon_gametest:arena";

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void breakingEveryEggSectionDropsExactlyOneCollectibleEgg(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(12, 2, 12));
        level.setBlock(base.below(), Blocks.BEDROCK.defaultBlockState(), 3);
        for (int part = 0; part < 3; part++) {
            helper.assertTrue(DragonContent.EGG.placeAt(level, base, 3), "A new complete egg must be placeable");
            helper.assertTrue(!DragonContent.EGG.canPlaceAt(level, base), "An occupied egg cannot be placed twice");
            level.destroyBlock(base.above(part), true);
            for (int section = 0; section < 3; section++)
                helper.assertTrue(level.getBlockState(base.above(section)).isAir(), "Breaking any section removes the complete egg");
            var drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds(), item -> item.getItem().is(DragonContent.EGG.asItem()));
            helper.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1,
                    "Each complete egg must produce one item, including upper/middle breaks; section=" + part);
            drops.forEach(ItemEntity::discard);
        }
        level.setBlock(base.offset(1, 1, 0), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(DragonContent.EGG.canPlaceAt(level, base), "The wide footprint must not overlap a neighbouring block");
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void incubatorPreservesOwnerAndProgressAndHatchesOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(20, 2, 20));
        level.setBlock(pos, DragonContent.INCUBATOR.defaultBlockState(), 3);
        var incubator = (CelestialIncubatorBlockEntity)level.getBlockEntity(pos);
        UUID owner = UUID.randomUUID();
        helper.assertTrue(incubator != null && incubator.insertEgg(owner), "An empty incubator accepts one egg");
        helper.assertFalse(incubator.insertEgg(UUID.randomUUID()), "A second egg cannot replace the first owner");
        for (int i = 0; i < 137; i++) CelestialIncubatorBlockEntity.tick(level, pos, level.getBlockState(pos), incubator);
        var saved = incubator.saveWithoutMetadata(level.registryAccess());
        var reloaded = new CelestialIncubatorBlockEntity(pos, level.getBlockState(pos));
        reloaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertTrue(reloaded.hasEgg() && reloaded.progress() == 137 && owner.equals(reloaded.ownerId()),
                "Egg, owner and progress must survive serialization");
        // Continue the actual installed entity after verifying its serialized state.
        for (int i = 137; i < CelestialIncubatorBlockEntity.INCUBATION_TICKS; i++)
            CelestialIncubatorBlockEntity.tick(level, pos, level.getBlockState(pos), incubator);
        var dragons = level.getEntitiesOfClass(CelestialDragonEntity.class, helper.getBounds(), d -> owner.equals(d.getOwnerId()));
        helper.assertTrue(dragons.size() == 1 && dragons.getFirst().isTamed(), "The egg must hatch into exactly one owned dragon");
        helper.assertFalse(incubator.hasEgg(), "A successful hatch consumes its stored egg");
        for (int i = 0; i < 40; i++) CelestialIncubatorBlockEntity.tick(level, pos, level.getBlockState(pos), incubator);
        helper.assertTrue(level.getEntitiesOfClass(CelestialDragonEntity.class, helper.getBounds(), d -> owner.equals(d.getOwnerId())).size() == 1,
                "Further ticks cannot duplicate a hatchling");
        dragons.forEach(CelestialDragonEntity::discard);
        level.destroyBlock(pos, false);
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void breakingOccupiedIncubatorReturnsEggAndBlockOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(14, 2, 14));
        level.setBlock(pos, DragonContent.INCUBATOR.defaultBlockState(), 3);
        var incubator = (CelestialIncubatorBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(incubator != null && incubator.insertEgg(UUID.randomUUID()), "Insert egg before breaking");
        level.destroyBlock(pos, true);
        level.destroyBlock(pos, true);
        int eggs = 0, blocks = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds())) {
            if (item.getItem().is(DragonContent.EGG.asItem())) eggs += item.getItem().getCount();
            if (item.getItem().is(DragonContent.INCUBATOR.asItem())) blocks += item.getItem().getCount();
        }
        helper.assertTrue(eggs == 1 && blocks == 1, "Breaking an occupied incubator returns exactly its egg and one incubator");
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 80, skyAccess = true)
    public void giantNestContainsWideEggAndOneWildDragon(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(32, 3, 32));
        helper.assertTrue(NestGenerator.generate(level, center), "The giant nest must generate successfully");
        for (int y = 2; y <= 4; y++) helper.assertTrue(level.getBlockState(center.above(y)).is(DragonContent.EGG), "The nest contains the complete three-block egg");
        var dragons = level.getEntitiesOfClass(CelestialDragonEntity.class, helper.getBounds());
        helper.assertTrue(dragons.size() == 1 && !dragons.getFirst().isTamed(), "A nest starts with one wild guardian");
        helper.assertTrue(NestGenerator.generate(level, center), "Reprocessing the same nest must retain its original inhabitants");
        helper.assertTrue(level.getEntitiesOfClass(CelestialDragonEntity.class, helper.getBounds()).size() == 1, "Reprocessing cannot duplicate the wild guardian");
        int special = 0;
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++)
            if (level.getBlockState(center.offset(x, 0, z)).is(DragonContent.CELESTIAL_STONE)) special++;
        helper.assertTrue(special > 800, "Nest footprint must be giant, using hundreds of custom foundation blocks");
        dragons.forEach(CelestialDragonEntity::discard);
        helper.succeed();
    }

    @GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
    public void biomeOverlayAndStructureRegistryRemainOverworldOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        var presets = level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworld = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var nether = MultiNoiseBiomeSource.createFromPreset(presets.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
        BlockPos center = GroveLayout.nearestCenter(0, 0);
        var sampler = level.getChunkSource().randomState().sampler();
        helper.assertTrue(overworld.getNoiseBiome(center.getX() >> 2, 20, center.getZ() >> 2, sampler).is(DragonWorld.CELESTIAL_GROVE),
                "The celestial biome must occur naturally at the shared nest center");
        helper.assertTrue(overworld.possibleBiomes().stream().anyMatch(b -> b.is(DragonWorld.CELESTIAL_GROVE)),
                "The biome must be discoverable by locate and feature sorting");
        helper.assertFalse(nether.possibleBiomes().stream().anyMatch(b -> b.is(DragonWorld.CELESTIAL_GROVE)), "Nether biome source remains unchanged");
        helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(DragonWorld.DRAGON_NEST).isPresent(), "The nest is registered as a locatable structure");
        helper.assertTrue(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, CelestialDragonMod.id("celestial_incubator"))).isPresent(),
                "The survival incubator crafting recipe must load");
        // Exercise actual biome sampling: the old rare layout covers only about 5% of this grid.
        int celestialSamples = 0;
        int totalSamples = 0;
        for (int x = -4096; x < 4096; x += 32) {
            for (int z = -4096; z < 4096; z += 32) {
                totalSamples++;
                if (overworld.getNoiseBiome(x >> 2, 20, z >> 2, sampler).is(DragonWorld.CELESTIAL_GROVE)) celestialSamples++;
            }
        }
        double coverage = (double)celestialSamples / totalSamples;
        helper.assertTrue(coverage > 0.98, "Testing worlds must make the celestial biome extremely common; coverage=" + coverage);
        System.out.println("Celestial testing coverage: " + celestialSamples + "/" + totalSamples + " = " + coverage);
        helper.succeed();
    }
}
