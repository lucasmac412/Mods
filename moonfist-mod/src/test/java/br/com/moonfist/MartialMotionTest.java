package br.com.moonfist;
import br.com.moonfist.animation.MartialMotion;
import br.com.moonfist.combat.Move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MartialMotionTest {
    @Test void everyClipIsFiniteContinuousAndRecoversWithoutSnapping(){
        for(Move m:Move.values())for(int stance=0;stance<3;stance++){
            float[] last=MartialMotion.sample(m,stance,false,0,0);
            for(float t=.025f;t<=m.duration;t+=.025f){
                float[] p=MartialMotion.sample(m,stance,false,t,0);
                for(int i=0;i<p.length;i++){assertTrue(Float.isFinite(p[i]),m.toString());assertTrue(Math.abs(p[i]-last[i])<.14f,m+" channel "+i+" discontinuity at "+t);}
                last=p;
            }
            if(m!=Move.IDLE){
                float[] end=MartialMotion.sample(m,stance,false,m.duration,0),rest=MartialMotion.stance(stance,0);
                for(int i=0;i<end.length;i++)if(i!=MartialMotion.ROLL&&i!=MartialMotion.TURN)assertEquals(rest[i],end[i],.001f,m+" recovery "+i);
            }
        }
    }
    @Test void straightPunchIsExtendedAtDamageTick(){
        var p=MartialMotion.sample(Move.JAB,0,false,Move.JAB.hits[0],0);
        assertEquals(-1.57f,p[MartialMotion.LA],.001f);
        var early=MartialMotion.sample(Move.JAB,0,false,1,0);
        assertTrue(p[MartialMotion.LA]<early[MartialMotion.LA]);
    }
    @Test void spinsAreOneDirectionAndFinishFullTurn(){
        for(Move m:new Move[]{Move.SPIN_KICK,Move.DODGE}){
            int channel=m==Move.DODGE?MartialMotion.ROLL:MartialMotion.TURN;
            float previous=0;
            for(float t=0;t<=m.duration;t+=.1f){float v=Math.abs(MartialMotion.sample(m,1,false,t,0)[channel]);assertTrue(v>=previous-.0001);previous=v;}
            assertEquals(Math.PI*2,previous,.005);
        }
    }
}
