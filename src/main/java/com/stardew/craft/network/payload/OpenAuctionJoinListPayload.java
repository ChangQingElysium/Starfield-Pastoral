package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record OpenAuctionJoinListPayload(List<AuctionSummary> auctions) implements CustomPacketPayload {
    public static final Type<OpenAuctionJoinListPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_auction_join_list"));

    public static final StreamCodec<FriendlyByteBuf, OpenAuctionJoinListPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.auctions().size());
            for (AuctionSummary summary : payload.auctions()) {
                summary.write(buf);
            }
        },
        buf -> {
            int count = buf.readVarInt();
            List<AuctionSummary> summaries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                summaries.add(AuctionSummary.read(buf));
            }
            return new OpenAuctionJoinListPayload(summaries);
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenAuctionJoinListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenAuctionJoinListPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new com.stardew.craft.client.gui.auction.AuctionJoinListScreen(payload.auctions()));
        }
    }

    public record AuctionSummary(UUID id, String name, String creatorName, int scheduledDay, int startMinute, int lotCount) {
        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 64);
            buf.writeUtf(creatorName, 48);
            buf.writeVarInt(scheduledDay);
            buf.writeVarInt(startMinute);
            buf.writeVarInt(lotCount);
        }

        private static AuctionSummary read(FriendlyByteBuf buf) {
            return new AuctionSummary(buf.readUUID(), buf.readUtf(64), buf.readUtf(48),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }
}
