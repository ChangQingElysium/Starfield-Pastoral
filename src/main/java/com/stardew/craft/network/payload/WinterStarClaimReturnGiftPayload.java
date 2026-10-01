package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.WinterStarFestivalService;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Claims the already-generated return gift at the reveal beat of the cutscene. */
public record WinterStarClaimReturnGiftPayload() implements CustomPacketPayload {
    public static final Type<WinterStarClaimReturnGiftPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "winter_star_claim_return_gift"));
    public static final StreamCodec<FriendlyByteBuf, WinterStarClaimReturnGiftPayload> STREAM_CODEC =
        StreamCodec.unit(new WinterStarClaimReturnGiftPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WinterStarClaimReturnGiftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WinterStarFestivalService.claimReturnGiftDuringCutscene(player);
            }
        });
    }
}
