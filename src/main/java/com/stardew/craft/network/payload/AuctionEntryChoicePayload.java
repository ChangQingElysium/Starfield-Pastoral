package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.event.InteriorPortalInteractionEvents;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record AuctionEntryChoicePayload(boolean enterAuction) implements CustomPacketPayload {
    public static final Type<AuctionEntryChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "auction_entry_choice"));

    public static final StreamCodec<FriendlyByteBuf, AuctionEntryChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.enterAuction()),
        buf -> new AuctionEntryChoicePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuctionEntryChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.enterAuction()) {
                    AuctionService.enterAuctionRoom(player);
                } else {
                    player.getPersistentData().putBoolean("stardewcraft_auction_enter_house_once", true);
                    InteriorPortalInteractionEvents.handlePortalInteraction(player, "mayor_house_enter");
                }
            }
        });
    }
}
