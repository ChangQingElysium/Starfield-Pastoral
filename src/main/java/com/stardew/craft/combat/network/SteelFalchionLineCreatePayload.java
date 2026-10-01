package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SteelFalchionLineCreatePayload(int lineId, double x, double y, double z, int durationTicks, float width)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SteelFalchionLineCreatePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "steel_falchion_line_create")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SteelFalchionLineCreatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SteelFalchionLineCreatePayload::lineId,
        ByteBufCodecs.DOUBLE,
        SteelFalchionLineCreatePayload::x,
        ByteBufCodecs.DOUBLE,
        SteelFalchionLineCreatePayload::y,
        ByteBufCodecs.DOUBLE,
        SteelFalchionLineCreatePayload::z,
        ByteBufCodecs.VAR_INT,
        SteelFalchionLineCreatePayload::durationTicks,
        ByteBufCodecs.FLOAT,
        SteelFalchionLineCreatePayload::width,
        SteelFalchionLineCreatePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SteelFalchionLineCreatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.create(
            payload.lineId(), payload.x(), payload.y(), payload.z(), payload.durationTicks(), payload.width()
        ));
    }
}
