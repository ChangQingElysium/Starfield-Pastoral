package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.StardewCraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.level.BlockEvent;

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
