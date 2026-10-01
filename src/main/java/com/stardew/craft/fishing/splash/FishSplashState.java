package com.stardew.craft.fishing.splash;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.payload.FishSplashSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-{@link ServerLevel} persistent state for fish splash points (中文俗称"气泡").
 * <p>
 * SDV parity ({@code GameLocation.fishSplashPoint}): each location holds at most one
 * splash point. We mirror that semantics by keying the map on the SDV-aligned
 * location key (e.g. {@code "Beach"}, {@code "Forest"}, {@code "IslandWest"}).
 * <p>
 * Stored on the Stardew Valley dimension level only — splash points do not exist
 * in mines / overworld / nether.
 */
public class FishSplashState extends SavedData {

	private static final String DATA_NAME = "stardewcraft_fish_splash";

	public record Entry(BlockPos pos, int createdGameMinutes) {}

	private final Map<String, Entry> byLocationKey = new HashMap<>();

	public Map<String, Entry> view() {
		return java.util.Collections.unmodifiableMap(byLocationKey);
	}

	public @Nullable Entry get(String locationKey) {
		return byLocationKey.get(locationKey);
	}

	public void put(String locationKey, Entry entry) {
		byLocationKey.put(locationKey, entry);
		setDirty();
	}

	public void remove(String locationKey) {
		if (byLocationKey.remove(locationKey) != null) {
			setDirty();
		}
	}

	/**
	 * Test whether the given block position lies inside the splash rect for any
	 * of the supplied location keys. SDV uses a 1×1 tile rect (64×64 px).
	 * Returns the matching entry or null.
	 */
	public @Nullable Entry findIntersecting(List<String> locationKeys, BlockPos bobberPos) {
		for (String key : locationKeys) {
			Entry e = byLocationKey.get(key);
			if (e == null) continue;
			BlockPos p = e.pos();
			// 1-tile rect: same XZ column, ignore Y (water surface vs depth).
			if (p.getX() == bobberPos.getX() && p.getZ() == bobberPos.getZ()) {
				return e;
			}
		}
		return null;
	}

	// ─── persistence ─────────────────────────────────────────────────────

	@Override
	@SuppressWarnings("null")
	public CompoundTag save(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") HolderLookup.Provider provider) {
		ListTag list = new ListTag();
		for (Map.Entry<String, Entry> e : byLocationKey.entrySet()) {
			CompoundTag c = new CompoundTag();
			c.putString("Key", e.getKey());
			c.putInt("X", e.getValue().pos().getX());
			c.putInt("Y", e.getValue().pos().getY());
			c.putInt("Z", e.getValue().pos().getZ());
			c.putInt("CreatedMin", e.getValue().createdGameMinutes());
			list.add(c);
		}
		tag.put("Splashes", list);
		return tag;
	}

	public static FishSplashState load(CompoundTag tag, HolderLookup.Provider provider) {
		FishSplashState state = new FishSplashState();
		ListTag list = tag.getList("Splashes", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = list.getCompound(i);
			state.byLocationKey.put(
					c.getString("Key"),
					new Entry(new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")),
							c.getInt("CreatedMin")));
		}
		return state;
	}

	public static FishSplashState get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(FishSplashState::new, FishSplashState::load),
				DATA_NAME);
	}

	/**
	 * Get state for the canonical Stardew Valley dimension regardless of which
	 * level the caller is in. Returns null if the dim is not loaded (e.g. server
	 * with the dim disabled).
	 */
	public static @Nullable FishSplashState getStardewState(ServerLevel anyLevel) {
		ServerLevel sv = anyLevel.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
		return sv == null ? null : get(sv);
	}

	// ─── sync ────────────────────────────────────────────────────────────

	/** Send the full snapshot to a single player (login / dim-change). */
	public void sendFullSnapshot(ServerPlayer player) {
		Map<String, BlockPos> snapshot = new LinkedHashMap<>();
		byLocationKey.forEach((k, v) -> snapshot.put(k, v.pos()));
		PacketDistributor.sendToPlayer(player, FishSplashSyncPayload.snapshot(snapshot));
	}

	/** Push a single change (add or remove) to every player in the Stardew dim. */
	public static void broadcastChange(ServerLevel stardewLevel, String locationKey, @Nullable BlockPos posOrNull) {
		FishSplashSyncPayload payload;
		if (posOrNull != null) {
			payload = FishSplashSyncPayload.diff(java.util.Map.of(locationKey, posOrNull), java.util.Set.of());
		} else {
			payload = FishSplashSyncPayload.diff(java.util.Map.of(), java.util.Set.of(locationKey));
		}
		for (ServerPlayer p : stardewLevel.players()) {
			PacketDistributor.sendToPlayer(p, payload);
		}
	}
}
