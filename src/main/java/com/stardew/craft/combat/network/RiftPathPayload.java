package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RiftPathPayload(float x, float y, float z, float yaw, float length, int durationTicks, int color)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<RiftPathPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "rift_path")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, RiftPathPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RiftPathPayload decode(ByteBuf buf) {
            float x = buf.readFloat();
            float y = buf.readFloat();
            float z = buf.readFloat();
            float yaw = buf.readFloat();
            float length = buf.readFloat();
            int durationTicks = ByteBufCodecs.VAR_INT.decode(buf);
            int color = buf.readInt();
            return new RiftPathPayload(x, y, z, yaw, length, durationTicks, color);
        }

        @Override
        public void encode(ByteBuf buf, RiftPathPayload value) {
            buf.writeFloat(value.x());
            buf.writeFloat(value.y());
            buf.writeFloat(value.z());
            buf.writeFloat(value.yaw());
            buf.writeFloat(value.length());
            ByteBufCodecs.VAR_INT.encode(buf, value.durationTicks());
            buf.writeInt(value.color());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RiftPathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.RiftPathEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.yaw(), payload.length(), payload.durationTicks(), payload.color()
        ));
    }
}
