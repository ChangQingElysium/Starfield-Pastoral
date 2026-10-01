package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SingularityRunePayload(float x, float y, float z, float radius, int durationTicks, int color)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SingularityRunePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "singularity_rune")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SingularityRunePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        SingularityRunePayload::x,
        ByteBufCodecs.FLOAT,
        SingularityRunePayload::y,
        ByteBufCodecs.FLOAT,
        SingularityRunePayload::z,
        ByteBufCodecs.FLOAT,
        SingularityRunePayload::radius,
        ByteBufCodecs.VAR_INT,
        SingularityRunePayload::durationTicks,
        ByteBufCodecs.INT,
        SingularityRunePayload::color,
        SingularityRunePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SingularityRunePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SingularityRuneEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.radius(), payload.durationTicks(), payload.color()
        ));
    }
}
