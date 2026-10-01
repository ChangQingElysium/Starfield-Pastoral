package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.auction.AuctionService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record AuctionCreateSubmitPayload(int slot, int dayOffset, int startMinute, int startingPrice,
                                         String name, String promo) implements CustomPacketPayload {
    public static final Type<AuctionCreateSubmitPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "auction_create_submit"));

    public static final StreamCodec<FriendlyByteBuf, AuctionCreateSubmitPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.slot());
            buf.writeVarInt(payload.dayOffset());
            buf.writeVarInt(payload.startMinute());
            buf.writeVarInt(payload.startingPrice());
            buf.writeUtf(payload.name(), 64);
            buf.writeUtf(payload.promo(), 200);
        },
        buf -> new AuctionCreateSubmitPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(64), buf.readUtf(200)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AuctionCreateSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AuctionService.createAuctionFromSlot(player, payload.slot(), payload.dayOffset(), payload.startMinute(),
                    payload.startingPrice(), payload.name(), payload.promo());
            }
        });
    }
}
