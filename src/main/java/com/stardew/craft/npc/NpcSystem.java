package com.stardew.craft.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
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
import com.stardew.craft.npc.runtime.NpcScheduleAuditService;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.npc.runtime.NpcSpawnManager;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class NpcSystem {

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
            NpcRuntimeManager.tickServer(event.getServer());
            NpcScheduleAuditService.tick(event.getServer());
        } finally {
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.NPC_TICK, startedAt);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities())
                if (entity instanceof StardewNpcEntity npc) com.stardew.craft.npc.runtime.NpcActorPersistence.capture(npc);
            NpcChunkForceManager.releaseAllForcedChunks(level);
        }
        com.stardew.craft.npc.runtime.NpcExecutionCoordinator.clear(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NpcRuntimeManager.onServerStopped(event.getServer());
        NpcScheduleAuditService.onServerStopped(event.getServer());
        NpcSpawnManager.onServerStopped(event.getServer());
        NpcCentralMovementService.onServerStopped(event.getServer());
        com.stardew.craft.npc.runtime.NpcInteractionService.onServerStopped();
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.clearExecutionState();
    }

    /**
     * 强制立刻执行一次 NPC 系统 tick，用于跨维度传送后确保 NPC 立刻刷新。
     */
    public static void forceTickNow(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;
        NpcRuntimeManager.ensureActiveWorld(level);
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
