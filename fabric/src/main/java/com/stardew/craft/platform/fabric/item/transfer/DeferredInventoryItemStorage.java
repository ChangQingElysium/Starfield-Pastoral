package com.stardew.craft.platform.fabric.item.transfer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.fabricmc.fabric.impl.transfer.item.ItemVariantImpl;
import net.minecraft.world.item.ItemStack;

/** A collector's aggregate input and first occupied output, backed by a real inventory. */
public final class DeferredInventoryItemStorage extends SnapshotParticipant<DeferredInventoryItemStorage.State>
        implements SlottedStorage<ItemVariant> {
    public interface Inventory {
        /** A pending recovery journal makes this view unavailable until normal recovery runs. */
        boolean available();
        List<ItemStack> read();
        Object captureState();
        boolean matchesState(Object state);
        void commit(List<ItemStack> items);
    }
    private final Inventory inventory;
    private final List<SingleSlotStorage<ItemVariant>> slots = List.of(new Slot(0), new Slot(1));
    private List<ItemStack> staged;
    private Baseline baseline;

    public DeferredInventoryItemStorage(Inventory inventory) { this.inventory = Objects.requireNonNull(inventory); }
    private List<ItemStack> contents() { return staged == null ? inventory.read() : staged; }
    private static List<ItemStack> copy(List<ItemStack> items) {
        return items.stream().map(ItemStack::copy).toList();
    }
    private int firstOutput() {
        if (!inventory.available()) return -1;
        List<ItemStack> items = contents();
        for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) return i;
        return -1;
    }
    private ItemStack output() {
        int index = firstOutput();
        return index < 0 ? ItemStack.EMPTY : contents().get(index);
    }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !inventory.available()) return 0;
        List<ItemStack> next = new ArrayList<>(copy(contents()));
        long remaining = amount;
        int limit = ItemVariantImpl.getMaxStackSize(resource);
        for (int i = 0; i < next.size() && remaining > 0; i++) {
            ItemStack item = next.get(i);
            if (item.isEmpty() || !resource.matches(item)) continue;
            int accepted = (int) Math.min(remaining, Math.max(0, item.getMaxStackSize() - item.getCount()));
            if (accepted > 0) { item.grow(accepted); remaining -= accepted; }
        }
        for (int i = 0; i < next.size() && remaining > 0; i++) {
            if (!next.get(i).isEmpty()) continue;
            int accepted = (int) Math.min(remaining, limit);
            next.set(i, resource.toStack(accepted));
            remaining -= accepted;
        }
        if (remaining == amount) return 0;
        reserve(transaction); staged = next;
        return amount - remaining;
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        int index = firstOutput();
        if (amount == 0 || index < 0 || !resource.matches(contents().get(index))) return 0;
        ItemStack item = contents().get(index);
        int taken = (int) Math.min(amount, item.getCount());
        List<ItemStack> next = new ArrayList<>(copy(contents()));
        next.set(index, item.copyWithCount(item.getCount() - taken));
        reserve(transaction); staged = next;
        return taken;
    }
    private void reserve(TransactionContext transaction) {
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (baseline != null) return;
        Baseline expected = new Baseline(Objects.requireNonNull(inventory.captureState()), copy(inventory.read()));
        baseline = expected;
        TransferTransactionGuards.register(transaction, () -> {
            // A rolled-back nested reservation must not attach to a later one.
            if (baseline != expected) return;
            List<ItemStack> original = expected.items;
            List<ItemStack> actual = inventory.read();
            boolean unchanged = actual.size() == original.size();
            for (int i = 0; unchanged && i < actual.size(); i++) unchanged = ItemStack.matches(original.get(i), actual.get(i));
            if (!inventory.available() || !inventory.matchesState(expected.context) || !unchanged) {
                throw new TransferTransactionGuards.Conflict("Collector inventory or recovery journal changed outside its open transaction");
            }
        });
    }
    @Override public int getSlotCount() { return 2; }
    @Override public SingleSlotStorage<ItemVariant> getSlot(int index) { return slots.get(index); }
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        return List.<StorageView<ItemVariant>>of(slots.get(0), slots.get(1)).iterator();
    }
    @Override protected State createSnapshot() { return new State(staged == null ? null : copy(staged), baseline); }
    @Override protected void readSnapshot(State state) { staged = state.items == null ? null : copy(state.items); baseline = state.baseline; }
    @Override protected void onFinalCommit() {
        List<ItemStack> committed = staged;
        staged = null; baseline = null;
        if (committed != null) inventory.commit(copy(committed));
    }
    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int index;
        private Slot(int index) { this.index = index; }
        private ItemStack stack() { return index == 1 ? output() : ItemStack.EMPTY; }
        @Override public boolean supportsInsertion() { return index == 0; }
        @Override public boolean supportsExtraction() { return index == 1; }
        @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return index == 0 ? DeferredInventoryItemStorage.this.insert(resource, amount, transaction) : 0;
        }
        @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return index == 1 ? DeferredInventoryItemStorage.this.extract(resource, amount, transaction) : 0;
        }
        @Override public boolean isResourceBlank() { return stack().isEmpty(); }
        @Override public ItemVariant getResource() { return ItemVariant.of(stack()); }
        @Override public long getAmount() { return stack().getCount(); }
        @Override public long getCapacity() { return 64; }
    }
    private record Baseline(Object context, List<ItemStack> items) {}
    protected record State(List<ItemStack> items, Baseline baseline) {}
}
