package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 肥料清理事件：
 * - 耕地被破坏时立即移除肥料
 * - 定期清理所有不在耕地上的肥料残留
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class FertilizerCleanupEvents {
	private FertilizerCleanupEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}
		var pos = event.getPos();
		// Some valid farmland breaks are completed manually by another handler and then canceled.
		// A protected/canceled break leaves the farmland in place and must keep its fertilizer.
		if (event.isCanceled()
				&& serverLevel.getBlockState(pos).getBlock() instanceof FarmBlock) {
			return;
		}
		var manager = FertilizerManager.get(serverLevel);
		manager.removeFertilizer(serverLevel, pos);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
		if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}
		FertilizerManager.get(serverLevel).removeFertilizer(serverLevel, event.getPos());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onRetill(BlockEvent.BlockToolModificationEvent event) {
		var finalState = event.getFinalState();
		if (event.isCanceled()
				|| event.isSimulated()
				|| event.getState().getBlock() instanceof FarmBlock
				|| finalState == null
				|| !(finalState.getBlock() instanceof FarmBlock)
				|| !(event.getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}
		// A newly created farmland tile must never inherit fertilizer from an older tile at the
		// same coordinates, even if it was re-tilled before the periodic cleanup ran.
		FertilizerManager.get(serverLevel).removeFertilizer(serverLevel, event.getPos());
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		// 每5秒（100 ticks）清理一次无效肥料，避免每tick都清理
		if (tickCounter++ >= 100) {
			tickCounter = 0;
			var overworld = event.getServer().overworld();
			var manager = FertilizerManager.get(overworld);
			manager.cleanupInvalidEntries(event.getServer());
		}
	}
	
	private static int tickCounter = 0;
}
