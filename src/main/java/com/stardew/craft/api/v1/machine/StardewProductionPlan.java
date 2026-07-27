package com.stardew.craft.api.v1.machine;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Validated output and duration selected before a timed machine consumes its inputs. */
public record StardewProductionPlan(
        ItemStack output,
        int minutes
) {
    public StardewProductionPlan {
        Objects.requireNonNull(output, "output");
        if (output.isEmpty()) {
            throw new IllegalArgumentException(
                    "production output must not be empty");
        }
        output = output.copy();
        if (minutes < 0) {
            throw new IllegalArgumentException(
                    "production minutes must be non-negative");
        }
    }

    @Override
    public ItemStack output() {
        return output.copy();
    }
}
