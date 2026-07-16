package com.stardew.craft.integration.jei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side JEI projection of one artisan machine operation.
 *
 * <p>The projection keeps every real input and output as an item stack so JEI can
 * build correct R/U lookup relationships. Text drawn by the category is only
 * supplementary metadata.</p>
 */
public record ArtisanJeiRecipe(
        ResourceLocation id,
        MachineJeiRegistry.Machine machine,
        List<Input> inputs,
        List<Output> outputs,
        int minutes,
        boolean keepInputQuality,
        int outputQuality
) {
    public ArtisanJeiRecipe {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    /** Stable comparison key used to make repeated server sync refreshes idempotent. */
    public String contentSignature() {
        StringBuilder result = new StringBuilder(id.toString())
                .append('|').append(machine.id())
                .append('|').append(minutes)
                .append('|').append(keepInputQuality)
                .append('|').append(outputQuality);
        for (Input input : inputs) {
            result.append("|in:").append(input.count()).append(':').append(input.auxiliary());
            input.stacks().forEach(stack -> appendStack(result, stack));
        }
        for (Output output : outputs) {
            result.append("|out:").append(output.minCount()).append(':')
                    .append(output.maxCount()).append(':').append(output.chance());
            output.stacks().forEach(stack -> appendStack(result, stack));
        }
        return result.toString();
    }

    public record Input(List<ItemStack> stacks, int count, boolean auxiliary) {
        public Input {
            stacks = copyStacks(stacks);
            count = Math.max(1, count);
        }
    }

    public record Output(List<ItemStack> stacks, int minCount, int maxCount, double chance) {
        public Output {
            stacks = copyStacks(stacks);
            minCount = Math.max(1, minCount);
            maxCount = Math.max(minCount, maxCount);
            chance = Math.max(0.0D, Math.min(1.0D, chance));
        }

        public boolean isRandom() {
            return chance < 0.999_999D || minCount != maxCount;
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    private static void appendStack(StringBuilder result, ItemStack stack) {
        result.append('[')
                .append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .append(':').append(stack.getCount())
                .append(':').append(stack.getComponentsPatch())
                .append(']');
    }
}
