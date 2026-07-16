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
public record OpenAuctionBidPayload(String auctionName, int lotIndex, int lotCount, ItemStack stack,
                                    String sellerName, String highestBidderName, int currentPrice,
                                    int nextBid, int remainingSeconds, boolean canBid) implements CustomPacketPayload {
    public static final Type<OpenAuctionBidPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_auction_bid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAuctionBidPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.auctionName(), 64);
            buf.writeVarInt(payload.lotIndex());
            buf.writeVarInt(payload.lotCount());
            ItemStack.STREAM_CODEC.encode(buf, payload.stack());
            buf.writeUtf(payload.sellerName(), 48);
            buf.writeUtf(payload.highestBidderName(), 48);
            buf.writeVarInt(payload.currentPrice());
            buf.writeVarInt(payload.nextBid());
            buf.writeVarInt(payload.remainingSeconds());
            buf.writeBoolean(payload.canBid());
        },
        buf -> new OpenAuctionBidPayload(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(),
            ItemStack.STREAM_CODEC.decode(buf), buf.readUtf(48), buf.readUtf(48), buf.readVarInt(),
            buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    public static OpenAuctionBidPayload from(MinecraftServer server, AuctionRecord auction, AuctionLot lot,
                                             int remainingSeconds, boolean canBid) {
        return new OpenAuctionBidPayload(auction.name(), auction.currentLotIndex() + 1, auction.lots().size(),
            lot.stack().copy(), PlayerDisplayName.get(server, lot.sellerId()),
            lot.highestBidderId() == null ? "" : PlayerDisplayName.get(server, lot.highestBidderId()),
            lot.currentPrice(), lot.nextBid(), remainingSeconds, canBid);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenAuctionBidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenAuctionBidPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new com.stardew.craft.client.gui.auction.AuctionBidScreen(payload));
        }
    }
}
