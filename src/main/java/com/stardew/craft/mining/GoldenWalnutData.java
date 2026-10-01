package com.stardew.craft.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nonnull;

/** The source team owns a lifetime drop quota; discovery and currency increase only on pickup. */
public final class GoldenWalnutData extends SavedData {
    private int musselDrops;
    private int volcanoDrops;
    private int found;
    private int balance;

    public static GoldenWalnutData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(GoldenWalnutData::new, GoldenWalnutData::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(GoldenWalnutData::new, GoldenWalnutData::load)), "stardewcraft_golden_walnuts");
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
        return tag;
    }

    public static GoldenWalnutData load(CompoundTag tag, HolderLookup.Provider provider) {
        GoldenWalnutData data = new GoldenWalnutData();
        data.musselDrops = Math.max(0, Math.min(5, tag.getInt("MusselStoneDrops")));
        data.volcanoDrops = Math.max(0, Math.min(5, tag.getInt("VolcanoMiningDrops")));
        data.found = Math.max(0, tag.getInt("Found"));
        data.balance = Math.max(0, tag.getInt("Balance"));
        return data;
    }
}
