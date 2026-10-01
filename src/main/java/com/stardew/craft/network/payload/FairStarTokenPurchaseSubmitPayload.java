package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.FairFestivalService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairStarTokenPurchaseSubmitPayload(int amount) implements CustomPacketPayload {
    public static final Type<FairStarTokenPurchaseSubmitPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_star_token_purchase_submit"));

    public static final StreamCodec<FriendlyByteBuf, FairStarTokenPurchaseSubmitPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.amount()),
        buf -> new FairStarTokenPurchaseSubmitPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairStarTokenPurchaseSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FairFestivalService.purchaseStarTokensFromNumberSelection(player, payload.amount());
            }
        });
    }
}
