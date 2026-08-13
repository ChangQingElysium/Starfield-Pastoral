package com.stardew.craft.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import com.stardew.craft.entity.npc.BooksellerEntity;
import com.stardew.craft.entity.npc.CamelMerchantEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import com.stardew.craft.npc.data.NpcDataManager;
import com.stardew.craft.npc.runtime.NpcCentralMovementService;
import com.stardew.craft.npc.runtime.NpcChunkForceManager;
import com.stardew.craft.npc.runtime.NpcRuntimeManager;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class NpcSystem {
    /** Tracks whether the last tick had players in the dimension. */
    private static boolean previouslyHadPlayers = false;

    private NpcSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {     
        event.addListener(new NpcDataManager.ReloadListener());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long startedAt = ServerPerformanceRecorder.startTiming();
        try {
            tickServer(event);
        } finally {
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.NPC_TICK, startedAt);
        }
    }

    private static void tickServer(ServerTickEvent.Post event) {
        NpcRuntimeManager.tickServer(event.getServer());
        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        NpcSpawnManager.prepareServerContext(level);

        boolean anyPlayerInStardew = false;
        boolean anyPlayerInMining = false;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            if (ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())) {
                anyPlayerInStardew = true;
            }
            if (ModMiningDimensions.STARDEW_MINING.equals(player.level().dimension())) {
                anyPlayerInStardew = true;
                anyPlayerInMining = true;
            }
        }

        if (!anyPlayerInStardew) {
            if (previouslyHadPlayers) {
                // Player just left — release all forced chunks and snap NPCs to schedule targets.
                NpcSpawnManager.onAllPlayersLeft(level);
                NpcChunkForceManager.releaseAllForcedChunks(level);
                // Also release mining dimension forced chunks
                ServerLevel mineLevel = event.getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
                if (mineLevel != null) {
                    NpcChunkForceManager.releaseAllForcedChunks(mineLevel);
                }
                NpcScheduleRuntimeService.invalidateCache();
                previouslyHadPlayers = false;
            }
            // No player in dimension — skip all NPC ticking.
            return;
        }

        if (!previouslyHadPlayers) {
            // Player just entered — invalidate caches & snap NPCs into position.
            NpcScheduleRuntimeService.invalidateCache();
            NpcSpawnManager.onPlayerEntered(level);
            previouslyHadPlayers = true;
        }

        // Existence and schedule target resolution are lifecycle maintenance, not
        // passage-of-time simulation. They must continue while dialogue/cutscenes
        // pause Stardew time, otherwise a newly entered area can remain NPC-free.
        NpcScheduleRuntimeService.tick(level);
        com.stardew.craft.festival.ActiveFestivalHandlers.tickNpcActors(level);
        NpcSpawnManager.tick(level);

        // Tick mining-dimension NPCs (e.g. Dwarf) when any player is in the mine
        if (anyPlayerInMining) {
            ServerLevel mineLevel = event.getServer().getLevel(ModMiningDimensions.STARDEW_MINING);
            if (mineLevel != null) {
                NpcSpawnManager.tickMiningDimension(mineLevel);
            }
        }

        if (com.stardew.craft.time.StardewTimePauseService.isPaused(event.getServer())) {
            return;
        }
        NpcCentralMovementService.tick(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NpcRuntimeManager.onServerStopped(event.getServer());
        NpcSpawnManager.onServerStopped(event.getServer());
        NpcCentralMovementService.onServerStopped(event.getServer());
        com.stardew.craft.npc.runtime.NpcInteractionService.onServerStopped();
        NpcScheduleRuntimeService.invalidateCache();
        previouslyHadPlayers = false;
    }

    /**
     * 强制立刻执行一次 NPC 系统 tick，用于跨维度传送后确保 NPC 立刻刷新。
     */
    public static void forceTickNow(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;
        if (!previouslyHadPlayers) {
            NpcScheduleRuntimeService.invalidateCache();
            NpcSpawnManager.onPlayerEntered(level);
            previouslyHadPlayers = true;
        }
        boolean recovered = NpcSpawnManager.forceNpcToCurrentSchedule(level, "wizard");
        if (!recovered) {
            // A malformed/missing schedule must not make the overworld tower portal
            // unusable. Fall back to the default spawn data and verify the result.
            NpcSpawnManager.forceSpawnNpc("wizard");
            NpcSpawnManager.tick(level);
            recovered = NpcSpawnManager.getTrackedNpc(level, "wizard") != null;
        }
        if (!recovered) {
            StardewCraft.LOGGER.warn("[NPC_SPAWN] Wizard was still unavailable after forced tower-entry recovery");
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof StardewNpcEntity npc)) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (NpcSpawnManager.onNpcJoin(serverLevel, npc)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNpcLeadInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (isSneakingProtectedNpcInteraction(event.getTarget(), event.getEntity())) {
            cancelProtectedNpcInteraction(event);
            return;
        }
        if (isProtectedNpcLikeEntity(event.getTarget())
                && event.getEntity().getItemInHand(event.getHand()).is(Items.LEAD)) {
            cancelProtectedNpcInteraction(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNpcLeadInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (isSneakingProtectedNpcInteraction(event.getTarget(), event.getEntity())) {
            cancelProtectedNpcInteraction(event);
            return;
        }
        if (isProtectedNpcLikeEntity(event.getTarget())
                && event.getEntity().getItemInHand(event.getHand()).is(Items.LEAD)) {
            cancelProtectedNpcInteraction(event);
        }
    }

    private static boolean isSneakingProtectedNpcInteraction(Entity target, net.minecraft.world.entity.player.Player player) {
        return player.isShiftKeyDown() && isProtectedNpcLikeEntity(target);
    }

    private static void cancelProtectedNpcInteraction(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static void cancelProtectedNpcInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static boolean isProtectedNpcLikeEntity(Entity entity) {
        return entity instanceof StardewNpcEntity
                || entity instanceof BooksellerEntity
                || entity instanceof TravelingCartEntity
                || entity instanceof CamelMerchantEntity
                || entity instanceof EventActorEntity
                || entity instanceof EventPlayerActorEntity;
    }
}
