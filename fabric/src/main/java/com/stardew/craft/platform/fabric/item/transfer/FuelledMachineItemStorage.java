package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Preserves the existing two handler slots and its hidden coal-buffer input. */
public final class FuelledMachineItemStorage implements SlottedStorage<ItemVariant> {
    private final DeferredFuelBuffer fuel;
    private final PlannedMachineItemStorage production;
    private final BooleanSupplier fuelAllowed;
    private final List<SingleSlotStorage<ItemVariant>> slots = List.of(new Slot(0), new Slot(1));
    public FuelledMachineItemStorage(DeferredFuelBuffer fuel, PlannedMachineItemStorage production, BooleanSupplier fuelAllowed) {
        this.fuel = Objects.requireNonNull(fuel, "fuel");
        this.production = Objects.requireNonNull(production, "production");
        this.fuelAllowed = Objects.requireNonNull(fuelAllowed, "fuelAllowed");
    }
    @Override public int getSlotCount() { return 2; }
    @Override public SingleSlotStorage<ItemVariant> getSlot(int slot) { return slots.get(slot); }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0) return 0;
        try (var planning = transaction.openNested()) {
            fuel.prepare(planning);
            long inserted;
            if (fuel.accepts(resource)) inserted = fuelAllowed.getAsBoolean() ? fuel.insert(resource, amount, planning) : 0;
            else inserted = production.insert(resource, amount, planning);
            if (inserted > 0) planning.commit();
            return inserted;
        }
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0) return 0;
        try (var planning = transaction.openNested()) {
            fuel.prepare(planning);
            long extracted = production.extract(resource, amount, planning);
            if (extracted > 0) planning.commit();
            return extracted;
        }
    }
    @Override public Iterator<StorageView<ItemVariant>> iterator() { return List.<StorageView<ItemVariant>>of(slots.get(0), slots.get(1)).iterator(); }
    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int slot;
        private Slot(int slot) { this.slot = slot; }
        private SingleSlotStorage<ItemVariant> delegate() { return production.getSlot(slot); }
        @Override public boolean supportsInsertion() { return slot == 0; }
        @Override public boolean supportsExtraction() { return slot == 1; }
        @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return slot == 0 ? FuelledMachineItemStorage.this.insert(resource, amount, transaction) : 0;
        }
        @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return slot == 1 ? FuelledMachineItemStorage.this.extract(resource, amount, transaction) : 0;
        }
        @Override public boolean isResourceBlank() { return delegate().isResourceBlank(); }
        @Override public ItemVariant getResource() { return delegate().getResource(); }
        @Override public long getAmount() { return delegate().getAmount(); }
        @Override public long getCapacity() { return delegate().getCapacity(); }
    }
}
