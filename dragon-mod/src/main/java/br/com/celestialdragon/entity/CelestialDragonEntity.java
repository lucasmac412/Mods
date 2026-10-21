package br.com.celestialdragon.entity;

import br.com.celestialdragon.config.DragonConfig;
import br.com.celestialdragon.sound.DragonSounds;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import br.com.celestialdragon.world.NestGenerator;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** A wingless celestial serpent. Every attack and ownership decision runs on the server. */
public final class CelestialDragonEntity extends PathfinderMob implements OwnableEntity {
    public static final double DETECTION_RANGE = 35.0;
    public static final int BITE_COOLDOWN = 18;
    public static final int BITE_DURATION = 14;
    public static final int BITE_HIT_DELAY = 5;
    public static final int BEAM_COOLDOWN = 100;
    public static final int BEAM_DURATION = 36;
    public static final int BEAM_CHARGE_TICKS = 10;
    public static final int BEAM_RECOVERY_TICKS = 4;
    public static final int SPIRIT_CHARGE_TICKS = 40;
    public static final int SPIRIT_COOLDOWN = 600;
    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(CelestialDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BOOST = SynchedEntityData.defineId(CelestialDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SPIRIT = SynchedEntityData.defineId(CelestialDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(
            CelestialDragonEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> BITE = SynchedEntityData.defineId(
            CelestialDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEAM = SynchedEntityData.defineId(
            CelestialDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> BEAM_END = SynchedEntityData.defineId(
            CelestialDragonEntity.class, EntityDataSerializers.VECTOR3);
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x65FFFF, 1.65F);
    private static final DustParticleOptions WHITE_CORE = new DustParticleOptions(0xE8FFFF, 1.35F);
    /** Optional Luma compatibility without linking its classes or requiring its mod. */
    private static final ClassValue<Optional<Method>> LUMA_OWNER_GETTER = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method method = type.getMethod("getOwnerId");
                return method.getReturnType() == UUID.class ? Optional.of(method) : Optional.empty();
            } catch (NoSuchMethodException | SecurityException missingApi) {
                return Optional.empty();
            }
        }
    };
    private final Set<UUID> beamHits = new HashSet<>();
    private BlockPos nest;
    private int biteCooldown;
    private int beamCooldown;
    private int verticalInputTick;
    private float riderVerticalInput;
    private Vec3 beamDirection = Vec3.Z_AXIS;
    private Vec3 idleDestination;
    private int spiritCooldown;
    private float boostAmount;
    private boolean sanctuarySupportsChecked;
    private final ServerBossEvent boss = new ServerBossEvent(UUID.randomUUID(),
            Component.literal("Dragão Celestial — Guardião do Ninho"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    public CelestialDragonEntity(EntityType<? extends CelestialDragonEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl<>(this, 12, true);
        setNoGravity(true);
        setPersistenceRequired();
        if (!level.isClientSide()) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(DragonConfig.current().maxHealth());
            setHealth(getMaxHealth());
        }
        xpReward = 35;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Zombie's vanilla FOLLOW_RANGE is the reference for the 35 block detection radius.
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, DragonConfig.current().maxHealth())
                .add(Attributes.FOLLOW_RANGE, DETECTION_RANGE)
                .add(Attributes.ATTACK_DAMAGE, DragonConfig.current().biteDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FLYING_SPEED, 0.55)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void registerGoals() {
        // A single bounded scan and flight controller below also work in open skies,
        // where walking navigation/melee goals cannot reach a target.
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, "");
        builder.define(BITE, 0);
        builder.define(BEAM, 0);
        builder.define(BEAM_END, new Vector3f());
        builder.define(DORMANT, false);
        builder.define(BOOST, false);
        builder.define(SPIRIT, 0);
    }

    public boolean isTamed() {
        return !entityData.get(OWNER).isEmpty();
    }

    public UUID getOwnerId() {
        String value = entityData.get(OWNER);
        if (value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalidOwner) {
            return null;
        }
    }

    @Override
    public EntityReference<LivingEntity> getOwnerReference() {
        UUID owner = getOwnerId();
        return owner == null ? null : EntityReference.of(owner);
    }

    /** Called by the incubator when its owner hatches this dragon. */
    public void tame(UUID owner) {
        entityData.set(OWNER, Objects.requireNonNull(owner).toString());
        entityData.set(DORMANT, false);
        boss.removeAllPlayers();
        setTarget(null);
        setPersistenceRequired();
    }

    public void setNest(BlockPos pos) {
        nest = pos == null ? null : pos.immutable();
    }

    public BlockPos getNest() {
        return nest;
    }

    public boolean isDormant() { return entityData.get(DORMANT); }
    public boolean isBoosting() { return entityData.get(BOOST) && getControllingPassenger() != null; }
    public int spiritTicks() { return entityData.get(SPIRIT); }
    public ServerBossEvent bossEvent() { return boss; }
    public void restAtNest() {
        if (isTamed() || nest == null) return;
        entityData.set(DORMANT, true);
        setNoAi(true);
        setTarget(null);
        setDeltaMovement(Vec3.ZERO);
    }
    public void wakeFromNest() {
        if (!isDormant()) return;
        entityData.set(DORMANT, false);
        setNoAi(false);
        playSound(DragonSounds.ROAR, 2.0F, 0.75F);
    }
    public void setRiderBoostInput(boolean boost) {
        entityData.set(BOOST, boost && isTamed() && getControllingPassenger() != null);
        verticalInputTick = tickCount;
    }

    public int biteTicks() {
        return entityData.get(BITE);
    }

    public int beamTicks() {
        return entityData.get(BEAM);
    }

    public Vec3 beamEnd() {
        return position().add(new Vec3(entityData.get(BEAM_END)));
    }

    public Vec3 mouthPosition() {
        var pose = DragonAttackAnimation.pose(biteTicks(), beamTicks(), tickCount);
        // Same two pivots and attack curve as the rendered neck/head. Model units are 1/16 block.
        double headPitch = pose.headPitch();
        double headY = 10 * Math.cos(headPitch) + 42.8 * Math.sin(headPitch) - 10;
        double headZ = 10 * Math.sin(headPitch) - 42.8 * Math.cos(headPitch) - 34;
        double pitch = getXRot() * Mth.DEG_TO_RAD + pose.neckPitch();
        double height = 2.8 - (headY * Math.cos(pitch) - headZ * Math.sin(pitch)) / 16;
        double forward = -(headY * Math.sin(pitch) + headZ * Math.cos(pitch) + pose.pullBack()) / 16;
        double yaw = getYRot() * Mth.DEG_TO_RAD;
        return position().add(-Math.sin(yaw) * forward, height, Math.cos(yaw) * forward);
    }

    /** All damage paths share this ownership check, including area attacks. */
    public static boolean isProtectedAlly(CelestialDragonEntity dragon, LivingEntity entity) {
        if (entity == dragon || dragon.isPassengerOfSameVehicle(entity)) return true;
        UUID owner = dragon.getOwnerId();
        if (owner == null) return false;
        if (owner.equals(entity.getUUID())) return true;
        if (entity instanceof CelestialDragonEntity other && owner.equals(other.getOwnerId())) return true;
        if (entity instanceof OwnableEntity pet && pet.getOwnerReference() != null
                && owner.equals(pet.getOwnerReference().getUUID())) return true;
        LivingEntity ownerEntity = dragon.getOwner();
        if (ownerEntity != null && ownerEntity.isAlliedTo(entity)) return true;
        return isOwnedLumaCompanion(entity, owner);
    }

    @Override
    public PlayerTeam getTeam() {
        LivingEntity owner = isTamed() ? getOwner() : null;
        return owner != null ? owner.getTeam() : super.getTeam();
    }

    private static boolean isOwnedLumaCompanion(LivingEntity entity, UUID owner) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!id.getNamespace().equals("luma") || !id.getPath().equals("companion")) return false;
        try {
            var getter = LUMA_OWNER_GETTER.get(entity.getClass());
            return getter.isPresent() && owner.equals(getter.get().invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException incompatibleApi) {
            return false;
        }
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        return entity instanceof LivingEntity living && isProtectedAlly(this, living)
                || super.considersEntityAsAlly(entity);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return legalTarget(target) && super.canAttack(target);
    }

    private boolean legalTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved()
                && !target.isInvulnerable() && !isProtectedAlly(this, target)
                && !isAlliedTo(target)
                && (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isTamed() && player.getUUID().equals(getOwnerId())
                && player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
            if (!level().isClientSide() && !isVehicle()) {
                player.startRiding(this);
                setTarget(null);
                getNavigation().stop();
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isTamed() && getPassengers().isEmpty() && passenger instanceof Player
                && passenger.getUUID().equals(getOwnerId());
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = getFirstPassenger();
        return passenger instanceof Player player && player.getUUID().equals(getOwnerId()) ? player : null;
    }

    @Override
    public boolean isSaddled() {
        return isTamed();
    }

    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, dimensions.height() * 0.96, -0.15 * scale);
    }

    public void setRiderVerticalInput(float input) {
        riderVerticalInput = Float.isFinite(input) ? Mth.clamp(input, -1.0F, 1.0F) : 0.0F;
        verticalInputTick = tickCount;
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        if (tickCount - verticalInputTick > 10) { riderVerticalInput = 0.0F; entityData.set(BOOST, false); }
        return new Vec3(rider.xxa * 0.8, riderVerticalInput * 0.85, rider.zza);
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        setYRot(rider.getYRot());
        setXRot(rider.getXRot() * 0.65F);
        yHeadRot = getYRot();
        yBodyRot = getYRot();
        setTarget(null);
        resetFallDistance();
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return 0.7F + boostAmount * 0.9F;
    }

    @Override
    public void travel(Vec3 input) {
        if (canSimulateMovement()) {
            travelFlying(input, getSpeed() * 0.18F, getSpeed() * 0.18F, getSpeed() * 0.18F);
        }
        calculateEntityAnimation(true);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void tick() {
        boostAmount = Mth.clamp(boostAmount + (isBoosting() ? 0.08F : -0.12F), 0, 1);
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (tickCount == 1 && isAlive() && !isDormant()) playSound(DragonSounds.ROAR, 1.8F, 0.85F);
        if (!sanctuarySupportsChecked && !isTamed() && nest != null && tickCount % 40 == 0)
            sanctuarySupportsChecked = NestGenerator.repairSanctuarySupports(server, nest);
        if (tickCount % 20 == 0) {
            double configuredMax = DragonConfig.current().maxHealth();
            var attribute = getAttribute(Attributes.MAX_HEALTH);
            if (attribute.getBaseValue() != configuredMax) {
                float healthBefore = getHealth();
                attribute.setBaseValue(configuredMax);
                setHealth(Math.min(healthBefore, getMaxHealth()));
            }
        }
        if (biteCooldown > 0) biteCooldown--;
        if (beamCooldown > 0) beamCooldown--;
        if (spiritCooldown > 0) spiritCooldown--;
        if (spiritTicks() > 0) entityData.set(SPIRIT, spiritTicks() - 1);
        if (tickCount % 10 == 0) updateBoss(server);
        if (isBoosting() && tickCount % 2 == 0) server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                getX(), getY() + 3, getZ(), 12, 2, 1.4, 2, 0.05);
        if (!isAlive()) {
            entityData.set(BITE, 0);
            entityData.set(BEAM, 0);
            entityData.set(SPIRIT, 0);
            entityData.set(BOOST, false);
            boss.removeAllPlayers();
            beamHits.clear();
            return;
        }
        if (biteTicks() > 0) {
            int remaining = biteTicks() - 1;
            entityData.set(BITE, remaining);
            if (remaining == BITE_DURATION - BITE_HIT_DELAY) applyBite(server);
        }
        if (beamTicks() > 0) {
            entityData.set(BEAM, beamTicks() - 1);
            if (beamTicks() > 0) tickBeam(server);
            else beamHits.clear();
        }
        if (isDormant()) { setDeltaMovement(Vec3.ZERO); return; }
        if (!isAlive() || isNoAi() || getControllingPassenger() != null) return;
        if (tickCount % 20 == 0) chooseTarget();
        LivingEntity target = getTarget();
        if (target != null && (!legalTarget(target) || distanceToSqr(target) > DETECTION_RANGE * DETECTION_RANGE
                || !hasLineOfSight(target))) {
            setTarget(null);
            target = null;
        }
        if (target != null) {
            getLookControl().setLookAt(target, 30, 30);
            Vec3 delta = target.getEyePosition().subtract(getEyePosition());
            setYRot((float) (Mth.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F);
            setXRot((float) -(Mth.atan2(delta.y, delta.horizontalDistance()) * 180.0 / Math.PI));
            yHeadRot = getYRot();
            yBodyRot = getYRot();
            if (isWithinMeleeAttackRange(target)) {
                getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
                tryBite();
            } else {
                getMoveControl().setWantedPosition(target.getX(), target.getY() + 0.4, target.getZ(), 0.9);
                if (distanceToSqr(target) > 36.0) tryBeam();
            }
        } else {
            flyNearHome();
        }
    }

    private void updateBoss(ServerLevel server) {
        if (isTamed() || nest == null || !isAlive()) { boss.removeAllPlayers(); return; }
        Vec3 center = Vec3.atCenterOf(nest);
        double range = NestGenerator.RADIUS + 20.0;
        if (isDormant()) {
            for (var player : server.players()) {
                double dx = player.getX() - center.x, dz = player.getZ() - center.z;
                if (player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && Math.abs(player.getY() - center.y) < 40 && dx * dx + dz * dz < (NestGenerator.RADIUS - 2.0) * (NestGenerator.RADIUS - 2.0)) {
                    wakeFromNest(); break;
                }
            }
        }
        boss.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0, 1));
        boss.setVisible(!isDormant());
        var nearby = new HashSet<net.minecraft.server.level.ServerPlayer>();
        if (!isDormant()) for (var player : server.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(center) <= range * range) {
                nearby.add(player); boss.addPlayer(player);
            }
        }
        for (var player : Set.copyOf(boss.getPlayers())) if (!nearby.contains(player)) boss.removePlayer(player);
    }

    @Override public void remove(RemovalReason reason) { boss.removeAllPlayers(); super.remove(reason); }

    public boolean trySpiritBomb() {
        if (!(level() instanceof ServerLevel server) || !isAlive() || isDormant() || !isTamed()
                || getControllingPassenger() == null || spiritCooldown > 0 || biteTicks() > 0 || beamTicks() > 0 || spiritTicks() > 0) return false;
        CelestialOrbEntity orb = br.com.celestialdragon.CelestialDragonMod.SPIRIT_ORB.create(server, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (orb == null) return false;
        orb.begin(this);
        if (!server.addFreshEntity(orb)) return false;
        spiritCooldown = SPIRIT_COOLDOWN;
        entityData.set(SPIRIT, SPIRIT_CHARGE_TICKS);
        playSound(DragonSounds.BEAM, 2.0F, 0.45F);
        return true;
    }

    private void chooseTarget() {
        LivingEntity best = null;
        double bestDistance = DETECTION_RANGE * DETECTION_RANGE;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(DETECTION_RANGE), this::legalTarget)) {
            double distance = distanceToSqr(candidate);
            if (distance <= bestDistance && hasLineOfSight(candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        setTarget(best);
    }

    private void flyNearHome() {
        Vec3 home = nest == null ? position() : Vec3.atCenterOf(nest).add(0, 5, 0);
        LivingEntity owner = isTamed() ? getOwner() : null;
        if (owner != null) home = owner.position().add(0, 3, 0);
        if (idleDestination == null || tickCount % 100 == 0 || position().distanceToSqr(idleDestination) < 4) {
            if (position().distanceToSqr(home) > 144) {
                idleDestination = home;
            } else {
                idleDestination = home.add((random.nextDouble() - 0.5) * 12,
                        (random.nextDouble() - 0.5) * 4, (random.nextDouble() - 0.5) * 12);
            }
        }
        getMoveControl().setWantedPosition(idleDestination.x, idleDestination.y, idleDestination.z, 0.55);
    }

    /** The rider sends only intent; the server finds legal targets in this short forward arc. */
    public boolean tryBite() {
        if (!(level() instanceof ServerLevel) || !isAlive() || biteCooldown > 0
                || isDormant() || spiritTicks() > 0 || biteTicks() > 0 || beamTicks() > 0) return false;
        biteCooldown = BITE_COOLDOWN;
        entityData.set(BITE, BITE_DURATION);
        return true;
    }

    private void applyBite(ServerLevel server) {
        playSound(DragonSounds.BITE, 1.25F, 0.9F + random.nextFloat() * 0.15F);
        Vec3 forward = getLookAngle();
        Vec3 origin = position().add(0, getBbHeight() * 0.52, 0);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(4.6, 1.8, 4.6), this::legalTarget)) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(origin);
            if (toTarget.lengthSqr() <= 36.0
                    && forward.dot(toTarget.normalize()) >= 0.15 && hasLineOfSight(target)) {
                target.hurtServer(server, damageSources().mobAttack(this), (float) DragonConfig.current().biteDamage());
            }
        }
    }

    public boolean tryBeam() {
        if (!(level() instanceof ServerLevel server) || !isAlive() || beamCooldown > 0
                || isDormant() || spiritTicks() > 0 || beamTicks() > 0 || biteTicks() > 0) return false;
        beamCooldown = BEAM_COOLDOWN;
        entityData.set(BEAM, BEAM_DURATION);
        LivingEntity rider = getControllingPassenger();
        beamDirection = (rider == null ? getLookAngle() : rider.getLookAngle()).normalize();
        beamHits.clear();
        tickBeam(server);
        return true;
    }

    private void tickBeam(ServerLevel server) {
        LivingEntity rider = getControllingPassenger();
        beamDirection = (rider == null ? getLookAngle() : rider.getLookAngle()).normalize();
        Vec3 origin = getEyePosition();
        Vec3 start = mouthPosition();
        Vec3 desiredEnd = origin.add(beamDirection.scale(DETECTION_RANGE));
        HitResult muzzleWall = level().clip(new ClipContext(origin, start,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        HitResult wall;
        if (muzzleWall.getType() == HitResult.Type.BLOCK) {
            // A long decorative snout cannot project an attack through a nearby wall.
            start = origin;
            wall = muzzleWall;
        } else {
            wall = level().clip(new ClipContext(start, desiredEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        }
        Vec3 end = wall.getLocation();
        entityData.set(BEAM_END, end.subtract(position()).toVector3f());
        int elapsed = BEAM_DURATION - beamTicks();
        if (elapsed < BEAM_CHARGE_TICKS) {
            // Energy gathers at the actual muzzle before either particles or damage form a beam.
            double phase = elapsed * 0.85;
            double radius = 0.85 * (1.0 - (double)elapsed / BEAM_CHARGE_TICKS) + 0.16;
            Vec3 charge = muzzleWall.getType() == HitResult.Type.BLOCK ? muzzleWall.getLocation() : start;
            for (int i = 0; i < 8; i++) {
                double angle = phase + i * Math.PI / 4;
                server.sendParticles(CYAN, true, false, charge.x + Math.cos(angle) * radius,
                        charge.y + Math.sin(angle) * radius, charge.z, 1, 0, 0, 0, 0);
            }
            server.sendParticles(WHITE_CORE, true, false, charge.x, charge.y, charge.z, 2, 0.07, 0.07, 0.07, 0);
            return;
        }
        if (beamTicks() <= BEAM_RECOVERY_TICKS) return;
        if (elapsed == BEAM_CHARGE_TICKS) {
            playSound(DragonSounds.BEAM, 1.2F, 0.9F);
            server.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFDFFFFF), true, false,
                    start.x, start.y, start.z, 1, 0, 0, 0, 0);
        }
        Vec3 beam = end.subtract(start);
        double length = beam.length();
        Vec3 across = beamDirection.cross(Vec3.Y_AXIS).normalize();
        if (across.lengthSqr() < 0.01) across = Vec3.X_AXIS;
        Vec3 up = across.cross(beamDirection).normalize();
        // A bright core and outer helix stay bounded by the fixed 35-block attack length.
        int points = Math.max(1, (int) Math.ceil(length / 0.6));
        for (int i = 0; tickCount % 2 == 0 && i <= points; i++) {
            double fraction = (double) i / points;
            Vec3 center = start.add(beam.scale(fraction));
            double phase = fraction * 20 + tickCount * 0.6;
            Vec3 spiral = center.add(across.scale(Math.cos(phase) * 0.38)).add(up.scale(Math.sin(phase) * 0.38));
            server.sendParticles(CYAN, true, false, spiral.x, spiral.y, spiral.z, 1, 0, 0, 0, 0);
            if (i % 2 == 0) server.sendParticles(WHITE_CORE, true, false, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            if (i % 6 == 0) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false,
                    center.x, center.y, center.z, 1, 0.04, 0.04, 0.04, 0.01);
        }
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(0.8), this::legalTarget)) {
            if (distanceToSqr(target) <= DETECTION_RANGE * DETECTION_RANGE
                    && !beamHits.contains(target.getUUID()) && hasLineOfSight(target)
                    && (target.getBoundingBox().inflate(0.55).contains(start)
                        || target.getBoundingBox().inflate(0.55).clip(start, end).isPresent())) {
                beamHits.add(target.getUUID());
                target.hurtServer(server, damageSources().mobAttack(this), (float) DragonConfig.current().beamDamage());
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker
                && (isProtectedAlly(this, attacker) || isAlliedTo(attacker))) return false;
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) wakeFromNest();
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() { return isDormant() ? null : DragonSounds.ROAR; }

    @Override public net.minecraft.sounds.SoundSource getSoundSource() { return net.minecraft.sounds.SoundSource.HOSTILE; }

    @Override
    public int getAmbientSoundInterval() { return 240; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return DragonSounds.HURT; }

    @Override
    protected SoundEvent getDeathSound() { return DragonSounds.DEATH; }

    @Override
    protected float getSoundVolume() { return 0.95F; }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("DragonOwner", entityData.get(OWNER));
        output.putString("DragonMode", isTamed() ? "tamed" : "wild");
        if (nest != null) output.putLong("DragonNest", nest.asLong());
        output.putInt("DragonBiteCooldown", biteCooldown);
        output.putInt("DragonBeamCooldown", beamCooldown);
        output.putInt("DragonSpiritCooldown", spiritCooldown);
        output.putBoolean("DragonDormant", isDormant());
        output.putBoolean("SanctuarySupportsV1", sanctuarySupportsChecked);
        // Health/attributes are persisted by LivingEntity above.
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String owner = input.getStringOr("DragonOwner", "");
        try {
            entityData.set(OWNER, owner.isEmpty() ? "" : UUID.fromString(owner).toString());
        } catch (IllegalArgumentException invalidOwner) {
            entityData.set(OWNER, "");
        }
        nest = input.getLong("DragonNest").map(BlockPos::of).orElse(null);
        biteCooldown = Mth.clamp(input.getIntOr("DragonBiteCooldown", 0), 0, BITE_COOLDOWN);
        beamCooldown = Mth.clamp(input.getIntOr("DragonBeamCooldown", 0), 0, BEAM_COOLDOWN);
        entityData.set(BITE, 0);
        entityData.set(BEAM, 0);
        spiritCooldown = Mth.clamp(input.getIntOr("DragonSpiritCooldown", 0), 0, SPIRIT_COOLDOWN);
        entityData.set(DORMANT, !isTamed() && nest != null && input.getBooleanOr("DragonDormant", false));
        sanctuarySupportsChecked = input.getBooleanOr("SanctuarySupportsV1", false);
        if (isDormant()) setNoAi(true);
        entityData.set(SPIRIT, 0);
        entityData.set(BOOST, false);
        setNoGravity(true);
        setPersistenceRequired();
    }
}
