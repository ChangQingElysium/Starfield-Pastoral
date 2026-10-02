package com.stardew.craft.port.net.neoforged.neoforge.capabilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** PORT(1.20.1): NeoForge block capability provider. */
@FunctionalInterface
public interface IBlockCapabilityProvider<T, C> {
    @Nullable
    T getCapability(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, C context);
}
