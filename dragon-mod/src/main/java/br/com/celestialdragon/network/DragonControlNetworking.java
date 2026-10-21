package br.com.celestialdragon.network;

import br.com.celestialdragon.entity.CelestialDragonEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class DragonControlNetworking {
    private DragonControlNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(DragonControlPayload.TYPE, DragonControlPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DragonControlPayload.TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
    }

    /** Derive the mount from the authenticated sender instead of accepting an entity ID. */
    public static void handle(ServerPlayer player, DragonControlPayload payload) {
        if (!player.isAlive() || player.isSpectator() || !Float.isFinite(payload.vertical())) {
            return;
        }
        if (!(player.getVehicle() instanceof CelestialDragonEntity dragon)
                || !dragon.isAlive() || !dragon.isTamed()
                || !player.getUUID().equals(dragon.getOwnerId())
                || dragon.getControllingPassenger() != player) {
            return;
        }
        dragon.setRiderVerticalInput(Mth.clamp(payload.vertical(), -1.0F, 1.0F));
        dragon.setRiderBoostInput(payload.boost());
        if (payload.dismount()) {
            dragon.setRiderBoostInput(false);
            dragon.setRiderVerticalInput(0);
            player.stopRiding();
            return;
        }
        if (payload.bite()) {
            dragon.tryBite();
        }
        if (payload.beam() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            dragon.tryBeam();
        }
        if (payload.spirit()) dragon.trySpiritBomb();
    }
}
