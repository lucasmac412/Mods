package br.com.celestialdragon.client;

import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.network.DragonControlPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class DragonControls {
    private static CelestialDragonEntity lastMount;
    private static float lastVertical;
    private static int heartbeat;
    private static long lastBiteTick = Long.MIN_VALUE;
    private static long lastBeamTick = Long.MIN_VALUE;

    private DragonControls() {
    }

    public static void register() {
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
            lastMount = null;
            heartbeat = 0;
            lastBiteTick = lastBeamTick = Long.MIN_VALUE;
            return;
        }
        boolean playing = minecraft.gui.screen() == null && minecraft.gui.overlay() == null && !minecraft.isPaused();
        float vertical = playing ? (minecraft.options.keyJump.isDown() ? 1.0F : 0.0F)
                - (minecraft.options.keySprint.isDown() ? 1.0F : 0.0F) : 0.0F;
        dragon.setRiderVerticalInput(vertical);
        if (lastMount != dragon || vertical != lastVertical || ++heartbeat >= 5) {
            send(vertical, false, false);
            heartbeat = 0;
        }
        lastMount = dragon;
        lastVertical = vertical;
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
        send(vertical, !beam, beam);
    }

    private static void send(float vertical, boolean bite, boolean beam) {
        if (ClientPlayNetworking.canSend(DragonControlPayload.TYPE)) {
            ClientPlayNetworking.send(new DragonControlPayload(vertical, bite, beam));
        }
    }
}
