package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SteelFalchionLinePulsePayload(int lineId, int durationTicks)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SteelFalchionLinePulsePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "steel_falchion_line_pulse")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SteelFalchionLinePulsePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SteelFalchionLinePulsePayload::lineId,
        ByteBufCodecs.VAR_INT,
        SteelFalchionLinePulsePayload::durationTicks,
        SteelFalchionLinePulsePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SteelFalchionLinePulsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.pulse(payload.lineId(), payload.durationTicks()));
    }
}
