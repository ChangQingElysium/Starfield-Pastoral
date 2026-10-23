package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

/** For pure-read producers: existing output is reserved; collection runs only on commit. */
public final class OutputOnlyItemStorage {
    private OutputOnlyItemStorage() {}
    public static PlannedMachineItemStorage of(Supplier<ItemStack> output, Supplier<Object> capture,
            Predicate<Object> unchanged, IntFunction<ItemStack> collect) {
        Objects.requireNonNull(output, "output"); Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(unchanged, "unchanged"); Objects.requireNonNull(collect, "collect");
        return new PlannedMachineItemStorage(new PlannedItemMachine() {
            @Override public boolean canInsert() { return false; }
            @Override public ItemStack input() { return ItemStack.EMPTY; }
            @Override public ItemStack output() { return output.get(); }
            @Override public Object captureState() { return capture.get(); }
            @Override public boolean matchesState(Object state) { return unchanged.test(state); }
            @Override public Optional<Insertion> planInsertion(ItemStack offered, boolean collecting, TransactionContext transaction) {
                return Optional.empty();
            }
            @Override public void commitExtraction(int count) {
                ItemStack expected = output.get().copyWithCount(count);
                ItemStack result = collect.apply(count);
                if (!ItemStack.matches(expected, result)) {
                    throw new IllegalStateException("Output-only machine violated its committed collection plan");
                }
            }
        });
    }
}
