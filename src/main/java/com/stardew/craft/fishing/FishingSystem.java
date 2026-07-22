package com.stardew.craft.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishing.data.FishingTreasurePoolData;
import com.stardew.craft.fishpond.service.FishPondDataService;
import com.stardew.craft.fishing.server.FishingSessionManager;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FishingSystem {
	private FishingSystem() {
	}

	@SubscribeEvent
	public static void onAddReloadListeners(AddReloadListenerEvent event) {
		event.addListener(new FishingDataManager.ReloadListener());
		event.addListener(new FishingTreasurePoolData.ReloadListener());
		event.addListener(new FishPondDataService.ReloadListener());
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		long startedAt = ServerPerformanceRecorder.startTiming();
		try {
			FishingSessionManager.tickServer(event.getServer());
		} finally {
			ServerPerformanceRecorder.finishTiming(PerformanceTiming.FISHING_TICK, startedAt);
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		FishingSessionManager.onServerStopped(event.getServer());
	}
}
