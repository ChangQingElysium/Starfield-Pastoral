package com.stardew.craft.cutscene.runtime;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.cutscene.data.EventTrigger;
import com.stardew.craft.cutscene.data.CutsceneTriggerLocations;
import com.stardew.craft.cutscene.network.ClientEventSeenCache;
import com.stardew.craft.cutscene.network.NotifyCutsceneStartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-side trigger checker that runs every N ticks.
 * Checks enter_area events by matching the player position against event AABB areas.
 *
 * Hook: called from ModClientEvents.onClientTick() after EventPlayer.tick().
 */
@OnlyIn(Dist.CLIENT)
public final class EventTriggerChecker {

    private static final int CHECK_INTERVAL = 4; // every ~0.2 seconds
    private static int tickCounter = 0;

    /** Cooldown after an event ends before checking again (prevents re-trigger). */
    private static int cooldownTicks = 0;
    private static final int POST_EVENT_COOLDOWN = 40; // 2 seconds

    /** Brief grace period after joining or changing dimension. */
    private static final int JOIN_GRACE_TICKS = 4; // ~0.2 seconds
    private static int joinGraceTicks = JOIN_GRACE_TICKS;
    private static ResourceKey<Level> lastDimension = null;

    private EventTriggerChecker() {}

    /**
     * Called every client tick from ModClientEvents.
     */
    public static void tick() {
        // Don't check during cutscene playback
        if (EventPlayer.get().isRunning()) {
            cooldownTicks = POST_EVENT_COOLDOWN;
            return;
        }

        // Post-event cooldown
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Only check in Stardew-related dimensions
        @SuppressWarnings("null")
        ResourceKey<Level> currentDim = mc.level.dimension();
        if (currentDim != ModDimensions.STARDEW_VALLEY
                && currentDim != ModMiningDimensions.STARDEW_MINING) {
            // Reset grace whenever we leave Stardew dimensions so re-entry
            // gets the same protection.
            lastDimension = currentDim;
            joinGraceTicks = JOIN_GRACE_TICKS;
            return;
        }

        // Cache sync checks below already protect initial login from stale data,
        // so dimension changes only need a few real client ticks for the level
        // and player position to settle.
        if (lastDimension != currentDim) {
            lastDimension = currentDim;
            joinGraceTicks = JOIN_GRACE_TICKS;
            tickCounter = 0;
        }

        tickCounter++;
        if (joinGraceTicks > 0) {
            joinGraceTicks--;
            if (joinGraceTicks > 0) return;
        }

        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Wait for every client cache that any precondition could read. If any of these
        // is stale (pre-login defaults / previous world's data), negative preconditions
        // like "not_mail" or "not_saw_event" would trivially pass and the event would
        // fire — then get marked seen — before the player's real state arrived. Positive
        // preconditions would also spuriously fail, letting an event fire after the player
        // has already walked past the trigger area.
        if (!ClientEventSeenCache.isSynced()) return;
        if (!com.stardew.craft.client.ClientPlayerDataCache.isSynced()) return;
        if (!com.stardew.craft.client.ClientMuseumDonationCache.isSynced()) return;
        if (!com.stardew.craft.client.NpcFriendshipClientCache.isSynced()) return;
        if (!com.stardew.craft.weather.ClientWeatherCache.isSynced()) return;
        if (!com.stardew.craft.client.hud.StardewTimeHud.isTimeSynced()) return;

        // Require the chunk under the player to be loaded — auto triggers
        // should never run while the world around the player is still
        // streaming in.
        LocalPlayer localPlayer = mc.player;
        net.minecraft.client.multiplayer.ClientLevel localLevel = mc.level;
        if (localPlayer == null || localLevel == null) return;
        net.minecraft.core.BlockPos playerBlock = localPlayer.blockPosition();
        if (!localLevel.isLoaded(playerBlock)) return;

        checkEnterAreaEvents(localPlayer);
    }

    private static void checkEnterAreaEvents(LocalPlayer player) {
        for (EventData event : EventRegistry.all()) {
            EventTrigger trigger = event.trigger();
            if (!"enter_area".equals(trigger.type())) continue;

            String location = trigger.location();
            if (location == null) continue; // ill-formed event, skip

            if (!CutsceneTriggerLocations.contains(player, trigger)) continue;

            // 3) Per-event seen check (server-synced)
            if (ClientEventSeenCache.hasSeen(event.id())) continue;

            // 4) Preconditions
            if (!PreconditionEvaluator.evaluate(event.preconditions())) continue;

            PacketDistributor.sendToServer(new NotifyCutsceneStartPayload(event.id()));
            cooldownTicks = 20;
            return; // only one event at a time
        }
    }

    /**
     * Check if any interact_npc events are pending for a given NPC.
     * Called from server-side NpcInteractionService before opening dialogue.
     * Returns the event ID to trigger, or null if none.
     */
    public static String findPendingNpcEvent(String npcId) {
        List<EventData> events = EventRegistry.getByNpc(npcId);
        if (events.isEmpty()) return null;

        for (EventData event : events) {
            if (ClientEventSeenCache.hasSeen(event.id())) continue;
            if (!PreconditionEvaluator.evaluate(event.preconditions())) continue;
            return event.id();
        }
        return null;
    }

    /**
     * Reset state (e.g. on disconnect).
     */
    public static void reset() {
        tickCounter = 0;
        cooldownTicks = 0;
        joinGraceTicks = JOIN_GRACE_TICKS;
        lastDimension = null;
    }
}
