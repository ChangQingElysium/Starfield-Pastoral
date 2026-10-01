package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BlackHolePostPayload(float x, float y, float z, float radiusNorm, float strength, int durationTicks)
        implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<BlackHolePostPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "black_hole_post")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, BlackHolePostPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        BlackHolePostPayload::x,
        ByteBufCodecs.FLOAT,
        BlackHolePostPayload::y,
        ByteBufCodecs.FLOAT,
        BlackHolePostPayload::z,
        ByteBufCodecs.FLOAT,
        BlackHolePostPayload::radiusNorm,
        ByteBufCodecs.FLOAT,
        BlackHolePostPayload::strength,
        ByteBufCodecs.VAR_INT,
        BlackHolePostPayload::durationTicks,
        BlackHolePostPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackHolePostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.stardew.craft.client.weapon.BlackHolePostEffectClient.add(
                payload.x(), payload.y(), payload.z(), payload.radiusNorm(), payload.strength(), payload.durationTicks()
            );
            com.stardew.craft.client.weapon.CameraShakeState.kick(0.4f, 8, 2.5f);
        });
    }
}
