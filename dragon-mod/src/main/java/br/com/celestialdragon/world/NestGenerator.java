package br.com.celestialdragon.world;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.content.CelestialEggBlock;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** A 113-block circular boss arena, eight towers, runic rings and a raised sleeping sanctuary. */
public final class NestGenerator {
    public static final int RADIUS = 56;
    public static final int HEIGHT = 36;
    private NestGenerator() { }
    public static boolean generate(ServerLevel level, BlockPos center) {
        build(level, center, bounds(center));
        return placeInhabitants(level, center);
    }
    public static BoundingBox bounds(BlockPos center) {
        return new BoundingBox(center.getX() - RADIUS, center.getY() - 4, center.getZ() - RADIUS,
                center.getX() + RADIUS, center.getY() + HEIGHT, center.getZ() + RADIUS);
    }
    static void build(WorldGenLevel level, BlockPos center, BoundingBox clip) {
        int minX = Math.max(-RADIUS, clip.minX() - center.getX()), maxX = Math.min(RADIUS, clip.maxX() - center.getX());
        int minZ = Math.max(-RADIUS, clip.minZ() - center.getZ()), maxZ = Math.min(RADIUS, clip.maxZ() - center.getZ());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            double radius = Math.sqrt(x * x + z * z);
            if (radius > RADIUS) continue;
            double bed = Math.sqrt(x * x + (z + 24) * (z + 24));
            double angle = Math.atan2(z, x);
            boolean runeRay = Math.abs(Math.sin(angle * 8)) < 0.035 && radius > 15;
            int towerDistance = 1000;
            for (int tower = 0; tower < 8; tower++) {
                int tx = (int)Math.round(Math.cos(tower * Math.PI / 4) * 48);
                int tz = (int)Math.round(Math.sin(tower * Math.PI / 4) * 48);
                towerDistance = Math.min(towerDistance, Math.max(Math.abs(x-tx), Math.abs(z-tz)));
            }
            for (int y = -4; y <= HEIGHT; y++) {
                BlockState state = Blocks.AIR.defaultBlockState();
                if (y <= 0) state = DragonContent.CELESTIAL_STONE.defaultBlockState();
                if (y == 1) {
                    state = DragonContent.CELESTIAL_BRICKS.defaultBlockState();
                    if ((radius > 17 && radius < 19) || (radius > 34 && radius < 36) || runeRay)
                        state = DragonContent.RUNESTONE.defaultBlockState();
                    if (radius > 48) state = DragonContent.CELESTIAL_OBSIDIAN.defaultBlockState();
                    if (Math.abs(x) < 3 && z > 0) state = DragonContent.RUNESTONE.defaultBlockState();
                }
                // A broad stepped stone bed and nest for the coiled guardian.
                if (bed < 14 && y >= 1 && y <= 5) {
                    if (bed < 14 - Math.max(0,y - 2)) state = (y == 5 ? DragonContent.NEST_BLOCK : DragonContent.CELESTIAL_OBSIDIAN).defaultBlockState();
                }
                boolean entrance = z > 45 && Math.abs(x) < 7;
                if (radius > 52 && y > 1 && y <= 7 && !entrance)
                    state = (y == 3 || y == 7 ? DragonContent.ARENA_PILLAR : DragonContent.CELESTIAL_OBSIDIAN).defaultBlockState();
                if (radius > 54 && y > 7 && y <= 10 && !entrance && ((x + z) & 3) < 2)
                    state = DragonContent.ARENA_PILLAR.defaultBlockState();
                // Eight watchtowers capped by bright celestial lanterns.
                if (towerDistance <= 3 && y > 1 && y <= 23) {
                    if (towerDistance >= 2 || y < 5 || y > 20) state = (y % 6 == 0 ? DragonContent.RUNESTONE : DragonContent.ARENA_PILLAR).defaultBlockState();
                    else state = Blocks.AIR.defaultBlockState();
                }
                if (towerDistance <= 1 && y > 23 && y <= 26) state = (towerDistance == 0 && y == 25 ? DragonContent.ARENA_LANTERN : DragonContent.ARENA_PILLAR).defaultBlockState();
                // Four sanctuary columns and a crown above the sleeping platform.
                if ((Math.abs(x) >= 10 && Math.abs(x) <= 12) && (Math.abs(z + 24) >= 9 && Math.abs(z + 24) <= 11)
                        && y > 4 && y <= 20) state = (y == 20 && Math.abs(x) == 11 && Math.abs(z + 24) == 10 ? DragonContent.ARENA_LANTERN : DragonContent.ARENA_PILLAR).defaultBlockState();
                if (Math.abs(x) <= 12 && z >= -36 && z <= -12 && y == 21 && (Math.abs(x) >= 10 || z <= -34 || z >= -14))
                    state = DragonContent.RUNESTONE.defaultBlockState();
                if (x == 0 && z == 0 && y == 1) state = DragonContent.RUNESTONE.defaultBlockState();
                BlockPos target = center.offset(x,y,z);
                if (clip.isInside(target)) {
                    boolean existingEgg = x == 0 && z == 0 && y >= 2 && y <= 4 && level.getBlockState(target).is(DragonContent.EGG);
                    if (!existingEgg && !level.getBlockState(target).equals(state)) level.setBlock(target,state,2);
                }
            }
        }
    }
    static boolean placeInhabitants(WorldGenLevel level, BlockPos center) {
        BlockPos egg = center.above(2);
        boolean complete = true;
        for (int part = 0; part < 3; part++) {
            BlockState state = level.getBlockState(egg.above(part));
            if (!state.is(DragonContent.EGG) || state.getValue(CelestialEggBlock.PART) != part) complete = false;
        }
        if (!complete && !DragonContent.EGG.placeAt(level,egg,2)) return false;
        UUID id = UUID.nameUUIDFromBytes(("celestial_dragon:nest:" + level.getSeed() + ":" + center.asLong()).getBytes(StandardCharsets.UTF_8));
        if (level.getLevel().getEntity(id) != null) return true;
        var dragon = CelestialDragonMod.DRAGON.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (dragon == null) return false;
        dragon.setUUID(id);
        dragon.setNest(egg);
        dragon.snapTo(center.getX() + 0.5, center.getY() + 5.0, center.getZ() - 24.5, 0, 0);
        dragon.restAtNest();
        level.addFreshEntityWithPassengers(dragon);
        return true;
    }
}
