package com.stardew.craft.cutscene.server;

import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.cutscene.network.CutsceneAnchorPayload;
import com.stardew.craft.cutscene.network.TriggerEventPayload;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.cutscene.data.EventTrigger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Server-side orchestrator for {@code wake_up} cutscene events.
 *
 * <p>At night settlement, {@link #enqueueAtNightSettlement(ServerPlayer)} scans
 * every {@code wake_up} event whose preconditions pass and appends its id to
 * the player's persisted {@link WakeUpEventQueueData} (dedup).
 *
 * <p>When the client acks wake-up (see {@code PlayerWokeUpPayload}), the server
 * calls {@link #dispatchNext(ServerPlayer)} which looks at the queue head,
 * pushes the per-player {@code farm_spawn} anchor, then sends the
 * {@code TriggerEventPayload}. The event is only removed from the queue once
 * the client confirms completion via {@code MarkEventSeenPayload} (which in
 * turn calls {@link #dispatchNext} again to chain the next one).
 */
public final class WakeUpEventScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Name used by cutscene JSON for the per-player farm spawn anchor. */
    public static final String FARM_SPAWN_ANCHOR = "farm_spawn";

    private WakeUpEventScheduler() {}

    /**
     * Called once per player during overnight settlement. Scans every wake_up
     * event whose preconditions pass and queues it (dedup).
     */
    public static void enqueueAtNightSettlement(ServerPlayer player) {
        enqueueEligible(player, true);
    }

    /**
     * Player login catch-up for per-player wake_up cutscenes.
     *
     * <p>Overnight settlement only scans players who are online at that moment.
     * Shared-farm members who were offline would otherwise permanently miss
     * their own next-morning cutscenes. Re-scan on login, keep queue deduped,
     * then immediately dispatch the queue head once cutscene data has synced.
     */
    public static void syncOnLogin(ServerPlayer player) {
        if (player == null) return;
        enqueueEligible(player, false);
        dispatchNext(player);
    }

    private static void enqueueEligible(ServerPlayer player, boolean allowBackfill) {
        if (player == null) return;
        if (allowBackfill) {
            com.stardew.craft.sewer.SewerService.backfillRustyKeyWakeUpEligibility(player);
        }

        ServerLevel level = player.serverLevel();
        WakeUpEventQueueData queue = WakeUpEventQueueData.get(level);
        EventSeenData seen = EventSeenData.get(level);
        UUID uuid = player.getUUID();

        // Deterministic order: events sorted by id ascending so multiple
        // qualifying events play in a stable sequence.
        List<EventData> candidates = EventRegistry.all().stream()
                .filter(e -> {
                    EventTrigger t = e.trigger();
                    return t != null && "wake_up".equals(t.type());
                })
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();

        for (EventData event : candidates) {
            if (seen.hasSeen(uuid, event.id())) continue;
            if (!ServerPreconditionEvaluator.evaluate(player, level, event.preconditions())) continue;
            if (queue.enqueueUnique(uuid, event.id())) {
                LOGGER.info("[WAKE_UP] Queued event '{}' for player {}", event.id(), player.getName().getString());
            }
        }
    }

    /**
     * Dispatch the head of the player's wake_up queue (if any). Sends the
     * per-player {@code farm_spawn} anchor first, then the
     * {@link TriggerEventPayload}. No-op if queue is empty or the player is
     * missing required state (e.g. no farm allocated).
     */
    public static void dispatchNext(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        WakeUpEventQueueData queue = WakeUpEventQueueData.get(level);
        UUID uuid = player.getUUID();

        String nextId = queue.peekFirst(uuid);
        if (nextId == null) return;
        if (ServerCutsceneTracker.isActive(uuid)) {
            LOGGER.debug("[WAKE_UP] Deferred '{}' for {} because another cutscene is active.",
                    nextId, player.getName().getString());
            return;
        }

        // Look up THIS player's farm spawn for the anchor. Multiplayer-safe:
        // every player has their own FarmInstance.
        BlockPos spawn = FarmInstanceRegistry.get().getFarmSpawnPoint(uuid);
        if (spawn == null) {
            LOGGER.warn("[WAKE_UP] Player {} has no farm spawn; cannot dispatch '{}'.",
                    player.getName().getString(), nextId);
            return;
        }

        // Anchor is the CENTER of the spawn block so offsets in JSON
        // (e.g. x:-2 → NPC two blocks west) line up with block grid.
        PacketDistributor.sendToPlayer(player,
                new CutsceneAnchorPayload(FARM_SPAWN_ANCHOR,
                        spawn.getX() + 0.5,
                        spawn.getY(),
                        spawn.getZ() + 0.5));

        if (!ServerCutsceneTracker.startEvent(player, nextId)) {
            LOGGER.warn("[WAKE_UP] Failed to dispatch '{}' to {}; keeping it queued.",
                    nextId, player.getName().getString());
            return;
        }

        LOGGER.info("[WAKE_UP] Dispatched '{}' to {} at anchor ({}, {}, {})",
                nextId, player.getName().getString(),
                spawn.getX(), spawn.getY(), spawn.getZ());
    }

    /** Called when the client reports an event finished. Removes it and dispatches next. */
    public static void onEventCompleted(ServerPlayer player, String eventId) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        WakeUpEventQueueData queue = WakeUpEventQueueData.get(level);
        UUID uuid = player.getUUID();

        // Pop the matching wake-up event. Completion of any other cutscene is
        // also the retry signal for a queue that was deferred behind it.
        if (!eventId.equals(queue.peekFirst(uuid))) {
            // A non-wake-up cutscene may have occupied the only active session while
            // this queue was waiting. Its completion is the retry signal.
            dispatchNext(player);
            return;
        }
        queue.remove(uuid, eventId);
        LOGGER.info("[WAKE_UP] Event '{}' completed for {}; dispatching next.",
                eventId, player.getName().getString());
        // Small scheduler-side delay not needed here — client is about to
        // unlock input anyway, and TriggerEventPayload just queues the event
        // on the client's EventPlayer (which is idle at this point).
        dispatchNext(player);
    }
}
