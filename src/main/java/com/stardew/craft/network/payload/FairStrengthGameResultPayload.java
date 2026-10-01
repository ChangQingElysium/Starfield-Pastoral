package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.fair.FairStrengthGameService;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairStrengthGameResultPayload(int power) implements CustomPacketPayload {
    public static final Type<FairStrengthGameResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_strength_game_result"));

    public static final StreamCodec<ByteBuf, FairStrengthGameResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            FairStrengthGameResultPayload::power,
            FairStrengthGameResultPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairStrengthGameResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FairStrengthGameService.complete(player, payload.power());
            }
        });
    }
}
