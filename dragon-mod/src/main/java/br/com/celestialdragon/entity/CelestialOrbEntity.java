package br.com.celestialdragon.entity;

import br.com.celestialdragon.config.DragonConfig;
import br.com.celestialdragon.sound.DragonSounds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sixty-block sphere; server collision and terrain removal are independent of client rendering. */
public final class CelestialOrbEntity extends Entity {
    public static final int CHARGE_TICKS=40, MAX_AGE=1240;
    public static final double MAX_RADIUS=30, SPEED=.30, CHARGE_HEIGHT=44;
    public static final int BLAST_WIDTH=20, BLAST_LENGTH=24, BLAST_DEPTH=6;
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(CelestialOrbEntity.class,EntityDataSerializers.INT);
    private UUID dragonId;
    private Vec3 direction=Vec3.ZERO;
    public CelestialOrbEntity(EntityType<? extends CelestialOrbEntity> type,Level level) { super(type,level);setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(AGE,0); }
    public int chargeAge() { return entityData.get(AGE); }
    public double radius(float partial) { return .5+(MAX_RADIUS-.5)*Math.min(1,(chargeAge()+partial)/CHARGE_TICKS); }
    public UUID dragonId() { return dragonId; }
    public void begin(CelestialDragonEntity dragon) { dragonId=dragon.getUUID();setPos(dragon.position().add(0,CHARGE_HEIGHT,0)); }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server))return;
        if(dragonId==null || !(server.getEntity(dragonId) instanceof CelestialDragonEntity dragon) || !dragon.isAlive() || !dragon.isTamed()) { discard();return; }
        int age=chargeAge()+1;entityData.set(AGE,age);
        if(age<=CHARGE_TICKS) {
            LivingEntity rider=dragon.getControllingPassenger();
            if(rider==null || !rider.getUUID().equals(dragon.getOwnerId())) { discard();return; }
            setPos(dragon.position().add(0,CHARGE_HEIGHT,0));
            if(age==CHARGE_TICKS) {
                direction=chooseTarget(server,rider).subtract(position()).normalize();
                server.playSound(null,getX(),getY(),getZ(),DragonSounds.SPIRIT_LAUNCH,SoundSource.HOSTILE,1.2F,1.0F);
            }
            return;
        }
        if(age>MAX_AGE || direction.lengthSqr()<.9) { dissipate(server);return; }
        Vec3 next=position().add(direction.scale(SPEED));
        // Only the centre must remain in loaded terrain. An unloaded corner of this enormous
        // visual sphere must not cancel a valid impact in the loaded world.
        if(!server.hasChunkAt(BlockPos.containing(next))) { dissipate(server);return; }
        setPos(next);
        Vec3 impact=firstContact(server,next);
        if(impact!=null)explodeAt(server,dragon,BlockPos.containing(impact.add(0,-.001,0)));
    }

    private Vec3 chooseTarget(ServerLevel server,LivingEntity rider) {
        Vec3 origin=rider.getEyePosition(),look=rider.getLookAngle();
        double loadedReach=0;
        for(int step=1;step<=128;step++) {
            if(!server.hasChunkAt(BlockPos.containing(origin.add(look.scale(step)))))break;
            loadedReach=step;
        }
        var hit=server.clip(new ClipContext(origin,origin.add(look.scale(loadedReach)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,rider));
        if(hit.getType()==HitResult.Type.BLOCK)return hit.getLocation();
        // A horizontal aim at the sky previously let the sphere expire without ever touching
        // terrain. For a miss, choose loaded terrain ahead instead of firing into empty air.
        for(int distance:new int[]{96,64,40,24}) {
            Vec3 ahead=origin.add(look.scale(distance));BlockPos pos=BlockPos.containing(ahead);
            if(!server.hasChunkAt(pos))continue;
            int y=server.getHeight(Heightmap.Types.MOTION_BLOCKING,pos.getX(),pos.getZ());
            if(y>server.getMinY())return new Vec3(ahead.x,y,ahead.z);
        }
        return origin.add(look.scale(96));
    }

    private Vec3 firstContact(ServerLevel server,Vec3 point) {
        Vec3 closestPoint=null;double closest=MAX_RADIUS*MAX_RADIUS;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=Mth.floor(point.x-MAX_RADIUS);x<=Mth.floor(point.x+MAX_RADIUS);x++)
            for(int z=Mth.floor(point.z-MAX_RADIUS);z<=Mth.floor(point.z+MAX_RADIUS);z++) {
                double dx=Mth.clamp(point.x,x,x+1)-point.x,dz=Mth.clamp(point.z,z,z+1)-point.z;
                double horizontal=dx*dx+dz*dz;if(horizontal>MAX_RADIUS*MAX_RADIUS)continue;
                pos.set(x,Mth.floor(point.y),z);if(!server.hasChunkAt(pos))continue;
                double halfHeight=Math.sqrt(MAX_RADIUS*MAX_RADIUS-horizontal);
                int bottom=Math.max(server.getMinY(),Mth.floor(point.y-halfHeight));
                // Height maps skip every empty vertical column above terrain. This avoids
                // scanning a 60x60x60 volume each tick, while still checking real block shapes.
                int top=Math.min(Mth.floor(point.y+halfHeight),server.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)-1);
                for(int y=top;y>=bottom;y--) {
                    pos.set(x,y,z);var shape=server.getBlockState(pos).getCollisionShape(server,pos);
                    if(shape.isEmpty())continue;
                    AABB box=shape.bounds().move(pos);
                    Vec3 contact=new Vec3(Mth.clamp(point.x,box.minX,box.maxX),Mth.clamp(point.y,box.minY,box.maxY),Mth.clamp(point.z,box.minZ,box.maxZ));
                    double squared=contact.distanceToSqr(point);
                    if(squared<=closest) { closest=squared;closestPoint=contact; }
                }
            }
        return closestPoint;
    }

    private void explodeAt(ServerLevel server,CelestialDragonEntity dragon,BlockPos center) {
        int lowest=center.getY();
        for(int x=-10;x<10;x++)for(int z=-12;z<12;z++) {
            BlockPos column=center.offset(x,0,z);if(!server.hasChunkAt(column))continue;
            int surface=center.getY();
            // Canopy contact should remove the tree AND excavate the terrain, not just six
            // layers of leaves. Search at most 64 blocks; never load/generate another chunk.
            while(surface>Math.max(server.getMinY(),center.getY()-64)) {
                var state=server.getBlockState(column.atY(surface));
                if(!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                        && !state.getCollisionShape(server,column.atY(surface)).isEmpty())break;
                surface--;
            }
            int bottom=Math.max(server.getMinY(),surface-BLAST_DEPTH+1);lowest=Math.min(lowest,bottom);
            for(int y=center.getY();y>=bottom;y--) {
                BlockPos pos=column.atY(y);var state=server.getBlockState(pos);
                if(!state.isAir() && state.getDestroySpeed(server,pos)>=0)server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            }
        }
        AABB blast=new AABB(center.getX()-10,lowest,center.getZ()-12,center.getX()+10,center.getY()+20,center.getZ()+12);
        for(LivingEntity target:server.getEntitiesOfClass(LivingEntity.class,blast,dragon::canAttack))
            target.hurtServer(server,damageSources().mobAttack(dragon),(float)(DragonConfig.current().beamDamage()*3));
        server.sendParticles(ParticleTypes.EXPLOSION,center.getX(),center.getY()+2,center.getZ(),30,10,4,12,0);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,center.getX(),center.getY()+1,center.getZ(),1,0,0,0,0);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK,center.getX(),center.getY()+3,center.getZ(),100,10,5,12,.2);
        server.playSound(null,center,DragonSounds.SPIRIT_IMPACT,SoundSource.HOSTILE,1.8F,1.0F);
        discard();
    }
    private void dissipate(ServerLevel server) { server.sendParticles(ParticleTypes.ELECTRIC_SPARK,getX(),getY(),getZ(),24,3,3,3,.1);discard(); }
    @Override public boolean hurtServer(ServerLevel server,net.minecraft.world.damagesource.DamageSource source,float amount) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        if(dragonId!=null)out.putString("Dragon",dragonId.toString());out.putInt("Age",chargeAge());
        out.putDouble("DX",direction.x);out.putDouble("DY",direction.y);out.putDouble("DZ",direction.z);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        try { dragonId=UUID.fromString(in.getStringOr("Dragon","")); } catch(IllegalArgumentException e) { dragonId=null; }
        entityData.set(AGE,Mth.clamp(in.getIntOr("Age",0),0,MAX_AGE));
        direction=new Vec3(in.getDoubleOr("DX",0),in.getDoubleOr("DY",0),in.getDoubleOr("DZ",0));
        if(!Double.isFinite(direction.lengthSqr()))direction=Vec3.ZERO;
    }
}
