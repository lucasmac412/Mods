package br.com.celestialdragon.client;

import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.network.DragonControlPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import org.lwjgl.glfw.GLFW;

public final class DragonControls {
    private static CelestialDragonEntity lastMount;
    private static float lastVertical;
    private static int heartbeat;
    private static int spiritCameraTicks;
    public static boolean isWideCamera() { return spiritCameraTicks > 0; }
    private static boolean lastBoost, lastCtrl;
    private static long ctrlTapTick = Long.MIN_VALUE;
    private static CameraType previousCamera;
    public static final KeyMapping SPIRIT = new KeyMapping("key.celestial_dragon.spirit", GLFW.GLFW_KEY_R, KeyMapping.Category.GAMEPLAY);
    private static long lastBiteTick = Long.MIN_VALUE;
    private static long lastBeamTick = Long.MIN_VALUE;

    private DragonControls() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(SPIRIT);
        ClientTickEvents.END_CLIENT_TICK.register(DragonControls::tick);
    }

    public static boolean isControllingDragon(Minecraft minecraft) {
        return controllingDragon(minecraft) != null;
    }

    /** Held items keep vanilla right-click behavior, including all spawn eggs and blocks. */
    public static boolean shouldUseBeamControls(Minecraft minecraft) {
        return controllingDragon(minecraft) != null
                && minecraft.player.getMainHandItem().isEmpty()
                && minecraft.player.getOffhandItem().isEmpty();
    }

    private static CelestialDragonEntity controllingDragon(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof CelestialDragonEntity dragon)
                || !dragon.isTamed() || !minecraft.player.getUUID().equals(dragon.getOwnerId())
                || dragon.getControllingPassenger() != minecraft.player) {
            return null;
        }
        return dragon;
    }

    private static void tick(Minecraft minecraft) {
        CelestialDragonEntity dragon = controllingDragon(minecraft);
        if (dragon == null) {
            if (lastMount != null) {
                lastMount.setRiderVerticalInput(0.0F);
            }
            if (previousCamera != null) minecraft.options.setCameraType(previousCamera);
            previousCamera = null;
            lastBoost = lastCtrl = false;
            ctrlTapTick = Long.MIN_VALUE;
            while (SPIRIT.consumeClick()) { }
            lastMount = null;
            heartbeat = 0;
            spiritCameraTicks = 0;
            lastBiteTick = lastBeamTick = Long.MIN_VALUE;
            return;
        }
        if (lastMount != dragon) {
            previousCamera = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Dragão: Shift acelera • Ctrl desce • 2× Ctrl desmonta • Espaço sobe • R Genki Dama"));
        }
        if (dragon.spiritTicks() > 0) spiritCameraTicks = 180;
        else if (spiritCameraTicks > 0) spiritCameraTicks--;
        boolean playing = minecraft.gui.screen() == null && minecraft.gui.overlay() == null && !minecraft.isPaused();
        if (!playing) ctrlTapTick = Long.MIN_VALUE;
        float vertical = playing ? (minecraft.options.keyJump.isDown() ? 1.0F : 0.0F)
                - (minecraft.options.keySprint.isDown() ? 1.0F : 0.0F) : 0.0F;
        dragon.setRiderVerticalInput(vertical);
        boolean boost = playing && minecraft.options.keyShift.isDown();
        dragon.setRiderBoostInput(boost);
        boolean ctrl = playing && minecraft.options.keySprint.isDown();
        long currentTick = minecraft.level.getGameTime();
        boolean dismount = false;
        if (ctrl && !lastCtrl) {
            dismount = ctrlTapTick != Long.MIN_VALUE && currentTick - ctrlTapTick <= 7;
            ctrlTapTick = dismount ? Long.MIN_VALUE : currentTick;
        }
        lastCtrl = ctrl;
        boolean spirit = false;
        while (SPIRIT.consumeClick()) spirit |= playing;
        if (spirit || dismount) send(vertical, false, false, boost, spirit, dismount);
        if (lastMount != dragon || vertical != lastVertical || boost != lastBoost || ++heartbeat >= 5) {
            send(vertical, false, false, boost, false, false);
            heartbeat = 0;
        }
        lastMount = dragon;
        lastVertical = vertical;
        lastBoost = boost;
        if (playing) {
            if (minecraft.options.keyAttack.isDown()) {
                requestAttack(minecraft, false);
            }
            if (minecraft.options.keyUse.isDown() && shouldUseBeamControls(minecraft)) {
                requestAttack(minecraft, true);
            }
        }
    }

    /** Called both for the initial click and held buttons, at most once per attack per tick. */
    public static void requestAttack(Minecraft minecraft, boolean beam) {
        if (controllingDragon(minecraft) == null || minecraft.level == null || minecraft.gui.screen() != null
                || minecraft.gui.overlay() != null || minecraft.isPaused()
                || beam && !shouldUseBeamControls(minecraft)) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (beam ? lastBeamTick == tick : lastBiteTick == tick) {
            return;
        }
        if (beam) {
            lastBeamTick = tick;
        } else {
            lastBiteTick = tick;
        }
        float vertical = (minecraft.options.keyJump.isDown() ? 1.0F : 0.0F)
                - (minecraft.options.keySprint.isDown() ? 1.0F : 0.0F);
        send(vertical, !beam, beam, minecraft.options.keyShift.isDown(), false, false);
    }

    private static void send(float vertical, boolean bite, boolean beam, boolean boost, boolean spirit, boolean dismount) {
        if (ClientPlayNetworking.canSend(DragonControlPayload.TYPE)) {
            ClientPlayNetworking.send(new DragonControlPayload(vertical, bite, beam, boost, spirit, dismount));
        }
    }
}
