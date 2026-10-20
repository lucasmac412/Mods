package br.com.celestialdragon.network;

import br.com.celestialdragon.CelestialDragonMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only control intent crosses the wire; targets and damage are chosen by the server. */
public record DragonControlPayload(float vertical, boolean bite, boolean beam) implements CustomPacketPayload {
    public static final Type<DragonControlPayload> TYPE = new Type<>(CelestialDragonMod.id("dragon_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DragonControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DragonControlPayload::vertical,
            ByteBufCodecs.BOOL, DragonControlPayload::bite,
            ByteBufCodecs.BOOL, DragonControlPayload::beam,
            DragonControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
