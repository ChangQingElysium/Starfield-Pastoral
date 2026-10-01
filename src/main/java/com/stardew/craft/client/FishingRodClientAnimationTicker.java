package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 驱动鱼竿第一人称动画状态机的客户端 tick。
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class FishingRodClientAnimationTicker {
	private FishingRodClientAnimationTicker() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		FishingRodCastAnimationState.tick();
	}
}
