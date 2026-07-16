package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.time.StardewTimePauseService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record StardewPauseStatePayload(boolean nonGameplay) implements CustomPacketPayload {

    public static final Type<StardewPauseStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "pause_state")
    );

    public static final StreamCodec<ByteBuf, StardewPauseStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        StardewPauseStatePayload::nonGameplay,
        StardewPauseStatePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StardewPauseStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StardewTimePauseService.updateClientState(player, payload.nonGameplay());
            }
        });
    }
}
