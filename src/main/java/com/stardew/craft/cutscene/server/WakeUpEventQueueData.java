package com.stardew.craft.cutscene.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * Server-side SavedData holding the per-player FIFO queue of wake_up cutscene
 * event ids that still need to play.
 * <p>
 * Populated at night-settlement time by {@link WakeUpEventScheduler}, drained
 * one-at-a-time as the client finishes each event (see
 * {@code MarkEventSeenPayload::handle}). Persisted so that a mid-overnight
 * crash / logout cannot drop scheduled events.
 */
public class WakeUpEventQueueData extends SavedData {

    private static final String DATA_NAME = "stardew_wake_up_event_queue";

    /** UUID → ordered event IDs still queued. */
    private final Map<UUID, LinkedList<String>> queues = new HashMap<>();

    public List<String> getQueue(UUID playerId) {
        LinkedList<String> q = queues.get(playerId);
        return q == null ? List.of() : List.copyOf(q);
    }

    /** Peek first queued id, or null if empty. */
    public String peekFirst(UUID playerId) {
        LinkedList<String> q = queues.get(playerId);
        return (q == null || q.isEmpty()) ? null : q.peekFirst();
    }

    /** Enqueue if not already present (dedup). Returns true if added. */
    public boolean enqueueUnique(UUID playerId, String eventId) {
        LinkedList<String> q = queues.computeIfAbsent(playerId, k -> new LinkedList<>());
        if (q.contains(eventId)) return false;
        q.addLast(eventId);
        setDirty();
        return true;
    }

    /** Remove the head if it matches eventId; otherwise remove first matching anywhere. */
    public boolean remove(UUID playerId, String eventId) {
        LinkedList<String> q = queues.get(playerId);
        if (q == null) return false;
        boolean removed = q.remove(eventId);
        if (removed) {
            if (q.isEmpty()) queues.remove(playerId);
            setDirty();
        }
        return removed;
    }

    // ─── persistence ───

    @Override
    public CompoundTag save(@javax.annotation.Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("PlayerCount", queues.size());
        int i = 0;
        for (var entry : queues.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());
            ListTag list = new ListTag();
            for (String id : entry.getValue()) list.add(StringTag.valueOf(id));
            playerTag.put("Queue", list);
            tag.put("Player_" + i, playerTag);
            i++;
        }
        return tag;
    }

    public static WakeUpEventQueueData load(CompoundTag tag, HolderLookup.Provider provider) {
        WakeUpEventQueueData data = new WakeUpEventQueueData();
        int count = tag.getInt("PlayerCount");
        for (int i = 0; i < count; i++) {
            CompoundTag playerTag = tag.getCompound("Player_" + i);
            UUID uuid = playerTag.getUUID("UUID");
            ListTag list = playerTag.getList("Queue", Tag.TAG_STRING);
            LinkedList<String> q = new LinkedList<>();
            for (int j = 0; j < list.size(); j++) q.add(list.getString(j));
            if (!q.isEmpty()) data.queues.put(uuid, q);
        }
        return data;
    }

    public static WakeUpEventQueueData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(WakeUpEventQueueData::new, WakeUpEventQueueData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(WakeUpEventQueueData::new, WakeUpEventQueueData::load)),
                DATA_NAME
        );
    }

    public static WakeUpEventQueueData get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("No server available");
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld not available");
        return overworld.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(WakeUpEventQueueData::new, WakeUpEventQueueData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(WakeUpEventQueueData::new, WakeUpEventQueueData::load)),
                DATA_NAME
        );
    }
}
