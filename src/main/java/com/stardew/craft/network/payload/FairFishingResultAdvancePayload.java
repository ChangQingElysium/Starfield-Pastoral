package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.fair.FairFishingGameService;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairFishingResultAdvancePayload() implements CustomPacketPayload {
    public static final Type<FairFishingResultAdvancePayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_fishing_result_advance"));

    public static final StreamCodec<ByteBuf, FairFishingResultAdvancePayload> STREAM_CODEC =
        StreamCodec.unit(new FairFishingResultAdvancePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairFishingResultAdvancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FairFishingGameService.claimFishingResultReward(player);
            }
        });
    }
}
