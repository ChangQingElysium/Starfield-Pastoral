package com.stardew.craft.fishing.splash;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.fishing.WaterFeatureSpawnRules;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Driver for fish splash points (气泡) — invoked once per in-game 10-minute boundary
 * from {@link com.stardew.craft.time.StardewTimeManager}.
 * <p>
 * SDV parity (GameLocation.performTenMinuteUpdate, lines 13654–13702 of GameLocation.cs):
 * <ul>
 *   <li>If no splash exists for a "location" → {@code r.NextBool()} (50%) gate, then 2 random
 *       tile tries: must be open water + {@code distanceToLand} in {@code (1, 5)} exclusive
 *       (i.e. 2..4).</li>
 *   <li>If splash exists → expire when {@code durationMin > 60} and {@code r.NextDouble() < 0.1 + duration/1800}.</li>
 * </ul>
 * MC adaptation: SDV's "location" maps to our SDV-aligned location key. We attempt
 * generation only for keys currently visited by an online player (otherwise a tree
 * falling in an empty forest costs CPU for no gameplay benefit).
 */
public final class FishSplashTicker {

	private FishSplashTicker() {}

	private static final int SEARCH_RADIUS = 24;          // blocks around player
	private static final int VERTICAL_PROBE = 6;          // y range to scan for water surface
	private static final int EXPIRY_MIN_DURATION = 60;    // SDV: 60 minutes before expiry checks start
	private static final double EXPIRY_BASE_CHANCE = 0.1; // SDV: 0.1 + dur/1800

	/** Called once per in-game 10-minute boundary on the server thread. */
	public static void performTenMinuteUpdate(MinecraftServer server) {
		ServerLevel stardew = server.getLevel(ModDimensions.STARDEW_VALLEY);
		if (stardew == null) return;

		var players = stardew.players();
		if (players.isEmpty()) {
			// No players in the dim → still age out splashes so they don't persist forever.
			expireOnly(stardew);
			return;
		}

		FishSplashState state = FishSplashState.get(stardew);
		StardewTimeManager tm = StardewTimeManager.get();
		// Absolute minute counter — uses (year, season, day, time) to be monotonically increasing
		// across day rollover so duration math is well defined.
		int nowMin = absoluteGameMinutes(tm);

		// 1) Group players by their location key for *generation* candidacy.
		//    A key gets one generation attempt per 10-min tick regardless of player count.
		Map<String, ServerPlayer> attemptByKey = new HashMap<>();
		for (ServerPlayer p : players) {
			if (WaterFeatureSpawnRules.isBlockedSpawnArea(stardew, p.blockPosition())) continue;
			Holder<Biome> bh = stardew.getBiome(p.blockPosition());
			List<String> keys = FishingDataManager.resolveVanillaAlignedLocationKeysStatic(
				stardew, bh, p.blockPosition());
			for (String k : keys) {
				if ("Default".equals(k)) continue;       // never spawn splash for the fallback bucket
				if ("Sewer".equals(k)) continue;
				attemptByKey.putIfAbsent(k, p);
			}
		}

		Random r = new Random();

		// 2) Expire existing entries (always, even if no nearby player).
		expireExpiredEntries(stardew, state, nowMin, r);

		// 3) Generate for keys that are eligible and currently empty.
		for (Map.Entry<String, ServerPlayer> attempt : attemptByKey.entrySet()) {
			String key = attempt.getKey();
			if (state.get(key) != null) continue;
			if (!r.nextBoolean()) continue;            // SDV: r.NextBool() gate

			ServerPlayer anchor = attempt.getValue();
			BlockPos chosen = pickSplashTile(stardew, anchor.blockPosition(), key, r);
			if (chosen != null) {
				state.put(key, new FishSplashState.Entry(chosen, nowMin));
				FishSplashState.broadcastChange(stardew, key, chosen);
				StardewCraft.LOGGER.debug("[SPLASH] generated splash for {} at {}", key, chosen);
			}
		}
	}

	private static void expireOnly(ServerLevel stardew) {
		FishSplashState state = FishSplashState.get(stardew);
		if (state.view().isEmpty()) return;
		StardewTimeManager tm = StardewTimeManager.get();
		int nowMin = absoluteGameMinutes(tm);
		expireExpiredEntries(stardew, state, nowMin, new Random());
	}

	/** Shared expiry pass: remove entries whose duration roll succeeds, broadcast each. */
	private static void expireExpiredEntries(ServerLevel stardew, FishSplashState state, int nowMin, Random r) {
		Set<String> toRemove = new HashSet<>();
		for (Map.Entry<String, FishSplashState.Entry> e : state.view().entrySet()) {
			if (WaterFeatureSpawnRules.isBlockedSpawnArea(stardew, e.getValue().pos())) {
				toRemove.add(e.getKey());
				continue;
			}
			int duration = nowMin - e.getValue().createdGameMinutes();
			if (duration < 0) duration = EXPIRY_MIN_DURATION + 1;
			if (duration > EXPIRY_MIN_DURATION
					&& r.nextDouble() < EXPIRY_BASE_CHANCE + duration / 1800.0) {
				toRemove.add(e.getKey());
			}
		}
		for (String key : toRemove) {
			state.remove(key);
			FishSplashState.broadcastChange(stardew, key, null);
			StardewCraft.LOGGER.debug("[SPLASH] expired splash for {}", key);
		}
	}

	/**
	 * SDV: 2 random tries, must be open water with {@code NoFishing == null} and
	 * {@code distanceToLand in (1, 5)}. We search in a small radius around an
	 * anchor player to avoid trawling unbounded MC space, and require the tile's
	 * biome to belong to the same SDV location key as the anchor's biome.
	 */
	private static @Nullable BlockPos pickSplashTile(ServerLevel level, BlockPos anchor, String requiredKey, Random r) {
		for (int tries = 0; tries < 2; tries++) {
			int dx = r.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
			int dz = r.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
			int probeY = anchor.getY() + 2;
			BlockPos surface = null;
			for (int dy = 0; dy < VERTICAL_PROBE; dy++) {
				BlockPos p = new BlockPos(anchor.getX() + dx, probeY - dy, anchor.getZ() + dz);
				if (!level.getFluidState(p).is(Fluids.WATER)) continue;
				// Must have air directly above (= "open water surface").
				if (!level.getBlockState(p.above()).isAir()) continue;
				surface = p;
				break;
			}
			if (surface == null) continue;
			if (!WaterFeatureSpawnRules.canSpawnAt(level, surface)) continue;

			// Biome key must match.
			Holder<Biome> bh = level.getBiome(surface);
			List<String> keys = FishingDataManager.resolveVanillaAlignedLocationKeysStatic(level, bh, surface);
			if (!keys.contains(requiredKey)) continue;

			int dist = distanceToLand(level, surface);
			if (dist <= 1 || dist >= 5) continue;     // SDV: toLand <= 1 || toLand >= 5 → reject

			return surface;
		}
		return null;
	}

	/**
	 * SDV {@code FishingRod.distanceToLand} (FishingRod.cs:851): expand a concentric
	 * square from 3×3 outward (Inflate(1,1) per iteration) and return the half-width
	 * of the first ring touching a non-water / off-map tile.
	 * <p>
	 * Mirrors C# behavior: {@code distance = r.Width / 2;} when found inside the loop
	 * (max width 11 → max return 5); returns 6 if none found, then caller subtracts 1.
	 * Final return is {@code distance - 1} (so range 0..5).
	 */
	private static int distanceToLand(ServerLevel level, BlockPos pos) {
		// Rectangle (cx-half, cz-half, side, side) starting at half=1 (3×3), grows by 1 each step.
		int half = 1;
		int distance = 1;
		boolean foundLand = false;
		while (!foundLand && (half * 2 + 1) <= 11) {
			int side = half * 2 + 1;
			// Iterate border tiles only.
			for (int dx = -half; dx <= half && !foundLand; dx++) {
				for (int dz = -half; dz <= half; dz++) {
					if (Math.abs(dx) != half && Math.abs(dz) != half) continue; // interior, skip
					BlockPos q = pos.offset(dx, 0, dz);
					// Off-map: SDV `!isTileOnMap` counts as land.
					if (!level.isInWorldBounds(q)) {
						foundLand = true;
						distance = side / 2;
						break;
					}
					// Non-water tile counts as land.
					if (!level.getFluidState(q).is(Fluids.WATER)) {
						foundLand = true;
						distance = side / 2;
						break;
					}
				}
			}
			half++;
		}
		if ((half * 2 + 1) > 11 && !foundLand) {
			distance = 6;
		}
		return distance - 1;
	}

	/** Monotonic in-game minute counter for duration math. */
	private static int absoluteGameMinutes(StardewTimeManager tm) {
		if (tm == null) return 0;
		// year/season/day are 1- or 0- based depending on getter; we only need monotonicity, so
		// any consistent linear combination works.
		int year = Math.max(1, tm.getCurrentYear());
		int season = tm.getCurrentSeason();   // 0..3
		int day = tm.getCurrentDay();         // 1..28
		int time = tm.getCurrentTime();       // 600..2600 (mod uses minutes)
		return ((year - 1) * 4 + season) * 28 * 1440 + (day - 1) * 1440 + time;
	}
}
