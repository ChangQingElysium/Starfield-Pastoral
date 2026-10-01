package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.auction.AuctionService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

@SuppressWarnings("null")
public record AuctionJoinSubmitPayload(UUID auctionId, int slot, int startingPrice) implements CustomPacketPayload {
    public static final Type<AuctionJoinSubmitPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "auction_join_submit"));

    public static final StreamCodec<FriendlyByteBuf, AuctionJoinSubmitPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.auctionId());
            buf.writeVarInt(payload.slot());
            buf.writeVarInt(payload.startingPrice());
        },
        buf -> new AuctionJoinSubmitPayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuctionJoinSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AuctionService.joinAuctionFromSlot(player, payload.auctionId(), payload.slot(), payload.startingPrice());
            }
        });
    }
}
