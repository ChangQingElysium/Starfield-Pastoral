package com.stardew.craft.platform.fabric.blockextension.piston;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

/** Null retains the push reaction declared by the block properties. */
public interface PistonPushProvider {
    @Nullable PushReaction getPistonPushReaction(BlockState state);
}
