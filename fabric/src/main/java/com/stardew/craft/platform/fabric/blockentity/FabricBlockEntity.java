package com.stardew.craft.platform.fabric.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Native base for the mod's block entities that need a deferred load callback. */
public abstract class FabricBlockEntity extends BlockEntity implements BlockEntityDataProvider {
    protected FabricBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    /** Called after data loading, immediately before the next block entity tick pass. */
    public void onLoad() {
        requestModelDataUpdate();
    }

    /**
     * Rebuild the client mesh through the native level hook. Fabric model data
     * providers must expose their current snapshot through the rendering bridge.
     */
    public void requestModelDataUpdate() {
        Level currentLevel = getLevel();
        if (currentLevel != null && currentLevel.isClientSide()) {
            BlockState currentState = getBlockState();
            currentLevel.sendBlockUpdated(getBlockPos(), currentState, currentState, Block.UPDATE_CLIENTS);
        }
    }
}
