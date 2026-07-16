package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.auction.AuctionWorldData.AuctionLot;
import com.stardew.craft.auction.AuctionWorldData.AuctionRecord;
import com.stardew.craft.player.PlayerDisplayName;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record SyncAuctionBoardPayload(boolean active, String auctionName, int lotIndex, int lotCount, ItemStack stack,
                                      String sellerName, String bidderName, int currentPrice, int nextBid,
                                      int remainingSeconds, boolean canBid) implements CustomPacketPayload {
    public static final Type<SyncAuctionBoardPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "sync_auction_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAuctionBoardPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.active());
            buf.writeUtf(payload.auctionName(), 64);
            buf.writeVarInt(payload.lotIndex());
            buf.writeVarInt(payload.lotCount());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.stack());
            buf.writeUtf(payload.sellerName(), 48);
            buf.writeUtf(payload.bidderName(), 48);
            buf.writeVarInt(payload.currentPrice());
            buf.writeVarInt(payload.nextBid());
            buf.writeVarInt(payload.remainingSeconds());
            buf.writeBoolean(payload.canBid());
        },
        buf -> new SyncAuctionBoardPayload(buf.readBoolean(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(48), buf.readUtf(48),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    public static SyncAuctionBoardPayload clear() {
        return new SyncAuctionBoardPayload(false, "", 0, 0, ItemStack.EMPTY, "", "", 0, 0, 0, false);
    }

    public static SyncAuctionBoardPayload from(MinecraftServer server, AuctionRecord auction, AuctionLot lot,
                                               int remainingSeconds, boolean canBid) {
        return new SyncAuctionBoardPayload(true, auction.name(), auction.currentLotIndex() + 1, auction.lots().size(),
            lot.stack().copy(), PlayerDisplayName.get(server, lot.sellerId()),
            lot.highestBidderId() == null ? "" : PlayerDisplayName.get(server, lot.highestBidderId()),
            lot.currentPrice(), lot.nextBid(), remainingSeconds, canBid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAuctionBoardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(SyncAuctionBoardPayload payload) {
        com.stardew.craft.client.auction.AuctionClientState.update(payload);
    }
}
