package com.stardew.craft.platform.fabric.item.transfer;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Frozen core crop outputs and its original, commit-only regrowth/removal transition. */
public record PreparedCropHarvest(List<ItemStack> outputs, BlockState nextRoot,
        BooleanSupplier stillValid, Consumer<Consumer<ItemStack>> commit) {
    public PreparedCropHarvest {
        outputs = outputs.stream().map(ItemStack::copy).toList();
        Objects.requireNonNull(nextRoot); Objects.requireNonNull(stillValid); Objects.requireNonNull(commit);
    }
    @Override public List<ItemStack> outputs() { return outputs.stream().map(ItemStack::copy).toList(); }
}
