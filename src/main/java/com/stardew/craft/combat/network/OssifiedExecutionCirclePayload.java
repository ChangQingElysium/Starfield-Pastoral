package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Phase packets share an exact release identity, including cancellation in the original dimension. */
public record OssifiedExecutionCirclePayload(int casterId, long castTick, int phase, double x, double y, double z,
        float radius, int durationTicks) implements CustomPacketPayload {
    public static final int START = 0, PULSE = 1, END = 2;
    public static final Type<OssifiedExecutionCirclePayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "ossified_execution_circle"));
    public static final StreamCodec<ByteBuf, OssifiedExecutionCirclePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public OssifiedExecutionCirclePayload decode(ByteBuf b) {
            return new OssifiedExecutionCirclePayload(b.readInt(), b.readLong(), b.readUnsignedByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readInt());
        }
        @Override public void encode(ByteBuf b, OssifiedExecutionCirclePayload p) {
            b.writeInt(p.casterId()).writeLong(p.castTick()).writeByte(p.phase()).writeDouble(p.x()).writeDouble(p.y()).writeDouble(p.z());
            b.writeFloat(p.radius());
            b.writeInt(p.durationTicks());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(OssifiedExecutionCirclePayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.OssifiedExecutionCircleEffectClient.add(p));
    }
}
