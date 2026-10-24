package br.com.moonfist.client;

import br.com.moonfist.animation.MartialMotion;
import br.com.moonfist.combat.Move;
import br.com.moonfist.network.CombatSnapshot;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class CombatView {
    private record Sample(CombatSnapshot state,long received,double started,float[] from,double changed){}
    private static final Map<Integer,Sample> SAMPLES=new HashMap<>();
    private static long clock(){var level=Minecraft.getInstance().level;return level==null?0:level.getGameTime();}
    private CombatView(){}
    public static void receive(CombatSnapshot state){
        long now=clock();var old=SAMPLES.get(state.entity());
        boolean action=old==null||old.state.serial()!=state.serial()||old.state.move()!=state.move();
        boolean change=action||old.state.guard()!=state.guard()||old.state.stance()!=state.stance();
        float[] from=change?(old==null?MartialMotion.stance(state.stance(),now):poseAt(old,now)):old.from;
        // A periodic network snapshot updates resources, without restarting the animation clock.
        SAMPLES.put(state.entity(),new Sample(state,now,action?now-state.age():old.started,from,change?now:old.changed));
        if(now%200==0)SAMPLES.entrySet().removeIf(e->now-e.getValue().received>100);
    }
    public static CombatSnapshot get(int entity){var s=SAMPLES.get(entity);return s==null||clock()-s.received>60?null:s.state;}
    public static float age(int entity,float partial){var s=SAMPLES.get(entity);return s==null?0:ageAt(s,clock()+partial);}
    private static float ageAt(Sample s,double now){return Move.byId(s.state.move())==Move.IDLE?(float)now:(float)Math.max(0,Math.min(Move.byId(s.state.move()).duration,now-s.started));}
    public static float[] pose(int entity,float partial){var s=SAMPLES.get(entity);double now=clock()+partial;return s==null?MartialMotion.stance(0,(float)now):poseAt(s,now);}
    private static float[] poseAt(Sample s,double now){
        float[] to=MartialMotion.sample(Move.byId(s.state.move()),s.state.stance(),s.state.guard(),ageAt(s,now),(float)now);
        float blend=MartialMotion.ease((float)(now-s.changed)/2f);
        float[] from=s.from.clone();
        for(int i:new int[]{MartialMotion.TURN,MartialMotion.ROLL}){
            float period=(float)(Math.PI*2);while(from[i]-to[i]>Math.PI)from[i]-=period;while(from[i]-to[i]<-Math.PI)from[i]+=period;
        }
        return MartialMotion.blend(from,to,blend);
    }
    public static void clear(){SAMPLES.clear();}
    public static CombatSnapshot idle(int id){return new CombatSnapshot(id,true,0,0,0,false,100,0,0,0,-1,0,0,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO);}
}
