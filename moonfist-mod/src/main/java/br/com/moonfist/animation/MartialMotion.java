package br.com.moonfist.animation;

import br.com.moonfist.combat.Move;

/** Authored windup/impact/follow-through poses. Angles in radians, time in server ticks. */
public final class MartialMotion {
    public static final int RA=0,LA=3,RL=6,LL=9,BODY=12,HEAD=15,HEIGHT=18,TURN=19,ROLL=20,WEIGHT=21;
    private MartialMotion(){}
    public static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*t*(t*(t*6-15)+10);}
    public static float[] blend(float[] a,float[] b,float t){float[] p=new float[22];for(int i=0;i<p.length;i++)p[i]=a[i]+(b[i]-a[i])*t;return p;}
    private static void joint(float[] p,int j,float x,float y,float z){p[j]=x;p[j+1]=y;p[j+2]=z;}
    public static float[] stance(int stance,float clock){
        float[] p=new float[22];float breath=(float)Math.sin(clock*.09)*.025f;
        joint(p,RA,-.95f+breath,-.18f,.12f);joint(p,LA,-1.22f-breath,.25f,-.12f);
        joint(p,RL,.12f,0,.055f);joint(p,LL,-.16f,0,-.055f);joint(p,BODY,.04f,.16f,0);
        if(stance==1){joint(p,RA,-.85f,-.4f,.25f);joint(p,LA,-1.12f,.4f,-.25f);p[BODY+1]=-.12f;}
        if(stance==2){joint(p,RA,-1.05f,-.15f,.3f);joint(p,LA,-1.05f,.15f,-.3f);p[HEIGHT]=-.045f;p[BODY]=.1f;}
        return p;
    }
    public static float[] sample(Move move,int stance,boolean guard,float age,float clock){
        float[] base=stance(stance,clock),wind=base.clone(),hit=base.clone();
        if(guard){joint(base,RA,-1.7f,-.48f,.17f);joint(base,LA,-1.85f,.52f,-.17f);base[BODY]=.16f;base[HEIGHT]=-.055f;return base;}
        if(move==Move.IDLE)return base;
        float length=move.duration,phase=Math.max(0,Math.min(1,age/length));
        if(move==Move.DODGE){
            float tuck=ease(age/2)*(1-ease((age-8)/4));
            joint(hit,RA,-1.9f,-.12f,.2f);joint(hit,LA,-1.9f,.12f,-.2f);
            joint(hit,RL,-1.35f,0,.08f);joint(hit,LL,-1.35f,0,-.08f);hit[BODY]=.55f;hit[HEIGHT]=-.22f;hit[WEIGHT]=1;
            float[] p=blend(base,hit,tuck);p[ROLL]=(float)(-Math.PI*2)*ease((age-1)/10);return p;
        }
        if(move==Move.STAGGER||move==Move.GUARD_BREAK){
            joint(hit,RA,.3f,-.2f,.45f);joint(hit,LA,.2f,.2f,-.45f);hit[BODY]=-.24f;hit[HEAD]=.3f;hit[HEIGHT]=-.08f;
            return blend(base,hit,ease(age/2)*(1-ease((age-length+9)/9)));
        }
        if(move==Move.VOID){
            float charge=ease(age/11)*(1-ease((age-29)/11));
            joint(hit,RA,-2.65f,-.6f,.12f);joint(hit,LA,-1.6f,.15f,-.16f);hit[BODY+1]=-.24f;hit[HEIGHT]=-.06f;
            float[] p=blend(base,hit,charge);
            if(age>=14&&age<=29){float pulse=(float)Math.sin((age-14)*Math.PI)*.07f;p[RA]+=pulse;p[BODY+1]+=pulse*.5f;}
            return p;
        }
        float impact=move.hits[0];wind[WEIGHT]=hit[WEIGHT]=.85f;
        switch(move){
            case JAB,PALM -> {joint(wind,LA,-.65f,.5f,-.1f);wind[BODY+1]=-.18f;
                joint(hit,LA,-1.57f,-.09f,-.04f);hit[BODY+1]=.48f;hit[BODY]=.14f;hit[LL]=-.28f;}
            case CROSS -> {joint(wind,RA,-.6f,-.55f,.15f);wind[BODY+1]=.42f;
                joint(hit,RA,-1.57f,.12f,.02f);hit[BODY+1]=-.55f;hit[BODY]=.17f;hit[RL]=-.24f;}
            case HOOK -> {joint(wind,RA,-.9f,-1.1f,.45f);wind[BODY+1]=.55f;
                joint(hit,RA,-1.48f,.7f,.16f);hit[BODY+1]=-.65f;hit[BODY+2]=-.1f;}
            case LOW_KICK,QUICK_HEAVY -> {joint(wind,RL,-.55f,-.4f,.16f);wind[BODY]=.18f;wind[HEIGHT]=-.06f;
                joint(hit,RL,move==Move.LOW_KICK?-1.05f:-1.55f,-.18f,.08f);hit[BODY]=-.24f;hit[BODY+1]=-.3f;hit[LA]=-.75f;hit[LL]=.12f;}
            case SPIN_KICK -> {joint(wind,LL,-.5f,.45f,-.22f);wind[BODY]=.18f;
                joint(hit,LL,-.25f,.2f,-1.45f);hit[BODY+2]=.3f;hit[RA]=-.6f;hit[LA]=-.7f;hit[HEIGHT]=.06f;}
            case HAMMER,POWER_HEAVY -> {joint(wind,RA,-2.8f,-.25f,.2f);wind[BODY]=-.14f;wind[BODY+1]=.35f;
                joint(hit,RA,-1.15f,.12f,.05f);hit[BODY]=.36f;hit[BODY+1]=-.4f;hit[HEIGHT]=-.1f;}
            case UPPERCUT -> {joint(wind,RA,-.25f,-.3f,.2f);wind[BODY]=.28f;wind[HEIGHT]=-.12f;wind[BODY+1]=.35f;
                joint(hit,RA,-2.25f,.15f,.05f);hit[BODY]=-.15f;hit[BODY+1]=-.45f;hit[HEIGHT]=.05f;}
            case SLAM -> {joint(wind,RA,-2.8f,-.18f,.1f);joint(wind,LA,-2.8f,.18f,-.1f);wind[BODY]=-.14f;wind[HEIGHT]=.07f;
                joint(hit,RA,-1.15f,-.08f,0);joint(hit,LA,-1.15f,.08f,0);hit[BODY]=.5f;hit[HEIGHT]=-.16f;hit[RL]=.25f;hit[LL]=-.3f;}
            case MID_HEAVY,WAVE -> {joint(wind,RA,-.5f,-.55f,.35f);joint(wind,LA,-.5f,.55f,-.35f);wind[BODY]=.14f;wind[HEIGHT]=-.06f;
                joint(hit,RA,-1.55f,.05f,.05f);joint(hit,LA,-1.55f,-.05f,-.05f);hit[BODY]=.22f;hit[LL]=-.3f;}
            default -> {}
        }
        // Maximum extension is exactly at the server's damage tick, with a short follow-through.
        float launch=Math.max(1,impact-2),settle=Math.min(length-2,impact+2);
        float[] follow=blend(hit,base,.12f);follow[BODY]=hit[BODY]*1.08f;
        float[] p=age<launch?blend(base,wind,ease(age/launch)):
            age<impact?blend(wind,hit,ease((age-launch)/(impact-launch))):
            age<settle?blend(hit,follow,ease((age-impact)/(settle-impact))):
            blend(follow,base,ease((age-settle)/(length-settle)));
        if(move==Move.SPIN_KICK)p[TURN]=(float)(Math.PI*2)*ease(age/length);
        return p;
    }
}
