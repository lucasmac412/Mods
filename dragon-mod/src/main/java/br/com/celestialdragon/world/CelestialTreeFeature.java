package br.com.celestialdragon.world;

import br.com.celestialdragon.content.DragonContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class CelestialTreeFeature extends Feature<NoneFeatureConfiguration> {
    public CelestialTreeFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin().getX() >> 4, context.origin().getZ() >> 4);
        for (int attempt = 0; attempt < 2; attempt++) {
            int x = chunk.getMinBlockX() + 4 + context.random().nextInt(8);
            int z = chunk.getMinBlockZ() + 4 + context.random().nextInt(8);
            if (GroveLayout.distanceSquared(x, z) < 34 * 34) continue;
            BlockPos base = GroveSurface.findExposedSoil(level, x, z);
            if (base == null || !level.getBiome(base).is(DragonWorld.CELESTIAL_GROVE)) continue;
            int height = 7 + context.random().nextInt(5);
            BlockState leaves = DragonContent.CELESTIAL_LEAVES.defaultBlockState();
            if (leaves.hasProperty(LeavesBlock.PERSISTENT)) leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
            for (int dy = height - 4; dy <= height + 1; dy++) {
                int radius = dy >= height ? 2 : 4;
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, 2);
                }
            }
            for (int dy = 0; dy < height; dy++) level.setBlock(base.above(dy), DragonContent.CELESTIAL_LOG.defaultBlockState(), 2);
            // Four ascending gold branches support an irregular, broad canopy.
            for (int d = 1; d <= 3; d++) {
                level.setBlock(base.offset(d, height - 4 + d / 2, 0), DragonContent.CELESTIAL_LOG.defaultBlockState(), 2);
                level.setBlock(base.offset(-d, height - 4 + d / 2, 0), DragonContent.CELESTIAL_LOG.defaultBlockState(), 2);
                level.setBlock(base.offset(0, height - 4 + d / 2, d), DragonContent.CELESTIAL_LOG.defaultBlockState(), 2);
                level.setBlock(base.offset(0, height - 4 + d / 2, -d), DragonContent.CELESTIAL_LOG.defaultBlockState(), 2);
            }
        }
        return true;
    }
}
