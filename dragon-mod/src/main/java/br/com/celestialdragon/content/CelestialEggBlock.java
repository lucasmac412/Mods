package br.com.celestialdragon.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Three real selectable blocks, one collectible egg. Only the anchor has loot. */
public final class CelestialEggBlock extends Block {
    public static final MapCodec<CelestialEggBlock> CODEC = simpleCodec(CelestialEggBlock::new);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    private static final VoxelShape[] SHAPES = {
            box(-6, 0, -6, 22, 16, 22), box(-8, 0, -8, 24, 16, 24), box(-4, 0, -4, 20, 16, 20)
    };

    public CelestialEggBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(PART)];
    }

    /** Checks the full visible footprint, including the half blocks extending into neighbours. */
    public boolean canPlaceAt(LevelReader level, BlockPos base) {
        if (base.getY() < level.getMinY() || base.getY() + 2 > level.getMaxY()) return false;
        if (!supported(level, base)) return false;
        for (int y = 0; y < 3; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = base.offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) return false;
                }
            }
        }
        return true;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return canPlaceAt(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }

    /** Structure placement also uses this so generated eggs follow exactly the player rules. */
    public boolean placeAt(LevelAccessor level, BlockPos base, int flags) {
        if (!canPlaceAt(level, base)) return false;
        for (int part = 0; part < 3; part++) {
            level.setBlock(base.above(part), defaultBlockState().setValue(PART, part),
                    (flags | UPDATE_KNOWN_SHAPE | UPDATE_CLIENTS) & ~UPDATE_NEIGHBORS);
        }
        return true;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        for (int part = 1; part < 3; part++) {
            level.setBlock(pos.above(part), defaultBlockState().setValue(PART, part), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    private boolean isPart(BlockState state, int part) {
        return state.is(this) && state.getValue(PART) == part;
    }

    private static boolean supported(LevelReader level, BlockPos base) {
        BlockState below = level.getBlockState(base.below());
        return !below.isAir() && below.getFluidState().isEmpty()
                && !below.getCollisionShape(level, base.below()).isEmpty();
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                               BlockPos pos, Direction direction, BlockPos neighbourPos,
                                               BlockState neighbour, RandomSource random) {
        int part = state.getValue(PART);
        // Adjacent links propagate removal toward the single loot-bearing anchor.
        if ((part > 0 && !isPart(level.getBlockState(pos.below()), part - 1))
                || (part < 2 && !isPart(level.getBlockState(pos.above()), part + 1))
                || (part == 0 && !supported(level, pos))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockPos base = pos.below(state.getValue(PART));
            for (int part = 0; part < 3; part++) {
                BlockPos p = base.above(part);
                if (!p.equals(pos) && isPart(level.getBlockState(p), part)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
