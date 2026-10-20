package br.com.celestialdragon.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class NestPiece extends StructurePiece {
    private final BlockPos center;
    private boolean inhabitantsPlaced;

    public NestPiece(BlockPos center) {
        super(DragonWorld.NEST_PIECE, 0, NestGenerator.bounds(center));
        this.center = center.immutable();
    }
    public NestPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(DragonWorld.NEST_PIECE, tag);
        center = new BlockPos(tag.getIntOr("CenterX", 0), tag.getIntOr("CenterY", 73), tag.getIntOr("CenterZ", 0));
        inhabitantsPlaced = tag.getBooleanOr("InhabitantsPlaced", false);
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CenterX", center.getX());
        tag.putInt("CenterY", center.getY());
        tag.putInt("CenterZ", center.getZ());
        tag.putBoolean("InhabitantsPlaced", inhabitantsPlaced);
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        NestGenerator.build(level, center, clip);
        if (!inhabitantsPlaced && clip.isInside(center.above(2))) {
            inhabitantsPlaced = NestGenerator.placeInhabitants(level, center);
        }
    }
}
