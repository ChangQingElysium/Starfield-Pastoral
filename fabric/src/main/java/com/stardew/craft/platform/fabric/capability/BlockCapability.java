package com.stardew.craft.platform.fabric.capability;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Project provider chain, also exposed through the corresponding real FAPI lookup. */
public final class BlockCapability<T, C> {
    enum Kind { ITEM, FLUID }
    final Kind kind;
    private final Map<Block, List<IBlockCapabilityProvider<T, C>>> providers = new IdentityHashMap<>();

    BlockCapability(Kind kind) { this.kind = kind; }

    synchronized boolean register(Block block, IBlockCapabilityProvider<T, C> provider) {
        boolean first = !providers.containsKey(block);
        providers.computeIfAbsent(block, ignored -> new ArrayList<>()).add(provider);
        return first;
    }

    public T getCapability(Level level, BlockPos position, C context) {
        return getCapability(level, position, level.getBlockState(position), level.getBlockEntity(position), context);
    }

    public T getCapability(Level level, BlockPos position, BlockState state, BlockEntity blockEntity, C context) {
        for (var provider : providers.getOrDefault(state.getBlock(), List.of())) {
            T value = provider.getCapability(level, position, state, blockEntity, context);
            if (value != null) return value;
        }
        return null;
    }
}
