package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.GeodeLootService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → Server: claim the pending geode treasure after the crack animation finishes.
 * Matches SDV behavior where the treasure is added to inventory only when geodeAnimationTimer <= 0.
 */
@SuppressWarnings("null")
public record GeodeClaimPayload() implements CustomPacketPayload {

    public static final Type<GeodeClaimPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "geode_claim"));

    public static final StreamCodec<FriendlyByteBuf, GeodeClaimPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new GeodeClaimPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeodeClaimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                GeodeLootService.handleGeodeClaim(sp);
            }
        });
    }
}
