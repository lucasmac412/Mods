package br.com.moonfist;

import br.com.moonfist.combat.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatStateTest {
    private static void ticks(CombatState s,int n){for(int i=0;i<n;i++)s.tick();}
    @Test void threeDistinctCombosAndHeavyAttacks(){
        for(int stance=0;stance<3;stance++){
            CombatState s=new CombatState();s.stance=stance;
            for(int combo=0;combo<3;combo++){
                assertTrue(s.request(CombatState.Input.LIGHT));assertEquals(Move.light(stance,combo),s.move);
                ticks(s,s.move.duration);assertEquals(Move.IDLE,s.move);
            }
            assertTrue(s.request(CombatState.Input.HEAVY));assertEquals(Move.heavy(stance),s.move);
        }
    }
    @Test void buffersOnlyOneLateInputAndChargesWhenItStarts(){
        var s=new CombatState();s.request(CombatState.Input.LIGHT);
        assertFalse(s.request(CombatState.Input.LIGHT));ticks(s,5);
        assertTrue(s.request(CombatState.Input.LIGHT));assertFalse(s.request(CombatState.Input.LIGHT));
        assertEquals(92,s.stamina);ticks(s,6);assertEquals(Move.CROSS,s.move);assertEquals(83,s.stamina);
        assertEquals(2,s.serial);
    }
    @Test void comboExpiresAfterRecovery(){var s=new CombatState();s.request(CombatState.Input.LIGHT);ticks(s,34);s.request(CombatState.Input.LIGHT);assertEquals(Move.JAB,s.move);}
    @Test void cannotSpendMissingStaminaOrCancelCommitment(){
        var s=new CombatState();s.stamina=7;assertFalse(s.request(CombatState.Input.LIGHT));assertEquals(7,s.stamina);
        s.stamina=100;assertTrue(s.request(CombatState.Input.HEAVY));assertFalse(s.request(CombatState.Input.DODGE));
    }
    @Test void hitWindowsAndLedgerSeparateAllEightCuts(){
        UUID target=UUID.randomUUID();var s=new CombatState();s.focus=60;assertTrue(s.request(CombatState.Input.VOID));int hits=0;
        for(int age=0;age<Move.VOID.duration;age++){int stage=Move.VOID.hitStage(age);if(stage>=0){hits++;assertTrue(s.canHit(target,stage));assertFalse(s.canHit(target,stage));}}
        assertEquals(8,hits);assertEquals(-1,Move.JAB.hitStage(2));assertEquals(0,Move.JAB.hitStage(3));
    }
    @Test void focusAndCooldownCannotBeBypassedByUnequipping(){
        var s=new CombatState();assertFalse(s.request(CombatState.Input.VOID));s.focus=60;assertTrue(s.request(CombatState.Input.VOID));
        assertEquals(0,s.focus);assertEquals(65,s.stamina);s.cancel();s.focus=100;
        assertFalse(s.request(CombatState.Input.VOID));assertEquals(240,s.voidCooldown);
        ticks(s,240);assertTrue(s.request(CombatState.Input.VOID));
    }
    @Test void repeatedGuardHeartbeatsDoNotRenewParryWindow(){
        var s=new CombatState();assertTrue(s.guard(true));assertTrue(s.parryWindow());ticks(s,4);s.guard(true);assertFalse(s.parryWindow());
        s.guard(false);s.guard(true);assertFalse(s.parryWindow());ticks(s,17);assertFalse(s.guarding);
    }
    @Test void dodgeHasShortBoundedInvulnerability(){
        var s=new CombatState();s.request(CombatState.Input.DODGE);
        for(int age=0;age<12;age++){assertEquals(age>=2&&age<=6,s.dodgeWindow());s.tick();}
        assertFalse(s.dodgeWindow());assertEquals(Move.IDLE,s.move);
    }
    @Test void guardBreakAndRiposteAreEarned(){
        var s=new CombatState();s.stamina=10;s.guard(true);assertFalse(s.absorb(10));assertEquals(Move.GUARD_BREAK,s.move);assertEquals(0,s.stamina);
        ticks(s,40);s.parried();assertEquals(22,s.focus);assertTrue(s.request(CombatState.Input.LIGHT));assertTrue(s.empowered);
        ticks(s,11);s.request(CombatState.Input.LIGHT);assertFalse(s.empowered);
    }
    @Test void regenHasDelayAndCapsAtOneHundred(){
        var s=new CombatState();s.request(CombatState.Input.LIGHT);ticks(s,18);assertEquals(92,s.stamina);s.tick();assertTrue(s.stamina>92);ticks(s,100);assertEquals(100,s.stamina);
        s.gainFocus(1000);assertEquals(100,s.focus);s.gainFocus(-1000);assertEquals(0,s.focus);
    }
}
