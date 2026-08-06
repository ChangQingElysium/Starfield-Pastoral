package com.stardew.craft.npc.runtime;

import com.stardew.craft.cutscene.data.EventRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player equivalent of Stardew Valley's {@code activeDialogueEvents},
 * {@code previousActiveDialogueEvents}, and the NPC-specific mail keys used to
 * ensure each NPC reacts to an event only once.
 */
public final class NpcDialogueEventData extends SavedData {
    private static final String DATA_NAME = "stardew_npc_dialogue_events";
    private static final int DEFAULT_DURATION = 4;

    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();

    public static NpcDialogueEventData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NpcDialogueEventData::new, NpcDialogueEventData::load),
                DATA_NAME
        );
    }

    /** Mirrors Farmer.autoGenerateActiveDialogueEvent. */
    public boolean activate(UUID playerId, String key) {
        return activate(playerId, key, DEFAULT_DURATION);
    }

    public boolean activate(UUID playerId, String key, int duration) {
        if (playerId == null || key == null || key.isBlank()) {
            return false;
        }
        boolean changed = players.computeIfAbsent(playerId, ignored -> new PlayerState())
                .activate(key, duration);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Adds both the semantic event ID and its original numeric/legacy ID. */
    public void activateEventSeen(UUID playerId, String eventId) {
        boolean changed = false;
        for (String equivalentId : EventRegistry.equivalentIds(eventId)) {
            changed |= players.computeIfAbsent(playerId, ignored -> new PlayerState())
                    .activate("eventSeen_" + equivalentId, DEFAULT_DURATION);
        }
        if (changed) {
            setDirty();
        }
    }

    public List<String> activeKeys(UUID playerId, String npcId) {
        PlayerState state = players.get(playerId);
        return state == null ? List.of() : state.activeKeys(npcId);
    }

    public boolean isActive(UUID playerId, String key) {
        PlayerState state = players.get(playerId);
        return state != null && state.active.containsKey(key);
    }

    public void markConsumed(UUID playerId, String npcId, String key) {
        if (playerId == null || npcId == null || key == null || key.isBlank()) {
            return;
        }
        if (players.computeIfAbsent(playerId, ignored -> new PlayerState())
                .consumed.add(consumedKey(npcId, key))) {
            setDirty();
        }
    }

    public Set<String> answeredDialogueIds(UUID playerId) {
        PlayerState state = players.get(playerId);
        return state == null ? Set.of() : Set.copyOf(state.answeredDialogueIds);
    }

    public void markDialogueAnswer(UUID playerId, String answerId) {
        if (playerId == null || answerId == null || answerId.isBlank()) {
            return;
        }
        if (players.computeIfAbsent(playerId, ignored -> new PlayerState())
                .answeredDialogueIds.add(answerId)) {
            setDirty();
        }
    }

    /** Mirrors the active/previous event section of Farmer.dayupdate. */
    public void onNewDay() {
        boolean changed = false;
        for (PlayerState state : players.values()) {
            changed |= state.onNewDay();
        }
        if (changed) {
            setDirty();
        }
    }

    static NpcDialogueEventData load(CompoundTag tag, HolderLookup.Provider provider) {
        NpcDialogueEventData data = new NpcDialogueEventData();
        ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            if (playerTag.hasUUID("UUID")) {
                data.players.put(playerTag.getUUID("UUID"), PlayerState.load(playerTag));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, PlayerState> entry : players.entrySet()) {
            CompoundTag playerTag = entry.getValue().save();
            playerTag.putUUID("UUID", entry.getKey());
            playerList.add(playerTag);
        }
        tag.put("Players", playerList);
        return tag;
    }

    private static String consumedKey(String npcId, String key) {
        return npcId.trim().toLowerCase(Locale.ROOT) + "\u0000" + key;
    }

    static final class PlayerState {
        private static final Map<Integer, String> MEMORY_SUFFIXES = Map.of(
                1, "_memory_oneday",
                7, "_memory_oneweek",
                14, "_memory_twoweeks",
                28, "_memory_fourweeks",
                56, "_memory_eightweeks",
                104, "_memory_oneyear"
        );

        final LinkedHashMap<String, Integer> active = new LinkedHashMap<>();
        final LinkedHashMap<String, Integer> previous = new LinkedHashMap<>();
        final Set<String> consumed = new LinkedHashSet<>();
        final Set<String> answeredDialogueIds = new LinkedHashSet<>();

        boolean activate(String key, int duration) {
            if (active.containsKey(key) || previous.containsKey(key)) {
                return false;
            }
            active.put(key, Math.max(0, duration));
            return true;
        }

        List<String> activeKeys(String npcId) {
            List<String> keys = new ArrayList<>();
            for (String key : active.keySet()) {
                if (!consumed.contains(consumedKey(npcId, key))) {
                    keys.add(key);
                }
            }
            return List.copyOf(keys);
        }

        boolean onNewDay() {
            if (active.isEmpty() && previous.isEmpty()) {
                return false;
            }
            Iterator<Map.Entry<String, Integer>> activeIterator = active.entrySet().iterator();
            while (activeIterator.hasNext()) {
                Map.Entry<String, Integer> entry = activeIterator.next();
                if (!entry.getKey().contains("_memory_")) {
                    previous.putIfAbsent(entry.getKey(), 0);
                }
                int remaining = entry.getValue() - 1;
                if (remaining < 0) {
                    activeIterator.remove();
                } else {
                    entry.setValue(remaining);
                }
            }

            for (Map.Entry<String, Integer> entry : previous.entrySet()) {
                int elapsed = entry.getValue() + 1;
                entry.setValue(elapsed);
                String suffix = MEMORY_SUFFIXES.get(elapsed);
                if (suffix != null) {
                    active.put(entry.getKey() + suffix, DEFAULT_DURATION);
                }
            }
            return true;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("Active", saveMap(active));
            tag.put("Previous", saveMap(previous));
            ListTag consumedList = new ListTag();
            for (String key : consumed) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Key", key);
                consumedList.add(entry);
            }
            tag.put("Consumed", consumedList);
            ListTag answeredList = new ListTag();
            for (String answerId : answeredDialogueIds) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Id", answerId);
                answeredList.add(entry);
            }
            tag.put("AnsweredDialogueIds", answeredList);
            return tag;
        }

        static PlayerState load(CompoundTag tag) {
            PlayerState state = new PlayerState();
            loadMap(tag.getList("Active", Tag.TAG_COMPOUND), state.active);
            loadMap(tag.getList("Previous", Tag.TAG_COMPOUND), state.previous);
            ListTag consumedList = tag.getList("Consumed", Tag.TAG_COMPOUND);
            for (int i = 0; i < consumedList.size(); i++) {
                String key = consumedList.getCompound(i).getString("Key");
                if (!key.isBlank()) {
                    state.consumed.add(key);
                }
            }
            ListTag answeredList = tag.getList("AnsweredDialogueIds", Tag.TAG_COMPOUND);
            for (int i = 0; i < answeredList.size(); i++) {
                String answerId = answeredList.getCompound(i).getString("Id");
                if (!answerId.isBlank()) {
                    state.answeredDialogueIds.add(answerId);
                }
            }
            return state;
        }

        private static ListTag saveMap(LinkedHashMap<String, Integer> map) {
            ListTag list = new ListTag();
            for (Map.Entry<String, Integer> mapEntry : map.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Key", mapEntry.getKey());
                entry.putInt("Days", mapEntry.getValue());
                list.add(entry);
            }
            return list;
        }

        private static void loadMap(ListTag list, LinkedHashMap<String, Integer> target) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String key = entry.getString("Key");
                if (!key.isBlank()) {
                    target.put(key, entry.getInt("Days"));
                }
            }
        }
    }
}
