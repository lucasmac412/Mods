package br.com.moonfist.combat;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.entity.LunarDisciple;
import br.com.moonfist.network.CombatIntent;
import br.com.moonfist.network.CombatSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CombatService {
    public static final ResourceKey<DamageType> STRIKE=ResourceKey.create(Registries.DAMAGE_TYPE,MoonfistMod.id("martial_strike"));
    public static final class Session {
        public final CombatState state=new CombatState();
        public Vec3 origin=Vec3.ZERO,direction=new Vec3(0,0,1),aim=Vec3.ZERO;
        public int lock=-1,hitPulse;
        private boolean wasEquipped;
        private long packetWindow,lastSeen;
        private int packets;
    }
    private static final class Posture { float value;long lastHit,staggerUntil; }
    private static final Map<UUID,Session> STATES=new HashMap<>();
    private static final Map<UUID,Posture> POSTURE=new HashMap<>();
    private CombatService(){}
    public static Session session(LivingEntity actor){return STATES.computeIfAbsent(actor.getUUID(),id->new Session());}
    public static boolean equipped(LivingEntity actor) {
        return actor.isAlive()&&!actor.isSpectator()&&!actor.isPassenger()
            &&actor.getMainHandItem().is(MoonfistMod.FISTS)&&actor.getOffhandItem().isEmpty();
    }
    public static boolean validTarget(LivingEntity actor,LivingEntity target) {
        if(target==actor||!target.isAlive()||target.isSpectator()||!target.attackable()||actor.isAlliedTo(target))return false;
        // PvP is deliberately disabled for this first version. Cooperative partners and pets are safe.
        if(target instanceof Player)return actor instanceof LunarDisciple && !((Player)target).isCreative();
        if(target instanceof TamableAnimal pet && pet.isTame())return false;
        return true;
    }
    public static void handle(ServerPlayer player,CombatIntent packet) {
        if(!Float.isFinite(packet.strafe())||!Float.isFinite(packet.forward())||packet.action()<0||packet.action()>8||!equipped(player))return;
        Session s=session(player);long now=player.level().getGameTime();
        if(now-s.packetWindow>=20){s.packetWindow=now;s.packets=0;}
        if(++s.packets>32)return;
        switch(packet.action()) {
            case CombatIntent.GUARD_ON -> s.state.guard(true);
            case CombatIntent.GUARD_OFF -> s.state.guard(false);
            case CombatIntent.STANCE -> {if(s.state.move==Move.IDLE&&!s.state.guarding){s.state.stance=(s.state.stance+1)%3;s.state.combo=0;s.state.comboGrace=0;}}
            case CombatIntent.LOCK -> {if(s.lock!=-1)s.lock=-1;else{var target=findTarget(player,16);s.lock=target==null?-1:target.getId();}}
            default -> {
                CombatState.Input input=switch(packet.action()) {
                    case 0->CombatState.Input.LIGHT;case 1->CombatState.Input.HEAVY;case 2->CombatState.Input.WAVE;
                    case 3->CombatState.Input.VOID;default->CombatState.Input.DODGE;
                };
                if(input==CombatState.Input.DODGE) {
                    if(s.state.move!=Move.IDLE)return;
                    double x=Math.max(-1,Math.min(1,packet.strafe())),z=Math.max(-1,Math.min(1,packet.forward()));
                    if(Math.abs(x)+Math.abs(z)<.01)z=-1;
                    double yaw=Math.toRadians(player.getYRot());
                    Vec3 dir=new Vec3(x*Math.cos(yaw)-z*Math.sin(yaw),0,x*Math.sin(yaw)+z*Math.cos(yaw)).normalize();
                    s.state.dodgeX=dir.x;s.state.dodgeZ=dir.z;
                }
                request(player,input);
            }
        }
        sync(player,s);
    }
    public static boolean request(LivingEntity actor,CombatState.Input input) {
        if(!equipped(actor)||staggered(actor))return false;
        Session s=session(actor);long before=s.state.serial;
        boolean accepted=s.state.request(input);
        if(accepted&&before!=s.state.serial)started(actor,s);
        return accepted;
    }
    private static void started(LivingEntity actor,Session s) {
        s.origin=actor.getEyePosition().add(0,-.35,0);s.direction=actor.getLookAngle().normalize();
        var level=(ServerLevel)actor.level();
        var lock=level.getEntity(s.lock);
        if(lock instanceof LivingEntity target && validTarget(actor,target)&&actor.distanceTo(target)<=16&&actor.hasLineOfSight(target))
            s.direction=target.getBoundingBox().getCenter().subtract(s.origin).normalize();
        s.aim=trace(actor,s.origin,s.origin.add(s.direction.scale(7)));
        if(s.state.move==Move.DODGE) {
            actor.setDeltaMovement(s.state.dodgeX*.7,Math.max(0,actor.getDeltaMovement().y),s.state.dodgeZ*.7);actor.hurtMarked=true;
            sound(actor,SoundEvents.PLAYER_ATTACK_SWEEP,.5f,1.5f);
        } else if(s.state.move.attack())sound(actor,SoundEvents.PLAYER_ATTACK_SWEEP,.55f,s.state.stance==2?.7f:1.15f);
        sync(actor,s);
    }
    public static void tickActor(LivingEntity actor) {
        if(!(actor.level() instanceof ServerLevel))return;
        Session s=STATES.get(actor.getUUID());
        boolean active=equipped(actor);
        if(!active&&s==null)return;
        if(s==null)s=session(actor);
        s.lastSeen=actor.level().getGameTime();
        if(!active) {
            s.state.cancel();s.state.tick();s.lock=-1;
            if(s.wasEquipped){s.wasEquipped=false;sync(actor,s);}return;
        }
        s.wasEquipped=true;
        var lock=((ServerLevel)actor.level()).getEntity(s.lock);
        if(!(lock instanceof LivingEntity t)||!validTarget(actor,t)||actor.distanceTo(t)>18||!actor.hasLineOfSight(t))s.lock=-1;
        long serial=s.state.serial;s.state.tick();
        if(serial!=s.state.serial)started(actor,s);
        Move move=s.state.move;
        if(move==Move.DODGE && s.state.age<=6) {
            double speed=s.state.age<=4?.65:.32;
            actor.setDeltaMovement(s.state.dodgeX*speed,actor.getDeltaMovement().y,s.state.dodgeZ*speed);
            if(s.state.age%2==0)actor.hurtMarked=true;
        }
        if(move.attack()) {
            if(move==Move.VOID&&s.state.age<14&&s.state.age%2==0) {
                burst((ServerLevel)actor.level(),actor.getBoundingBox().getCenter(),0x59eef5,8,.7);
                if(s.state.age==2)sound(actor,SoundEvents.AMETHYST_BLOCK_RESONATE,.8f,.7f);
            }
            if(move==Move.WAVE&&s.state.age>=7&&s.state.age<=24)wave(actor,s);
            else {int stage=move.hitStage(s.state.age);if(stage>=0)hit(actor,s,stage);}
        }
        if(actor.tickCount%4==0)sync(actor,s);
    }
    private static void hit(LivingEntity actor,Session s,int stage) {
        Move move=s.state.move;Vec3 center=move==Move.VOID?s.aim:actor.position().add(0,.8,0);
        double radius=move==Move.VOID?2.5:move.range;
        var level=(ServerLevel)actor.level();
        for(var target:level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),t->validTarget(actor,t))) {
            if(actor.distanceTo(target)>move.range+target.getBbWidth()*.5 || !actor.hasLineOfSight(target))continue;
            Vec3 to=target.getBoundingBox().getCenter().subtract(actor.getEyePosition()).normalize();
            if(move!=Move.VOID&&actor.getLookAngle().dot(to)<(move==Move.SPIN_KICK||move==Move.SLAM?-.2:.35))continue;
            if(s.state.canHit(target.getUUID(),stage))damage(actor,target,s);
        }
        if(move==Move.VOID)burst(level,center,0xff72d6,12,.9);
    }
    private static void wave(LivingEntity actor,Session s) {
        double distance=(s.state.age-6)*.8;
        Vec3 end=trace(actor,s.origin,s.origin.add(s.direction.scale(distance)));
        Vec3 wanted=s.origin.add(s.direction.scale(distance));
        if(end.distanceToSqr(wanted)>.04)return;
        var level=(ServerLevel)actor.level();burst(level,end,0x41e9f2,5,.35);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,new AABB(end,end).inflate(1.1),t->validTarget(actor,t))) {
            if(actor.distanceTo(target)<=15&&actor.hasLineOfSight(target)&&s.state.canHit(target.getUUID(),0))damage(actor,target,s);
        }
    }
    private static void damage(LivingEntity actor,LivingEntity target,Session s) {
        var level=(ServerLevel)actor.level();float amount=s.state.move.damage*(s.state.empowered?1.65f:1);
        if(target.entityTags().contains("moonfist_training")) {
            if(target.getHealth()<30)target.setHealth(target.getMaxHealth());
            amount=Math.min(amount,target.getHealth()-1);
        }
        var source=new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STRIKE),actor);
        if(target.hurtServer(level,source,amount)) {
            s.hitPulse++;s.state.gainFocus(3);postureHit(target,s.state.empowered?55:amount*3.5f);
            Vec3 delta=target.position().subtract(actor.position()).normalize();target.push(delta.x*.12,.055,delta.z*.12);target.hurtMarked=true;
            burst(level,target.getBoundingBox().getCenter(),0xff6edb,6,.2);
            sound(target,SoundEvents.PLAYER_ATTACK_CRIT,.7f,s.state.move==Move.VOID?1.6f:1.05f);
            sync(actor,s);
        }
    }
    public static boolean allowDamage(LivingEntity defender,DamageSource source,float amount) {
        if(source.getEntity() instanceof LivingEntity attacker && staggered(attacker))return false;
        if(!equipped(defender)||!(source.getEntity() instanceof LivingEntity attacker))return true;
        Session s=session(defender);
        if(s.state.dodgeWindow()){s.state.gainFocus(8);s.hitPulse++;sync(defender,s);return false;}
        Vec3 from=attacker.position().subtract(defender.position()).multiply(1,0,1).normalize();
        if(!s.state.guarding||defender.getLookAngle().multiply(1,0,1).normalize().dot(from)<.25)return true;
        if(s.state.parryWindow()) {
            s.state.parried();stagger(attacker,30);s.hitPulse++;
            sound(defender,SoundEvents.AMETHYST_BLOCK_RESONATE,1,1.3f);
            burst((ServerLevel)defender.level(),defender.getEyePosition(),0x74fff8,24,.6);sync(defender,s);return false;
        }
        boolean blocked=s.state.absorb(amount);
        sound(defender,blocked?SoundEvents.SHIELD_BLOCK.value():SoundEvents.SHIELD_BREAK.value(),.8f,1);
        sync(defender,s);return !blocked;
    }
    public static void postureHit(LivingEntity target,float amount) {
        Posture p=POSTURE.computeIfAbsent(target.getUUID(),k->new Posture());
        long now=target.level().getGameTime();
        if(now<p.staggerUntil)return;
        if(now-p.lastHit>40)p.value=Math.max(0,p.value-(now-p.lastHit-40)*.4f);
        p.lastHit=now;p.value=Math.min(100,p.value+amount);
        if(p.value>=100){p.value=0;stagger(target,40);}
    }
    public static void stagger(LivingEntity target,int ticks) {
        var p=POSTURE.computeIfAbsent(target.getUUID(),k->new Posture());p.staggerUntil=target.level().getGameTime()+ticks;
        if(target instanceof Mob mob)mob.getNavigation().stop();
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,ticks,5,false,false));
        if(STATES.containsKey(target.getUUID()))session(target).state.stagger();
    }
    public static boolean staggered(LivingEntity target) {var p=POSTURE.get(target.getUUID());return p!=null&&target.level().getGameTime()<p.staggerUntil;}
    public static float posture(LivingEntity target){var p=POSTURE.get(target.getUUID());return p==null?0:Math.max(0,p.value-Math.max(0,target.level().getGameTime()-p.lastHit-40)*.4f);}
    public static LivingEntity findTarget(LivingEntity actor,double range) {
        LivingEntity best=null;double bestScore=Double.MAX_VALUE;
        for(var target:actor.level().getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(range),t->validTarget(actor,t))) {
            if(!actor.hasLineOfSight(target)||actor.distanceTo(target)>range)continue;
            double dot=actor.getLookAngle().dot(target.getBoundingBox().getCenter().subtract(actor.getEyePosition()).normalize());
            if(dot<.45)continue;double score=actor.distanceTo(target)+(1-dot)*8;
            if(score<bestScore){best=target;bestScore=score;}
        }return best;
    }
    public static Vec3 trace(LivingEntity actor,Vec3 from,Vec3 to) {
        var hit=actor.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        return hit.getType()==HitResult.Type.MISS?to:hit.getLocation();
    }
    private static void sync(LivingEntity actor,Session s) {
        var target=((ServerLevel)actor.level()).getEntity(s.lock);
        var packet=new CombatSnapshot(actor.getId(),equipped(actor),s.state.move.ordinal(),s.state.age,s.state.stance,s.state.guarding,
            s.state.stamina,s.state.focus,s.state.waveCooldown,s.state.voidCooldown,s.lock,target instanceof LivingEntity t?posture(t):0,
            s.state.serial,s.hitPulse,s.origin,s.direction,s.aim);
        for(var p:PlayerLookup.tracking(actor))if(ServerPlayNetworking.canSend(p,CombatSnapshot.TYPE))ServerPlayNetworking.send(p,packet);
        if(actor instanceof ServerPlayer p && ServerPlayNetworking.canSend(p,CombatSnapshot.TYPE))ServerPlayNetworking.send(p,packet);
    }
    private static void burst(ServerLevel level,Vec3 pos,int color,int count,double spread) {
        level.sendParticles(new DustParticleOptions(color,1.2f),pos.x,pos.y,pos.z,count,spread,spread*.5,spread,.025);
    }
    private static void sound(LivingEntity entity,net.minecraft.sounds.SoundEvent sound,float volume,float pitch) {
        entity.level().playSound(null,entity.getX(),entity.getY(),entity.getZ(),sound,SoundSource.PLAYERS,volume,pitch);
    }
    public static void remove(UUID id){STATES.remove(id);POSTURE.remove(id);}
    public static void clear(){STATES.clear();POSTURE.clear();}
    public static void prune(MinecraftServer server) {
        if(server.getTickCount()%200!=0)return;
        long now=server.overworld().getGameTime();
        STATES.entrySet().removeIf(e->now-e.getValue().lastSeen>400);
        POSTURE.entrySet().removeIf(e->now-e.getValue().lastHit>600&&now>e.getValue().staggerUntil);
    }
}
