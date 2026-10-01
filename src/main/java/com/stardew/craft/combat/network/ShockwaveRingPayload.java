package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ShockwaveRingPayload(float x, float y, float z, float radius, int durationTicks, int color)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<ShockwaveRingPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "shockwave_ring")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, ShockwaveRingPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        ShockwaveRingPayload::x,
        ByteBufCodecs.FLOAT,
        ShockwaveRingPayload::y,
        ByteBufCodecs.FLOAT,
        ShockwaveRingPayload::z,
        ByteBufCodecs.FLOAT,
        ShockwaveRingPayload::radius,
        ByteBufCodecs.VAR_INT,
        ShockwaveRingPayload::durationTicks,
        ByteBufCodecs.INT,
        ShockwaveRingPayload::color,
        ShockwaveRingPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShockwaveRingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.ShockwaveRingEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.radius(), payload.durationTicks(), payload.color()
        ));
    }
}
