package com.stardew.craft.platform.fabric.blockextension.removal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public interface PlayerDestroyProvider {
    default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return PlayerBlockRemoval.defaultRemove(state, level, pos, player, willHarvest, fluid);
    }
}
