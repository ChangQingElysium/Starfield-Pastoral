package com.stardew.craft.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nonnull;

/** The source team owns a lifetime drop quota; discovery and currency increase only on pickup. */
public final class GoldenWalnutData extends SavedData {
    private int musselDrops;
    private int volcanoDrops;
    private int found;
    private int balance;
    private final java.util.Set<String> collectedNuts = new java.util.LinkedHashSet<>();
    private final java.util.Map<String, Integer> limitedDrops = new java.util.LinkedHashMap<>();

    public static GoldenWalnutData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(GoldenWalnutData::new, GoldenWalnutData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(GoldenWalnutData::new, GoldenWalnutData::load)), "stardewcraft_golden_walnuts");
    }

    /** The stable farm identity survives ownership changes and prevents recycled slots inheriting progress. */
    public static GoldenWalnutData get(MinecraftServer server, java.util.UUID farmId) {
        return server.overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(GoldenWalnutData::new, GoldenWalnutData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(GoldenWalnutData::new, GoldenWalnutData::load)),
                "stardewcraft_golden_walnuts_" + farmId);
    }

    public static GoldenWalnutData at(ServerLevel level, BlockPos pos) {
        return com.stardew.craft.gingerisland.IslandContext.farmInstance(level, pos)
                .map(id -> get(level.getServer(), id)).orElseGet(() -> get(level.getServer()));
    }

    /** Source MarkCollectedNut marks debris issuance; discovery remains a separate pickup operation. */
    public boolean markCollectedNut(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Empty walnut collectible key");
        if (!collectedNuts.add(key)) return false;
        setDirty();
        return true;
    }

    public boolean isCollected(String key) { return collectedNuts.contains(key); }
    public int limitedDrops(String key) { return limitedDrops.getOrDefault(key, 0); }
    public boolean reserveLimitedDrop(String key, int maximum) {
        if (key == null || key.isBlank() || maximum < 1) throw new IllegalArgumentException("Invalid walnut quota");
        int count = limitedDrops(key);
        if (count >= maximum) return false;
        limitedDrops.put(key, count + 1);
        setDirty();
        return true;
    }

    public boolean spend(int count) {
        if (count <= 0 || balance < count) return false;
        balance -= count;
        setDirty();
        return true;
    }

    public boolean reserveMusselDrop() {
        if (musselDrops >= 5) return false;
        musselDrops++;
        setDirty();
        return true;
    }

    public boolean reserveVolcanoDrop() {
        if (volcanoDrops >= 5) return false;
        volcanoDrops++;
        setDirty();
        return true;
    }

    public int volcanoDrops() { return volcanoDrops; }
    public int musselDrops() { return musselDrops; }
    public int found() { return found; }
    public int balance() { return balance; }

    public void discover(int count) {
        // Farmer.foundWalnut checks the current total before adding the collected stack.
        if (count <= 0 || found >= 130) return;
        found += count;
        balance += count;
        setDirty();
    }

    @Override @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("MusselStoneDrops", musselDrops);
        tag.putInt("VolcanoMiningDrops", volcanoDrops);
        tag.putInt("Found", found);
        tag.putInt("Balance", balance);
        var collected = new net.minecraft.nbt.ListTag();
        for (String key : collectedNuts) collected.add(net.minecraft.nbt.StringTag.valueOf(key));
        tag.put("CollectedNuts", collected);
        var limits = new CompoundTag();
        limitedDrops.forEach(limits::putInt);
        tag.put("LimitedDrops", limits);
        return tag;
    }

    public static GoldenWalnutData load(CompoundTag tag, HolderLookup.Provider provider) {
        GoldenWalnutData data = new GoldenWalnutData();
        data.musselDrops = Math.max(0, Math.min(5, tag.getInt("MusselStoneDrops")));
        data.volcanoDrops = Math.max(0, Math.min(5, tag.getInt("VolcanoMiningDrops")));
        data.found = Math.max(0, tag.getInt("Found"));
        data.balance = Math.max(0, tag.getInt("Balance"));
        for (var value : tag.getList("CollectedNuts", net.minecraft.nbt.Tag.TAG_STRING)) {
            if (!value.getAsString().isBlank()) data.collectedNuts.add(value.getAsString());
        }
        CompoundTag limits = tag.getCompound("LimitedDrops");
        for (String key : limits.getAllKeys()) if (!key.isBlank()) data.limitedDrops.put(key, Math.max(0, limits.getInt(key)));
        return data;
    }
}
