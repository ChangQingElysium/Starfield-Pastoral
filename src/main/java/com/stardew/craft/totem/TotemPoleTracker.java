package com.stardew.craft.totem;

import com.stardew.craft.block.utility.totem.TotemType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局图腾柱注册表 — 以 SavedData 形式存储在 Stardew 维度中。
 * 每个图腾柱拥有唯一 int ID（0 为系统柱，>0 为玩家放置）。
 */
@SuppressWarnings("null")
public class TotemPoleTracker extends SavedData {

    private static final String DATA_NAME = "stardewcraft_totem_poles";

    public record PoleEntry(BlockPos pos, String name, TotemType type, boolean systemPole) {}

    private final Map<Integer, PoleEntry> poles = new HashMap<>();
    private int nextId = 3; // 0/1/2 保留给系统柱

    public TotemPoleTracker() {
        super();
    }

    /* ---------- 查询 ---------- */

    @Nullable
    public PoleEntry getPole(int id) {
        return poles.get(id);
    }

    /** 获取指定类型的系统柱（ID 0 组中对应类型） */
    @Nullable
    public PoleEntry getDefaultPole(TotemType type) {
        // 系统柱以 type.ordinal() 的负值存储 (-1, -2, -3)，0 不使用
        // 简化：遍历找 systemPole && type 匹配
        for (PoleEntry entry : poles.values()) {
            if (entry.systemPole() && entry.type() == type) {
                return entry;
            }
        }
        return null;
    }

    public List<PoleEntry> getAllPoles(TotemType type) {
        List<PoleEntry> result = new ArrayList<>();
        for (PoleEntry entry : poles.values()) {
            if (entry.type() == type) {
                result.add(entry);
            }
        }
        return result;
    }

    /* ---------- 注册 / 注销 ---------- */

    public int allocateId() {
        // 确保不和已有ID冲突
        while (poles.containsKey(nextId)) {
            nextId++;
        }
        int id = nextId++;
        setDirty();
        return id;
    }

    public void register(int id, PoleEntry entry) {
        poles.put(id, entry);
        setDirty();
    }

    public void unregister(int id) {
        if (poles.remove(id) != null) {
            setDirty();
        }
    }

    public void updateName(int id, String newName) {
        PoleEntry old = poles.get(id);
        if (old != null) {
            poles.put(id, new PoleEntry(old.pos(), newName, old.type(), old.systemPole()));
            setDirty();
        }
    }

    /* ---------- 序列化 ---------- */

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Map.Entry<Integer, PoleEntry> e : poles.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", e.getKey());
            entry.putLong("pos", e.getValue().pos().asLong());
            entry.putString("name", e.getValue().name());
            entry.putString("type", e.getValue().type().getId());
            entry.putBoolean("system", e.getValue().systemPole());
            list.add(entry);
        }
        tag.put("poles", list);
        return tag;
    }

    private static TotemPoleTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        TotemPoleTracker tracker = new TotemPoleTracker();
        tracker.nextId = tag.getInt("nextId");
        ListTag list = tag.getList("poles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int id = entry.getInt("id");
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            String name = entry.getString("name");
            TotemType type = TotemType.fromId(entry.getString("type"));
            boolean system = entry.getBoolean("system");
            tracker.poles.put(id, new PoleEntry(pos, name, type, system));
        }
        return tracker;
    }

    /* ---------- 获取实例 ---------- */

    public static TotemPoleTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(TotemPoleTracker::new, TotemPoleTracker::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(TotemPoleTracker::new, TotemPoleTracker::load)),
                DATA_NAME
        );
    }
}
