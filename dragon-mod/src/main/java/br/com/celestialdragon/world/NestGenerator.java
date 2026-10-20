package br.com.celestialdragon.world;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.content.CelestialEggBlock;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Procedural 41 by 41 fantasy bowl. Structure generation always clips to its current chunk. */
public final class NestGenerator {
    public static final int RADIUS = 20;
    public static final int HEIGHT = 18;
    private NestGenerator() { }

    /** Explicit command/test entry point. The supplied position is the bottom center of the nest. */
    public static boolean generate(ServerLevel level, BlockPos center) {
        build(level, center, bounds(center));
        return placeInhabitants(level, center);
    }

    public static BoundingBox bounds(BlockPos center) {
        return new BoundingBox(center.getX() - RADIUS, center.getY() - 2, center.getZ() - RADIUS,
                center.getX() + RADIUS, center.getY() + HEIGHT, center.getZ() + RADIUS);
    }

    static void build(WorldGenLevel level, BlockPos center, BoundingBox clip) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            double radius = Math.sqrt(dx * dx / 400.0 + dz * dz / 289.0);
            double angle = Math.atan2(dz / 17.0, dx / 20.0);
            int floor = 1 + (int)(6 * radius * radius);
            boolean rib = Math.abs(Math.sin(angle * 6)) < 0.12;
            boolean branch = Math.abs(Math.sin(angle * 9 + radius * 5)) < 0.20;
            for (int dy = -2; dy <= HEIGHT; dy++) {
                BlockState state = null;
                if (radius <= 1) {
                    if (dy <= 0) state = DragonContent.CELESTIAL_STONE.defaultBlockState();
                    else if (dy < floor) state = DragonContent.CELESTIAL_LOG.defaultBlockState();
                    else if (dy == floor) state = DragonContent.NEST_BLOCK.defaultBlockState();
                    else state = Blocks.AIR.defaultBlockState();
                    if (rib && radius > 0.38 && dy >= floor && dy <= floor + 1) state = Blocks.GOLD_BLOCK.defaultBlockState();
                    if (radius > 0.88 && dy >= floor && dy <= floor + 2) state = DragonContent.CELESTIAL_LOG.defaultBlockState();
                    if (radius > 0.94 && dy == floor + 3 && branch) state = DragonContent.NEST_BLOCK.defaultBlockState();
                    if (radius > 0.93 && dy == floor + 2 && rib) state = Blocks.GOLD_BLOCK.defaultBlockState();
                } else if (radius <= 1.13 && branch && dy >= 4 && dy <= 7 + (int)(3 * Math.sin(angle * 9))) {
                    state = DragonContent.CELESTIAL_LOG.defaultBlockState();
                }
                // Black-and-gold pillars curl around the outside of the bowl like talons.
                if (radius > 0.78 && radius < 1.04 && rib && dy > floor + 2 && dy <= floor + 6) {
                    state = dy == floor + 6 ? Blocks.GOLD_BLOCK.defaultBlockState() : DragonContent.NEST_BLOCK.defaultBlockState();
                }
                if (dx == 0 && dz == 0 && dy == 1) state = Blocks.GOLD_BLOCK.defaultBlockState();
                if (state != null) {
                    BlockPos target = center.offset(dx, dy, dz);
                    if (clip.isInside(target)) {
                        // A resumed piece may already contain its three-part egg.
                        boolean existingEgg = dx == 0 && dz == 0 && dy >= 2 && dy <= 4
                                && level.getBlockState(target).is(DragonContent.EGG);
                        if (!existingEgg) level.setBlock(target, state, 2);
                    }
                }
            }
        }
    }

    static boolean placeInhabitants(WorldGenLevel level, BlockPos center) {
        BlockPos egg = center.above(2);
        boolean completeEgg = true;
        for (int part = 0; part < 3; part++) {
            BlockState state = level.getBlockState(egg.above(part));
            if (!state.is(DragonContent.EGG) || state.getValue(CelestialEggBlock.PART) != part) completeEgg = false;
        }
        if (!completeEgg && !DragonContent.EGG.placeAt(level, egg, 2)) return false;
        UUID id = UUID.nameUUIDFromBytes(("celestial_dragon:nest:" + level.getSeed() + ":" + center.asLong()).getBytes(StandardCharsets.UTF_8));
        if (level.getLevel().getEntity(id) != null) return true;
        CelestialDragonEntity dragon = CelestialDragonMod.DRAGON.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (dragon == null) return false;
        dragon.setUUID(id);
        dragon.setNest(egg);
        dragon.setPersistenceRequired();
        dragon.snapTo(center.getX() + 5.5, center.getY() + 2.0, center.getZ() + 3.5, 190, 0);
        level.addFreshEntityWithPassengers(dragon);
        return true;
    }
}
