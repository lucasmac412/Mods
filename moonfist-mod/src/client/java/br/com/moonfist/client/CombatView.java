package br.com.moonfist.client;

import br.com.moonfist.combat.Move;
import br.com.moonfist.network.CombatSnapshot;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class CombatView {
    private record Sample(CombatSnapshot state,long received){}
    private static final Map<Integer,Sample> SAMPLES=new HashMap<>();
    private static long clock(){var level=Minecraft.getInstance().level;return level==null?0:level.getGameTime();}
    private CombatView(){}
    public static void receive(CombatSnapshot state){SAMPLES.put(state.entity(),new Sample(state,clock()));}
    public static CombatSnapshot get(int entity) {
        var sample=SAMPLES.get(entity);return sample==null||clock()-sample.received>60?null:sample.state;
    }
    public static float age(int entity,float partial) {
        var sample=SAMPLES.get(entity);if(sample==null||sample.state.move()==Move.IDLE.ordinal())return clock()+partial;
        return Math.min(Move.byId(sample.state.move()).duration,
            sample.state.age()+Math.max(0,clock()-sample.received)+partial);
    }
    public static void clear(){SAMPLES.clear();}
    public static CombatSnapshot idle(int id){return new CombatSnapshot(id,true,0,0,0,false,100,0,0,0,-1,0,0,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO);}
}
