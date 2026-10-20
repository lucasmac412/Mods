package br.com.celestialdragon.content;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** All incubation state lives in the block entity; wall time never advances an unloaded egg. */
public final class CelestialIncubatorBlockEntity extends BlockEntity {
    public static final int INCUBATION_TICKS = 6_000;
    private boolean egg;
    private int progress;
    private UUID owner;

    public CelestialIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(DragonContent.INCUBATOR_ENTITY, pos, state);
    }

    public boolean hasEgg() { return egg; }
    public int progress() { return progress; }
    public UUID ownerId() { return owner; }

    /** Returns false without changing either egg if an incubator is already occupied. */
    public boolean insertEgg(UUID ownerId) {
        Objects.requireNonNull(ownerId, "The player who inserts an egg owns the hatchling");
        if (egg || level == null || level.isClientSide() || isRemoved()) return false;
        egg = true;
        owner = ownerId;
        progress = 0;
        updateOccupied(true);
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.2F);
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CelestialIncubatorBlockEntity incubator) {
        if (!(level instanceof ServerLevel server) || !incubator.egg || incubator.owner == null || incubator.isRemoved()) return;
        boolean justFinished = incubator.progress == INCUBATION_TICKS - 1;
        if (incubator.progress < INCUBATION_TICKS) {
            incubator.progress++;
            incubator.setChanged();
        }
        if (server.getGameTime() % 20 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 1.15D,
                    pos.getZ() + 0.5D, 2, 0.3D, 0.15D, 0.3D, 0.005D);
        }
        if (incubator.progress >= INCUBATION_TICKS && (justFinished || server.getGameTime() % 20 == 0)) {
            incubator.tryHatch(server);
        }
    }

    private void tryHatch(ServerLevel server) {
        CelestialDragonEntity dragon = CelestialDragonMod.DRAGON.create(server, EntitySpawnReason.TRIGGERED);
        if (dragon == null) return;
        dragon.tame(owner);
        dragon.setNest(worldPosition);
        dragon.setPersistenceRequired();
        // Check the real dragon bounds, so even a low ceiling keeps the egg safe inside.
        int[][] offsets = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {4, 4}, {-4, 4}, {4, -4}, {-4, -4}};
        for (int y = 1; y <= 4; y++) {
            for (int[] offset : offsets) {
                BlockPos candidate = worldPosition.offset(offset[0], y, offset[1]);
                dragon.snapTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, 0.0F, 0.0F);
                AABB bounds = dragon.getBoundingBox();
                if (bounds.minY < server.getMinY() || bounds.maxY > server.getMaxY() + 1
                        || !server.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                        || !server.getWorldBorder().isWithinBounds(bounds) || server.containsAnyLiquid(bounds)
                        || !server.noCollision(dragon, bounds)) continue;
                if (!server.addFreshEntity(dragon)) return;
                // Clearing only follows a successful insertion into the world.
                egg = false;
                owner = null;
                progress = 0;
                updateOccupied(false);
                setChanged();
                server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.1F, 0.65F);
                server.sendParticles(ParticleTypes.END_ROD, dragon.getX(), dragon.getY() + 1.0D,
                        dragon.getZ(), 35, 1.0D, 1.0D, 1.0D, 0.04D);
                return;
            }
        }
    }

    private void updateOccupied(boolean occupied) {
        if (level != null && getBlockState().is(DragonContent.INCUBATOR)) {
            level.setBlock(worldPosition, getBlockState().setValue(CelestialIncubatorBlock.OCCUPIED, occupied), Block.UPDATE_ALL);
        }
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState replacement) {
        if (egg && level instanceof ServerLevel) {
            egg = false;
            owner = null;
            progress = 0;
            setChanged();
            Block.popResource(level, worldPosition, new ItemStack(DragonContent.EGG));
        }
        super.preRemoveSideEffects(pos, replacement);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("Egg", egg);
        output.putInt("Progress", progress);
        output.storeNullable("Owner", UUIDUtil.CODEC, owner);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        egg = input.getBooleanOr("Egg", false);
        progress = egg ? Mth.clamp(input.getIntOr("Progress", 0), 0, INCUBATION_TICKS) : 0;
        owner = egg ? input.read("Owner", UUIDUtil.CODEC).orElse(null) : null;
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
}
