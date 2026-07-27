package com.stardew.craft.api.v1.machine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/** Client-safe JEI projection for a machine operation that cannot be derived from recipe JSON. */
public record StardewMachineRecipeDisplay(
        ResourceLocation id,
        ResourceLocation machineId,
        List<Input> inputs,
        List<Output> outputs,
        int minutes,
        boolean keepInputQuality,
        int outputQuality
) {
    public StardewMachineRecipeDisplay {
        id = Objects.requireNonNull(id, "id");
        machineId = Objects.requireNonNull(machineId, "machineId");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Machine display requires a primary input");
        }
        if (minutes < 0) {
            throw new IllegalArgumentException("Machine display minutes must be non-negative");
        }
    }

    public record Input(List<ItemStack> stacks, int count, boolean auxiliary) {
        public Input {
            stacks = copyStacks(stacks);
            if (stacks.isEmpty()) {
                throw new IllegalArgumentException("Machine display input cannot be empty");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Machine display input count must be positive");
            }
        }

        @Override
        public List<ItemStack> stacks() {
            return copyStacks(stacks);
        }
    }

    public record Output(
            List<ItemStack> stacks,
            int minCount,
            int maxCount,
            double chance
    ) {
        public Output {
            stacks = copyStacks(stacks);
            if (stacks.isEmpty()) {
                throw new IllegalArgumentException("Machine display output cannot be empty");
            }
            if (minCount <= 0 || maxCount < minCount) {
                throw new IllegalArgumentException("Invalid machine display output count range");
            }
            if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
                throw new IllegalArgumentException("Machine display output chance must be 0..1");
            }
        }

        @Override
        public List<ItemStack> stacks() {
            return copyStacks(stacks);
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        return stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }
}
