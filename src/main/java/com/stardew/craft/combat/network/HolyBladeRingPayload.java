package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record HolyBladeRingPayload(double x, double y, double z, float maxRadius, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<HolyBladeRingPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "holy_blade_ring")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, HolyBladeRingPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE,
        HolyBladeRingPayload::x,
        ByteBufCodecs.DOUBLE,
        HolyBladeRingPayload::y,
        ByteBufCodecs.DOUBLE,
        HolyBladeRingPayload::z,
        ByteBufCodecs.FLOAT,
        HolyBladeRingPayload::maxRadius,
        ByteBufCodecs.VAR_INT,
        HolyBladeRingPayload::durationTicks,
        HolyBladeRingPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HolyBladeRingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.HolyBladeRingEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.maxRadius(), payload.durationTicks()
        ));
    }
}
