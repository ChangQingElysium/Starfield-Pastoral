package com.stardew.craft.manager;

import com.stardew.craft.block.decor.ScarecrowBlock;
import com.stardew.craft.blockentity.ScarecrowBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 稻草人空间索引（per-dimension SavedData）。
 * - 维度内所有稻草人位置 + 半径
 * - 提供按点查询：findScarecrowCovering
 * - 性能：使用 ConcurrentHashMap，且查询 O(N) 但稻草人数量通常 <100
 */
public class ScarecrowManager extends SavedData {
    private static final String DATA_NAME = "stardewcraft_scarecrows";
    private final Map<Long, Integer> radiusByPos = new ConcurrentHashMap<>();

    public static ScarecrowManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(ScarecrowManager::new, ScarecrowManager::load),
                DATA_NAME);
    }

    public void register(ServerLevel level, BlockPos pos, int radius) {
        Integer prev = radiusByPos.put(pos.asLong(), radius);
        if (prev == null || prev != radius) {
            setDirty();
        }
    }

    public void unregister(ServerLevel level, BlockPos pos) {
        if (radiusByPos.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    /**
     * 找到任意一个保护范围覆盖 target 的稻草人位置。
     * @return 覆盖该点的稻草人位置；若没有则返回 null
     */
    @Nullable
    public BlockPos findScarecrowCovering(BlockPos target) {
        for (Map.Entry<Long, Integer> entry : radiusByPos.entrySet()) {
            BlockPos sp = BlockPos.of(entry.getKey());
            int r = entry.getValue();
            if (ScarecrowBlock.protects(sp, target, r)) {
                return sp;
            }
        }
        return null;
    }

    /** 当 BE 被加载但 SavedData 还没记录时（旧存档），由扫描补录。 */
    public void ensureRegistered(ServerLevel level, BlockPos pos, int radius) {
        radiusByPos.computeIfAbsent(pos.asLong(), k -> { setDirty(); return radius; });
    }

    /** 通知 BE 增加 crowsScared 计数。 */
    public static void notifyScarecrowScared(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ScarecrowBlockEntity scare) {
            scare.incrementCrowsScared();
        }
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Integer> entry : radiusByPos.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("Pos", entry.getKey());
            e.putInt("Radius", entry.getValue());
            list.add(e);
        }
        tag.put("Scarecrows", list);
        return tag;
    }

    public static ScarecrowManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ScarecrowManager mgr = new ScarecrowManager();
        ListTag list = tag.getList("Scarecrows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            mgr.radiusByPos.put(e.getLong("Pos"), e.getInt("Radius"));
        }
        return mgr;
    }

    /** 调试/检视用：返回所有稻草人位置→半径的快照（不要修改返回值）。 */
    public Map<BlockPos, Integer> snapshot() {
        Map<BlockPos, Integer> out = new HashMap<>();
        for (Map.Entry<Long, Integer> e : radiusByPos.entrySet()) {
            out.put(BlockPos.of(e.getKey()), e.getValue());
        }
        return out;
    }
}
