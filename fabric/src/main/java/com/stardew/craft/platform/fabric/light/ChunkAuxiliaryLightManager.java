package com.stardew.craft.platform.fabric.light;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/** Copy-on-write snapshots make full sync/reload atomic to concurrent lighting readers. */
public final class ChunkAuxiliaryLightManager implements AuxiliaryLightManager {
    public static final String NBT_KEY = "stardewcraft:aux_lights";
    public static final String LEGACY_NBT_KEY = "neoforge:aux_lights";
    private final ChunkPos chunk;
    private final Consumer<Change> changed;
    private volatile Map<Long, Byte> lights = Map.of();

    public record Change(BlockPos pos, int previous, int value) {}

    public ChunkAuxiliaryLightManager(ChunkPos chunk, Consumer<Change> changed) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @Override public int getLightAt(BlockPos pos) { return lights.getOrDefault(pos.asLong(), (byte) 0); }

    @Override public void setLightAt(BlockPos pos, int value) {
        pos = pos.immutable();
        validatePosition(chunk, pos.asLong());
        value = Mth.clamp(value, 0, 15);
        Change change;
        synchronized (this) {
            int old = getLightAt(pos);
            if (old == value) return;
            var next = new HashMap<>(lights);
            if (value == 0) next.remove(pos.asLong()); else next.put(pos.asLong(), (byte) value);
            lights = Map.copyOf(next);
            change = new Change(pos.immutable(), old, value);
        }
        changed.accept(change);
    }

    public Map<Long, Byte> snapshot() { return lights; }

    /** For chunk deserialization, before the native chunk light-source scan; no world/thread side effects. */
    public void load(ListTag input) {
        var next = new HashMap<Long, Byte>();
        for (int index = 0; index < input.size(); index++) {
            CompoundTag entry = input.getCompound(index);
            long pos = entry.getLong("pos");
            validatePosition(chunk, pos);
            int light = Mth.clamp(entry.getByte("level"), 0, 15);
            if (light == 0) next.remove(pos); else next.put(pos, (byte) light);
        }
        synchronized (this) { lights = Map.copyOf(next); }
    }

    public ListTag save() {
        var result = new ListTag();
        new TreeMap<>(lights).forEach((pos, light) -> {
            var entry = new CompoundTag(); entry.putLong("pos", pos); entry.putByte("level", light); result.add(entry);
        });
        return result;
    }

    public void loadChunkTag(CompoundTag input) {
        String key = input.contains(NBT_KEY, net.minecraft.nbt.Tag.TAG_LIST) ? NBT_KEY : LEGACY_NBT_KEY;
        if (input.contains(key, net.minecraft.nbt.Tag.TAG_LIST)) load(input.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND));
    }

    public void saveInto(CompoundTag output) {
        output.put(NBT_KEY, save());
        output.remove(LEGACY_NBT_KEY);
    }

    /** Apply a wire update to the current client chunk and invalidate every changed light source. */
    public void apply(Map<Long, Byte> values, boolean full) {
        values.forEach((pos, value) -> {
            validatePosition(chunk, pos);
            if (value < 0 || value > 15 || (full && value == 0)) throw new IllegalArgumentException("Invalid auxiliary light level");
        });
        Map<Long, Byte> before;
        Map<Long, Byte> after;
        synchronized (this) {
            before = lights;
            var next = new HashMap<Long, Byte>();
            if (!full) next.putAll(before);
            values.forEach((pos, value) -> { if (value == 0) next.remove(pos); else next.put(pos, value); });
            lights = after = Map.copyOf(next);
        }
        var touched = new java.util.HashSet<>(before.keySet()); touched.addAll(after.keySet());
        for (long pos : touched) {
            int previous = before.getOrDefault(pos, (byte) 0), value = after.getOrDefault(pos, (byte) 0);
            if (previous != value) changed.accept(new Change(BlockPos.of(pos), previous, value));
        }
    }

    static void validatePosition(ChunkPos chunk, long pos) {
        if (BlockPos.getX(pos) >> 4 != chunk.x || BlockPos.getZ(pos) >> 4 != chunk.z) {
            throw new IllegalArgumentException("Auxiliary light position belongs to another chunk: " + BlockPos.of(pos));
        }
    }
}
