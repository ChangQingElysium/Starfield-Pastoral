package com.stardew.craft.animal.runtime;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Server-thread ledger. Legacy imports and their receipts are saved with the resulting state. */
public final class LivestockWorldData extends SavedData {
    private final Map<UUID, LivestockRecord> animals = new LinkedHashMap<>();
    // Receipts outlive sales, collection and farm deletion. They are never regenerated from live rows.
    private final Map<String, UUID> legacyImports = new LinkedHashMap<>();
    public UUID legacyImport(String source) { return legacyImports.get(source); }
    public boolean importLegacyAnimal(String source, LivestockRecord animal, boolean newborn) {
        if (legacyImports.containsKey(source)) return false;
        if (animals.containsKey(animal.id())) throw new IllegalStateException("Legacy animal UUID collision");
        put(animal); newborn(animal.id(), newborn); legacyImports.put(source, animal.id()); setDirty(); return true;
    }
    public boolean importLegacyProduct(String source, Product product) {
        if (legacyImports.containsKey(source)) return false;
        if (eggs.containsKey(product.id())) throw new IllegalStateException("Legacy product UUID collision");
        product(product); legacyImports.put(source, product.id()); setDirty(); return true;
    }
    public void importLegacyHay(String source, UUID farm, int amount) {
        if (legacyImports.containsKey(source)) return;
        hay(farm, Math.addExact(hay(farm), Math.max(0, amount)));
        legacyImports.put(source, farm); setDirty();
    }
    public void importLegacyHome(String source, UUID home, boolean doorOpen, int day) {
        if (legacyImports.containsKey(source)) return;
        outdoorsAllowed(home, doorOpen); feedDays.put(home, Math.max(feedDay(home), day));
        legacyImports.put(source, home); setDirty();
    }
    private final Map<UUID, Product> eggs = new LinkedHashMap<>();
    private final Map<UUID,Integer> birthDays = new HashMap<>();
    private final Set<UUID> newborns = new HashSet<>();
    public int birthDay(UUID farm) { return birthDays.getOrDefault(farm,-1); }
    public void birthDay(UUID farm,int day) { birthDays.put(farm,day);setDirty(); }
    public boolean newborn(UUID id) { return newborns.contains(id); }
    public void newborn(UUID id,boolean value) { if(value)newborns.add(id);else newborns.remove(id);setDirty(); }
    private Batch pending;
    private long nextRandomId = 2;
    private final Map<UUID, Integer> hay = new HashMap<>();
    private final Map<UUID, Integer> feedDays = new HashMap<>();
    private final Set<UUID> closedHomes = new HashSet<>();
    public boolean outdoorsAllowed(UUID home) { return !closedHomes.contains(home); }
    public void outdoorsAllowed(UUID home, boolean allowed) { if (allowed) closedHomes.remove(home); else closedHomes.add(home); setDirty(); }
    public int hay(UUID farm) { return hay.getOrDefault(farm, 0); }
    public void hay(UUID farm, int amount) { if (amount < 0) throw new IllegalArgumentException("Negative hay"); hay.put(farm, amount); setDirty(); }
    public int feedDay(UUID home) { return feedDays.getOrDefault(home, -1); }

    public record Product(UUID id, UUID animal, UUID home, boolean large, int quality, String item, int count, BlockPos position, CompoundTag stackData) {
        public Product {stackData=stackData.copy();}
        @Override public CompoundTag stackData(){return stackData.copy();}
        public Product(UUID id,UUID animal,UUID home,boolean large,int quality,String item,int count,BlockPos position){this(id,animal,home,large,quality,item,count,position,new CompoundTag());}
        public static Product fromStack(net.minecraft.server.level.ServerLevel level,LivestockRecord animal,net.minecraft.world.item.ItemStack stack){
            return new Product(UUID.randomUUID(),animal.id(),animal.home(),false,com.stardew.craft.item.quality.QualityHelper.getQuality(stack),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),null,(CompoundTag)PortItemStacks.save(stack, level.registryAccess()));
        }
        public Product(UUID id, UUID animal, UUID home, boolean large, int quality) { this(id, animal, home, large, quality, large ? "large_egg_white" : "egg_white", 1, null); }
        public CompoundTag save() {
            var tag = new CompoundTag(); tag.putUUID("Id", id); tag.putUUID("Animal", animal); tag.putUUID("Home", home);
            tag.putBoolean("Large", large); tag.putInt("Quality", quality); tag.put("Stack",stackData.copy()); tag.putString("Item", item); tag.putInt("Count", count); if (position != null) tag.putLong("Position", position.asLong()); return tag;
        }
        public static Product load(CompoundTag tag) { return new Product(tag.getUUID("Id"), tag.getUUID("Animal"), tag.getUUID("Home"), tag.getBoolean("Large"), tag.getInt("Quality"), tag.contains("Item") ? tag.getString("Item") : tag.getBoolean("Large") ? "large_egg_white" : "egg_white", tag.contains("Count") ? tag.getInt("Count") : 1, tag.contains("Position") ? BlockPos.of(tag.getLong("Position")) : null, tag.getCompound("Stack")); }
    }
    /** Write-ahead day result + exact local troughs to empty, replayed before projection/interaction. */
    public record Batch(UUID home, List<LivestockRecord> animals, List<Product> eggs, List<BlockPos> consumed, List<BlockPos> filled, UUID farm, int hayAfter, int day, Map<BlockPos, CompoundTag> collectors, CompoundTag statTargets) {
        public Batch(UUID home,List<LivestockRecord> animals,List<Product> eggs,List<BlockPos> consumed,List<BlockPos> filled,UUID farm,int hayAfter,int day,Map<BlockPos,CompoundTag> collectors){this(home,animals,eggs,consumed,filled,farm,hayAfter,day,collectors,new CompoundTag());}
        @Override public CompoundTag statTargets(){return statTargets.copy();}
        public Batch(UUID home, List<LivestockRecord> animals, List<Product> eggs, List<BlockPos> consumed, List<BlockPos> filled, UUID farm, int hayAfter, int day) { this(home, animals, eggs, consumed, filled, farm, hayAfter, day, Map.of()); }
        public Batch(UUID home, List<LivestockRecord> animals, List<Product> eggs, List<BlockPos> consumed) { this(home, animals, eggs, consumed, List.of(), null, 0, -1, Map.of()); }
        public Batch { statTargets=statTargets.copy();animals = List.copyOf(animals); eggs = List.copyOf(eggs); consumed = List.copyOf(consumed); filled = List.copyOf(filled); collectors = Map.copyOf(collectors); }
        CompoundTag save() {
            var tag = new CompoundTag(); tag.putUUID("Home", home);tag.put("StatTargets",statTargets.copy());
            var rows = new ListTag(); animals.forEach(a -> rows.add(a.save())); tag.put("Animals", rows);
            var products = new ListTag(); eggs.forEach(e -> products.add(e.save())); tag.put("Eggs", products);
            tag.putLongArray("Consumed", consumed.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("Filled", filled.stream().mapToLong(BlockPos::asLong).toArray());
            if (farm != null) tag.putUUID("Farm", farm); tag.putInt("HayAfter", hayAfter); tag.putInt("Day", day);
            var boxes = new ListTag(); collectors.forEach((pos, state) -> { var box = new CompoundTag(); box.putLong("Pos", pos.asLong()); box.put("State", state.copy()); boxes.add(box); }); tag.put("Collectors", boxes); return tag;
        }
        static Batch load(CompoundTag tag) {
            var boxes = new LinkedHashMap<BlockPos, CompoundTag>(); for (var entry : tag.getList("Collectors", Tag.TAG_COMPOUND)) { var box = (CompoundTag)entry; boxes.put(BlockPos.of(box.getLong("Pos")), box.getCompound("State")); }
            return new Batch(tag.getUUID("Home"), tag.getList("Animals", Tag.TAG_COMPOUND).stream().map(t -> LivestockRecord.load((CompoundTag)t)).toList(),
                    tag.getList("Eggs", Tag.TAG_COMPOUND).stream().map(t -> Product.load((CompoundTag)t)).toList(),
                    Arrays.stream(tag.getLongArray("Consumed")).mapToObj(BlockPos::of).toList(),
                    Arrays.stream(tag.getLongArray("Filled")).mapToObj(BlockPos::of).toList(), tag.hasUUID("Farm") ? tag.getUUID("Farm") : null, tag.getInt("HayAfter"), tag.getInt("Day"), boxes,tag.getCompound("StatTargets"));
        }
    }
    public static LivestockWorldData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Livestock access requires server thread");
        return server.overworld().getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(new Factory<>(LivestockWorldData::new, LivestockWorldData::load)), com.stardew.craft.port.PortSavedData.constructor(new Factory<>(LivestockWorldData::new, LivestockWorldData::load)), "stardew_livestock");
    }
    public void removeFarm(UUID farm, Set<UUID> homes) {
        var removed = animals.values().stream().filter(a -> a.farm().equals(farm)).map(LivestockRecord::id).collect(java.util.stream.Collectors.toSet());
        animals.keySet().removeAll(removed); newborns.removeAll(removed);
        eggs.values().removeIf(e -> homes.contains(e.home()) || removed.contains(e.animal()));
        hay.remove(farm); birthDays.remove(farm); feedDays.keySet().removeAll(homes); closedHomes.removeAll(homes); setDirty();
    }
    public List<LivestockRecord> all() { return List.copyOf(animals.values()); }
    public LivestockRecord find(UUID id) { return animals.get(id); }
    public int occupancy(UUID home) { return (int) animals.values().stream().filter(a -> a.home().equals(home)).count(); }
    public void put(LivestockRecord animal) { animals.put(animal.id(), animal); setDirty(); }
    public long allocateRandomId() { long id = nextRandomId; nextRandomId += 2; setDirty(); return id; }
    public void remove(UUID id) { if (animals.remove(id) != null) { newborns.remove(id); setDirty(); } }
    public void product(Product egg) { eggs.put(egg.id(), egg); setDirty(); }
    public List<Product> eggs() { return List.copyOf(eggs.values()); }
    public Product egg(UUID id) { return eggs.get(id); }
    public boolean collect(UUID id) { boolean removed = eggs.remove(id) != null; if (removed) setDirty(); return removed; }
    public Batch pending() { return pending; }
    public void prepare(Batch batch) { if (pending != null) throw new IllegalStateException("Day journal already pending"); pending = batch; setDirty(); }
    public void finish() {
        if (pending == null) return;
        pending.animals.forEach(this::put); pending.eggs.forEach(e -> eggs.put(e.id, e));
        if (pending.farm != null) { hay(pending.farm, pending.hayAfter); feedDays.put(pending.home, pending.day); }
        pending = null; setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("Format", 1); tag.putLong("NextRandomId", nextRandomId);
        var imported = new CompoundTag(); legacyImports.forEach(imported::putUUID); tag.put("LegacyImports", imported);
        var rows = new ListTag(); animals.values().forEach(a -> rows.add(a.save())); tag.put("Animals", rows);
        var products = new ListTag(); eggs.values().forEach(e -> products.add(e.save())); tag.put("Eggs", products);
        var births = new CompoundTag(); birthDays.forEach((id,day)->births.putInt(id.toString(),day)); tag.put("BirthDays",births);
        var babies = new ListTag(); newborns.forEach(id->babies.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));tag.put("Newborns",babies);
        var feed = new CompoundTag(); hay.forEach((id, amount) -> feed.putInt(id.toString(), amount)); tag.put("FarmHay", feed);
        var days = new CompoundTag(); feedDays.forEach((id, day) -> days.putInt(id.toString(), day)); tag.put("FeedDays", days);
        var doors = new ListTag(); closedHomes.forEach(id -> doors.add(net.minecraft.nbt.StringTag.valueOf(id.toString()))); tag.put("ClosedHomes", doors);
        if (pending != null) tag.put("Pending", pending.save()); return tag;
    }
    public static LivestockWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("Format") != 1) throw new IllegalArgumentException("Unsupported livestock format");
        var data = new LivestockWorldData(); data.nextRandomId = tag.getLong("NextRandomId");
        var imported = tag.getCompound("LegacyImports");
        for (var key : imported.getAllKeys()) data.legacyImports.put(key, imported.getUUID(key));
        for (var row : tag.getList("Animals", Tag.TAG_COMPOUND)) { var a = LivestockRecord.load((CompoundTag)row); data.animals.put(a.id(), a); }
        for (var row : tag.getList("Eggs", Tag.TAG_COMPOUND)) { var e = Product.load((CompoundTag)row); data.eggs.put(e.id(), e); }
        var births = tag.getCompound("BirthDays");for(var key:births.getAllKeys())data.birthDays.put(UUID.fromString(key),births.getInt(key));
        for(var value:tag.getList("Newborns",Tag.TAG_STRING))data.newborns.add(UUID.fromString(value.getAsString()));
        var feed = tag.getCompound("FarmHay"); for (var key : feed.getAllKeys()) data.hay.put(UUID.fromString(key), feed.getInt(key));
        var days = tag.getCompound("FeedDays"); for (var key : days.getAllKeys()) data.feedDays.put(UUID.fromString(key), days.getInt(key));
        for (var value : tag.getList("ClosedHomes", Tag.TAG_STRING)) data.closedHomes.add(UUID.fromString(value.getAsString()));
        if (tag.contains("Pending")) data.pending = Batch.load(tag.getCompound("Pending")); return data;
    }
}
