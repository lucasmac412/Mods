package br.com.celestialdragon.world;

import br.com.celestialdragon.content.DragonContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class CelestialRockFeature extends Feature<NoneFeatureConfiguration> {
    public CelestialRockFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (context.random().nextInt(3) != 0) return false;
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin().getX() >> 4, context.origin().getZ() >> 4);
        int x = chunk.getMinBlockX() + 5 + context.random().nextInt(6);
        int z = chunk.getMinBlockZ() + 5 + context.random().nextInt(6);
        BlockPos base = GroveSurface.findExposedSoil(level, x, z);
        if (base == null || !level.getBiome(base).is(DragonWorld.CELESTIAL_GROVE)
                || GroveLayout.distanceSquared(x, z) < 32 * 32) return false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 3; dy++) {
            if (dx * dx + dz * dz + dy * dy * 2 <= 8) {
                level.setBlock(base.offset(dx, dy, dz), DragonContent.CELESTIAL_STONE.defaultBlockState(), 2);
            }
        }
        return true;
    }
}
