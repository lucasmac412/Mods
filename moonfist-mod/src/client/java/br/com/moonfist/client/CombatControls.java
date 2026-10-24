package br.com.moonfist.client;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.combat.CombatService;
import br.com.moonfist.network.CombatIntent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

public final class CombatControls {
    public static final KeyMapping DODGE=key("dodge",GLFW.GLFW_KEY_LEFT_ALT),GUARD=key("guard",GLFW.GLFW_KEY_G),
        LOCK=key("lock",GLFW.GLFW_KEY_C),STANCE=key("stance",GLFW.GLFW_KEY_Z),WAVE=key("wave",GLFW.GLFW_KEY_R),
        VOID=key("void",GLFW.GLFW_KEY_CAPS_LOCK);
    private static boolean activeBefore,guardBefore,tutorial;
    private static CameraType previous;
    private static long lastAttackTick=-1;private static int lastAttack=-1;
    private CombatControls(){}
    private static KeyMapping key(String name,int key){return new KeyMapping("key.moonfist."+name,key,KeyMapping.Category.GAMEPLAY);}
    public static boolean active(Minecraft client){return client.player!=null&&CombatService.equipped(client.player)&&client.gui.screen()==null;}
    public static void register() {
        for(var key:new KeyMapping[]{DODGE,GUARD,LOCK,STANCE,WAVE,VOID})KeyMappingHelper.registerKeyMapping(key);
        ClientTickEvents.END_CLIENT_TICK.register(CombatControls::tick);
    }
    public static void attack(Minecraft client,boolean heavy) {
        if(!active(client))return;
        long tick=client.level.getGameTime();int action=heavy?CombatIntent.HEAVY:CombatIntent.LIGHT;
        if(tick==lastAttackTick&&action==lastAttack)return;
        lastAttackTick=tick;lastAttack=action;send(client,action);
    }
    private static void tick(Minecraft client) {
        boolean equipped=client.player!=null&&CombatService.equipped(client.player);
        if(equipped&&!activeBefore) {
            previous=client.options.getCameraType();client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if(!tutorial){client.player.sendOverlayMessage(Component.literal("Punhos da Lua Azul · Alt: esquiva | G: defesa | Z: postura"));tutorial=true;}
        }
        if(!equipped&&activeBefore&&previous!=null){
            if(client.player==null||!client.player.isPassenger())client.options.setCameraType(previous);
            previous=null;
        }
        activeBefore=equipped;
        boolean ready=active(client);
        boolean guard=ready&&GUARD.isDown();
        if(guard!=guardBefore||guard&&client.player.tickCount%8==0)send(client,guard?CombatIntent.GUARD_ON:CombatIntent.GUARD_OFF);
        guardBefore=guard;
        if(!ready){for(var key:new KeyMapping[]{DODGE,LOCK,STANCE,WAVE,VOID})while(key.consumeClick()){}return;}
        while(DODGE.consumeClick())send(client,CombatIntent.DODGE);
        while(LOCK.consumeClick())send(client,CombatIntent.LOCK);
        while(STANCE.consumeClick())send(client,CombatIntent.STANCE);
        while(WAVE.consumeClick())send(client,CombatIntent.WAVE);
        while(VOID.consumeClick())send(client,CombatIntent.VOID);
        var state=CombatView.get(client.player.getId());
        if(state!=null&&state.lockTarget()!=-1&&client.level.getEntity(state.lockTarget()) instanceof LivingEntity target&&target.isAlive()) {
            var direction=target.getBoundingBox().getCenter().subtract(client.player.getEyePosition());
            float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)));
            client.player.setYRot(Mth.approachDegrees(client.player.getYRot(),yaw,16));
            client.player.setXRot(Mth.approachDegrees(client.player.getXRot(),Mth.clamp(pitch,-60,60),9));
        }
    }
    private static void send(Minecraft client,int action) {
        if(client.player==null||!ClientPlayNetworking.canSend(CombatIntent.TYPE))return;
        float x=(client.options.keyLeft.isDown()?1:0)-(client.options.keyRight.isDown()?1:0);
        float z=(client.options.keyUp.isDown()?1:0)-(client.options.keyDown.isDown()?1:0);
        ClientPlayNetworking.send(new CombatIntent(action,x,z));
    }
}
