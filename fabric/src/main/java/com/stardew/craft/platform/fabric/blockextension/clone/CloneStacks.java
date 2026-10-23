package com.stardew.craft.platform.fabric.blockextension.clone;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** Empty custom results suppress picking; only unowned blocks reach the native fallback. */
public final class CloneStacks {
    private CloneStacks() {}
    public static ItemStack pick(BlockState state, HitResult target, LevelReader level, BlockPos pos,
            Player player, Supplier<ItemStack> fallback) {
        return state.getBlock() instanceof CloneStackProvider provider
                ? Objects.requireNonNull(provider.getCloneItemStack(state, target, level, pos, player), "clone stack")
                : fallback.get();
    }
    public static ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return pick(state, target, level, pos, player, () -> state.getBlock().getCloneItemStack(level, pos, state));
    }
}
