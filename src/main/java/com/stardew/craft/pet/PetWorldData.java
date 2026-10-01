package com.stardew.craft.pet;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One saved authority for identity, bowl ownership and daily care; no entity NBT copies. */
public final class PetWorldData extends SavedData {
    public record Bowl(UUID farm, BlockPos position, String style, int wateredDay, boolean outdoors) {
        public Bowl(UUID farm, BlockPos position, String style, int wateredDay) { this(farm, position, style, wateredDay, true); }
        public Bowl watered(int day) { return new Bowl(farm, position, style, day, outdoors); }
    }
    private final Map<UUID, PetRecord> pets = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> byFarm = new HashMap<>();
    private final Map<BlockPos, Bowl> bowls = new LinkedHashMap<>();
    private final Set<UUID> initialChoices = new HashSet<>(), lovedFarms = new HashSet<>(), preparedFarms = new HashSet<>();
    private final Set<UUID> squareBowlFarms = new HashSet<>();

    public static PetWorldData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Pet state requires server thread");
        return server.overworld().getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(new Factory<>(PetWorldData::new, PetWorldData::load)), com.stardew.craft.port.PortSavedData.constructor(new Factory<>(PetWorldData::new, PetWorldData::load)), "stardew_pets");
    }
    public static PetWorldData peek(MinecraftServer server) {
        return server.overworld() == null ? null : server.overworld().getDataStorage().get(com.stardew.craft.port.PortSavedData.loader(new Factory<>(PetWorldData::new, PetWorldData::load)), "stardew_pets");
    }
    public Collection<PetRecord> all() { return Collections.unmodifiableCollection(pets.values()); }
    public List<PetRecord> forFarm(UUID farm) { return byFarm.getOrDefault(farm, Set.of()).stream().map(pets::get).toList(); }
    public PetRecord find(UUID id) { return pets.get(id); }
    public void put(PetRecord pet) { pets.put(pet.id, pet); byFarm.computeIfAbsent(pet.farm, key -> new LinkedHashSet<>()).add(pet.id); initialChoices.add(pet.farm); setDirty(); }
    public void remove(UUID id) { var pet = pets.remove(id); if (pet != null) { byFarm.get(pet.farm).remove(id); setDirty(); } }
    public boolean chooseInitial(UUID farm) { boolean first = initialChoices.add(farm); if (first) setDirty(); return first; }
    public boolean needsInitialChoice(UUID farm) { return !initialChoices.contains(farm); }
    public boolean loved(UUID farm) { return lovedFarms.contains(farm); }
    public void markLoved(UUID farm) { if (lovedFarms.add(farm)) setDirty(); }
    public boolean prepared(UUID farm) { return preparedFarms.contains(farm); }
    public void markPrepared(UUID farm) { if (preparedFarms.add(farm)) setDirty(); }
    public boolean squareBowlFloor(UUID farm) { return squareBowlFarms.contains(farm); }
    public void markSquareBowlFloor(UUID farm) { if (squareBowlFarms.add(farm)) setDirty(); }
    public Collection<Bowl> bowls() { return List.copyOf(bowls.values()); }
    public Bowl bowl(BlockPos pos) { return bowls.get(pos); }
    public void bowl(Bowl bowl) { bowls.put(bowl.position().immutable(), bowl); setDirty(); }
    public void removeBowl(BlockPos pos) {
        if (bowls.remove(pos) == null) return;
        for (var pet : pets.values()) if (pos.equals(pet.bowl)) pet.bowl = null;
        setDirty();
    }
    public PetRecord occupant(BlockPos pos) { return pets.values().stream().filter(p -> pos.equals(p.bowl)).findFirst().orElse(null); }
    public void removeFarm(UUID farm) {
        for (var pet : forFarm(farm)) remove(pet.id);
        byFarm.remove(farm); bowls.values().removeIf(b -> b.farm().equals(farm));
        initialChoices.remove(farm); lovedFarms.remove(farm); preparedFarms.remove(farm); squareBowlFarms.remove(farm); setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("Format", 1);
        var rows = new ListTag(); pets.values().forEach(p -> rows.add(p.save())); tag.put("Pets", rows);
        var dishes = new ListTag(); bowls.values().forEach(b -> { var row = new CompoundTag(); row.putUUID("Farm", b.farm()); row.putLong("Pos", b.position().asLong()); row.putString("Style", b.style()); row.putInt("Watered", b.wateredDay()); row.putBoolean("Outdoors", b.outdoors()); dishes.add(row); }); tag.put("Bowls", dishes);
        tag.put("InitialChoices", saveIds(initialChoices)); tag.put("LovedFarms", saveIds(lovedFarms)); tag.put("PreparedFarms", saveIds(preparedFarms));
        tag.put("SquareBowlFarms", saveIds(squareBowlFarms));
        return tag;
    }
    private static ListTag saveIds(Set<UUID> ids) { var rows = new ListTag(); ids.forEach(id -> { var row = new CompoundTag(); row.putUUID("Id", id); rows.add(row); }); return rows; }
    private static void loadIds(CompoundTag tag, String key, Set<UUID> ids) { for (var entry : tag.getList(key, Tag.TAG_COMPOUND)) ids.add(((CompoundTag) entry).getUUID("Id")); }
    public static PetWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("Format") != 1) throw new IllegalArgumentException("Unsupported pet save format");
        var data = new PetWorldData();
        for (var row : tag.getList("Pets", Tag.TAG_COMPOUND)) data.put(PetRecord.load((CompoundTag) row));
        for (var entry : tag.getList("Bowls", Tag.TAG_COMPOUND)) { var row = (CompoundTag) entry; var b = new Bowl(row.getUUID("Farm"), BlockPos.of(row.getLong("Pos")), row.getString("Style"), row.getInt("Watered"), !row.contains("Outdoors") || row.getBoolean("Outdoors")); data.bowls.put(b.position(), b); }
        loadIds(tag, "InitialChoices", data.initialChoices); loadIds(tag, "LovedFarms", data.lovedFarms); loadIds(tag, "PreparedFarms", data.preparedFarms);
        loadIds(tag, "SquareBowlFarms", data.squareBowlFarms);
        data.setDirty(false); return data;
    }
}
