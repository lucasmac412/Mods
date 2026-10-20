package br.com.celestialdragon.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Shared spatial layout used by biome sampling and structure placement. */
public final class GroveLayout {
    public static final int RADIUS = 192;
    private static final CelestialNestPlacement PLACEMENT = new CelestialNestPlacement();
    private static final ConcurrentMap<Long, BlockPos> CENTERS = new ConcurrentHashMap<>();
    private GroveLayout() { }

    public static BlockPos nearestCenter(int x, int z) {
        int chunkX = Math.floorDiv(x, 16), chunkZ = Math.floorDiv(z, 16);
        int regionX = Math.floorDiv(chunkX, CelestialNestPlacement.SPACING);
        int regionZ = Math.floorDiv(chunkZ, CelestialNestPlacement.SPACING);
        BlockPos best = BlockPos.ZERO;
        long distance = Long.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos candidate = centerForRegion(regionX + dx, regionZ + dz);
            int cx = candidate.getX(), cz = candidate.getZ();
            long d = (long)(x - cx) * (x - cx) + (long)(z - cz) * (z - cz);
            if (d < distance) { distance = d; best = candidate; }
        }
        return best;
    }

    private static BlockPos centerForRegion(int regionX, int regionZ) {
        long key = ((long) regionX << 32) ^ (regionZ & 0xffffffffL);
        // Cache entries are immutable and depend only on coordinates, so clearing is safe
        // even while other generation workers are sampling the same regions.
        if (CENTERS.size() >= 4096) CENTERS.clear();
        return CENTERS.computeIfAbsent(key, ignored -> {
            ChunkPos chunk = PLACEMENT.getPotentialStructureChunk(0,
                    regionX * CelestialNestPlacement.SPACING, regionZ * CelestialNestPlacement.SPACING);
            return new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        });
    }

    public static boolean contains(int x, int z) { return distanceSquared(x, z) <= RADIUS * RADIUS; }
    public static long distanceSquared(int x, int z) {
        BlockPos center = nearestCenter(x, z);
        return (long)(x - center.getX()) * (x - center.getX())
                + (long)(z - center.getZ()) * (z - center.getZ());
    }
}
