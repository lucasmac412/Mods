package br.com.celestialdragon.network;

import br.com.celestialdragon.CelestialDragonMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only control intent crosses the wire; targets and damage are chosen by the server. */
public record DragonControlPayload(float vertical, boolean bite, boolean beam, boolean boost,
                                   boolean spirit, boolean dismount) implements CustomPacketPayload {
    public DragonControlPayload(float vertical, boolean bite, boolean beam) {
        this(vertical, bite, beam, false, false, false);
    }
    public static final Type<DragonControlPayload> TYPE = new Type<>(CelestialDragonMod.id("dragon_control_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DragonControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DragonControlPayload::vertical,
            ByteBufCodecs.BOOL, DragonControlPayload::bite,
            ByteBufCodecs.BOOL, DragonControlPayload::beam,
            ByteBufCodecs.BOOL, DragonControlPayload::boost,
            ByteBufCodecs.BOOL, DragonControlPayload::spirit,
            ByteBufCodecs.BOOL, DragonControlPayload::dismount,
            DragonControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
