package com.stardew.craft.platform.fabric.extension;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Implemented by authored blocks; simulation may not change the world. */
public interface ToolModificationProvider {
    @Nullable
    BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate);
}
