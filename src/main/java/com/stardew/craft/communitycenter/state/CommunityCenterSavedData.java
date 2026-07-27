package com.stardew.craft.communitycenter.state;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressCauses;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Per-player persistence for Community Center progress.
 * <p>
 * 每位玩家拥有独立的 bundle 进度、奖励状态和区域完成标记。
 * 存储结构: UUID → { bundleSlots, bundleRewards, areasComplete }
 */
public class CommunityCenterSavedData extends SavedData {

    private static final String DATA_NAME = "stardew_community_center";

    // NBT tag names
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_UUID = "UUID";
    private static final String TAG_BUNDLES = "Bundles";
    private static final String TAG_BUNDLE_ID = "Id";
    private static final String TAG_SLOTS = "Slots";
    private static final String TAG_REWARD_AVAILABLE = "RewardAvailable";
    private static final String TAG_AREAS_COMPLETE = "AreasComplete";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_ADDON_DATA = "AddonData";

    /** 当前存储版本 (2 = per-player) */
    private static final int CURRENT_VERSION = 2;

    /** 单个玩家的 CC 进度数据 */
    private static class PlayerProgress {
        final Map<Integer, boolean[]> bundleSlots = new HashMap<>();
        final Map<Integer, Boolean> bundleRewards = new HashMap<>();
        final boolean[] areasComplete = new boolean[7];
        CompoundTag addonData = new CompoundTag();
    }

    private final Map<UUID, PlayerProgress> playerData = new HashMap<>();

    // ── Static accessor ──

    public static CommunityCenterSavedData get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("No server available");

        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CommunityCenterSavedData::new, CommunityCenterSavedData::load),
                DATA_NAME
        );
    }

    public static CommunityCenterSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CommunityCenterSavedData::new, CommunityCenterSavedData::load),
                DATA_NAME
        );
    }

    // ── Internal helper ──

    private PlayerProgress getProgress(UUID player) {
        return playerData.computeIfAbsent(player, k -> new PlayerProgress());
    }

    // ── Load / Save ──

    public static CommunityCenterSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        CommunityCenterSavedData data = new CommunityCenterSavedData();

        int version = tag.getInt(TAG_VERSION);

        if (version >= 2) {
            // Per-player format
            ListTag playersList = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
            for (int p = 0; p < playersList.size(); p++) {
                CompoundTag playerTag = playersList.getCompound(p);
                UUID uuid = playerTag.getUUID(TAG_UUID);
                PlayerProgress progress = data.getProgress(uuid);
                loadProgressFromTag(playerTag, progress);
            }
        } else {
            // Legacy V1 (global) format: 迁移到一个特殊的 "legacy" UUID
            // 第一个进入 CC 的玩家会继承此进度
            UUID legacyUUID = new UUID(0L, 0L);
            PlayerProgress legacy = data.getProgress(legacyUUID);
            loadProgressFromTag(tag, legacy);
            StardewCraft.LOGGER.info("[COMMUNITY CENTER] Migrated legacy global data to per-player format (legacy UUID placeholder)");
        }

        int totalBundles = 0;
        for (PlayerProgress pp : data.playerData.values()) totalBundles += pp.bundleSlots.size();
        StardewCraft.LOGGER.info("[COMMUNITY CENTER] Loaded state: {} players, {} total bundle entries",
                data.playerData.size(), totalBundles);
        return data;
    }

    private static void loadProgressFromTag(CompoundTag tag, PlayerProgress progress) {
        ListTag bundlesList = tag.getList(TAG_BUNDLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < bundlesList.size(); i++) {
            CompoundTag bundleTag = bundlesList.getCompound(i);
            int bundleId = bundleTag.getInt(TAG_BUNDLE_ID);
            byte[] slotBytes = bundleTag.getByteArray(TAG_SLOTS);
            boolean[] slots = new boolean[slotBytes.length];
            for (int j = 0; j < slotBytes.length; j++) {
                slots[j] = slotBytes[j] != 0;
            }
            progress.bundleSlots.put(bundleId, slots);
            progress.bundleRewards.put(bundleId, bundleTag.getBoolean(TAG_REWARD_AVAILABLE));
        }

        if (tag.contains(TAG_AREAS_COMPLETE)) {
            byte[] areaBytes = tag.getByteArray(TAG_AREAS_COMPLETE);
            for (int i = 0; i < Math.min(areaBytes.length, progress.areasComplete.length); i++) {
                progress.areasComplete[i] = areaBytes[i] != 0;
            }
        }
        if (tag.contains(TAG_ADDON_DATA, Tag.TAG_COMPOUND)) {
            progress.addonData = tag.getCompound(TAG_ADDON_DATA).copy();
        }
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);

        ListTag playersList = new ListTag();
        for (Map.Entry<UUID, PlayerProgress> entry : playerData.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_UUID, entry.getKey());
            saveProgressToTag(playerTag, entry.getValue());
            playersList.add(playerTag);
        }
        tag.put(TAG_PLAYERS, playersList);

        return tag;
    }

    private void saveProgressToTag(CompoundTag tag, PlayerProgress progress) {
        ListTag bundlesList = new ListTag();
        for (Map.Entry<Integer, boolean[]> entry : progress.bundleSlots.entrySet()) {
            CompoundTag bundleTag = new CompoundTag();
            bundleTag.putInt(TAG_BUNDLE_ID, entry.getKey());

            boolean[] slots = entry.getValue();
            byte[] slotBytes = new byte[slots.length];
            for (int i = 0; i < slots.length; i++) {
                slotBytes[i] = (byte) (slots[i] ? 1 : 0);
            }
            bundleTag.putByteArray(TAG_SLOTS, slotBytes);
            bundleTag.putBoolean(TAG_REWARD_AVAILABLE,
                    progress.bundleRewards.getOrDefault(entry.getKey(), false));

            bundlesList.add(bundleTag);
        }
        tag.put(TAG_BUNDLES, bundlesList);

        byte[] areaBytes = new byte[progress.areasComplete.length];
        for (int i = 0; i < progress.areasComplete.length; i++) {
            areaBytes[i] = (byte) (progress.areasComplete[i] ? 1 : 0);
        }
        tag.putByteArray(TAG_AREAS_COMPLETE, areaBytes);
        if (!progress.addonData.isEmpty()) {
            tag.put(TAG_ADDON_DATA, progress.addonData.copy());
        }
    }

    // ── Bundle Slot Operations (all take UUID) ──

    /** Get or initialize the slot array for a bundle. */
    public boolean[] getSlots(UUID player, int bundleId) {
        return getProgress(player).bundleSlots.computeIfAbsent(bundleId, id -> {
            BundleDefinition def =
                    StardewCommunityCenterVariantRegistry.bundle(player, id);
            int size = (def != null) ? def.totalSlots() : 0;
            return new boolean[size];
        });
    }

    /** Mark a specific ingredient slot as completed. Returns true if state changed. */
    public boolean markSlotComplete(UUID player, int bundleId, int slotIndex) {
        boolean[] slots = getSlots(player, bundleId);
        if (slotIndex < 0 || slotIndex >= slots.length) return false;
        if (slots[slotIndex]) return false;

        var online = onlinePlayer(player);
        StardewProgressSnapshot before = online == null
                ? null : StardewProgressRegistry
                        .communityCenterBundleSnapshot(online, bundleId);
        slots[slotIndex] = true;
        setDirty();
        dispatchBundleChange(
                online, bundleId, before,
                StardewProgressCauses.BUNDLE_DEPOSIT);
        return true;
    }

    /** Check if a specific slot is complete. */
    public boolean isSlotComplete(UUID player, int bundleId, int slotIndex) {
        boolean[] slots = getProgress(player).bundleSlots.get(bundleId);
        if (slots == null || slotIndex < 0 || slotIndex >= slots.length) return false;
        return slots[slotIndex];
    }

    /** Count how many slots are filled for a bundle. */
    public int countFilledSlots(UUID player, int bundleId) {
        boolean[] slots = getProgress(player).bundleSlots.get(bundleId);
        if (slots == null) return 0;
        int count = 0;
        for (boolean s : slots) {
            if (s) count++;
        }
        return count;
    }

    /**
     * Check if a bundle is complete (filled slots >= requiredCount).
     */
    public boolean isBundleComplete(UUID player, int bundleId) {
        BundleDefinition def =
                StardewCommunityCenterVariantRegistry.bundle(player, bundleId);
        if (def == null) return false;
        return countFilledSlots(player, bundleId) >= def.requiredCount();
    }

    /** Mark all slots of a bundle as complete. */
    public void markBundleAllSlotsComplete(UUID player, int bundleId) {
        boolean[] slots = getSlots(player, bundleId);
        boolean changed = false;
        var online = onlinePlayer(player);
        StardewProgressSnapshot before = online == null
                ? null : StardewProgressRegistry
                        .communityCenterBundleSnapshot(online, bundleId);
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i]) {
                slots[i] = true;
                changed = true;
            }
        }
        if (changed) {
            setDirty();
            dispatchBundleChange(
                    online, bundleId, before,
                    StardewProgressCauses.BUNDLE_DEPOSIT);
        }
    }

    // ── Bundle Reward Operations ──

    public boolean isRewardAvailable(UUID player, int bundleId) {
        return getProgress(player).bundleRewards.getOrDefault(bundleId, false);
    }

    public void setRewardAvailable(UUID player, int bundleId, boolean available) {
        boolean previous = getProgress(player).bundleRewards
                .getOrDefault(bundleId, false);
        if (previous == available) {
            return;
        }
        var online = onlinePlayer(player);
        StardewProgressSnapshot before = online == null
                ? null : StardewProgressRegistry
                        .communityCenterBundleSnapshot(online, bundleId);
        getProgress(player).bundleRewards.put(bundleId, available);
        setDirty();
        dispatchBundleChange(
                online, bundleId, before,
                available
                        ? StardewProgressCauses.BUNDLE_DEPOSIT
                        : StardewProgressCauses.REWARD_CLAIM);
    }

    // ── Area Operations ──

    public boolean isAreaComplete(UUID player, int areaId) {
        PlayerProgress p = playerData.get(player);
        if (p == null || areaId < 0 || areaId >= p.areasComplete.length) return false;
        return p.areasComplete[areaId];
    }

    /** Mark an area as complete. Returns true if state changed. */
    public boolean markAreaComplete(UUID player, int areaId) {
        PlayerProgress p = getProgress(player);
        if (areaId < 0 || areaId >= p.areasComplete.length) return false;
        if (p.areasComplete[areaId]) return false;
        var online = onlinePlayer(player);
        StardewProgressSnapshot before = online == null
                ? null : StardewProgressRegistry
                        .communityCenterAreaSnapshot(online, areaId);
        p.areasComplete[areaId] = true;
        setDirty();
        if (online != null && before != null) {
            StardewProgressSnapshot after = StardewProgressRegistry
                    .communityCenterAreaSnapshot(online, areaId);
            if (after != null) {
                StardewProgressRegistry.dispatchChanges(
                        online, before, after,
                        StardewProgressCauses.BUNDLE_DEPOSIT);
            }
        }
        return true;
    }

    /**
     * Check if all 6 main areas (0-5) are complete for a player.
     */
    public boolean areAllAreasComplete(UUID player) {
        PlayerProgress p = playerData.get(player);
        if (p == null) return false;
        for (int i = 0; i <= 5; i++) {
            if (!p.areasComplete[i]) return false;
        }
        return true;
    }

    /** Count total completed bundles across all areas for a player. */
    public int numberOfCompleteBundles(UUID player) {
        PlayerProgress p = playerData.get(player);
        if (p == null) return 0;
        int count = 0;
        for (int bundleId : p.bundleSlots.keySet()) {
            if (isBundleComplete(player, bundleId)) count++;
        }
        return count;
    }

    // ── Bulk Operations (for debug commands) ──

    /** Reset all progress for a player. */
    public void resetAll(UUID player) {
        PlayerProgress p = getProgress(player);
        p.bundleSlots.clear();
        p.bundleRewards.clear();
        Arrays.fill(p.areasComplete, false);
        setDirty();
    }

    /** Complete everything for a player (debug). */
    public void completeAll(UUID player) {
        PlayerProgress p = getProgress(player);
        for (BundleDefinition def
                : StardewCommunityCenterVariantRegistry.all(player)) {
            boolean[] slots = getSlots(player, def.bundleId());
            Arrays.fill(slots, true);
            p.bundleRewards.put(def.bundleId(), false); // reward already claimed
        }
        for (int i = 0; i <= 5; i++) {
            p.areasComplete[i] = true;
        }
        setDirty();
    }

    /** Get an unmodifiable view of all bundle slot data for a player (for syncing). */
    public Map<Integer, boolean[]> getBundleSlotsView(UUID player) {
        PlayerProgress p = playerData.get(player);
        if (p == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(p.bundleSlots);
    }

    /** Get all tracked player UUIDs. */
    public Set<UUID> getAllPlayers() {
        return Collections.unmodifiableSet(playerData.keySet());
    }

    public CompoundTag getAddonData(UUID player) {
        PlayerProgress progress = playerData.get(player);
        return progress == null
                ? new CompoundTag()
                : progress.addonData.copy();
    }

    public CompoundTag getAddonData(UUID player, String id) {
        PlayerProgress progress = playerData.get(player);
        if (progress == null || !progress.addonData.contains(id, Tag.TAG_COMPOUND)) {
            return null;
        }
        return progress.addonData.getCompound(id).copy();
    }

    public void putAddonData(UUID player, String id, CompoundTag value) {
        getProgress(player).addonData.put(id, value.copy());
        setDirty();
    }

    public boolean removeAddonData(UUID player, String id) {
        PlayerProgress progress = playerData.get(player);
        if (progress == null || !progress.addonData.contains(id)) {
            return false;
        }
        progress.addonData.remove(id);
        setDirty();
        return true;
    }

    private static net.minecraft.server.level.ServerPlayer onlinePlayer(
            UUID playerId
    ) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null
                ? null : server.getPlayerList().getPlayer(playerId);
    }

    private static void dispatchBundleChange(
            net.minecraft.server.level.ServerPlayer player,
            int bundleId,
            StardewProgressSnapshot before,
            net.minecraft.resources.ResourceLocation cause
    ) {
        if (player == null || before == null) {
            return;
        }
        StardewProgressSnapshot after = StardewProgressRegistry
                .communityCenterBundleSnapshot(player, bundleId);
        if (after != null) {
            StardewProgressRegistry.dispatchChanges(
                    player, before, after, cause);
        }
    }
}
