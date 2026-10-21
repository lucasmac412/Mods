package br.com.celestialdragon.entity;

import br.com.celestialdragon.config.DragonConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A server-owned, two-second charge and slowly travelling twenty-block spirit sphere. */
public final class CelestialOrbEntity extends Entity {
    public static final int CHARGE_TICKS = 40;
    public static final double MAX_RADIUS = 10;
    public static final double SPEED = 0.30;
    public static final int BLAST_WIDTH = 20, BLAST_LENGTH = 24, BLAST_DEPTH = 6;
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(CelestialOrbEntity.class, EntityDataSerializers.INT);
    private UUID dragonId;
    private Vec3 direction = Vec3.ZERO;

    public CelestialOrbEntity(EntityType<? extends CelestialOrbEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(AGE, 0); }
    public int chargeAge() { return entityData.get(AGE); }
    public double radius(float partial) { return 0.5 + 9.5 * Math.min(1, (chargeAge() + partial) / CHARGE_TICKS); }
    public UUID dragonId() { return dragonId; }
    public void begin(CelestialDragonEntity dragon) { dragonId = dragon.getUUID(); setPos(dragon.position().add(0, 22, 0)); }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (dragonId == null) { discard(); return; }
        if (!(server.getEntity(dragonId) instanceof CelestialDragonEntity dragon) || !dragon.isAlive() || !dragon.isTamed()) { discard(); return; }
        int age = chargeAge() + 1;
        entityData.set(AGE, age);
        if (age <= CHARGE_TICKS) {
            LivingEntity rider = dragon.getControllingPassenger();
            if (rider == null || !rider.getUUID().equals(dragon.getOwnerId())) { discard(); return; }
            setPos(dragon.position().add(0, 22, 0));
            if (age == CHARGE_TICKS) {
                Vec3 origin = rider.getEyePosition();
                Vec3 end = server.clip(new ClipContext(origin, origin.add(rider.getLookAngle().scale(96)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider)).getLocation();
                direction = end.subtract(position()).normalize();
            }
            return;
        }
        if (age > 540 || direction.lengthSqr() < 0.9) { discard(); return; }
        Vec3 next = position().add(direction.scale(SPEED));
        // Do not generate chunks or explode into an unloaded region.
        for (int cx = ((int)Math.floor(next.x - 10)) >> 4; cx <= ((int)Math.floor(next.x + 10)) >> 4; cx++)
            for (int cz = ((int)Math.floor(next.z - 10)) >> 4; cz <= ((int)Math.floor(next.z + 10)) >> 4; cz++)
                if (!server.hasChunkAt(new BlockPos(cx << 4, (int)next.y, cz << 4))) { discard(); return; }
        setPos(next);
        AABB hull = new AABB(next.x - MAX_RADIUS, next.y - MAX_RADIUS, next.z - MAX_RADIUS,
                next.x + MAX_RADIUS, next.y + MAX_RADIUS, next.z + MAX_RADIUS);
        Vec3 impact = null;
        double closest = MAX_RADIUS * MAX_RADIUS;
        for (var shape : server.getBlockCollisions(this, hull)) {
            AABB box = shape.bounds();
            Vec3 point = new Vec3(Mth.clamp(next.x, box.minX, box.maxX), Mth.clamp(next.y, box.minY, box.maxY), Mth.clamp(next.z, box.minZ, box.maxZ));
            double distance = point.distanceToSqr(next);
            if (distance <= closest) { closest = distance; impact = point; }
        }
        if (impact != null) explodeAt(server, dragon, BlockPos.containing(impact.add(0, -0.001, 0)));
    }

    private void explodeAt(ServerLevel server, CelestialDragonEntity dragon, BlockPos center) {
        for (int x = -10; x < 10; x++) for (int z = -12; z < 12; z++) for (int y = -BLAST_DEPTH + 1; y <= 0; y++) {
            BlockPos pos = center.offset(x, y, z);
            if (!server.hasChunkAt(pos)) continue;
            var state = server.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(server, pos) >= 0)
                server.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        AABB blast = new AABB(center.getX() - 10, center.getY() - 6, center.getZ() - 12,
                center.getX() + 10, center.getY() + 20, center.getZ() + 12);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, blast, dragon::canAttack))
            target.hurtServer(server, damageSources().mobAttack(dragon), (float)(DragonConfig.current().beamDamage() * 3));
        server.sendParticles(ParticleTypes.EXPLOSION, center.getX(), center.getY() + 2, center.getZ(), 30, 10, 4, 12, 0);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX(), center.getY() + 1, center.getZ(), 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.getX(), center.getY() + 3, center.getZ(), 100, 10, 5, 12, 0.2);
        server.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4, 0.55F);
        discard();
    }

    @Override public boolean hurtServer(ServerLevel server, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        if (dragonId != null) out.putString("Dragon", dragonId.toString());
        out.putInt("Age", chargeAge());
        out.putDouble("DX", direction.x); out.putDouble("DY", direction.y); out.putDouble("DZ", direction.z);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        try { dragonId = UUID.fromString(in.getStringOr("Dragon", "")); } catch (IllegalArgumentException ignored) { dragonId = null; }
        entityData.set(AGE, Mth.clamp(in.getIntOr("Age", 0), 0, 540));
        direction = new Vec3(in.getDoubleOr("DX", 0), in.getDoubleOr("DY", 0), in.getDoubleOr("DZ", 0));
        if (!Double.isFinite(direction.lengthSqr())) direction = Vec3.ZERO;
    }
}
