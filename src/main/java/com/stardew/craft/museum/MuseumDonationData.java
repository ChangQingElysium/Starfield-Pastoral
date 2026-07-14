package com.stardew.craft.museum;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

import java.util.*;

/**
 * Museum donation persistence — per-player data.
 * Each player has their own donated items, stand display items, session pending items,
 * donation mode flag, and claimed rewards.
 */
public class MuseumDonationData extends SavedData {
    private static final String DATA_NAME = "stardew_museum_donations";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_DONATED = "donated";
    private static final String TAG_DONATION_MODE = "donation_mode";
    private static final String TAG_SESSION_PENDING = "session_pending";
    private static final String TAG_STAND_ITEMS = "stand_items";
    private static final String TAG_CLAIMED_REWARDS = "claimed_rewards";
    private static final String TAG_SCHEMA_VERSION = "schema_version";
    private static final int CURRENT_SCHEMA_VERSION = 2;

    /** Per-player data keyed by UUID string */
    private final Map<String, PlayerMuseumData> playerData = new HashMap<>();

    public static MuseumDonationData get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(MuseumDonationData::new, MuseumDonationData::load),
                        DATA_NAME
                );
    }

    private PlayerMuseumData getOrCreate(UUID playerId) {
        String key = playerId.toString();
        PlayerMuseumData existing = playerData.get(key);
        if (existing != null) return existing;

        // Copy legacy shared data to every new player (old save migration)
        PlayerMuseumData legacy = playerData.get("__legacy__");
        PlayerMuseumData fresh;
        if (legacy != null) {
            fresh = legacy.copy();
            // Reset session-only state for the new player
            fresh.donationModeActive = false;
            fresh.sessionPendingItems.clear();
        } else {
            fresh = new PlayerMuseumData();
        }
        playerData.put(key, fresh);
        setDirty();
        return fresh;
    }

    public static MuseumDonationData load(CompoundTag tag, HolderLookup.Provider provider) {
        MuseumDonationData data = new MuseumDonationData();

        // Try loading legacy global data (migration from old shared format)
        if (!tag.contains(TAG_PLAYERS) && tag.contains(TAG_DONATED)) {
            PlayerMuseumData legacy = new PlayerMuseumData();
            legacy.load(tag);
            data.playerData.put("__legacy__", legacy);
        }

        if (tag.contains(TAG_PLAYERS, CompoundTag.TAG_COMPOUND)) {
            CompoundTag playersTag = tag.getCompound(TAG_PLAYERS);
            for (String uuidStr : playersTag.getAllKeys()) {
                if (uuidStr == null) continue;
                CompoundTag playerTag = playersTag.getCompound(uuidStr);
                PlayerMuseumData pData = new PlayerMuseumData();
                pData.load(playerTag);
                data.playerData.put(uuidStr, pData);
            }
        }

        return data;
    }

    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<String, PlayerMuseumData> entry : playerData.entrySet()) {
            if (entry.getKey() == null) continue;
            CompoundTag playerTag = new CompoundTag();
            entry.getValue().save(playerTag);
            playersTag.put(entry.getKey(), playerTag);
        }
        tag.put(TAG_PLAYERS, playersTag);
        tag.putInt(TAG_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        return tag;
    }

    // ── Per-player donation methods ──

    /**
     * Resolve player data for reading. Falls back to legacy shared data if
     * the player has no personal entry yet (old save migration).
     */
    private PlayerMuseumData resolve(UUID playerId) {
        PlayerMuseumData pData = playerData.get(playerId.toString());
        if (pData != null) return pData;
        return playerData.get("__legacy__"); // may be null
    }

    public boolean donate(UUID playerId, String itemId) {
        PlayerMuseumData pData = getOrCreate(playerId);
        boolean added = pData.donatedItems.add(itemId);
        if (added) setDirty();
        return added;
    }

    public boolean isDonated(UUID playerId, String itemId) {
        PlayerMuseumData pData = resolve(playerId);
        return pData != null && pData.donatedItems.contains(itemId);
    }

    public Set<String> getDonatedItems(UUID playerId) {
        PlayerMuseumData pData = resolve(playerId);
        if (pData == null) return Collections.emptySet();
        return Collections.unmodifiableSet(pData.donatedItems);
    }

    public void debugResetPlayer(UUID playerId) {
        playerData.put(playerId.toString(), new PlayerMuseumData());
        setDirty();
    }

    public boolean isDonationModeActive(UUID playerId) {
        PlayerMuseumData pData = resolve(playerId);
        return pData != null && pData.donationModeActive;
    }

    public void startDonationMode(UUID playerId) {
        PlayerMuseumData pData = getOrCreate(playerId);
        if (!pData.donationModeActive) {
            pData.donationModeActive = true;
            setDirty();
        }
    }

    public EndSessionResult endDonationMode(UUID playerId) {
        PlayerMuseumData pData = playerData.get(playerId.toString());
        if (pData == null) return new EndSessionResult(true, Collections.emptySet());

        Set<String> missing = getMissingSessionItems(pData);
        if (!missing.isEmpty()) {
            return new EndSessionResult(false, missing);
        }

        for (String itemId : getManagedStandItems(pData).values()) {
            pData.donatedItems.add(itemId);
        }
        pData.donationModeActive = false;
        pData.sessionPendingItems.clear();
        setDirty();
        return new EndSessionResult(true, Collections.emptySet());
    }

    /**
     * 强制结束捐赠模式（不检查缺失物品）。
     * 用于室内布局版本升级时：展示柜可能被重置，继续检查 missing 会永远失败。
     */
    public void forceEndDonationMode(UUID playerId) {
        PlayerMuseumData pData = playerData.get(playerId.toString());
        if (pData == null) return;
        for (String itemId : getManagedStandItems(pData).values()) {
            pData.donatedItems.add(itemId);
        }
        pData.donationModeActive = false;
        pData.sessionPendingItems.clear();
        pData.standDisplayItems.clear();
        setDirty();
    }

    public boolean canDonateItem(UUID playerId, String itemId) {
        PlayerMuseumData pData = resolve(playerId);
        if (pData == null || !pData.donationModeActive) return false;
        return !getManagedStandItems(pData).containsValue(itemId);
    }

    public Map<String, String> getStandDisplayItems(UUID playerId) {
        PlayerMuseumData pData = resolve(playerId);
        if (pData == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(getManagedStandItems(pData));
    }

    public void setStandDisplayedItem(UUID playerId, String standKey, String itemId) {
        String normalizedStandKey = MuseumExhibitStandManager.normalizeManagedStandKey(standKey);
        if (normalizedStandKey == null) return;
        PlayerMuseumData pData = getOrCreate(playerId);
        if (itemId == null || itemId.isBlank()) {
            if (pData.standDisplayItems.remove(normalizedStandKey) != null) setDirty();
            return;
        }
        String old = pData.standDisplayItems.put(normalizedStandKey, itemId);
        if (!itemId.equals(old)) setDirty();
    }

    public boolean ensureManagedStandLayout(ServerLevel level, UUID playerId) {
        PlayerMuseumData resolved = resolve(playerId);
        if (resolved == null) return false;

        PlayerMuseumData pData = playerData.containsKey(playerId.toString())
            ? resolved
            : getOrCreate(playerId);

        TreeSet<String> requiredItems = new TreeSet<>(pData.donatedItems);
        if (pData.donationModeActive) {
            requiredItems.addAll(pData.sessionPendingItems);
        }
        if (requiredItems.isEmpty()) {
            return false;
        }

        Map<String, String> managedItems = getManagedStandItems(pData);
        requiredItems.removeAll(new HashSet<>(managedItems.values()));
        if (requiredItems.isEmpty()) {
            return false;
        }

        List<net.minecraft.core.BlockPos> standPositions = MuseumExhibitStandManager.collectManagedStandPositions(level);
        if (standPositions.isEmpty()) {
            return false;
        }

        Set<String> usedStandKeys = new HashSet<>(managedItems.keySet());
        Iterator<String> itemIterator = requiredItems.iterator();
        boolean changed = false;
        for (net.minecraft.core.BlockPos pos : standPositions) {
            if (!itemIterator.hasNext()) {
                break;
            }
            String standKey = standKey(level, pos);
            if (usedStandKeys.contains(standKey)) {
                continue;
            }
            String itemId = itemIterator.next();
            pData.standDisplayItems.put(standKey, itemId);
            usedStandKeys.add(standKey);
            changed = true;
        }

        if (changed) {
            setDirty();
        }
        return changed;
    }

    public void markSessionPendingItem(UUID playerId, String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        PlayerMuseumData pData = getOrCreate(playerId);
        if (pData.sessionPendingItems.add(itemId)) setDirty();
    }

    private Set<String> getMissingSessionItems(PlayerMuseumData pData) {
        Set<String> requiredItems = new HashSet<>(pData.donatedItems);
        requiredItems.addAll(pData.sessionPendingItems);
        Set<String> displayed = new HashSet<>(getManagedStandItems(pData).values());
        Set<String> missing = new HashSet<>();
        for (String req : requiredItems) {
            if (!displayed.contains(req)) missing.add(req);
        }
        return missing;
    }

    public static String standKey(ServerLevel level, net.minecraft.core.BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        ResourceLocation dimId = dimension.location();
        return dimId + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public record EndSessionResult(boolean success, Set<String> missingItems) {}

    private Map<String, String> getManagedStandItems(PlayerMuseumData pData) {
        Map<String, String> managed = new HashMap<>();
        for (Map.Entry<String, String> entry : pData.standDisplayItems.entrySet()) {
            String normalizedStandKey = MuseumExhibitStandManager.normalizeManagedStandKey(entry.getKey());
            String itemId = entry.getValue();
            if (normalizedStandKey != null && itemId != null && !itemId.isBlank()) {
                managed.put(normalizedStandKey, itemId);
            }
        }
        return managed;
    }

    // ── Museum Reward tracking (per-player) ──

    public Set<String> getClaimedMuseumRewards(UUID playerId) {
        PlayerMuseumData pData = resolve(playerId);
        if (pData == null) return Collections.emptySet();
        return Collections.unmodifiableSet(pData.claimedMuseumRewards);
    }

    public boolean isRewardClaimed(UUID playerId, String rewardId) {
        PlayerMuseumData pData = playerData.get(playerId.toString());
        return pData != null && pData.claimedMuseumRewards.contains(rewardId);
    }

    public void claimReward(UUID playerId, String rewardId) {
        PlayerMuseumData pData = getOrCreate(playerId);
        if (pData.claimedMuseumRewards.add(rewardId)) setDirty();
    }

    /**
     * Get all player UUID strings that have data.
     */
    public Set<String> getAllPlayerUUIDs() {
        return Collections.unmodifiableSet(playerData.keySet());
    }

    // ── Inner class for per-player data ──

    private static class PlayerMuseumData {
        final Set<String> donatedItems = new HashSet<>();
        final Set<String> sessionPendingItems = new HashSet<>();
        final Map<String, String> standDisplayItems = new HashMap<>();
        final Set<String> claimedMuseumRewards = new HashSet<>();
        boolean donationModeActive;

        void load(CompoundTag tag) {
            ListTag list = tag.getList(TAG_DONATED, CompoundTag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) donatedItems.add(list.getString(i));

            donationModeActive = tag.getBoolean(TAG_DONATION_MODE);

            ListTag pendingList = tag.getList(TAG_SESSION_PENDING, CompoundTag.TAG_STRING);
            for (int i = 0; i < pendingList.size(); i++) sessionPendingItems.add(pendingList.getString(i));

            CompoundTag standTag = tag.getCompound(TAG_STAND_ITEMS);
            for (String key : standTag.getAllKeys()) {
                if (key == null) continue;
                String itemId = Objects.requireNonNull(standTag.getString(key));
                String normalizedStandKey = MuseumExhibitStandManager.normalizeManagedStandKey(key);
                if (normalizedStandKey != null && !itemId.isBlank()) standDisplayItems.put(normalizedStandKey, itemId);
            }

            if (tag.contains(TAG_CLAIMED_REWARDS, CompoundTag.TAG_LIST)) {
                ListTag rewardList = tag.getList(TAG_CLAIMED_REWARDS, CompoundTag.TAG_STRING);
                for (int i = 0; i < rewardList.size(); i++) {
                    String id = rewardList.getString(i);
                    if (!id.isBlank()) claimedMuseumRewards.add(id);
                }
            }
        }

        PlayerMuseumData copy() {
            PlayerMuseumData c = new PlayerMuseumData();
            c.donatedItems.addAll(this.donatedItems);
            c.sessionPendingItems.addAll(this.sessionPendingItems);
            c.standDisplayItems.putAll(this.standDisplayItems);
            c.claimedMuseumRewards.addAll(this.claimedMuseumRewards);
            c.donationModeActive = this.donationModeActive;
            return c;
        }

        void save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String id : donatedItems) if (id != null) list.add(StringTag.valueOf(id));
            tag.put(TAG_DONATED, list);

            tag.putBoolean(TAG_DONATION_MODE, donationModeActive);

            ListTag pendingList = new ListTag();
            for (String id : sessionPendingItems) if (id != null) pendingList.add(StringTag.valueOf(id));
            tag.put(TAG_SESSION_PENDING, pendingList);

            CompoundTag standTag = new CompoundTag();
            for (Map.Entry<String, String> entry : standDisplayItems.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null)
                    standTag.putString(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
            }
            tag.put(TAG_STAND_ITEMS, standTag);

            ListTag rewardList = new ListTag();
            for (String id : claimedMuseumRewards) if (id != null) rewardList.add(StringTag.valueOf(id));
            tag.put(TAG_CLAIMED_REWARDS, rewardList);
        }
    }
}
