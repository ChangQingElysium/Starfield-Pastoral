package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record FireRingEffectPayload(float x, float y, float z, float maxRadius, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<FireRingEffectPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "fire_ring")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, FireRingEffectPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        FireRingEffectPayload::x,
        ByteBufCodecs.FLOAT,
        FireRingEffectPayload::y,
        ByteBufCodecs.FLOAT,
        FireRingEffectPayload::z,
        ByteBufCodecs.FLOAT,
        FireRingEffectPayload::maxRadius,
        ByteBufCodecs.VAR_INT,
        FireRingEffectPayload::durationTicks,
        FireRingEffectPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FireRingEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.FireRingEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.maxRadius(), payload.durationTicks()
        ));
    }
}
