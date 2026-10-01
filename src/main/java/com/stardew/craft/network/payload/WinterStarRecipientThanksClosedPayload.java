package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.WinterStarFestivalService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record WinterStarRecipientThanksClosedPayload() implements CustomPacketPayload {
    public static final Type<WinterStarRecipientThanksClosedPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "winter_star_recipient_thanks_closed"));
    public static final StreamCodec<FriendlyByteBuf, WinterStarRecipientThanksClosedPayload> STREAM_CODEC = StreamCodec.unit(
        new WinterStarRecipientThanksClosedPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(WinterStarRecipientThanksClosedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) WinterStarFestivalService.advanceReturnGift(player);
        });
    }
}
