package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Objects;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/** One physical machine cell, shared by every connected-network entry point. */
public final class DeferredItemCellStorage extends SnapshotParticipant<DeferredItemCellStorage.State>
        implements SingleSlotStorage<ItemVariant> {
    public interface Cell {
        ItemStack read();
        boolean accepts(ItemVariant resource);
        ItemStack normalizeInsertion(ItemStack input);
        Object captureState();
        boolean matchesState(Object state);
        void commit(ItemStack value);
        int capacity();
    }
    private final Cell cell;
    private ItemStack staged;
    private Object baseline;
    public DeferredItemCellStorage(Cell cell) { this.cell = Objects.requireNonNull(cell, "cell"); }
    private ItemStack stack() { return staged != null ? staged : cell.read(); }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        ItemStack current = stack();
        if (amount == 0 || !cell.accepts(resource) || (!current.isEmpty() && !resource.matches(current))) return 0;
        int accepted = (int) Math.min(amount, Math.max(0, cell.capacity() - current.getCount()));
        if (accepted == 0) return 0;
        ItemStack next = cell.normalizeInsertion(resource.toStack(current.getCount() + accepted));
        if (next.isEmpty() || next.getCount() != current.getCount() + accepted) {
            throw new IllegalStateException("Cell returned an invalid normalized insertion");
        }
        reserve(transaction);
        staged = next;
        return accepted;
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        ItemStack current = stack();
        if (amount == 0 || !resource.matches(current)) return 0;
        int count = (int) Math.min(amount, current.getCount());
        if (count == 0) return 0;
        reserve(transaction);
        staged = current.copyWithCount(current.getCount() - count);
        return count;
    }
    private void reserve(TransactionContext transaction) {
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (baseline != null) return;
        baseline = Objects.requireNonNull(cell.captureState(), "cell state");
        TransferTransactionGuards.register(transaction, () -> {
            if (baseline != null && !cell.matchesState(baseline)) {
                throw new TransferTransactionGuards.Conflict("Machine cell changed outside its open transaction");
            }
        });
    }
    @Override public boolean isResourceBlank() { return stack().isEmpty(); }
    @Override public ItemVariant getResource() { return ItemVariant.of(stack()); }
    @Override public long getAmount() { return stack().getCount(); }
    @Override public long getCapacity() { return cell.capacity(); }
    @Override protected State createSnapshot() { return new State(staged == null ? null : staged.copy(), baseline); }
    @Override protected void readSnapshot(State state) { staged = state.stack == null ? null : state.stack.copy(); baseline = state.baseline; }
    @Override protected void onFinalCommit() {
        ItemStack value = staged;
        staged = null; baseline = null;
        if (value != null) cell.commit(value);
    }
    protected record State(ItemStack stack, Object baseline) {}
}
