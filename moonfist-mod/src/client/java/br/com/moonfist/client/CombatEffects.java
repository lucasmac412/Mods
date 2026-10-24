package br.com.moonfist.client;

import br.com.moonfist.combat.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class CombatEffects {
    private CombatEffects(){}
    public static void submit(LivingEntityRenderState state,PoseStack pose,SubmitNodeCollector collector) {
        if(!(state instanceof FighterRenderInfo info)||!info.moonfist$active())return;
        var s=CombatView.get(info.moonfist$id());if(s==null)return;
        Move move=Move.byId(s.move());float age=CombatView.age(info.moonfist$id(),info.moonfist$partial());
        Vec3 actor=new Vec3(state.x,state.y,state.z);
        collector.submitCustomGeometry(pose,RenderTypes.lightning(),(p,v)->{
            double pulse=Math.sin(state.ageInTicks*.18)*.035;
            if(s.guard()||move==Move.VOID||s.focus()>=60) {
                EnergyGeometry.ring(p,v,new Vec3(0,.025,0),new Vec3(0,1,0),.8+pulse,.025,0x8853e9f3);
                EnergyGeometry.ring(p,v,new Vec3(0,.03,0),new Vec3(0,1,0),1.04-pulse,.015,0x886e58ee);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;EnergyGeometry.streak(p,v,new Vec3(Math.cos(a)*.6,.035,Math.sin(a)*.6),new Vec3(Math.cos(a)*1.18,.035,Math.sin(a)*1.18),.012,0x887ee8ed);}
            }
            if(move.attack()&&move!=Move.WAVE&&move!=Move.VOID&&age>=move.hits[0]-2&&age<=move.hits[0]+4) {
                Vec3 center=new Vec3(0,.9,0).add(s.direction().scale(.85));
                EnergyGeometry.ring(p,v,center,s.direction(),.2+(age-move.hits[0]+2)*.13,.05,0xcc70f8ff);
                EnergyGeometry.streak(p,v,center.add(-.45,-.4,0),center.add(.45,.4,0),.03,0xddfa8ddf);
            }
            if(move==Move.WAVE&&age>=7&&age<=25) {
                for(int i=0;i<3;i++){
                    Vec3 c=s.origin().add(s.direction().scale((age-6)*.8-i*.3)).subtract(actor);
                    EnergyGeometry.ring(p,v,c,s.direction(),.72+i*.08,.065-i*.014,i==0?0xee9cffff:0x887653ec);
                }
            }
            if(move==Move.VOID) {
                Vec3 c=s.aim().subtract(actor);
                double radius=.35+Math.min(1,age/14)*2.0;
                EnergyGeometry.ring(p,v,new Vec3(c.x,.04,c.z),new Vec3(0,1,0),radius,.045,0xcc9e5bf4);
                EnergyGeometry.ring(p,v,c,new Vec3(0,1,0),radius*.6,.025,0x886ef2f2);
                for(int i=0;i<8;i++) {
                    float t=age-(14+i*2);if(t<0||t>4)continue;
                    double angle=i*2.399;
                    Vec3 d=new Vec3(Math.cos(angle)*2.3,Math.sin(angle)*2.1,Math.cos(angle*.7)*.8);
                    EnergyGeometry.streak(p,v,c.subtract(d),c.add(d),.24,0x889a55ff);
                    EnergyGeometry.streak(p,v,c.subtract(d),c.add(d),.075,0xffccffff);
                    EnergyGeometry.ring(p,v,c,s.direction(),.8+t*.35,.035,0x99ed83ee);
                }
                for(int i=0;i<6;i++) {
                    double a=i*Math.PI/3+age*.08;
                    Vec3 low=new Vec3(Math.cos(a)*.55,.1,Math.sin(a)*.55),mid=low.scale(1.15).add(.12,.7,-.1);
                    EnergyGeometry.streak(p,v,low,mid,.022,0xbb62e9ed);
                    EnergyGeometry.streak(p,v,mid,low.add(0,1.5,0),.018,0x99dd8bff);
                }
            }
            if(move==Move.DODGE) {
                for(int i=0;i<3;i++)EnergyGeometry.ring(p,v,new Vec3(0,.1+i*.35,0),new Vec3(0,1,0),.4+i*.08,.025,0x776ef7f5);
            }
        });
    }
}
