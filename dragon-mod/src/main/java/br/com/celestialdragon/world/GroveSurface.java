package br.com.celestialdragon.world;

import br.com.celestialdragon.content.DragonContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;

/** Finds actual exposed grove ground after its terrain feature has raised or lowered a column. */
final class GroveSurface {
    private GroveSurface() { }

    static BlockPos findExposedSoil(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        // The *_WG heightmaps can still describe pre-decoration terrain at this stage.
        for (int y = level.getMaxY() - 2; y >= level.getMinY(); y--) {
            if (!level.getBlockState(probe.set(x, y, z)).is(DragonContent.CELESTIAL_SOIL)) continue;
            BlockPos base = new BlockPos(x, y + 1, z);
            return level.getBlockState(base).isAir() ? base : null;
        }
        return null;
    }
}
