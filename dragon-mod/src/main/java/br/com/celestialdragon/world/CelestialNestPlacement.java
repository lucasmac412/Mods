package br.com.celestialdragon.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** Original rare layout, shared by biome sampling and nest placement. */
public final class CelestialNestPlacement extends RandomSpreadStructurePlacement {
    // 96 chunks = 1536 blocks per region; restores the density from version 0.1.0.
    public static final int SPACING = 96;
    public static final int SEPARATION = 48;
    public static final int SALT = 87542109;
    public static final MapCodec<CelestialNestPlacement> CODEC = MapCodec.unit(CelestialNestPlacement::new);

    public CelestialNestPlacement() { super(SPACING, SEPARATION, RandomSpreadType.LINEAR, SALT); }
    @Override public ChunkPos getPotentialStructureChunk(long seed, int x, int z) {
        return super.getPotentialStructureChunk(0L, x, z);
    }
    @Override public StructurePlacementType<?> type() { return DragonWorld.NEST_PLACEMENT; }
}
