package br.com.celestialdragon.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A broad woven, non-full-height surface for the enormous eggs. */
public final class CelestialNestBlock extends Block {
    public static final MapCodec<CelestialNestBlock> CODEC = simpleCodec(CelestialNestBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 6, 16);

    public CelestialNestBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
