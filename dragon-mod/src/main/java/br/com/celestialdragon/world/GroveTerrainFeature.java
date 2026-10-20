package br.com.celestialdragon.world;

import br.com.celestialdragon.content.DragonContent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Runs before structures and ores, and writes only the current decoration chunk. */
public final class GroveTerrainFeature extends Feature<NoneFeatureConfiguration> {
    public GroveTerrainFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin().getX() >> 4, context.origin().getZ() >> 4);
        Map<BlockPos, Integer> centerHeights = new HashMap<>();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                BlockPos probe = new BlockPos(x, 90, z);
                if (!level.getBiome(probe).is(DragonWorld.CELESTIAL_GROVE)) continue;
                BlockPos center = GroveLayout.nearestCenter(x, z);
                double distance = Math.sqrt((double)(x - center.getX()) * (x - center.getX())
                        + (double)(z - center.getZ()) * (z - center.getZ()));
                int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                int waterTop = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int centerHeight = centerHeights.computeIfAbsent(center, c -> Math.clamp(
                        context.chunkGenerator().getBaseHeight(c.getX(), c.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                                level, level.getLevel().getChunkSource().randomState()) - 1, 72,
                        level.getMaxY() - NestGenerator.HEIGHT - 1));
                double blend = Math.clamp((distance - 30.0) / 44.0, 0.0, 1.0);
                int raised = Math.max(surface, 63 + (int)(9 * (1.0 - distance / GroveLayout.RADIUS)));
                int top = (int)Math.round(centerHeight * (1.0 - blend) + raised * blend);
                // A smooth island or grove, with a level clearing only around the giant nest.
                for (int y = Math.min(surface - 3, top - 4); y <= Math.max(top, waterTop); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (y > top) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    else if (y >= top - 2) level.setBlock(pos, DragonContent.CELESTIAL_SOIL.defaultBlockState(), 2);
                    else level.setBlock(pos, DragonContent.CELESTIAL_STONE.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }
}
