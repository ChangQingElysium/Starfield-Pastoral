package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Phase packets share an exact release identity, including cancellation in the original dimension. */
public record ObsidianCrackPayload(int casterId, long castTick, int phase, double x, double y, double z,
        float yaw, float length, int durationTicks) implements CustomPacketPayload {
    public static final int START = 0, PULSE = 1, END = 2;
    public static final Type<ObsidianCrackPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "obsidian_crack"));
    public static final StreamCodec<ByteBuf, ObsidianCrackPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public ObsidianCrackPayload decode(ByteBuf b) {
            return new ObsidianCrackPayload(b.readInt(), b.readLong(), b.readUnsignedByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readInt());
        }
        @Override public void encode(ByteBuf b, ObsidianCrackPayload p) {
            b.writeInt(p.casterId()).writeLong(p.castTick()).writeByte(p.phase()).writeDouble(p.x()).writeDouble(p.y()).writeDouble(p.z());
            b.writeFloat(p.yaw()).writeFloat(p.length());
            b.writeInt(p.durationTicks());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(ObsidianCrackPayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.ObsidianCrackEffectClient.add(p));
    }
}
