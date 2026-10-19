package br.com.luma.entity;

import br.com.luma.ai.Action;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A server-controlled companion with bounded, survival-style tasks.
 * No language-model response is executable code: only Action values reach this class.
 */
public final class CompanionEntity extends PathfinderMob {
    private static final int WORK_INTERVAL = 10;
    private static final int MAX_MINED_BLOCKS = 16;
    private static final int AUTO_LOG_BUDGET = 8;
    private static final double TASK_RADIUS_SQUARED = 12.0 * 12.0;
    private static final double REACH_SQUARED = 4.5 * 4.5;
    private static final int MAX_STALLED_TICKS = 400;
    private static final Direction[] HORIZONTAL = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final NonNullList<ItemStack> supplies = NonNullList.withSize(27, ItemStack.EMPTY);
    private UUID ownerId;
    private Action mode = Action.FOLLOW;
    private Consumer<String> speech;
    private int minedBlocks;
    private int stalledTicks;
    private BlockPos miningTarget;
    private int miningTicks;
    private BlockPos buildOrigin;
    private int buildCursor;

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setCustomName(Component.literal("Luma"));
        setCustomNameVisible(true);
        getNavigation().setCanFloat(true);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.LAVA, -1.0F);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.ON_TOP_OF_POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.FIRE, -1.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);
        setPathfindingMalus(PathType.DAMAGING, -1.0F);
        setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, -1.0F);
        setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    public void setOwner(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public boolean isOwner(Player player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    public void setSpeech(Consumer<String> speech) {
        this.speech = speech;
    }

    public void command(Action action) {
        if (action == Action.CHAT) return;
        if (action == Action.STATUS) {
            say(describe());
            return;
        }
        mode = action;
        getNavigation().stop();
        setTarget(null);
        miningTarget = null;
        miningTicks = 0;
        minedBlocks = 0;
        stalledTicks = 0;
        buildOrigin = null;
        buildCursor = 0;
    }

    public String describe() {
        String state = switch (mode) {
            case FOLLOW -> "seguindo você";
            case STAY -> "esperando aqui";
            case STOP -> "parada";
            case DEFEND -> "protegendo você";
            case AUTO -> "em modo autônomo";
            case MINE -> "minerando";
            case BUILD -> "construindo";
            default -> "esperando uma ordem";
        };
        int occupied = 0;
        for (ItemStack stack : supplies) if (!stack.isEmpty()) occupied++;
        String tool = getMainHandItem().isEmpty() ? "nenhuma" : getMainHandItem().getHoverName().getString();
        return "Estou " + state + ". Vida: " + Math.round(getHealth()) + "/"
                + Math.round(getMaxHealth()) + ". Ferramenta/arma: " + tool
                + ". Mochila: " + occupied + "/27 espaços. Blocos para abrigo: "
                + countBuildingMaterials() + ".";
    }

    /** Transfers the held stack, or equips one supplied melee weapon/tool. */
    public boolean giveItem(ServerPlayer player) {
        if (!canExchangeWith(player)) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            say("Segure uma ferramenta, arma ou materiais na mão antes de usar /luma entregar.");
            return false;
        }
        if (isHandEquipment(held)) {
            ItemStack replacement = held.split(1);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.isEmpty() ? ItemStack.EMPTY : held);
            ItemStack previous = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, replacement);
            returnToPlayer(player, previous);
            say("Equipei " + replacement.getHoverName().getString() + ". A ferramenta anterior foi devolvida.");
            return true;
        }
        int before = held.getCount();
        store(held);
        boolean transferred = held.getCount() < before;
        player.setItemInHand(InteractionHand.MAIN_HAND, held.isEmpty() ? ItemStack.EMPTY : held);
        say(transferred ? "Guardei os materiais na mochila." : "Minha mochila está cheia. Use /luma devolver.");
        return transferred;
    }

    public void returnItems(ServerPlayer player) {
        if (!canExchangeWith(player)) return;
        command(Action.STOP);
        ItemStack tool = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        returnToPlayer(player, tool);
        for (int slot = 0; slot < supplies.size(); slot++) {
            ItemStack stack = supplies.set(slot, ItemStack.EMPTY);
            returnToPlayer(player, stack);
        }
        say("Devolvi meus itens. Se sua mochila ficou cheia, os itens restantes estão no chão perto de você.");
    }

    private boolean canExchangeWith(ServerPlayer player) {
        if (!isOwner(player)) return false;
        if (player.level() != level() || distanceToSqr(player) > 36.0) {
            say("Chegue a até seis blocos de mim para trocar os itens.");
            return false;
        }
        return true;
    }

    private static void returnToPlayer(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    private static boolean isHandEquipment(ItemStack stack) {
        return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target instanceof Monster && !isAlliedTo(target) && super.canAttack(target);
    }

    @Override
    public boolean doHurtTarget(ServerLevel server, Entity target) {
        return target instanceof Monster monster && canAttack(monster) && super.doHurtTarget(server, target);
    }

    @Override
    public int getMaxFallDistance() {
        return 2;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || tickCount % WORK_INTERVAL != 0 || !isAlive()) return;
        ServerPlayer owner = ownerId == null ? null : server.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.level() != server || !owner.isAlive() || owner.isSpectator()) {
            pause();
            return;
        }
        if (mode == Action.STAY || mode == Action.STOP) {
            pause();
            return;
        }
        // Stay near the owner. Never teleport or load distant chunks for a task.
        if (distanceToSqr(owner) > TASK_RADIUS_SQUARED) {
            setTarget(null);
            miningTarget = null;
            miningTicks = 0;
            if (distanceToSqr(owner) <= 64.0 * 64.0) getNavigation().moveTo(owner, 1.1);
            else getNavigation().stop();
            return;
        }
        if (defend(server, owner)) return;
        if (mode == Action.MINE || mode == Action.BUILD || mode == Action.AUTO) {
            if (!server.getGameRules().get(GameRules.MOB_GRIEFING)) {
                if (mode != Action.AUTO) finish("A regra minecraft:mob_griefing está desativada: não posso alterar blocos.");
                follow(owner);
                return;
            }
            if (mode == Action.BUILD) {
                build(server, owner);
                return;
            }
            if (mode == Action.MINE) {
                mine(server, owner, false);
                return;
            }
            if (minedBlocks < AUTO_LOG_BUDGET && getMainHandItem().is(ItemTags.AXES)) {
                if (mine(server, owner, true)) return;
            }
        }
        follow(owner);
    }

    private void pause() {
        getNavigation().stop();
        setTarget(null);
        miningTicks = 0;
    }

    private void follow(ServerPlayer owner) {
        if (distanceToSqr(owner) > 9.0) getNavigation().moveTo(owner, 1.05);
        else getNavigation().stop();
    }

    /** Only hostile monsters near the owner are candidates; players and pets never are. */
    private boolean defend(ServerLevel server, ServerPlayer owner) {
        Monster enemy = server.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(8.0),
                        mob -> mob.isAlive() && canAttack(mob) && hasLineOfSight(mob)
                                && mob.distanceToSqr(owner) <= 64.0 && distanceToSqr(mob) <= TASK_RADIUS_SQUARED)
                .stream().min(Comparator.comparingDouble(mob -> mob.distanceToSqr(owner))).orElse(null);
        if (enemy == null) {
            setTarget(null);
            return false;
        }
        setTarget(enemy);
        getLookControl().setLookAt(enemy, 30.0F, 30.0F);
        if (distanceToSqr(enemy) <= 4.0 && hasLineOfSight(enemy)) {
            getNavigation().stop();
            if (tickCount % 20 == 0) {
                swing(InteractionHand.MAIN_HAND);
                if (doHurtTarget(server, enemy)) {
                    // Mob attacks do not run the player's postHurtEnemy durability step.
                    getMainHandItem().postHurtEnemy(enemy, this);
                }
            }
        } else getNavigation().moveTo(enemy, 1.15);
        return true;
    }

    /** Returns true while an autonomous mining task has useful work. */
    private boolean mine(ServerLevel server, ServerPlayer owner, boolean automatic) {
        ItemStack tool = getMainHandItem();
        if (!tool.is(ItemTags.AXES) && !tool.is(ItemTags.PICKAXES)) {
            if (!automatic) finish("Preciso de um machado para troncos ou de uma picareta para pedra. Use /luma entregar.");
            return false;
        }
        int budget = automatic ? AUTO_LOG_BUDGET : MAX_MINED_BLOCKS;
        if (minedBlocks >= budget) {
            if (!automatic) finish("Concluí o limite de " + budget + " blocos desta ordem. Os itens estão na mochila.");
            return false;
        }
        if (miningTarget == null || !canMine(server, owner, miningTarget, automatic)) {
            miningTarget = findMiningTarget(server, owner, automatic);
            miningTicks = 0;
        }
        if (miningTarget == null) {
            if (!automatic) finish("Não encontrei troncos ou pedra expostos e seguros por perto para esta ferramenta.");
            return false;
        }
        if (getEyePosition().distanceToSqr(Vec3.atCenterOf(miningTarget)) > REACH_SQUARED
                || !canSeeBlock(server, miningTarget)) {
            approach(server, miningTarget);
            if (stalled()) return false;
            return true;
        }
        getNavigation().stop();
        getLookControl().setLookAt(miningTarget.getX() + 0.5, miningTarget.getY() + 0.5, miningTarget.getZ() + 0.5);
        swing(InteractionHand.MAIN_HAND);
        BlockState state = server.getBlockState(miningTarget);
        int requiredTicks = Math.max(10, (int)Math.ceil(state.getDestroySpeed(server, miningTarget)
                * 30.0F / Math.max(1.0F, tool.getDestroySpeed(state))));
        miningTicks += WORK_INTERVAL;
        if (miningTicks < requiredTicks) return true;
        // Compute normal loot with the actual tool, then collect it only after successful removal.
        List<ItemStack> drops = Block.getDrops(state, server, miningTarget, null, this, tool);
        if (server.destroyBlock(miningTarget, false, this, Block.UPDATE_LIMIT)) {
            for (ItemStack drop : drops) {
                store(drop);
                if (!drop.isEmpty()) dropAtFeet(server, drop);
            }
            tool.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
            minedBlocks++;
            stalledTicks = 0;
        }
        miningTarget = null;
        miningTicks = 0;
        return true;
    }

    private BlockPos findMiningTarget(ServerLevel server, ServerPlayer owner, boolean automatic) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos center = blockPosition();
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(6, 4, 6))) {
            if (!canMine(server, owner, position, automatic)) continue;
            double distance = getEyePosition().distanceToSqr(Vec3.atCenterOf(position));
            if (distance < bestDistance) {
                best = position.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean canMine(ServerLevel server, ServerPlayer owner, BlockPos pos, boolean automatic) {
        if (!safeToChange(server, owner, pos) || pos.getY() < owner.blockPosition().getY()) return false;
        BlockState state = server.getBlockState(pos);
        if (state.getDestroySpeed(server, pos) < 0 || !state.getFluidState().isEmpty()) return false;
        // Removing a dry block beside a liquid can cause a flood. Never open that boundary.
        if (hasAdjacentHazard(server, pos)) return false;
        BlockPos above = pos.above();
        if (!server.hasChunkAt(above) || server.getBlockState(above).getBlock() instanceof FallingBlock) return false;
        boolean log = state.is(BlockTags.LOGS) && getMainHandItem().is(ItemTags.AXES);
        boolean stone = !automatic && getMainHandItem().is(ItemTags.PICKAXES)
                && getMainHandItem().isCorrectToolForDrops(state) && isSimpleStone(state);
        if (!log && !stone) return false;
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            if (server.hasChunkAt(neighbor) && server.getBlockState(neighbor).isAir()) return true;
        }
        return false;
    }

    private static boolean isSimpleStone(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    private void build(ServerLevel server, ServerPlayer owner) {
        if (buildOrigin == null) {
            int required = shelterPlan(BlockPos.ZERO).size();
            if (countBuildingMaterials() < required) {
                finish("Para um abrigo 5 por 5, preciso de " + required
                        + " blocos: tábuas, troncos, terra, pedregulho ou pedras simples. Entregue com /luma entregar.");
                return;
            }
            buildOrigin = findBuildSite(server, owner);
            buildCursor = 0;
            if (buildOrigin == null) {
                finish("Não achei um terreno plano e vazio de 5 por 5 perto de você. Não vou substituir blocos existentes.");
                return;
            }
            say("Encontrei um terreno livre. Vou montar o abrigo com seus blocos, deixando uma entrada aberta.");
        }
        List<BlockPos> plan = shelterPlan(buildOrigin);
        if (buildCursor >= plan.size()) {
            finish("Abrigo pronto! Deixei uma entrada de dois blocos e devolvo os materiais restantes com /luma devolver.");
            return;
        }
        BlockPos position = plan.get(buildCursor);
        if (protectsCompanion(position)) {
            getNavigation().moveTo(owner, 1.05);
            stalled();
            return;
        }
        if (!safeToChange(server, owner, position) || !server.getBlockState(position).isAir()) {
            finish("Parei a construção: o espaço foi ocupado ou deixou de ser seguro. Os materiais restantes estão comigo.");
            return;
        }
        if (!server.getEntities(this, new AABB(position)).isEmpty() || getBoundingBox().intersects(new AABB(position))) {
            follow(owner);
            stalled();
            return;
        }
        if (getEyePosition().distanceToSqr(Vec3.atCenterOf(position)) > REACH_SQUARED) {
            approach(server, position);
            stalled();
            return;
        }
        ItemStack material = nextBuildingMaterial();
        if (material.isEmpty()) {
            finish("Meus blocos acabaram. A parte já construída fica no mundo; preciso de outra ordem e materiais para um novo abrigo.");
            return;
        }
        BlockState placed = ((BlockItem)material.getItem()).getBlock().defaultBlockState();
        if (!placed.canSurvive(server, position) || !placed.isCollisionShapeFullBlock(server, position)) {
            finish("Este material não pode formar um abrigo seguro aqui. Entregue tábuas ou pedregulho.");
            return;
        }
        if (server.setBlock(position, placed, Block.UPDATE_ALL)) {
            material.shrink(1);
            buildCursor++;
            stalledTicks = 0;
            swing(InteractionHand.MAIN_HAND);
            getLookControl().setLookAt(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        } else stalled();
    }

    /** A floor, three-block-high walls, roof, and a two-block-high open doorway. */
    private static List<BlockPos> shelterPlan(BlockPos origin) {
        List<BlockPos> plan = new ArrayList<>(96);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) plan.add(origin.offset(x, 0, z));
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
                if (x != 0 && x != 4 && z != 0 && z != 4) continue;
                if (x == 2 && z == 0 && y <= 2) continue;
                plan.add(origin.offset(x, y, z));
            }
        }
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) plan.add(origin.offset(x, 4, z));
        return plan;
    }

    private BlockPos findBuildSite(ServerLevel server, ServerPlayer owner) {
        int[][] offsets = {{2, 2}, {-6, 2}, {2, -6}, {-6, -6}};
        for (int[] offset : offsets) {
            BlockPos origin = owner.blockPosition().offset(offset[0], 0, offset[1]);
            boolean clear = true;
            for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(4, 4, 4))) {
                if (!safeToChange(server, owner, pos) || !server.getBlockState(pos).isAir()
                        || !server.getEntities(this, new AABB(pos)).isEmpty()
                        || getBoundingBox().intersects(new AABB(pos))) {
                    clear = false;
                    break;
                }
            }
            if (!clear) continue;
            for (int x = 0; x < 5 && clear; x++) for (int z = 0; z < 5; z++) {
                BlockPos below = origin.offset(x, -1, z);
                if (!server.hasChunkAt(below) || server.getBlockEntity(below) != null
                        || !server.getBlockState(below).isFaceSturdy(server, below, Direction.UP)) {
                    clear = false;
                    break;
                }
            }
            if (clear) return origin;
        }
        return null;
    }

    private boolean safeToChange(ServerLevel server, ServerPlayer owner, BlockPos position) {
        if (!server.hasChunkAt(position) || !server.getWorldBorder().isWithinBounds(position)
                || owner.position().distanceToSqr(Vec3.atCenterOf(position)) > TASK_RADIUS_SQUARED
                || server.getBlockEntity(position) != null || protectsCompanion(position)) return false;
        AABB blockBox = new AABB(position);
        for (Player player : server.players()) {
            // Protect occupied blocks and every block supporting any player's feet.
            if (player.getBoundingBox().intersects(blockBox)
                    || player.getBoundingBox().move(0.0, -1.0, 0.0).intersects(blockBox)) return false;
        }
        return true;
    }

    private boolean protectsCompanion(BlockPos position) {
        AABB blockBox = new AABB(position);
        return getBoundingBox().intersects(blockBox)
                || getBoundingBox().move(0.0, -1.0, 0.0).intersects(blockBox);
    }

    private static boolean isHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POINTED_DRIPSTONE);
    }

    private static boolean hasAdjacentHazard(ServerLevel server, BlockPos position) {
        for (Direction side : Direction.values()) {
            BlockPos neighbor = position.relative(side);
            if (!server.hasChunkAt(neighbor) || isHazard(server.getBlockState(neighbor))) return true;
        }
        return false;
    }

    private boolean canSeeBlock(ServerLevel server, BlockPos position) {
        BlockHitResult hit = server.clip(new ClipContext(getEyePosition(), Vec3.atCenterOf(position),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getBlockPos().equals(position);
    }

    private void approach(ServerLevel server, BlockPos position) {
        for (Direction side : HORIZONTAL) {
            BlockPos stand = new BlockPos(position.getX(), blockPosition().getY(), position.getZ()).relative(side);
            if (!server.hasChunkAt(stand) || !server.hasChunkAt(stand.above()) || !server.hasChunkAt(stand.below())) continue;
            if (server.getBlockState(stand).isAir() && server.getBlockState(stand.above()).isAir()
                    && server.getBlockState(stand.below()).isFaceSturdy(server, stand.below(), Direction.UP)
                    && !isHazard(server.getBlockState(stand.below())) && !hasAdjacentHazard(server, stand)
                    && getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0)) return;
        }
        getNavigation().stop();
    }

    private boolean stalled() {
        stalledTicks += WORK_INTERVAL;
        if (stalledTicks > MAX_STALLED_TICKS) {
            finish("Não consegui chegar à tarefa com segurança. Aproxime-me da área e dê a ordem novamente.");
            return true;
        }
        return false;
    }

    private static boolean isBuildingMaterial(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        BlockState state = item.getBlock().defaultBlockState();
        return state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || isSimpleStone(state)
                || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.BRICKS);
    }

    private int countBuildingMaterials() {
        int count = 0;
        for (ItemStack stack : supplies) if (isBuildingMaterial(stack)) count += stack.getCount();
        return count;
    }

    private ItemStack nextBuildingMaterial() {
        for (ItemStack stack : supplies) if (!stack.isEmpty() && isBuildingMaterial(stack)) return stack;
        return ItemStack.EMPTY;
    }

    /** Inserts as much as fits and leaves any remainder in the caller's stack. */
    private void store(ItemStack source) {
        if (source.isEmpty()) return;
        for (ItemStack stack : supplies) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, source)) {
                int amount = Math.min(source.getCount(), stack.getMaxStackSize() - stack.getCount());
                if (amount > 0) {
                    stack.grow(amount);
                    source.shrink(amount);
                    if (source.isEmpty()) return;
                }
            }
        }
        for (int slot = 0; slot < supplies.size(); slot++) {
            if (supplies.get(slot).isEmpty()) {
                supplies.set(slot, source.split(Math.min(source.getCount(), source.getMaxStackSize())));
                if (source.isEmpty()) return;
            }
        }
    }

    private void finish(String message) {
        mode = Action.STAY;
        pause();
        miningTarget = null;
        buildOrigin = null;
        buildCursor = 0;
        stalledTicks = 0;
        say(message);
    }

    private void say(String message) {
        if (speech != null) speech.accept(message);
        else if (level() instanceof ServerLevel server && ownerId != null) {
            ServerPlayer owner = server.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) owner.sendSystemMessage(Component.literal("Luma: " + message));
        }
    }

    private void dropAtFeet(ServerLevel server, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(server, getX(), getY() + 0.25, getZ(), stack);
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
    }

    @Override
    public void die(DamageSource source) {
        // Return delivered items as normal world drops even if vanilla mob loot is off.
        // Clear each slot before super.die so equipment and repeated callbacks cannot duplicate it.
        if (level() instanceof ServerLevel server) {
            ItemStack hand = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            dropAtFeet(server, hand);
            for (int slot = 0; slot < supplies.size(); slot++) dropAtFeet(server, supplies.set(slot, ItemStack.EMPTY));
        }
        super.die(source);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (ownerId != null) output.putString("LumaOwner", ownerId.toString());
        output.putString("LumaMode", mode.name());
        output.putInt("LumaMined", minedBlocks);
        ContainerHelper.saveAllItems(output.child("LumaSupplies"), supplies);
        if (buildOrigin != null) {
            output.putLong("LumaBuildOrigin", buildOrigin.asLong());
            output.putInt("LumaBuildCursor", buildCursor);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            String savedOwner = input.getStringOr("LumaOwner", "");
            ownerId = savedOwner.isEmpty() ? null : UUID.fromString(savedOwner);
        } catch (IllegalArgumentException invalidOwner) {
            ownerId = null;
        }
        try {
            mode = Action.valueOf(input.getStringOr("LumaMode", Action.FOLLOW.name()));
        } catch (IllegalArgumentException invalidMode) {
            mode = Action.STAY;
        }
        if (mode == Action.CHAT || mode == Action.STATUS) mode = Action.STAY;
        minedBlocks = Math.max(0, Math.min(MAX_MINED_BLOCKS, input.getIntOr("LumaMined", 0)));
        supplies.clear();
        ContainerHelper.loadAllItems(input.childOrEmpty("LumaSupplies"), supplies);
        buildOrigin = input.getLong("LumaBuildOrigin").map(BlockPos::of).orElse(null);
        buildCursor = Math.max(0, Math.min(96, input.getIntOr("LumaBuildCursor", 0)));
        if (mode != Action.BUILD) {
            buildOrigin = null;
            buildCursor = 0;
        }
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }
}
