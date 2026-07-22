package com.stardew.craft.cutscene.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.server.EventSeenData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

/**
 * Client → Server: notify that the player finished watching an event.
 */
public record MarkEventSeenPayload(String eventId, long sessionId) implements CustomPacketPayload {

    public static final Type<MarkEventSeenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mark_event_seen"));

    public static final StreamCodec<ByteBuf, MarkEventSeenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MarkEventSeenPayload::eventId,
            ByteBufCodecs.VAR_LONG, MarkEventSeenPayload::sessionId,
            MarkEventSeenPayload::new);

    @SuppressWarnings("null")
    public static void handle(MarkEventSeenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!com.stardew.craft.cutscene.server.ServerCutsceneTracker.authorizeCompletion(
                    player, payload.sessionId, payload.eventId)) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Rejected unauthorized cutscene completion '{}' from {}",
                        payload.eventId, player.getName().getString());
                return;
            }
            // Cutscene ended client-side: release server-side action lock
            com.stardew.craft.cutscene.server.ServerCutsceneTracker.clear(player);
            com.stardew.craft.festival.EggFestivalService.onCutsceneCompleted(player, payload.eventId);
            com.stardew.craft.festival.FlowerDanceService.onCutsceneCompleted(player, payload.eventId);
            com.stardew.craft.festival.LuauFestivalService.onCutsceneCompleted(player, payload.eventId);
            com.stardew.craft.festival.MoonlightJelliesFestivalService.onCutsceneCompleted(player, payload.eventId);
            com.stardew.craft.festival.FestivalOfIceService.onCutsceneCompleted(player, payload.eventId);
            com.stardew.craft.quest.DarkTalismanQuestService.onCutsceneCompleted(player, payload.eventId);
            EventSeenData data = EventSeenData.get(player.serverLevel());
            boolean winterStarRuntimeEvent = com.stardew.craft.festival.WinterStarFestivalService
                    .onCutsceneCompleted(player, payload.eventId);
            if (!winterStarRuntimeEvent) {
                data.markSeen(player.getUUID(), payload.eventId);
            }

            // Sync back full list to confirm
            var seen = data.getSeenEvents(player.getUUID());
            PacketDistributor.sendToPlayer(player, new SyncEventSeenPayload(new ArrayList<>(seen)));

            // If this was a queued wake_up event, drop it from the queue and
            // dispatch the next one (if any). No-op for other trigger types.
            com.stardew.craft.cutscene.server.WakeUpEventScheduler.onEventCompleted(
                    player, payload.eventId);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
