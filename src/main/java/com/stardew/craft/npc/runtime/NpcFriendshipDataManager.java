package com.stardew.craft.npc.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Per-player NPC friendship and gift state persistence.
 */
public final class NpcFriendshipDataManager extends SavedData {
    private static final String DATA_NAME = "stardew_npc_friendship";

    private final Map<UUID, Map<String, FriendshipState>> playerState = new HashMap<>();

    public static NpcFriendshipDataManager get(net.minecraft.server.level.ServerLevel level) {
        net.minecraft.server.level.ServerLevel persistentLevel = level.getServer().overworld();
        if (persistentLevel == null) {
            persistentLevel = level;
        }
        return persistentLevel.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NpcFriendshipDataManager::new, NpcFriendshipDataManager::load),
            DATA_NAME
        );
    }

    public FriendshipState getOrCreate(UUID playerId, String npcId) {
        return playerState
            .computeIfAbsent(playerId, k -> new HashMap<>())
            .computeIfAbsent(npcId, k -> new FriendshipState());
    }

    public FriendshipState get(UUID playerId, String npcId) {
        if (playerId == null || npcId == null || npcId.isBlank()) {
            return null;
        }
        Map<String, FriendshipState> npcMap = playerState.get(playerId);
        return npcMap == null ? null : npcMap.get(npcId);
    }

    public int getMaxPointsForNpc(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return 0;
        }

        int max = 0;
        for (Map<String, FriendshipState> npcMap : playerState.values()) {
            FriendshipState state = npcMap.get(npcId);
            if (state != null && state.points() > max) {
                max = state.points();
            }
        }
        return max;
    }

    public void clearPlayer(UUID playerId) {
        playerState.remove(playerId);
        setDirty();
    }

    public int getPointsForNpc(UUID playerId, String npcId) {
        if (playerId == null || npcId == null || npcId.isBlank()) {
            return 0;
        }
        Map<String, FriendshipState> npcMap = playerState.get(playerId);
        if (npcMap == null) {
            return 0;
        }
        FriendshipState state = npcMap.get(npcId);
        return state == null ? 0 : Math.max(0, state.points());
    }

    public Map<String, Integer> getPointsForPlayer(UUID playerId) {
        if (playerId == null) {
            return Map.of();
        }
        Map<String, FriendshipState> npcMap = playerState.get(playerId);
        if (npcMap == null || npcMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, FriendshipState> entry : npcMap.entrySet()) {
            FriendshipState state = entry.getValue();
            if (state != null && state.points() > 0) {
                result.put(entry.getKey(), Math.max(0, state.points()));
            }
        }
        return Map.copyOf(result);
    }

    boolean settleNewDay(
            int previousDayKey,
            int newWeekKey,
            Predicate<String> knownNpc,
            Predicate<String> datableNpc,
            ToIntFunction<String> maxPoints
    ) {
        boolean changed = false;
        for (Map<String, FriendshipState> npcMap : playerState.values()) {
            for (Map.Entry<String, FriendshipState> entry : npcMap.entrySet()) {
                String npcId = entry.getKey();
                FriendshipState state = entry.getValue();
                boolean known = knownNpc.test(npcId);

                int beforePoints = state.points();
                if (known) {
                    int delta = NpcFriendshipDailyService.calculateDailyFriendshipDelta(
                            beforePoints,
                            state.lastTalkDayKey() == previousDayKey,
                            datableNpc.test(npcId)
                    );
                    if (delta != 0) {
                        state.addPoints(delta, maxPoints.applyAsInt(npcId));
                    }
                }

                if (state.lastGiftWeekKey() != newWeekKey) {
                    // Farmer.updateFriendshipGifts: giving both weekly gifts grants
                    // +10 friendship when the next Sunday begins.
                    if (known && state.giftsThisWeek() >= 2) {
                        state.addPoints(10, maxPoints.applyAsInt(npcId));
                    }
                    state.normalizeGiftWeek(newWeekKey);
                    changed = true;
                }
                changed |= state.points() != beforePoints;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public static NpcFriendshipDataManager load(CompoundTag tag, HolderLookup.Provider provider) {
        NpcFriendshipDataManager manager = new NpcFriendshipDataManager();
        int playerCount = tag.getInt("PlayerCount");
        for (int i = 0; i < playerCount; i++) {
            CompoundTag p = tag.getCompound("Player_" + i);
            UUID playerId = p.getUUID("UUID");
            int npcCount = p.getInt("NpcCount");
            Map<String, FriendshipState> npcMap = new HashMap<>();
            for (int n = 0; n < npcCount; n++) {
                CompoundTag nt = p.getCompound("Npc_" + n);
                String npcId = nt.getString("NpcId");
                if (npcId == null || npcId.isBlank()) {
                    continue;
                }
                npcMap.put(npcId, FriendshipState.fromNbt(nt));
            }
            manager.playerState.put(playerId, npcMap);
        }
        return manager;
    }

    @Override
    @SuppressWarnings("null")
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        tag.putInt("PlayerCount", playerState.size());
        int i = 0;
        for (Map.Entry<UUID, Map<String, FriendshipState>> pEntry : playerState.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("UUID", pEntry.getKey());
            p.putInt("NpcCount", pEntry.getValue().size());

            int n = 0;
            for (Map.Entry<String, FriendshipState> nEntry : pEntry.getValue().entrySet()) {
                CompoundTag nt = nEntry.getValue().toNbt();
                nt.putString("NpcId", nEntry.getKey());
                p.put("Npc_" + n, nt);
                n++;
            }

            tag.put("Player_" + i, p);
            i++;
        }
        return tag;
    }

    public static final class FriendshipState {
        private int points;
        private int giftsThisWeek;
        private int lastGiftDayKey = Integer.MIN_VALUE;
        private int lastGiftWeekKey = Integer.MIN_VALUE;
        private int lastTalkDayKey = Integer.MIN_VALUE;
        private int firstMetDayKey = Integer.MAX_VALUE;
        private int dialogueDayKey = Integer.MIN_VALUE;
        private int dialogueInteractionsToday;

        public int points() {
            return points;
        }

        public int giftsThisWeek() {
            return giftsThisWeek;
        }

        public int lastGiftDayKey() {
            return lastGiftDayKey;
        }

        public int lastTalkDayKey() {
            return lastTalkDayKey;
        }

        public int firstMetDayKey() {
            return firstMetDayKey;
        }

        public int lastGiftWeekKey() {
            return lastGiftWeekKey;
        }

        public int dialogueDayKey() {
            return dialogueDayKey;
        }

        public int dialogueInteractionsToday() {
            return dialogueInteractionsToday;
        }

        public void setLastTalkDayKey(int key) {
            this.lastTalkDayKey = key;
            if (key >= 0 && this.firstMetDayKey == Integer.MAX_VALUE) {
                this.firstMetDayKey = key;
            }
        }

        public void addPoints(int delta, int maxPoints) {
            this.points = Math.max(0, Math.min(this.points + delta, maxPoints));
        }

        public void normalizeGiftWeek(int weekKey) {
            if (this.lastGiftWeekKey != weekKey) {
                this.giftsThisWeek = 0;
                this.lastGiftWeekKey = weekKey;
            }
        }

        public void applyGiftCounters(int dayKey, int weekKey) {
            normalizeGiftWeek(weekKey);
            this.lastGiftDayKey = dayKey;
            this.giftsThisWeek++;
            if (dayKey >= 0 && this.firstMetDayKey == Integer.MAX_VALUE) {
                this.firstMetDayKey = dayKey;
            }
        }

        public int nextDialogueIndexForDay(int dayKey) {
            if (this.dialogueDayKey != dayKey) {
                this.dialogueDayKey = dayKey;
                this.dialogueInteractionsToday = 0;
            }
            int index = this.dialogueInteractionsToday;
            this.dialogueInteractionsToday++;
            return index;
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Points", points);
            tag.putInt("GiftsThisWeek", giftsThisWeek);
            tag.putInt("LastGiftDayKey", lastGiftDayKey);
            tag.putInt("LastGiftWeekKey", lastGiftWeekKey);
            tag.putInt("LastTalkDayKey", lastTalkDayKey);
            tag.putInt("FirstMetDayKey", firstMetDayKey);
            tag.putInt("DialogueDayKey", dialogueDayKey);
            tag.putInt("DialogueInteractionsToday", dialogueInteractionsToday);
            return tag;
        }

        public static FriendshipState fromNbt(CompoundTag tag) {
            FriendshipState state = new FriendshipState();
            state.points = tag.getInt("Points");
            state.giftsThisWeek = tag.getInt("GiftsThisWeek");
            state.lastGiftDayKey = tag.contains("LastGiftDayKey") ? tag.getInt("LastGiftDayKey") : Integer.MIN_VALUE;
            state.lastGiftWeekKey = tag.contains("LastGiftWeekKey") ? tag.getInt("LastGiftWeekKey") : Integer.MIN_VALUE;
            state.lastTalkDayKey = tag.contains("LastTalkDayKey") ? tag.getInt("LastTalkDayKey") : Integer.MIN_VALUE;
            state.firstMetDayKey = tag.contains("FirstMetDayKey") ? tag.getInt("FirstMetDayKey") : Integer.MAX_VALUE;
            state.dialogueDayKey = tag.contains("DialogueDayKey") ? tag.getInt("DialogueDayKey") : Integer.MIN_VALUE;
            state.dialogueInteractionsToday = tag.contains("DialogueInteractionsToday") ? Math.max(0, tag.getInt("DialogueInteractionsToday")) : 0;
            return state;
        }
    }
}
