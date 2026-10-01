package com.stardew.craft.client.fishing;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side cache of currently-active fish splash points (气泡).
 * Populated by {@link com.stardew.craft.network.payload.FishSplashSyncPayload}.
 * Read by the particle handler each client tick.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFishSplashState {

	private static final Map<String, BlockPos> SPLASHES = new LinkedHashMap<>();

	private ClientFishSplashState() {}

	public static void replaceAll(Map<String, BlockPos> snapshot) {
		SPLASHES.clear();
		SPLASHES.putAll(snapshot);
	}

	public static void put(String key, BlockPos pos) {
		SPLASHES.put(key, pos);
	}

	public static void remove(String key) {
		SPLASHES.remove(key);
	}

	public static void clearAll() {
		SPLASHES.clear();
	}

	public static Map<String, BlockPos> view() {
		return Collections.unmodifiableMap(SPLASHES);
	}
}
