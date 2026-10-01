package com.stardew.craft.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishing.data.FishingTreasurePoolData;
import com.stardew.craft.fishpond.service.FishPondDataService;
import com.stardew.craft.fishing.server.FishingSessionManager;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

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
