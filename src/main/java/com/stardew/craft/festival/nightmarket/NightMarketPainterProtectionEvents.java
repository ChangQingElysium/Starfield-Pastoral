package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class NightMarketPainterProtectionEvents {
    private NightMarketPainterProtectionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && NightMarketPainterService.isProtectedDisplayPosition(level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
