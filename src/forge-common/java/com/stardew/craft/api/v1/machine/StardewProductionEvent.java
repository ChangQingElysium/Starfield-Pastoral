package com.stardew.craft.api.v1.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable server-side snapshot for one timed-production lifecycle transition. */
public record StardewProductionEvent(
        StardewProductionPhase phase,
        ResourceLocation machineId,
        ServerLevel level,
        BlockPos position,
        ItemStack input,
        ItemStack output,
        long readyAtAbsoluteMinute
) {
    public StardewProductionEvent {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        input = input.copy();
        output = output.copy();
    }

    @Override
    public ItemStack input() {
        return input.copy();
    }

    @Override
    public ItemStack output() {
        return output.copy();
    }
}
