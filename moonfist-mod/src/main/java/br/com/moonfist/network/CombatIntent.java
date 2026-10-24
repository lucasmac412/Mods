package br.com.moonfist.network;

import br.com.moonfist.MoonfistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CombatIntent(int action,float strafe,float forward) implements CustomPacketPayload {
    public static final int LIGHT=0,HEAVY=1,WAVE=2,VOID=3,DODGE=4,GUARD_ON=5,GUARD_OFF=6,STANCE=7,LOCK=8;
    public static final Type<CombatIntent> TYPE=new Type<>(MoonfistMod.id("intent_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CombatIntent> CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.action);b.writeFloat(p.strafe);b.writeFloat(p.forward);},
        b->new CombatIntent(b.readVarInt(),b.readFloat(),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
