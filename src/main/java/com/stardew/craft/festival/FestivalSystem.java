package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FestivalSystem {
    private FestivalSystem() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FestivalRegistry.ReloadListener());
        event.addListener(
                new FestivalMapOverlayRegistry.ReloadListener());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(ServerTickEvent.Post event) {
        long startedAt = ServerPerformanceRecorder.startTiming();
        try {
            tickServer(event);
        } finally {
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.FESTIVAL_TICK, startedAt);
        }
    }

    private static void tickServer(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        if (com.stardew.craft.time.StardewTimePauseService.shouldPauseLevel(level)) {
            return;
        }
        FestivalMapOverlayManager.tick(level);
        FestivalService.advancePreparingSessions(level);
        FestivalService.tickPassiveFestivals(level);
        ActiveFestivalHandlers.tickAll(level);
        tickAddonMechanics(level);
    }

    private static void tickAddonMechanics(ServerLevel level) {
        FestivalWorldData data = FestivalWorldData.get(level);
        for (FestivalDefinition definition : FestivalRegistry.all()) {
            data.getSession(definition.id()).ifPresent(session -> {
                if (session.phase() != FestivalSessionPhase.OPEN
                        && session.phase()
                        != FestivalSessionPhase.MAIN_EVENT) {
                    return;
                }
                com.stardew.craft.api.v1.internal.festival
                        .StardewFestivalMechanicRegistry.tick(
                                level, definition, session);
            });
        }
    }
}
