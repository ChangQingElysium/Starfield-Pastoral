package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.desert.DesertFestivalMineService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record DesertFestivalMarlonRatingClaimPayload() implements CustomPacketPayload {
    public static final Type<DesertFestivalMarlonRatingClaimPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "desert_festival_marlon_rating_claim"));

    public static final StreamCodec<FriendlyByteBuf, DesertFestivalMarlonRatingClaimPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new DesertFestivalMarlonRatingClaimPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesertFestivalMarlonRatingClaimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DesertFestivalMineService.handleMarlonRatingClaim(player);
            }
        });
    }
}
