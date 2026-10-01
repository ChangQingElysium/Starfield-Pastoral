package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record TemperedRingPayload(long id, boolean active, double x, double y, double z,
        float radius, int duration) implements CustomPacketPayload {
    public static final Type<TemperedRingPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID,"tempered_ring"));
    public static final StreamCodec<ByteBuf,TemperedRingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public TemperedRingPayload decode(ByteBuf b) {
            return new TemperedRingPayload(b.readLong(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readInt());
        }
        @Override public void encode(ByteBuf b,TemperedRingPayload p) {
            b.writeLong(p.id).writeBoolean(p.active).writeDouble(p.x).writeDouble(p.y).writeDouble(p.z).writeFloat(p.radius).writeInt(p.duration);
        }
    };
    public TemperedRingPayload ended() { return new TemperedRingPayload(id,false,x,y,z,radius,duration); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(TemperedRingPayload p,IPayloadContext c) {
        c.enqueueWork(() -> com.stardew.craft.client.weapon.TemperedRingClient.receive(p));
    }
}
