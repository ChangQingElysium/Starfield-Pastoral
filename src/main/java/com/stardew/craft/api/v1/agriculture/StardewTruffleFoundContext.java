package com.stardew.craft.api.v1.agriculture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Stable event view for pig truffle discoveries and Truffle Crab replacements. */
public record StardewTruffleFoundContext(
        ServerLevel level,
        long animalId,
        String animalTypeId,
        BlockPos anchor,
        ItemStack truffle
) {
    public StardewTruffleFoundContext {
        Objects.requireNonNull(level, "level");
        animalTypeId = Objects.requireNonNull(animalTypeId, "animalTypeId");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        truffle = Objects.requireNonNull(truffle, "truffle").copy();
    }

    @Override
    public ItemStack truffle() {
        return truffle.copy();
    }
}
