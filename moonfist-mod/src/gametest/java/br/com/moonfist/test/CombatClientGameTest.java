package br.com.moonfist.test;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.client.CombatView;
import br.com.moonfist.combat.*;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** Actual Minecraft + networking + OpenGL, not a standalone mock animation. */
public final class CombatClientGameTest implements FabricClientGameTest {
    private static final Path IMAGES=Path.of("../../reports/client").toAbsolutePath().normalize();
    private static void image(ClientGameTestContext c,String name){c.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(IMAGES));}
    @Override public void runTest(ClientGameTestContext c){
        c.getInput().resizeWindow(1280,720);
        c.runOnClient(mc->{mc.options.renderDistance().set(3);mc.options.simulationDistance().set(3);mc.options.pauseOnLostFocus=false;mc.options.fov().set(70);});
        try(var world=c.worldBuilder().adjustSettings(s->s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()){
            var server=world.getServer();var conn=world.getConnection();
            server.runCommand("time set noon");server.runCommand("weather clear");
            UUID target=server.computeOnServer(ignored->{
                var level=conn.getServerLevel();var p=conn.getServerPlayer();
                for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)level.setBlockAndUpdate(new BlockPos(x,160,z),
                    (Math.abs(x)==16||Math.abs(z)==16?Blocks.POLISHED_BLACKSTONE_BRICKS:((x+z)%4==0?Blocks.CHISELED_STONE_BRICKS:Blocks.SMOOTH_STONE)).defaultBlockState());
                p.teleportTo(level,0,161,-3,Set.of(),0,0,false);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MoonfistMod.FISTS));
                p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                var mob=MoonfistMod.DISCIPLE.create(level,EntitySpawnReason.COMMAND);mob.setNoAi(true);mob.snapTo(0,161,-.5,180,0);
                mob.yBodyRot=180;mob.setYHeadRot(180);
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);mob.setHealth(300);level.addFreshEntity(mob);return mob.getUUID();
            });
            conn.waitForChunksDownload();conn.waitForClientboundEntityUpdates(MoonfistMod.DISCIPLE);conn.waitForChunksRender();c.waitTicks(15);
            c.runOnClient(mc->mc.gui.hud.getChat().clearMessages(true));
            c.getInput().lookAt(0,0);c.waitTicks(3);
            c.runOnClient(mc->{if(mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK)throw new AssertionError("Gloves must select third person once");});
            image(c,"01-stance-and-hud");
            float before=server.computeOnServer(ignored->conn.getServerLevel().getEntity(target) instanceof net.minecraft.world.entity.LivingEntity t?t.getHealth():0f);
            c.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT,1);c.waitTicks(4);image(c,"02-real-left-click-jab");c.waitTicks(12);
            float after=server.computeOnServer(ignored->((net.minecraft.world.entity.LivingEntity)conn.getServerLevel().getEntity(target)).getHealth());
            if(after>=before)throw new AssertionError("Actual mouse jab must damage target on server");
            c.getInput().pressKey(GLFW.GLFW_KEY_Z);conn.waitForServerboundPackets();c.waitTicks(4);
            if(server.computeOnServer(ignored->CombatService.session(conn.getServerPlayer()).state.stance)!=1)throw new AssertionError("Z changes stance");
            c.getInput().pressKey(GLFW.GLFW_KEY_C);conn.waitForServerboundPackets();c.waitTicks(5);
            if(server.computeOnServer(ignored->CombatService.session(conn.getServerPlayer()).lock)==-1)throw new AssertionError("C must lock visible target");
            c.getInput().holdKey(GLFW.GLFW_KEY_G);c.waitTicks(6);
            if(!server.computeOnServer(ignored->CombatService.session(conn.getServerPlayer()).state.guarding))throw new AssertionError("G must hold server guard");
            image(c,"03-guard-and-target-posture");c.getInput().releaseKey(GLFW.GLFW_KEY_G);c.waitTicks(4);
            var start=server.computeOnServer(ignored->conn.getServerPlayer().position());
            c.getInput().pressKey(GLFW.GLFW_KEY_LEFT_ALT);c.waitTicks(5);image(c,"04-directional-dodge");c.waitTicks(10);
            var end=server.computeOnServer(ignored->conn.getServerPlayer().position());
            if(start.distanceTo(end)<.4)throw new AssertionError("Alt must move actual server player");
            server.runOnServer(ignored->{
                var p=conn.getServerPlayer();p.teleportTo(conn.getServerLevel(),0,161,-6,Set.of(),0,0,false);
                CombatService.session(p).state.stamina=100;CombatService.session(p).state.focus=100;
            });conn.waitForClientboundPackets();c.waitTicks(8);c.getInput().lookAt(0,0);
            c.getInput().pressKey(GLFW.GLFW_KEY_R);c.waitTicks(10);
            if(server.computeOnServer(ignored->CombatService.session(conn.getServerPlayer()).state.waveCooldown)<=0)throw new AssertionError("R must start shockwave on server");
            image(c,"05-shockwave");c.waitTicks(26);
            server.runOnServer(ignored->CombatService.session(conn.getServerPlayer()).state.stamina=100);
            c.getInput().pressKey(GLFW.GLFW_KEY_CAPS_LOCK);c.waitTicks(10);image(c,"06-void-telegraph");c.waitTicks(9);image(c,"07-eight-void-cuts");c.waitTicks(30);
            if(server.computeOnServer(ignored->CombatService.session(conn.getServerPlayer()).state.voidCooldown)<=0)throw new AssertionError("Caps special was not sent");
            c.runOnClient(mc->mc.options.setCameraType(CameraType.FIRST_PERSON));c.waitTicks(3);image(c,"08-first-person-compact-gloves");
            c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);c.waitForScreen(PauseScreen.class);c.getInput().pressKey(GLFW.GLFW_KEY_R);c.waitTicks(2);
            c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);c.waitFor(mc->mc.gui.screen()==null);
            // Regression: no global right-click cancellation, even after equipping/unequipping gloves.
            server.runOnServer(ignored->{var p=conn.getServerPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COW_SPAWN_EGG));p.teleportTo(conn.getServerLevel(),8,161,-5,Set.of(),0,60,false);});
            conn.waitForClientboundPackets();c.waitTicks(5);c.getInput().lookAt(new BlockPos(8,160,-3));
            c.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_RIGHT,1);conn.waitForServerboundPackets();c.waitTicks(4);
            int cows=server.computeOnServer(ignored->conn.getServerLevel().getEntities(EntityTypes.COW,e->e.distanceTo(conn.getServerPlayer())<10).size());
            if(cows<1)throw new AssertionError("Vanilla cow spawn egg must still work after using gloves");image(c,"09-vanilla-eggs-still-work");
            server.runOnServer(ignored->{
                var p=conn.getServerPlayer();p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;p.onUpdateAbilities();
                p.teleportTo(conn.getServerLevel(),0,161,-3,Set.of(),0,0,false);p.setHealth(20);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MoonfistMod.FISTS));CombatService.session(p).state.cancel();
                var npc=(br.com.moonfist.entity.LunarDisciple)conn.getServerLevel().getEntity(target);
                npc.snapTo(0,161,-.8,180,0);CombatService.session(npc).state.cancel();CombatService.session(npc).state.stamina=100;npc.setNoAi(false);
            });
            server.waitFor(ignored->conn.getServerPlayer().getHealth()<20,180);image(c,"10-survival-duel");
            server.runOnServer(ignored->{
                var p=conn.getServerPlayer();p.setGameMode(GameType.CREATIVE);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                p.teleportTo(conn.getServerLevel(),3,161,-4,Set.of(),35,0,false);
                var npc=(br.com.moonfist.entity.LunarDisciple)conn.getServerLevel().getEntity(target);
                npc.setNoAi(true);npc.snapTo(0,161,0,180,0);npc.setYHeadRot(180);npc.yBodyRot=180;
            });
            c.waitTicks(45);c.runOnClient(mc->mc.options.setCameraType(CameraType.FIRST_PERSON));
            c.getInput().lookAt(new BlockPos(0,162,0));c.getInput().pressKey(GLFW.GLFW_KEY_F1);
            int frame=0;
            for(Move move:new Move[]{Move.JAB,Move.HOOK,Move.SPIN_KICK,Move.POWER_HEAVY,Move.DODGE,Move.VOID}){
                server.runOnServer(ignored->{
                    var npc=(br.com.moonfist.entity.LunarDisciple)conn.getServerLevel().getEntity(target);
                    var state=CombatService.session(npc).state;state.cancel();state.stamina=100;state.focus=100;state.voidCooldown=0;
                    state.stance=move==Move.HOOK||move==Move.SPIN_KICK?1:move==Move.POWER_HEAVY?2:0;
                    state.combo=move==Move.HOOK?1:move==Move.SPIN_KICK?2:0;state.comboGrace=22;
                    CombatService.request(npc,move==Move.POWER_HEAVY?CombatState.Input.HEAVY:move==Move.DODGE?CombatState.Input.DODGE:move==Move.VOID?CombatState.Input.VOID:CombatState.Input.LIGHT);
                });
                for(int tick=0;tick<move.duration+4;tick+=2){c.waitTicks(2);image(c,String.format(java.util.Locale.ROOT,"motion-%03d",frame++));}
            }
            System.out.println("MOONFIST CLIENT PASS: real mouse damage, stance, lock, guard, dodge, wave, Caps special, first-person render, menus and vanilla eggs");
        }
    }
}
