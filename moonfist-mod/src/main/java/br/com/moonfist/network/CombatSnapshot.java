package br.com.moonfist.network;

import br.com.moonfist.MoonfistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Only server-authored state reaches renderers and HUD; entity IDs are never damage authority. */
public record CombatSnapshot(int entity,boolean equipped,int move,int age,int stance,boolean guard,
        float stamina,float focus,int waveCooldown,int voidCooldown,int lockTarget,float targetPosture,
        long serial,int hitPulse,Vec3 origin,Vec3 direction,Vec3 aim) implements CustomPacketPayload {
    public static final Type<CombatSnapshot> TYPE=new Type<>(MoonfistMod.id("state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CombatSnapshot> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.entity);b.writeBoolean(p.equipped);b.writeVarInt(p.move);b.writeVarInt(p.age);b.writeVarInt(p.stance);
        b.writeBoolean(p.guard);b.writeFloat(p.stamina);b.writeFloat(p.focus);b.writeVarInt(p.waveCooldown);b.writeVarInt(p.voidCooldown);
        b.writeInt(p.lockTarget);b.writeFloat(p.targetPosture);b.writeVarLong(p.serial);b.writeVarInt(p.hitPulse);
        vector(b,p.origin);vector(b,p.direction);vector(b,p.aim);
    },b->new CombatSnapshot(b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),
            b.readFloat(),b.readFloat(),b.readVarInt(),b.readVarInt(),b.readInt(),b.readFloat(),b.readVarLong(),b.readVarInt(),
            vector(b),vector(b),vector(b)));
    private static void vector(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
