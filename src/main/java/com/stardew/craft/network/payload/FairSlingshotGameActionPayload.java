package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.fair.FairSlingshotGameService;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairSlingshotGameActionPayload(int action, int score) implements CustomPacketPayload {
    public static final int ACTION_START = 0;
    public static final int ACTION_COMPLETE = 1;

    public static final Type<FairSlingshotGameActionPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_slingshot_game_action"));

    public static final StreamCodec<ByteBuf, FairSlingshotGameActionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            FairSlingshotGameActionPayload::action,
            ByteBufCodecs.INT,
            FairSlingshotGameActionPayload::score,
            FairSlingshotGameActionPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairSlingshotGameActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.action() == ACTION_START) {
                FairSlingshotGameService.start(player);
            } else if (payload.action() == ACTION_COMPLETE) {
                FairSlingshotGameService.complete(player, payload.score());
            }
        });
    }
}
