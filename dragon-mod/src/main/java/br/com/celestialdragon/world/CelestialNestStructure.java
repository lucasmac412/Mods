package br.com.celestialdragon.world;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class CelestialNestStructure extends Structure {
    public static final MapCodec<CelestialNestStructure> CODEC = simpleCodec(CelestialNestStructure::new);
    public CelestialNestStructure(StructureSettings settings) { super(settings); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        int y = Math.clamp(context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState()), 73,
                context.heightAccessor().getMaxY() - NestGenerator.HEIGHT);
        BlockPos center = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new NestPiece(center))));
    }
    @Override public StructureType<?> type() { return DragonWorld.NEST_TYPE; }
}
