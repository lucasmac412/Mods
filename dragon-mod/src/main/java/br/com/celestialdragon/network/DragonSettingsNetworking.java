package br.com.celestialdragon.network;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.config.DragonConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** The server validates permissions and values; the client receives a separate display snapshot. */
public final class DragonSettingsNetworking {
    public record Request(boolean apply, double health, double bite, double beam) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(CelestialDragonMod.id("settings_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBoolean(p.apply); buf.writeDouble(p.health); buf.writeDouble(p.bite); buf.writeDouble(p.beam); },
                buf -> new Request(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(double health, double bite, double beam, boolean canEdit, String message) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(CelestialDragonMod.id("settings_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeDouble(p.health); buf.writeDouble(p.bite); buf.writeDouble(p.beam); buf.writeBoolean(p.canEdit); buf.writeUtf(p.message, 256); },
                buf -> new Snapshot(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readUtf(256)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private DragonSettingsNetworking() {}
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            boolean allowed = canEdit(player);
            String message = allowed ? "Valores do servidor. Vida em pontos: 2 pontos = 1 coração." : "Somente o dono do mundo ou administradores podem alterar.";
            if (payload.apply()) {
                if (!allowed) message = "Você não tem permissão para alterar estes valores.";
                else {
                    try {
                        var next = new DragonConfig.Values(payload.health(), payload.bite(), payload.beam());
                        message = DragonConfig.save(next) ? "Salvo. Vale para dragões selvagens e domados; não cura os existentes."
                                : "Não foi possível salvar o arquivo; os valores anteriores foram mantidos.";
                    } catch (IllegalArgumentException invalid) {
                        message = "Valores inválidos: vida de 1 a 1024; danos de 0 a 1000.";
                    }
                }
            }
            var v = DragonConfig.current();
            ServerPlayNetworking.send(player, new Snapshot(v.maxHealth(), v.biteDamage(), v.beamDamage(), allowed, message));
        }));
    }
    public static boolean canEdit(ServerPlayer player) {
        var server = player.level().getServer();
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || server != null && !server.isDedicatedServer() && server.getSingleplayerProfile() != null
                && server.getSingleplayerProfile().id().equals(player.getUUID());
    }
}
