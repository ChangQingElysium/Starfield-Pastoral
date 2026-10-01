package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record AccretionDiskPayload(float x, float y, float z, float radius, int durationTicks, int color)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<AccretionDiskPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "accretion_disk")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, AccretionDiskPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        AccretionDiskPayload::x,
        ByteBufCodecs.FLOAT,
        AccretionDiskPayload::y,
        ByteBufCodecs.FLOAT,
        AccretionDiskPayload::z,
        ByteBufCodecs.FLOAT,
        AccretionDiskPayload::radius,
        ByteBufCodecs.VAR_INT,
        AccretionDiskPayload::durationTicks,
        ByteBufCodecs.INT,
        AccretionDiskPayload::color,
        AccretionDiskPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AccretionDiskPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.AccretionDiskEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.radius(), payload.durationTicks(), payload.color()
        ));
    }
}
