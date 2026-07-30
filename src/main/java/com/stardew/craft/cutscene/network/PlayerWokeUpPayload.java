package com.stardew.craft.cutscene.network;

import com.mojang.logging.LogUtils;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.server.WakeUpEventScheduler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * Client → Server: the player just regained control after overnight settlement
 * (final "OK" on the ShippingMenu). Server dispatches the next queued wake_up
 * cutscene if there is one.
 */
public record PlayerWokeUpPayload() implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<PlayerWokeUpPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "player_woke_up"));

    public static final StreamCodec<ByteBuf, PlayerWokeUpPayload> STREAM_CODEC =
            StreamCodec.unit(new PlayerWokeUpPayload());

    public static void handle(PlayerWokeUpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            LOGGER.info("[WAKE_UP] Player {} ack'd wake-up; dispatching.",
                    player.getName().getString());
            com.stardew.craft.network.overnight.OvernightSettlementTracker
                    .acknowledgePendingSettlement(player);
            com.stardew.craft.time.StardewTimePauseService.endOvernightSettlement(player);
            WakeUpEventScheduler.dispatchNext(player);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
