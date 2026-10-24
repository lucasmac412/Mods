package br.com.moonfist.test;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.combat.*;
import br.com.moonfist.network.CombatIntent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class CombatGameTests {
    private static final String ARENA="moonfist_gametest:arena";
    private static ServerPlayer fighter(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);
        p.snapTo(h.absoluteVec(new Vec3(16,4,16)),0,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MoonfistMod.FISTS));return p;
    }
    private static br.com.moonfist.entity.LunarDisciple defender(GameTestHelper h){
        var p=MoonfistMod.DISCIPLE.create(h.getLevel(),EntitySpawnReason.COMMAND);p.setNoAi(true);p.setNoGravity(true);
        p.snapTo(h.absoluteVec(new Vec3(16,4,16)),0,0);h.getLevel().addFreshEntity(p);return p;
    }
    private static Cow cow(GameTestHelper h,double z){
        var mob=EntityTypes.COW.create(h.getLevel(),EntitySpawnReason.COMMAND);
        mob.snapTo(h.absoluteVec(new Vec3(16,4,z)),0,0);mob.setNoAi(true);mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);mob.setHealth(200);h.getLevel().addFreshEntity(mob);return mob;
    }
    private static void ticks(LivingEntity p,int n){for(int i=0;i<n;i++)CombatService.tickActor(p);}
    private static void close(GameTestHelper h,ServerPlayer p){CombatService.remove(p.getUUID());h.getLevel().getServer().getPlayerList().remove(p);h.succeed();}
    private static void same(GameTestHelper h,float a,float b,String why){h.assertTrue(Math.abs(a-b)<.02,why+" expected="+a+" actual="+b);}
    @GameTest(structure=ARENA,maxTicks=40) public void jabHasWindupAndSingleHit(GameTestHelper h){
        var p=fighter(h);var t=cow(h,18);h.assertTrue(CombatService.request(p,CombatState.Input.LIGHT),"start jab");
        ticks(p,2);same(h,200,t.getHealth(),"no early damage");ticks(p,1);same(h,197.5f,t.getHealth(),"jab at authored hit tick");
        ticks(p,8);same(h,197.5f,t.getHealth(),"no duplicate damage from held/animation frames");close(h,p);
    }
    @GameTest(structure=ARENA,maxTicks=40) public void everyStanceAndHeavyUsesItsAuthoredDamage(GameTestHelper h){
        var p=fighter(h);var state=CombatService.session(p).state;
        for(int stance=0;stance<3;stance++)for(int combo=0;combo<4;combo++){
            state.cancel();state.stamina=100;state.stance=stance;state.combo=combo%3;state.comboGrace=22;
            var t=cow(h,18);CombatService.request(p,combo==3?CombatState.Input.HEAVY:CombatState.Input.LIGHT);
            Move move=state.move;ticks(p,move.duration);same(h,200-move.damage,t.getHealth(),"authored "+move.title);t.discard();
        }close(h,p);
    }
    @GameTest(structure=ARENA,maxTicks=40) public void voidCutsApplyEightRealDamageStages(GameTestHelper h){
        var p=fighter(h);var t=cow(h,23);CombatService.session(p).state.focus=60;
        h.assertTrue(CombatService.request(p,CombatState.Input.VOID),"earned focus allows special");ticks(p,13);same(h,200,t.getHealth(),"special telegraph safe");
        ticks(p,15);same(h,186,t.getHealth(),"eight cuts bypass only vanilla hit cooldown");close(h,p);
    }
    @GameTest(structure=ARENA,maxTicks=40) public void wallsBlockBothMeleeAndShockwaves(GameTestHelper h){
        var p=fighter(h);var t=cow(h,18);
        for(int x=15;x<=17;x++)for(int y=4;y<=7;y++)h.setBlock(x,y,17,Blocks.STONE);
        CombatService.request(p,CombatState.Input.LIGHT);ticks(p,11);same(h,200,t.getHealth(),"wall blocks jab");
        CombatService.request(p,CombatState.Input.WAVE);ticks(p,28);same(h,200,t.getHealth(),"wave cannot pass through wall");
        h.assertTrue(h.getBlockState(new net.minecraft.core.BlockPos(16,5,17)).is(Blocks.STONE),"combat does not grief blocks");close(h,p);
    }
    @GameTest(structure=ARENA,maxTicks=40) public void shockwaveHasRangeAndDoesNotHitTwice(GameTestHelper h){
        var p=fighter(h);var t=cow(h,24);var far=cow(h,34);
        CombatService.request(p,CombatState.Input.WAVE);ticks(p,28);same(h,194.5f,t.getHealth(),"moving wave hits once");same(h,200,far.getHealth(),"outside fourteen blocks safe");close(h,p);
    }
    @GameTest(structure=ARENA,maxTicks=40) public void frontalParryCreatesRiposteAndStopsEnemy(GameTestHelper h){
        var p=defender(h);var t=cow(h,18);var s=CombatService.session(p).state;s.stamina=50;s.guard(true);
        t.hurtServer(h.getLevel(),t.damageSources().generic(),0); // Exercise native attributes before attack.
        p.hurtServer(h.getLevel(),p.damageSources().mobAttack(t),4);
        same(h,100,p.getHealth(),"front parry stops damage");h.assertTrue(s.focus==22&&s.riposteTicks>0,"parry earns focus and riposte");
        h.assertTrue(CombatService.staggered(t),"parried attacker staggered");h.assertTrue(CombatService.request(p,CombatState.Input.DODGE),"dodge can release guard");CombatService.remove(p.getUUID());h.succeed();
    }
    @GameTest(structure=ARENA,maxTicks=40) public void dodgingDoesNotProtectAgainstEnvironment(GameTestHelper h){
        var p=defender(h);var t=cow(h,18);CombatService.request(p,CombatState.Input.DODGE);ticks(p,2);
        p.hurtServer(h.getLevel(),p.damageSources().mobAttack(t),4);same(h,100,p.getHealth(),"dodge avoids combat in its window");
        p.hurtServer(h.getLevel(),p.damageSources().fall(),4);same(h,96,p.getHealth(),"fall damage remains dangerous");CombatService.remove(p.getUUID());h.succeed();
    }
    @GameTest(structure=ARENA,maxTicks=40) public void unequippingCancelsAndMalformedPacketsDoNothing(GameTestHelper h){
        var p=fighter(h);CombatService.handle(p,new CombatIntent(0,Float.NaN,0));h.assertTrue(CombatService.session(p).state.move==Move.IDLE,"NaN input rejected");
        CombatService.request(p,CombatState.Input.LIGHT);var t=cow(h,18);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);ticks(p,4);
        same(h,200,t.getHealth(),"unequip cancels pending hits");h.assertTrue(!CombatService.request(p,CombatState.Input.LIGHT),"unarmed requests denied");close(h,p);
    }
    @GameTest(structure=ARENA,maxTicks=40) public void discipleSpawnAndCooperativeSafety(GameTestHelper h){
        var p=fighter(h);var other=h.makeMockServerPlayerInLevel();other.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!CombatService.validTarget(p,other),"co-op ally protected");
        var disciple=MoonfistMod.DISCIPLE.create(h.getLevel(),EntitySpawnReason.SPAWN_ITEM_USE);
        disciple.finalizeSpawn(h.getLevel(),h.getLevel().getCurrentDifficultyAt(h.absolutePos(new net.minecraft.core.BlockPos(16,4,18))),EntitySpawnReason.SPAWN_ITEM_USE,null);
        h.assertTrue(disciple.getMainHandItem().is(MoonfistMod.FISTS)&&!disciple.isBaby(),"spawn egg keeps adult fighter and gloves");
        h.assertTrue(CombatService.validTarget(disciple,h.makeMockPlayer(GameType.SURVIVAL)),"duel opponent can attack survival player");
        h.getLevel().getServer().getPlayerList().remove(other);close(h,p);
    }
}
