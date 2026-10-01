package com.stardew.craft.blockentity.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-{@link ServerLevel} registry of all placed lightning rod <b>main-part</b>
 * block positions, plus a set of pending charge positions that were selected by
 * {@link com.stardew.craft.weather.LightningStrikeScheduler} while the target
 * chunk was unloaded.
 *
 * <p>The scheduler runs globally every 10 in-game minutes and cannot rely on
 * block entities being ticked, so all persistent state lives here.</p>
 */
public class LightningRodRegistry extends SavedData {

    private static final String DATA_NAME = "stardewcraft_lightning_rods";
    private static final String TAG_RODS = "rods";
    private static final String TAG_PENDING = "pending";

    private final Set<BlockPos> rods = new LinkedHashSet<>();
    private final Set<BlockPos> pending = new HashSet<>();

    public LightningRodRegistry() {}

    public static LightningRodRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(LightningRodRegistry::new, LightningRodRegistry::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(LightningRodRegistry::new, LightningRodRegistry::load)),
            DATA_NAME);
    }

    public static LightningRodRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        LightningRodRegistry data = new LightningRodRegistry();
        readPositions(tag, TAG_RODS, data.rods);
        readPositions(tag, TAG_PENDING, data.pending);
        return data;
    }

    private static void readPositions(CompoundTag tag, String key, Set<BlockPos> out) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(key, Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            Tag t = list.get(i);
            if (t instanceof LongTag lt) {
                out.add(BlockPos.of(lt.getAsLong()));
            }
        }
    }

    @Nonnull
    @Override
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.put(TAG_RODS, writePositions(rods));
        tag.put(TAG_PENDING, writePositions(pending));
        return tag;
    }

    private static ListTag writePositions(Set<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) list.add(LongTag.valueOf(pos.asLong()));
        return list;
    }

    public void add(BlockPos mainPos) {
        if (rods.add(mainPos.immutable())) setDirty();
    }

    public void remove(BlockPos mainPos) {
        boolean changed = rods.remove(mainPos);
        changed |= pending.remove(mainPos);
        if (changed) setDirty();
    }

    public Set<BlockPos> positions() {
        return rods;
    }

    public int size() {
        return rods.size();
    }

    public void addPending(BlockPos mainPos) {
        if (pending.add(mainPos.immutable())) setDirty();
    }

    public boolean consumePending(BlockPos mainPos) {
        boolean removed = pending.remove(mainPos);
        if (removed) setDirty();
        return removed;
    }
}
