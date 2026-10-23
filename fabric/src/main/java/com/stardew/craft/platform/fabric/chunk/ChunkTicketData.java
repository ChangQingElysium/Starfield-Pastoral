package com.stardew.craft.platform.fabric.chunk;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Own-controller state plus an opaque copy of every other chunks.dat extension. */
public final class ChunkTicketData {
    private final CompoundTag original;
    private final Map<TicketOwner, LongSet> tickets = new LinkedHashMap<>();
    private final Long2IntOpenHashMap tickingCounts = new Long2IntOpenHashMap();
    private Set<ResourceLocation> controlledIds;
    private boolean validated, restored, failed;

    public ChunkTicketData(CompoundTag original) { this.original = original.copy(); }

    void prepare(Set<ResourceLocation> ids) {
        if (controlledIds != null) return;
        requireList(original, "ModForced", Tag.TAG_COMPOUND);
        Map<TicketOwner, LongSet> parsed = new LinkedHashMap<>();
        for (Tag item : original.getList("ModForced", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) item; ResourceLocation id = controllerId(entry);
            if (id == null || !ids.contains(id)) continue;
            requireList(entry, "ModForced", Tag.TAG_COMPOUND);
            for (Tag value : entry.getList("ModForced", Tag.TAG_COMPOUND)) {
                var chunk = (CompoundTag) value; long position = chunk.getLong("Chunk");
                readOwners(parsed, id, position, chunk, "Blocks", false, false);
                readOwners(parsed, id, position, chunk, "TickingBlocks", false, true);
                readOwners(parsed, id, position, chunk, "Entities", true, false);
                readOwners(parsed, id, position, chunk, "TickingEntities", true, true);
            }
        }
        controlledIds = Set.copyOf(ids);
        parsed.forEach((owner, chunks) -> chunks.forEach((long chunk) -> add(owner, chunk)));
    }

    private static void readOwners(Map<TicketOwner, LongSet> parsed, ResourceLocation id, long chunk, CompoundTag entry, String key, boolean entity, boolean ticking) {
        requireList(entry, key, entity ? Tag.TAG_INT_ARRAY : Tag.TAG_COMPOUND);
        for (Tag value : entry.getList(key, entity ? Tag.TAG_INT_ARRAY : Tag.TAG_COMPOUND)) {
            Object owner;
            if (entity) owner = NbtUtils.loadUUID(value);
            else { var pos = (CompoundTag) value; owner = new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z")); }
            parsed.computeIfAbsent(new TicketOwner(id, owner, ticking), ignored -> new LongOpenHashSet()).add(chunk);
        }
    }

    private static void requireList(CompoundTag tag, String key, int elementType) {
        if (!tag.contains(key)) return;
        if (!(tag.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != elementType)
            throw new IllegalArgumentException("Invalid owned ticket list: " + key);
    }

    static ResourceLocation controllerId(CompoundTag entry) {
        // Parsing a foreign or malformed controller must never prevent its raw data from being preserved.
        String id = entry.contains("Controller", Tag.TAG_STRING) ? entry.getString("Controller") : entry.getString("Mod") + ":default";
        return ResourceLocation.tryParse(id);
    }

    boolean contains(TicketOwner owner, long chunk) { var values = tickets.get(owner); return values != null && values.contains(chunk); }
    boolean add(TicketOwner owner, long chunk) {
        if (!tickets.computeIfAbsent(owner, ignored -> new LongOpenHashSet()).add(chunk)) return false;
        if (owner.ticking()) tickingCounts.addTo(chunk, 1); return true;
    }
    boolean remove(TicketOwner owner, long chunk) {
        var values = tickets.get(owner); if (values == null || !values.remove(chunk)) return false;
        if (values.isEmpty()) tickets.remove(owner);
        if (owner.ticking()) { int next = tickingCounts.get(chunk) - 1; if (next == 0) tickingCounts.remove(chunk); else tickingCounts.put(chunk, next); }
        return true;
    }
    boolean removeAll(ResourceLocation id, Object owner) {
        boolean changed = false;
        for (var ticket : List.copyOf(tickets.keySet())) if (ticket.controller().equals(id) && ticket.owner().equals(owner)) {
            for (long chunk : tickets.get(ticket).toLongArray()) changed |= remove(ticket, chunk);
        }
        return changed;
    }
    boolean hasTickets() { return !tickets.isEmpty(); }
    boolean shouldForceTicks(long chunk) { return tickingCounts.containsKey(chunk); }
    Map<TicketOwner, LongSet> snapshot() { var result = new LinkedHashMap<TicketOwner, LongSet>(); tickets.forEach((key, value) -> result.put(key, new LongOpenHashSet(value))); return result; }

    <T> Map<T, TicketSet> ownerSnapshot(ResourceLocation id, Class<T> kind) {
        Map<T, LongSet[]> result = new HashMap<>();
        tickets.forEach((ticket, chunks) -> {
            if (ticket.controller().equals(id) && kind.isInstance(ticket.owner()))
                result.computeIfAbsent(kind.cast(ticket.owner()), ignored -> new LongSet[]{new LongOpenHashSet(), new LongOpenHashSet()})[ticket.ticking() ? 1 : 0].addAll(chunks);
        });
        var frozen = new HashMap<T, TicketSet>(); result.forEach((key, values) -> frozen.put(key, new TicketSet(values[0], values[1]))); return Map.copyOf(frozen);
    }
    boolean validated() { return validated; }
    void validated(boolean value) { validated = value; }
    boolean restored() { return restored; }
    void restored(boolean value) { restored = value; }
    void failed() { failed = true; }
    void requireHealthy() { if (failed) throw new IllegalStateException("Ticket validation or restoration previously failed"); }

    public void saveInto(CompoundTag output) {
        for (String key : original.getAllKeys()) if (!key.equals("Forced")) output.put(key, original.get(key).copy());
        if (controlledIds == null || failed) return;
        var merged = new ListTag();
        for (Tag item : original.getList("ModForced", Tag.TAG_COMPOUND)) if (controllerId((CompoundTag) item) == null || !controlledIds.contains(controllerId((CompoundTag) item))) merged.add(item.copy());
        Map<ResourceLocation, Map<Long, CompoundTag>> byController = new LinkedHashMap<>();
        tickets.forEach((ticket, chunks) -> {
            var byChunk = byController.computeIfAbsent(ticket.controller(), ignored -> new LinkedHashMap<>());
            for (long chunk : chunks) {
                var entry = byChunk.computeIfAbsent(chunk, value -> { var tag = new CompoundTag(); tag.putLong("Chunk", value); return tag; });
                boolean entity = ticket.owner() instanceof UUID;
                String key = (ticket.ticking() ? "Ticking" : "") + (entity ? "Entities" : "Blocks");
                var owners = entry.getList(key, entity ? Tag.TAG_INT_ARRAY : Tag.TAG_COMPOUND);
                if (entity) owners.add(NbtUtils.createUUID((UUID) ticket.owner()));
                else { var pos = (BlockPos) ticket.owner(); var tag = new CompoundTag(); tag.putInt("X", pos.getX()); tag.putInt("Y", pos.getY()); tag.putInt("Z", pos.getZ()); owners.add(tag); }
                entry.put(key, owners);
            }
        });
        byController.forEach((id, chunks) -> { var entry = new CompoundTag(); entry.putString("Controller", id.toString()); var values = new ListTag(); chunks.values().forEach(values::add); entry.put("ModForced", values); merged.add(entry); });
        if (merged.isEmpty()) output.remove("ModForced"); else output.put("ModForced", merged);
    }
}
