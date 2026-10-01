package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record WaterRingEffectPayload(float x, float y, float z, float maxRadius, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<WaterRingEffectPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "water_ring")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, WaterRingEffectPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        WaterRingEffectPayload::x,
        ByteBufCodecs.FLOAT,
        WaterRingEffectPayload::y,
        ByteBufCodecs.FLOAT,
        WaterRingEffectPayload::z,
        ByteBufCodecs.FLOAT,
        WaterRingEffectPayload::maxRadius,
        ByteBufCodecs.VAR_INT,
        WaterRingEffectPayload::durationTicks,
        WaterRingEffectPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WaterRingEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.WaterRingEffectClient.add(payload.x(), payload.y(), payload.z(), payload.maxRadius(), payload.durationTicks()));
    }
}
