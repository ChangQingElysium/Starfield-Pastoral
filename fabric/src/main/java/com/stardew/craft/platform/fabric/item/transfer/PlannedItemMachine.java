package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

/** The machine supplies business plans; the storage owns reservation/rollback. */
public interface PlannedItemMachine {
    default boolean canInsert() { return true; }
    ItemStack input();
    ItemStack output();
    Object captureState();
    boolean matchesState(Object state);
    Optional<Insertion> planInsertion(ItemStack offered, boolean collectingOutput, TransactionContext transaction);
    void commitExtraction(int count);
    default int inputLimit() { return 64; }

    @FunctionalInterface
    interface Planner {
        Optional<Insertion> prepare(ItemStack offered, boolean collectingOutput, TransactionContext transaction);
    }

    record Insertion(ItemStack trackedInput, int consumed, BooleanSupplier stillValid, Runnable commit) {
        public Insertion {
            Objects.requireNonNull(trackedInput, "trackedInput");
            Objects.requireNonNull(commit, "commit");
            Objects.requireNonNull(stillValid, "stillValid");
            if (consumed <= 0 || trackedInput.isEmpty()) throw new IllegalArgumentException("Invalid machine insertion plan");
            trackedInput = trackedInput.copy();
        }
        @Override public ItemStack trackedInput() { return trackedInput.copy(); }
    }
}
