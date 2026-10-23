package com.stardew.craft.platform.fabric.modeldata;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Common-side-safe dirty notification. The client world schedules its native section rebuild. */
public final class ModelDataUpdates {
    private ModelDataUpdates() {}

    public static void request(BlockEntity entity) {
        var level = entity.getLevel();
        if (level == null || !level.isClientSide) return;
        var pos = entity.getBlockPos();
        var state = entity.getBlockState();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }
}
