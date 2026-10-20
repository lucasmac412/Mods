package br.com.celestialdragon.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** Fixed regions keep biome and nest aligned; this development build makes groves abundant. */
public final class CelestialNestPlacement extends RandomSpreadStructurePlacement {
    // Temporary testing density: 256x256 blocks per nest, instead of the original 1536x1536.
    public static final int SPACING = 16;
    public static final int SEPARATION = 8;
    public static final int SALT = 87542109;
    public static final MapCodec<CelestialNestPlacement> CODEC = MapCodec.unit(CelestialNestPlacement::new);

    public CelestialNestPlacement() { super(SPACING, SEPARATION, RandomSpreadType.LINEAR, SALT); }
    @Override public ChunkPos getPotentialStructureChunk(long seed, int x, int z) {
        return super.getPotentialStructureChunk(0L, x, z);
    }
    @Override public StructurePlacementType<?> type() { return DragonWorld.NEST_PLACEMENT; }
}
