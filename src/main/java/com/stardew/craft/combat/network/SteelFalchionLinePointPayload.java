package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SteelFalchionLinePointPayload(int lineId, double x, double y, double z)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SteelFalchionLinePointPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "steel_falchion_line_point")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SteelFalchionLinePointPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SteelFalchionLinePointPayload::lineId,
        ByteBufCodecs.DOUBLE,
        SteelFalchionLinePointPayload::x,
        ByteBufCodecs.DOUBLE,
        SteelFalchionLinePointPayload::y,
        ByteBufCodecs.DOUBLE,
        SteelFalchionLinePointPayload::z,
        SteelFalchionLinePointPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SteelFalchionLinePointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.addPoint(
            payload.lineId(), payload.x(), payload.y(), payload.z()
        ));
    }
}
