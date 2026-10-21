package br.com.celestialdragon.content;

import br.com.celestialdragon.CelestialDragonMod;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** Creative-only testing eggs. The server assigns the player owner before publishing the entity. */
public final class DragonSpawnEggItem extends SpawnEggItem {
    private final boolean tamed;

    public DragonSpawnEggItem(boolean tamed, Properties properties) {
        super(properties);
        this.tamed = tamed;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            if (player != null && !level.isClientSide())
                player.sendOverlayMessage(Component.translatable("message.celestial_dragon.spawn_egg.creative_only"));
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (!level.mayInteract(player, clicked) || !player.mayUseItemAt(clicked, context.getClickedFace(), stack))
            return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockPos spawn = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        var dragon = CelestialDragonMod.DRAGON.create(server, EntitySpawnReason.SPAWN_ITEM_USE);
        if (dragon == null) return InteractionResult.FAIL;
        dragon.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0);
        AABB bounds = dragon.getBoundingBox();
        if (bounds.minY < server.getMinY() || bounds.maxY > server.getMaxY() + 1
                || !server.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                || !server.getWorldBorder().isWithinBounds(bounds) || server.containsAnyLiquid(bounds)
                || !server.noCollision(dragon, bounds)) {
            player.sendOverlayMessage(Component.translatable("message.celestial_dragon.spawn_egg.blocked"));
            return InteractionResult.FAIL;
        }
        dragon.setNest(spawn);
        if (tamed) dragon.tame(player.getUUID());
        // Preserve a player-given display name without accepting an owner/entity tag from the item.
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) dragon.setCustomName(name);
        if (!server.addFreshEntity(dragon)) return InteractionResult.FAIL;
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        server.gameEvent(player, GameEvent.ENTITY_PLACE, spawn);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Vanilla's fluid-use path spawns through EntityType directly, bypassing ownership.
        // These large test dragons are placed with useOn on a block in an open, dry area.
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                               Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(tamed ? "item.celestial_dragon.tamed_dragon_spawn_egg.desc"
                : "item.celestial_dragon.wild_dragon_spawn_egg.desc").withStyle(tamed ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("message.celestial_dragon.spawn_egg.creative_only").withStyle(ChatFormatting.GRAY));
    }
}
