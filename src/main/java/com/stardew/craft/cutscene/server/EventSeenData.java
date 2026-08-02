package com.stardew.craft.cutscene.server;

import com.stardew.craft.cutscene.data.EventRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * Server-side SavedData tracking which events each player has seen.
 * Stored per-player as a Set&lt;String&gt; of event IDs.
 */
public class EventSeenData extends SavedData {

    private static final String DATA_NAME = "stardew_events_seen";

    /** UUID → set of event IDs the player has watched. */
    private final Map<UUID, Set<String>> playerEvents = new HashMap<>();

    // ─── public API ───

    public Set<String> getSeenEvents(UUID playerId) {
        Set<String> seen = playerEvents.get(playerId);
        if (seen == null || seen.isEmpty()) {
            return Set.of();
        }
        Set<String> canonical = new HashSet<>();
        for (String eventId : seen) {
            canonical.add(EventRegistry.canonicalId(eventId));
        }
        return Set.copyOf(canonical);
    }

    public boolean hasSeen(UUID playerId, String eventId) {
        Set<String> seen = playerEvents.get(playerId);
        return seen != null && EventRegistry.equivalentIds(eventId).stream().anyMatch(seen::contains);
    }

    public boolean hasAnyPlayerSeen(String eventId) {
        Set<String> equivalentIds = EventRegistry.equivalentIds(eventId);
        return playerEvents.values().stream()
                .anyMatch(events -> equivalentIds.stream().anyMatch(events::contains));
    }

    public void markSeen(UUID playerId, String eventId) {
        String canonicalId = EventRegistry.canonicalId(eventId);
        Set<String> events = playerEvents.computeIfAbsent(playerId, k -> new HashSet<>());
        events.removeAll(EventRegistry.equivalentIds(canonicalId));
        events.add(canonicalId);
        setDirty();
    }

    public void clearSeen(UUID playerId) {
        playerEvents.remove(playerId);
        setDirty();
    }

    public boolean clearSeen(UUID playerId, String eventId) {
        Set<String> seen = playerEvents.get(playerId);
        if (seen == null) return false;
        boolean removed = seen.removeAll(EventRegistry.equivalentIds(eventId));
        if (seen.isEmpty()) {
            playerEvents.remove(playerId);
        }
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ─── persistence ───

    @Override
    public CompoundTag save(@javax.annotation.Nonnull CompoundTag tag, @javax.annotation.Nonnull HolderLookup.Provider provider) {
        tag.putInt("PlayerCount", playerEvents.size());
        int index = 0;
        for (var entry : playerEvents.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());
            ListTag eventList = new ListTag();
            Set<String> canonicalIds = new HashSet<>();
            for (String eventId : entry.getValue()) {
                canonicalIds.add(EventRegistry.canonicalId(eventId));
            }
            for (String eventId : canonicalIds) {
                eventList.add(StringTag.valueOf(eventId));
            }
            playerTag.put("Events", eventList);
            tag.put("Player_" + index, playerTag);
            index++;
        }
        return tag;
    }

    public static EventSeenData load(CompoundTag tag, HolderLookup.Provider provider) {
        EventSeenData data = new EventSeenData();
        int count = tag.getInt("PlayerCount");
        for (int i = 0; i < count; i++) {
            CompoundTag playerTag = tag.getCompound("Player_" + i);
            UUID uuid = playerTag.getUUID("UUID");
            ListTag eventList = playerTag.getList("Events", Tag.TAG_STRING);
            Set<String> events = new HashSet<>();
            for (int j = 0; j < eventList.size(); j++) {
                events.add(eventList.getString(j));
            }
            data.playerEvents.put(uuid, events);
        }
        return data;
    }

    // ─── singleton access ───

    public static EventSeenData get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("No server available");
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld not available");
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EventSeenData::new, EventSeenData::load),
                DATA_NAME
        );
    }

    public static EventSeenData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EventSeenData::new, EventSeenData::load),
                DATA_NAME
        );
    }
}
