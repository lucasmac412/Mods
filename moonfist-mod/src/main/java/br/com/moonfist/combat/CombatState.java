package br.com.moonfist.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned combat clock; no frame-time damage, held-click repeat or client-authored costs. */
public final class CombatState {
    public enum Input { LIGHT, HEAVY, WAVE, VOID, DODGE }
    public Move move=Move.IDLE;
    public int age,stance,combo,comboGrace,regenDelay,waveCooldown,voidCooldown,riposteTicks;
    public float stamina=100,focus;
    public boolean guarding,empowered;
    public long serial;
    public double dodgeX,dodgeZ;
    private Input queued;
    private int queuedFor;
    private int guardAge,guardLease,parryCooldown;
    private final Map<Integer, Set<UUID>> hitLedger=new HashMap<>();

    public boolean request(Input input) {
        if(input==null)return false;
        if(move!=Move.IDLE) {
            if((input==Input.LIGHT||input==Input.HEAVY) && move.attack()
                    && age>=move.duration-6 && queued==null) { queued=input;queuedFor=8;return true; }
            return false;
        }
        if(guarding && input!=Input.DODGE)return false;
        Move next=switch(input) {
            case LIGHT -> Move.light(stance,comboGrace>0?combo:0);
            case HEAVY -> Move.heavy(stance);
            case WAVE -> Move.WAVE;
            case VOID -> Move.VOID;
            case DODGE -> Move.DODGE;
        };
        if(stamina<next.cost || next==Move.WAVE&&waveCooldown>0
                || next==Move.VOID&&(voidCooldown>0||focus<60))return false;
        stamina-=next.cost;regenDelay=18;guarding=false;guardLease=0;
        if(next==Move.WAVE)waveCooldown=50;
        if(next==Move.VOID){voidCooldown=240;focus-=60;}
        empowered=next.attack()&&riposteTicks>0;
        if(empowered)riposteTicks=0;
        begin(next);return true;
    }
    private void begin(Move next) { move=next;age=0;serial++;hitLedger.clear(); }
    public void tick() {
        if(waveCooldown>0)waveCooldown--;if(voidCooldown>0)voidCooldown--;if(riposteTicks>0)riposteTicks--;
        if(parryCooldown>0)parryCooldown--;if(guardLease>0)guardLease--;else guarding=false;
        if(guarding) { guardAge++;stamina=Math.max(0,stamina-.22f);if(stamina<=0)breakGuard(); }
        if(regenDelay>0)regenDelay--;
        else if(move==Move.IDLE)stamina=Math.min(100,stamina+(guarding?.3f:1.8f));
        if(queuedFor>0)queuedFor--;else queued=null;
        if(move!=Move.IDLE) {
            age++;
            if(age>=move.duration) {
                if(move.light()){combo=(combo+1)%3;comboGrace=22;}else{combo=0;comboGrace=0;}
                move=Move.IDLE;age=0;empowered=false;
                Input buffered=queued;queued=null;
                if(buffered!=null)request(buffered);
            }
        } else if(comboGrace>0)comboGrace--;
    }
    public boolean guard(boolean held) {
        if(!held){guarding=false;guardLease=0;return true;}
        if(move!=Move.IDLE||stamina<5)return false;
        if(!guarding){guardAge=parryCooldown==0?0:10;parryCooldown=14;}
        guarding=true;guardLease=16;return true;
    }
    public boolean parryWindow(){return guarding&&guardAge<4;}
    public boolean dodgeWindow(){return move==Move.DODGE&&age>=2&&age<=6;}
    public boolean canHit(UUID target,int stage) { return hitLedger.computeIfAbsent(stage,k->new HashSet<>()).add(target); }
    public void gainFocus(float amount){focus=Math.max(0,Math.min(100,focus+amount));}
    public void parried(){gainFocus(22);stamina=Math.min(100,stamina+14);riposteTicks=35;}
    public boolean absorb(float damage) {
        float cost=Math.max(8,damage*3);
        if(stamina>=cost){stamina-=cost;regenDelay=20;return true;}
        breakGuard();return false;
    }
    private void breakGuard(){guarding=false;guardLease=0;stamina=0;regenDelay=25;queued=null;begin(Move.GUARD_BREAK);}
    public void stagger(){guarding=false;queued=null;regenDelay=30;begin(Move.STAGGER);}
    public void cancel(){move=Move.IDLE;age=0;guarding=false;guardLease=0;queued=null;combo=0;comboGrace=0;hitLedger.clear();}
    public static String stanceName(int stance){return switch(Math.floorMod(stance,3)){case 0->"Fluxo";case 1->"Lua";default->"Impacto";};}
}
