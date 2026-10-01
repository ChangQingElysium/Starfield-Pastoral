package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SteelFalchionLineBurstPayload(int lineId) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SteelFalchionLineBurstPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "steel_falchion_line_burst")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SteelFalchionLineBurstPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SteelFalchionLineBurstPayload::lineId,
        SteelFalchionLineBurstPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SteelFalchionLineBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.burst(payload.lineId()));
    }
}
