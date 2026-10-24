package br.com.moonfist.client;

import br.com.moonfist.animation.MartialMotion;
import br.com.moonfist.combat.Move;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public final class CombatPose {
    private CombatPose(){}
    public static float smooth(float t){return MartialMotion.ease(t);}
    public static float strike(Move m,float age){if(!m.attack())return 0;float h=m.hits[0];return smooth((age-h+2)/2)*(1-smooth((age-h-1)/Math.max(1,m.duration-h-1)));}
    public static void apply(HumanoidModel<?> m,float[] p){
        ModelPart[] parts={m.rightArm,m.leftArm,m.rightLeg,m.leftLeg,m.body,m.head};
        for(int i=0;i<6;i++){
            ModelPart part=parts[i];int j=i*3;
            if(i==5){part.xRot+=p[j];part.yRot-=p[MartialMotion.BODY+1]*.3f;continue;}
            float gait=(i==2||i==3)?1-p[MartialMotion.WEIGHT]:0;
            part.xRot=part.xRot*gait+p[j];part.yRot=part.yRot*gait+p[j+1];part.zRot=part.zRot*gait+p[j+2];
        }
        // Shoulders follow torso rotation; hands no longer float beside a turning chest.
        float yaw=p[MartialMotion.BODY+1],sin=(float)Math.sin(yaw),cos=(float)Math.cos(yaw);
        m.rightArm.x=-5*cos;m.rightArm.z=5*sin;m.leftArm.x=5*cos;m.leftArm.z=-5*sin;
        m.rightArm.yRot+=yaw*.55f;m.leftArm.yRot+=yaw*.55f;
    }
}
