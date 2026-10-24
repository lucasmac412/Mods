package br.com.moonfist.client;

import br.com.moonfist.combat.Move;
import br.com.moonfist.network.CombatSnapshot;
import net.minecraft.client.model.HumanoidModel;

/** Original smooth poses, evaluated at render partial ticks instead of snapping each server tick. */
public final class CombatPose {
    private CombatPose(){}
    public static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float strike(Move move,float age) {
        if(!move.attack())return 0;
        float hit=move.hits.length==0?8:move.hits[0];
        if(age<hit-1)return .18f*smooth(age/Math.max(1,hit-1));
        if(age<hit+1.2f)return .18f+.82f*smooth((age-hit+1)/2.2f);
        return 1-smooth((age-hit-1.2f)/Math.max(1,move.duration-hit-1.2f));
    }
    public static void apply(HumanoidModel<?> model,CombatSnapshot state,float age) {
        Move move=Move.byId(state.move());
        float breathe=(float)Math.sin(age*.12)*.035f;
        model.rightArm.xRot=-.95f+breathe;model.rightArm.yRot=-.26f;model.rightArm.zRot=.16f;
        model.leftArm.xRot=-1.08f-breathe;model.leftArm.yRot=.32f;model.leftArm.zRot=-.18f;
        model.body.yRot=.13f;model.body.xRot=.07f;
        if(state.stance()==0){model.leftLeg.xRot-=.12f;model.rightLeg.xRot+=.12f;}
        if(state.stance()==2){model.body.yRot=-.17f;model.rightArm.xRot=-.7f;model.leftArm.xRot=-.7f;}
        if(state.guard()) {
            model.rightArm.xRot=model.leftArm.xRot=-1.65f;
            model.rightArm.yRot=-.75f;model.leftArm.yRot=.75f;
            model.rightArm.zRot=.25f;model.leftArm.zRot=-.25f;model.body.xRot=.18f;return;
        }
        float s=strike(move,age);
        float hit=move.hits.length==0?8:move.hits[0];
        float wind=smooth(age/Math.max(1,hit-1)),drop=smooth((age-hit+1)/3),recover=smooth((age-hit-2)/Math.max(1,move.duration-hit-2));
        switch(move) {
            case JAB,PALM -> {model.leftArm.xRot=-1.1f-.9f*s;model.leftArm.yRot=.3f-.55f*s;model.body.yRot=.13f+.4f*s;}
            case CROSS -> {model.rightArm.xRot=-.95f-1.15f*s;model.rightArm.yRot=-.26f+.7f*s;model.body.yRot=.13f-.65f*s;}
            case HOOK -> {model.rightArm.xRot=-1.25f-.35f*s;model.rightArm.yRot=-1.1f+1.9f*s;model.rightArm.zRot=.7f*s;model.body.yRot=-.6f*s;}
            case LOW_KICK,QUICK_HEAVY -> {model.rightLeg.xRot=-1.65f*s;model.rightLeg.yRot=-.5f*s;model.body.xRot=-.3f*s;model.leftArm.xRot=-.7f;}
            case SPIN_KICK -> {model.leftLeg.xRot=-1.3f*s;model.leftLeg.zRot=-.85f*s;model.body.yRot=.25f*s;model.body.xRot=.2f*s;}
            case HAMMER,POWER_HEAVY -> {model.rightArm.xRot=-1.1f-1.9f*wind*(1-drop)+.7f*drop*(1-recover);model.rightArm.zRot=.35f*wind*(1-drop);model.body.xRot=.55f*s;model.body.yRot=-.5f*s;}
            case UPPERCUT -> {model.rightArm.xRot=-.6f-2.25f*s;model.rightArm.yRot=.6f*s;model.body.xRot=-.3f*s;model.rightLeg.xRot=.3f*s;}
            case SLAM -> {model.rightArm.xRot=model.leftArm.xRot=-.95f-1.95f*wind*(1-drop)+.25f*drop*(1-recover);model.body.xRot=.7f*s;model.leftLeg.xRot=-.4f*s;model.rightLeg.xRot=.45f*s;}
            case MID_HEAVY,WAVE -> {model.leftArm.xRot=model.rightArm.xRot=-.7f-1.25f*s;model.leftArm.yRot=.55f*(1-s);model.rightArm.yRot=-.55f*(1-s);model.body.xRot=.22f*s;}
            case VOID -> {float p=smooth(age/12)*(1-smooth((age-28)/12));model.leftArm.xRot=-1.08f-.72f*p;model.rightArm.xRot=-.95f-1.7f*p;model.rightArm.yRot=-.26f-.54f*p;model.body.yRot=.3f*p;}
            case DODGE -> {model.body.xRot=.8f;model.leftLeg.xRot=-1.2f;model.rightLeg.xRot=-.8f;model.leftArm.xRot=model.rightArm.xRot=-1.8f;}
            case GUARD_BREAK,STAGGER -> {model.body.xRot=.7f;model.head.xRot=.65f;model.leftArm.xRot=.3f;model.rightArm.xRot=.4f;}
            default -> {}
        }
    }
}
