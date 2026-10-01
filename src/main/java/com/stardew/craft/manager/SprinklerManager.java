package com.stardew.craft.manager;

import com.stardew.craft.block.utility.SprinklerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SprinklerManager extends SavedData {
    private static final String DATA_NAME = "stardew_sprinkler_manager";

    private final Set<GlobalPos> sprinklerPositions = new HashSet<>();

    /** 返回所有已注册洒水器位置的不可变快照。 */
    public java.util.List<GlobalPos> getAllSprinklerPositions() {
        return new java.util.ArrayList<>(sprinklerPositions);
    }

    private boolean isProcessing = false;
    private final Set<GlobalPos> pendingAdds = new HashSet<>();
    private final Set<GlobalPos> pendingRemoves = new HashSet<>();

    @SuppressWarnings("null")
    public void addSprinkler(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        if (isProcessing) {
            pendingAdds.add(gp);
            pendingRemoves.remove(gp);
            setDirty();
            return;
        }
        if (sprinklerPositions.add(gp)) {
            setDirty();
        }
    }

    @SuppressWarnings("null")
    public void removeSprinkler(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        if (isProcessing) {
            pendingRemoves.add(gp);
            pendingAdds.remove(gp);
            setDirty();            return;
        }
        if (sprinklerPositions.remove(gp)) {
            setDirty();
        }
    }

    private void applyPendingChanges() {
        boolean changed = false;
        if (!pendingRemoves.isEmpty()) {
            changed |= sprinklerPositions.removeAll(pendingRemoves);
            pendingRemoves.clear();
        }
        if (!pendingAdds.isEmpty()) {
            changed |= sprinklerPositions.addAll(pendingAdds);
            pendingAdds.clear();
        }
        if (changed) {
            setDirty();
        }
    }

    @SuppressWarnings("null")
    public void waterDaily(ServerLevel level) {
        isProcessing = true;
        try {
            List<GlobalPos> snapshot = new java.util.ArrayList<>(sprinklerPositions);
            for (GlobalPos gp : snapshot) {
                if (gp.dimension() != level.dimension()) {
                    continue;
                }
                BlockPos pos = gp.pos();

                // 多人农场优化：跳过离线玩家农场中的喷头
                if (!com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, pos)) {
                    continue;
                }

                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof SprinklerBlock sprinkler)) {
                    removeSprinkler(level, pos);
                    continue;
                }

                SprinklerBlock.waterNow(level, pos, sprinkler.getTier(), false);
            }
        } finally {
            isProcessing = false;
            applyPendingChanges();
        }
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        ListTag list = new ListTag();
        for (GlobalPos pos : sprinklerPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putString("Dimension", pos.dimension().location().toString());
            posTag.put("Pos", NbtUtils.writeBlockPos(pos.pos()));
            list.add(posTag);
        }
        tag.put("Sprinklers", list);
        return tag;
    }

    @SuppressWarnings("null")
    public static SprinklerManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        SprinklerManager manager = new SprinklerManager();
        if (tag.contains("Sprinklers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Sprinklers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(posTag.getString("Dimension")));
                BlockPos pos = NbtUtils.readBlockPos(posTag, "Pos").orElse(BlockPos.ZERO);
                manager.sprinklerPositions.add(GlobalPos.of(dim, pos));
            }
        }
        return manager;
    }

    @SuppressWarnings("null")
    public static SprinklerManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(
                        SprinklerManager::new,
                        SprinklerManager::load,
                        null
                )), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(
                        SprinklerManager::new,
                        SprinklerManager::load,
                        null
                )),
                DATA_NAME
        );
    }
}
