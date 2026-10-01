package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * 记录公共区域（小镇等非农场区域）中被玩家移除的方块（杂草等），
 * 以便在换季时批量恢复。
 */
@SuppressWarnings("null")
public class PublicAreaBlockTracker extends SavedData {

    private static final String DATA_NAME = "stardew_public_area_blocks";

    /** 被移除的方块：坐标 → 原始方块状态 */
    private final Map<BlockPos, BlockState> removedBlocks = new HashMap<>();

    public PublicAreaBlockTracker() {}

    public static PublicAreaBlockTracker get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new PublicAreaBlockTracker();
        return server.overworld().getDataStorage().computeIfAbsent(com.stardew.craft.port.PortSavedData.loader(factory()), com.stardew.craft.port.PortSavedData.constructor(factory()), DATA_NAME);
    }

    /**
     * 记录一个被移除的方块（仅记录第一次移除，避免覆盖原始状态）。
     */
    public void recordRemoval(BlockPos pos, BlockState originalState) {
        removedBlocks.putIfAbsent(pos, originalState);
        setDirty();
    }

    /**
     * 换季时批量恢复所有记录的方块，然后清空记录。
     */
    public void restoreAll(ServerLevel level) {
        if (removedBlocks.isEmpty()) return;

        int restored = 0;
        for (var entry : removedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            // 只恢复当前为空气的位置，避免覆盖玩家放置的方块
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
                restored++;
            }
        }

        StardewCraft.LOGGER.info("[PUBLIC_AREA] Restored {}/{} blocks on season change",
                restored, removedBlocks.size());
        removedBlocks.clear();
        setDirty();
    }

    // ── NBT ──

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        ListTag list = new ListTag();
        for (var entry : removedBlocks.entrySet()) {
            CompoundTag blockTag = new CompoundTag();
            blockTag.putLong("Pos", entry.getKey().asLong());
            blockTag.putString("Block", BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()).toString());
            list.add(blockTag);
        }
        tag.put("RemovedBlocks", list);
        return tag;
    }

    private static PublicAreaBlockTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        PublicAreaBlockTracker tracker = new PublicAreaBlockTracker();

        if (tag.contains("RemovedBlocks")) {
            ListTag list = tag.getList("RemovedBlocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag blockTag = list.getCompound(i);
                BlockPos pos = BlockPos.of(blockTag.getLong("Pos"));
                ResourceLocation blockId = new ResourceLocation(blockTag.getString("Block"));
                Block block = BuiltInRegistries.BLOCK.get(blockId);
                if (block != null) {
                    tracker.removedBlocks.put(pos, block.defaultBlockState());
                }
            }
        }

        StardewCraft.LOGGER.info("[PUBLIC_AREA] Loaded {} tracked removed blocks",
                tracker.removedBlocks.size());
        return tracker;
    }

    public static com.stardew.craft.port.PortSavedData.Factory<PublicAreaBlockTracker> factory() {
        return new com.stardew.craft.port.PortSavedData.Factory<>(PublicAreaBlockTracker::new, PublicAreaBlockTracker::load);
    }
}
