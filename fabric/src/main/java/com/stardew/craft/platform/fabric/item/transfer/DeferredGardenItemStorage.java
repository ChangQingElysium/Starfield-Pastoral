package com.stardew.craft.platform.fabric.item.transfer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.fabricmc.fabric.impl.transfer.item.ItemVariantImpl;
import net.minecraft.world.item.ItemStack;

/** The garden owns immutable crop/soil/queue plans; this participant owns transaction ordering. */
public final class DeferredGardenItemStorage<S> extends SnapshotParticipant<DeferredGardenItemStorage.Snapshot<S>>
        implements SlottedStorage<ItemVariant> {
    public record Step<S>(S state, int consumed, BooleanSupplier stillValid, Runnable commit) {
        public Step {
            Objects.requireNonNull(state); Objects.requireNonNull(stillValid); Objects.requireNonNull(commit);
            if (consumed <= 0) throw new IllegalArgumentException("Garden plans must consume a positive item amount");
        }
    }
    public interface Garden<S> {
        boolean available();
        S read();
        boolean matches(S baseline);
        /** May forecast using a disposable RNG copy; must not reserve or mutate the world. */
        ItemStack preview(S state);
        Optional<Step<S>> insert(S state, ItemStack offered, TransactionContext transaction);
        Optional<Step<S>> extract(S state, ItemVariant wanted, long amount, TransactionContext transaction);
    }
    private record Baseline<S>(S state) {}
    private final Garden<S> garden;
    private final List<SingleSlotStorage<ItemVariant>> slots = List.of(new Slot(0), new Slot(1));
    private S staged;
    private Baseline<S> baseline;
    private List<Step<S>> steps = new ArrayList<>();

    public DeferredGardenItemStorage(Garden<S> garden) { this.garden = Objects.requireNonNull(garden); }
    private S state() { return staged == null ? garden.read() : staged; }
    private ItemStack output() { return garden.available() ? garden.preview(state()) : ItemStack.EMPTY; }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !garden.available()) return 0;
        int offered = (int) Math.min(amount, ItemVariantImpl.getMaxStackSize(resource));
        return plan(transaction, offered, nested -> garden.insert(state(), resource.toStack(offered), nested));
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !garden.available()) return 0;
        return plan(transaction, amount, nested -> garden.extract(state(), resource, amount, nested));
    }
    private long plan(TransactionContext transaction, long offered,
            java.util.function.Function<TransactionContext, Optional<Step<S>>> planner) {
        try (var nested = transaction.openNested()) {
            reserve(nested);
            var prepared = planner.apply(nested);
            if (prepared.isEmpty()) return 0;
            Step<S> step = prepared.get();
            if (step.consumed > offered) throw new IllegalStateException("Garden plan exceeds the item reservation");
            staged = step.state; steps.add(step);
            nested.commit();
            return step.consumed;
        }
    }
    private void reserve(TransactionContext transaction) {
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (baseline != null) return;
        Baseline<S> expected = new Baseline<>(Objects.requireNonNull(garden.read()));
        baseline = expected;
        TransferTransactionGuards.register(transaction, () -> {
            if (baseline != expected) return;
            if (!garden.available() || !garden.matches(expected.state)
                    || steps.stream().anyMatch(step -> !step.stillValid.getAsBoolean())) {
                throw new TransferTransactionGuards.Conflict("Garden crop, soil, outputs or harvest rules changed outside its open transaction");
            }
        });
    }
    @Override public int getSlotCount() { return 2; }
    @Override public SingleSlotStorage<ItemVariant> getSlot(int index) { return slots.get(index); }
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        return List.<StorageView<ItemVariant>>of(slots.get(0), slots.get(1)).iterator();
    }
    @Override protected Snapshot<S> createSnapshot() { return new Snapshot<>(staged, baseline, List.copyOf(steps)); }
    @Override protected void readSnapshot(Snapshot<S> snapshot) {
        staged = snapshot.staged; baseline = snapshot.baseline; steps = new ArrayList<>(snapshot.steps);
    }
    @Override protected void onFinalCommit() {
        var committed = steps;
        staged = null; baseline = null; steps = new ArrayList<>();
        for (Step<S> step : committed) step.commit.run();
    }
    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int index;
        private Slot(int index) { this.index = index; }
        private ItemStack stack() { return index == 0 ? ItemStack.EMPTY : output(); }
        @Override public boolean supportsInsertion() { return index == 0; }
        @Override public boolean supportsExtraction() { return index == 1; }
        @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return index == 0 ? DeferredGardenItemStorage.this.insert(resource, amount, transaction) : 0;
        }
        @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return index == 1 ? DeferredGardenItemStorage.this.extract(resource, amount, transaction) : 0;
        }
        @Override public boolean isResourceBlank() { return stack().isEmpty(); }
        @Override public ItemVariant getResource() { return ItemVariant.of(stack()); }
        @Override public long getAmount() { return stack().getCount(); }
        @Override public long getCapacity() { return 64; }
    }
    protected record Snapshot<S>(S staged, Baseline<S> baseline, List<Step<S>> steps) {}
}
