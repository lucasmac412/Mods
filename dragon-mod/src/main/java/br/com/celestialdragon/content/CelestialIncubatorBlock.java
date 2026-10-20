package br.com.celestialdragon.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** An egg is inserted with a right click; incubation proceeds only while its chunk ticks. */
public final class CelestialIncubatorBlock extends BaseEntityBlock {
    public static final MapCodec<CelestialIncubatorBlock> CODEC = simpleCodec(CelestialIncubatorBlock::new);
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

    public CelestialIncubatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OCCUPIED, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(OCCUPIED); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CelestialIncubatorBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, DragonContent.INCUBATOR_ENTITY, CelestialIncubatorBlockEntity::tick);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(DragonContent.EGG.asItem())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof CelestialIncubatorBlockEntity incubator)) return InteractionResult.PASS;
        if (incubator.hasEgg()) {
            showProgress(player, incubator);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && incubator.insertEgg(player.getUUID())) stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CelestialIncubatorBlockEntity incubator) {
            showProgress(player, incubator);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void showProgress(Player player, CelestialIncubatorBlockEntity incubator) {
        if (player.level().isClientSide()) return;
        String key = !incubator.hasEgg() ? "message.celestial_dragon.incubator.empty"
                : incubator.progress() >= CelestialIncubatorBlockEntity.INCUBATION_TICKS
                ? "message.celestial_dragon.incubator.blocked" : "message.celestial_dragon.incubator.progress";
        player.sendOverlayMessage(Component.translatable(key,
                incubator.progress() * 100 / CelestialIncubatorBlockEntity.INCUBATION_TICKS));
    }
}
