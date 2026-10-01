package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.shop.PrizeTicketRewardService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> Server: claim the current Prize Machine reward after the button animation reaches payout. */
@SuppressWarnings("null")
public record PrizeTicketClaimPayload() implements CustomPacketPayload {
    public static final Type<PrizeTicketClaimPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "prize_ticket_claim"));

    public static final StreamCodec<FriendlyByteBuf, PrizeTicketClaimPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new PrizeTicketClaimPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PrizeTicketClaimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PrizeTicketRewardService.handlePrizeTicketClaim(player);
            }
        });
    }
}