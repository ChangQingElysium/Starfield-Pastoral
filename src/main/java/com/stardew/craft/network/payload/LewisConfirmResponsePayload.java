package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.money.SharedMoneyService;
import com.stardew.craft.npc.runtime.FarmCancellationService;
import com.stardew.craft.npc.runtime.LewisCivicService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record LewisConfirmResponsePayload(UUID requestId, int kind, boolean accepted) implements CustomPacketPayload {
    public static final Type<LewisConfirmResponsePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "lewis_confirm_response"));

    public static final StreamCodec<FriendlyByteBuf, LewisConfirmResponsePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.requestId());
            buf.writeVarInt(payload.kind());
            buf.writeBoolean(payload.accepted());
        },
        buf -> new LewisConfirmResponsePayload(buf.readUUID(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LewisConfirmResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.kind() == OpenLewisConfirmPayload.KIND_MONEY_SHARE) {
                SharedMoneyService.handleConfirm(player, payload.requestId(), payload.accepted());
            } else if (payload.kind() == OpenLewisConfirmPayload.KIND_FARM_CANCEL) {
                FarmCancellationService.handleConfirm(player, payload.requestId(), payload.accepted());
            } else if (payload.kind() == OpenLewisConfirmPayload.KIND_MONEY_CONTRACT_CLAIM) {
                LewisCivicService.handleMoneyContractClaimConfirm(player, payload.accepted());
            } else if (payload.kind() == OpenLewisConfirmPayload.KIND_AUCTION_START) {
                AuctionService.handleStartConfirm(player, payload.requestId(), payload.accepted());
            } else if (payload.kind() == OpenLewisConfirmPayload.KIND_AUCTION_CANCEL) {
                AuctionService.handleCancelConfirm(player, payload.requestId(), payload.accepted());
            }
        });
    }
}
