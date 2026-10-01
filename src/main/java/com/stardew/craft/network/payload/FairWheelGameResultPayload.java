package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.festival.fair.FairWheelGameService;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record FairWheelGameResultPayload(int wager, boolean won) implements CustomPacketPayload {
    public static final Type<FairWheelGameResultPayload> TYPE =
        new Type<>(new ResourceLocation(StardewCraft.MODID, "fair_wheel_game_result"));

    public static final StreamCodec<ByteBuf, FairWheelGameResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            FairWheelGameResultPayload::wager,
            ByteBufCodecs.BOOL,
            FairWheelGameResultPayload::won,
            FairWheelGameResultPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FairWheelGameResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FairWheelGameService.complete(player, payload.wager(), payload.won());
            }
        });
    }
}
