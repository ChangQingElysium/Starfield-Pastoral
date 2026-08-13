package com.stardew.craft.cutscene;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.cutscene.network.ClientEventSeenCache;
import com.stardew.craft.cutscene.network.SyncEventRegistryPayload;
import com.stardew.craft.cutscene.network.SyncEventSeenPayload;
import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventTriggerChecker;
import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;

/**
 * Server-side event bus hooks for the cutscene/event system.
 * - Registers JSON reload listener
 * - Syncs eventsSeen on player login
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CutsceneSystem {

    private CutsceneSystem() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EventRegistry.ReloadListener());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Sync cutscene event JSON data so client can play them on dedicated servers
            PacketDistributor.sendToPlayer(player, SyncEventRegistryPayload.current());

            EventSeenData data = EventSeenData.get(player.serverLevel());
            var seen = data.getSeenEvents(player.getUUID());
            PacketDistributor.sendToPlayer(player, new SyncEventSeenPayload(new ArrayList<>(seen)));

            // Push friendship overview so that client-side "friendship" preconditions can be
            // evaluated without waiting for the player to open the social menu. Without this,
            // auto-triggered enter_area events gated on friendship never fire.
            com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload.sendOverviewTo(player);

            // Offline players miss the overnight wake_up scan. Rebuild and dispatch their
            // personal queue after login sync so shared-farm storylines stay independent.
            com.stardew.craft.cutscene.server.WakeUpEventScheduler.syncOnLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        long startedAt = ServerPerformanceRecorder.startTiming();
        try {
            scanTriggers(player);
        } finally {
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.CUTSCENE_TRIGGER_SCAN, startedAt);
        }
    }

    private static void scanTriggers(ServerPlayer player) {
        EventSeenData seen = EventSeenData.get(player.serverLevel());
        for (var cutscene : EventRegistry.all()) {
            String rawType = cutscene.trigger().type();
            if (rawType == null || rawType.indexOf(':') < 1) continue;
            if (seen.hasSeen(player.getUUID(), cutscene.id())) continue;
            if (!com.stardew.craft.cutscene.server.ServerPreconditionEvaluator.evaluate(
                    player, player.serverLevel(), cutscene.preconditions())) continue;
            var type = net.minecraft.resources.ResourceLocation.tryParse(rawType);
            if (type == null) continue;
            boolean matches = com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers
                    .test(type, cutscene.trigger().raw(), player)
                    .resultOrPartial(message -> StardewCraft.LOGGER.error(
                            "[Cutscene] Trigger {} failed for {}: {}", type, cutscene.id(), message))
                    .orElse(false);
            if (matches) {
                com.stardew.craft.cutscene.server.ServerCutsceneTracker.startEvent(player, cutscene.id());
                return;
            }
        }
    }

    /**
     * Client-side cleanup on disconnect. Without this, the seen-event set from a previous
     * world/server lingers in memory; reconnecting to a different save would incorrectly
     * treat same-id events as already seen until the new SyncEventSeenPayload arrives.
     */
    public static final class ClientEvents {
        private ClientEvents() {}

        public static void resetClientState() {
            EventPlayer.get().reset();
            com.stardew.craft.network.payload.ClientNpcVisibilityState.clear();
            ClientEventSeenCache.reset();
            EventTriggerChecker.reset();
        }
    }
}
