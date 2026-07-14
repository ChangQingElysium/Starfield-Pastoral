package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
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
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (level == null) {
            return;
        }
        FestivalMapOverlayManager.tick(level);
        FestivalService.advancePreparingSessions(level);
        FestivalService.tickPassiveFestivals(level);
        ActiveFestivalHandlers.tickAll(level);
    }
}
