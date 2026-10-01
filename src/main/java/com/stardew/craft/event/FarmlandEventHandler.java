package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public class FarmlandEventHandler {

    /**
     * 防止星露谷维度的耕地被踩坏
     */
    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        
        // 检查维度是否为星露谷
        if (event.getLevel() instanceof net.minecraft.world.level.Level level 
                && level.dimension() == ModDimensions.STARDEW_VALLEY) {
            event.setCanceled(true);
        }
    }

}
