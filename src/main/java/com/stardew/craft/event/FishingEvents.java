package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.ItemFishedEvent;

/**
 * 阻止使用 Stardew 钓竿时触发原版钓鱼战利品表
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class FishingEvents {
    
    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }
        
        // 检查主手或副手是否持有我们的钓竿
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        
        boolean usingStardewRod = false;
        if (!mainHand.isEmpty() && mainHand.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem) {
            usingStardewRod = true;
        }
        if (!offHand.isEmpty() && offHand.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem) {
            usingStardewRod = true;
        }
        
        // 如果使用的是我们的钓竿，取消原版战利品掉落
        if (usingStardewRod) {
            event.setCanceled(true);
        }
    }
}
