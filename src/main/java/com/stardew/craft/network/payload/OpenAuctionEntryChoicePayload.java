package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record OpenAuctionEntryChoicePayload() implements CustomPacketPayload {
    public static final Type<OpenAuctionEntryChoicePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "open_auction_entry_choice"));

    public static final StreamCodec<FriendlyByteBuf, OpenAuctionEntryChoicePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new OpenAuctionEntryChoicePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenAuctionEntryChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(OpenAuctionEntryChoicePayload::handleClient);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new com.stardew.craft.client.gui.auction.AuctionEntryChoiceScreen());
    }
}
