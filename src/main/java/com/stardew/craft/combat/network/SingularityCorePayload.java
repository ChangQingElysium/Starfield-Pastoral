package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SingularityCorePayload(float x, float y, float z, float radius, int durationTicks, int color)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<SingularityCorePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "singularity_core")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, SingularityCorePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        SingularityCorePayload::x,
        ByteBufCodecs.FLOAT,
        SingularityCorePayload::y,
        ByteBufCodecs.FLOAT,
        SingularityCorePayload::z,
        ByteBufCodecs.FLOAT,
        SingularityCorePayload::radius,
        ByteBufCodecs.VAR_INT,
        SingularityCorePayload::durationTicks,
        ByteBufCodecs.INT,
        SingularityCorePayload::color,
        SingularityCorePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SingularityCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.SingularityCoreEffectClient.add(
            payload.x(), payload.y(), payload.z(), payload.radius(), payload.durationTicks(), payload.color()
        ));
    }
}
