package com.stardew.craft.combat.network;
import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
public record BloodForgeEffectPayload(int casterId,int targetId,long castTick,int phase,int durationTicks,
        double x,double y,double z) implements CustomPacketPayload {
    public static final int RECOVERY=0, HEAT_START=1, HEAT_END=2;
    public static final Type<BloodForgeEffectPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID,"blood_forge_effect"));
    public static final StreamCodec<ByteBuf,BloodForgeEffectPayload> STREAM_CODEC=new StreamCodec<>() {
        @Override public BloodForgeEffectPayload decode(ByteBuf b) {return new BloodForgeEffectPayload(b.readInt(),b.readInt(),b.readLong(),b.readUnsignedByte(),b.readInt(),b.readDouble(),b.readDouble(),b.readDouble());}
        @Override public void encode(ByteBuf b,BloodForgeEffectPayload p) {b.writeInt(p.casterId).writeInt(p.targetId).writeLong(p.castTick).writeByte(p.phase).writeInt(p.durationTicks).writeDouble(p.x).writeDouble(p.y).writeDouble(p.z);}
    };
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void handle(BloodForgeEffectPayload p,IPayloadContext context) {context.enqueueWork(() -> com.stardew.craft.client.weapon.BloodForgeVisuals.effect(p));}
}
