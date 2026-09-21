package com.stardew.craft.api.v1.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable fact emitted after a general machine-cycle transition. */
public record StardewMachineCycleEvent(
        StardewProductionPhase phase,
        StardewMachineCycleKind kind,
        ResourceLocation machineId,
        ServerLevel level,
        BlockPos position,
        ItemStack input,
        ItemStack output,
        long readyAtAbsoluteMinute,
        boolean automation
) {
    public StardewMachineCycleEvent {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(level, "level");
        position = Objects.requireNonNull(
                position, "position").immutable();
        input = Objects.requireNonNull(input, "input").copy();
        output = Objects.requireNonNull(output, "output").copy();
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
