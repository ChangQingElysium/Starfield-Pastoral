package com.stardew.craft.platform.fabric.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.FullChunkStatus;

/** Immutable pre-placement block and block-entity data; positions never retain mutable BlockPos. */
public final class BlockSnapshot {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;
    private final CompoundTag blockEntityTag;
    private final int flags;
    private final int recursionLeft;

    private BlockSnapshot(LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
        this.level = level;
        this.pos = pos.immutable();
        this.state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        this.blockEntityTag = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess()).copy();
        this.flags = flags;
        this.recursionLeft = recursionLeft;
    }

    public static BlockSnapshot create(LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
        return new BlockSnapshot(level, pos, flags, recursionLeft);
    }

    public LevelAccessor getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public BlockState getState() { return state; }
    public BlockState getCurrentState() { return level.getBlockState(pos); }
    public int getFlags() { return flags; }
    public CompoundTag getBlockEntityTag() { return blockEntityTag == null ? null : blockEntityTag.copy(); }

    public void restore() {
        level.setBlock(pos, state, flags | Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        if (blockEntityTag != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) throw new IllegalStateException("Restored block entity is missing at " + pos);
            blockEntity.loadWithComponents(blockEntityTag.copy(), level.registryAccess());
            blockEntity.setChanged();
        }
    }

    void commit() {
        if (!(level instanceof Level world)) return;
        BlockState current = getCurrentState();
        current.onPlace(world, pos, state, (flags & Block.UPDATE_MOVE_BY_PISTON) != 0);
        notifyChange(state, current, flags);
    }

    void notifyChange(BlockState previous, BlockState current, int updateFlags) {
        if (!(level instanceof Level world) || world.getBlockState(pos) != current) return;
        if (previous != current) world.setBlocksDirty(pos, previous, current);
        if ((updateFlags & Block.UPDATE_CLIENTS) != 0
                && (world.isClientSide || world.getChunkAt(pos).getFullStatus() != null
                && world.getChunkAt(pos).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
            world.sendBlockUpdated(pos, previous, current, updateFlags);
        }
        if ((updateFlags & Block.UPDATE_NEIGHBORS) != 0) {
            world.blockUpdated(pos, previous.getBlock());
            if (!world.isClientSide && current.hasAnalogOutputSignal()) {
                world.updateNeighbourForOutputSignal(pos, current.getBlock());
            }
        }
        if ((updateFlags & Block.UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
            int neighborFlags = updateFlags & ~Block.UPDATE_NEIGHBORS & ~Block.UPDATE_SUPPRESS_DROPS;
            previous.updateIndirectNeighbourShapes(world, pos, neighborFlags, recursionLeft - 1);
            current.updateNeighbourShapes(world, pos, neighborFlags, recursionLeft - 1);
            current.updateIndirectNeighbourShapes(world, pos, neighborFlags, recursionLeft - 1);
        }
        world.onBlockStateChange(pos, previous, current);
    }
}
