package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Objects;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/** Hidden, insert-only fuel plus private production consumption in one transaction. */
public final class DeferredFuelBuffer extends SnapshotParticipant<DeferredFuelBuffer.State> {
    public interface Access {
        int amount();
        int capacity();
        boolean accepts(ItemVariant variant);
        Object captureState();
        boolean matchesState(Object state);
        void commit(int amount);
    }
    private final Access access;
    private Integer initial;
    private Object baseline;
    private int delta;
    public DeferredFuelBuffer(Access access) { this.access = Objects.requireNonNull(access, "access"); }
    public boolean accepts(ItemVariant variant) { return access.accepts(variant); }
    public int amount() { return (initial != null ? initial : checkedAmount()) + delta; }
    private int checkedAmount() {
        int value = access.amount();
        if (value < 0 || value > access.capacity()) throw new IllegalStateException("Invalid machine fuel buffer: " + value);
        return value;
    }
    /** Register before the production participant so fuel is committed before STARTED observers run. */
    public void prepare(TransactionContext transaction) {
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (initial != null) return;
        initial = checkedAmount();
        baseline = Objects.requireNonNull(access.captureState(), "fuel state");
        TransferTransactionGuards.register(transaction, () -> {
            if (initial != null && (access.amount() != initial || !access.matchesState(baseline))) {
                throw new TransferTransactionGuards.Conflict("Machine fuel changed outside its open transaction");
            }
        });
    }
    public long insert(ItemVariant variant, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, amount);
        if (!accepts(variant) || amount == 0) return 0;
        int count = (int) Math.min(amount, Math.max(0, access.capacity() - amount()));
        if (count == 0) return 0;
        prepare(transaction);
        delta += count;
        return count;
    }
    public boolean consume(int amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Negative fuel consumption");
        if (amount() < amount) return false;
        if (amount == 0) return true;
        prepare(transaction);
        delta -= amount;
        return true;
    }
    @Override protected State createSnapshot() { return new State(initial, baseline, delta); }
    @Override protected void readSnapshot(State state) { initial = state.initial; baseline = state.baseline; delta = state.delta; }
    /** Idempotent dependency flush, valid only after the outer transaction committed. */
    public void commitBeforeProduction() {
        if (Transaction.getLifecycle() != Transaction.Lifecycle.OUTER_CLOSING) {
            throw new IllegalStateException("Fuel may only be applied from an outer final-commit callback");
        }
        applyPending();
    }
    @Override protected void onFinalCommit() { applyPending(); }
    private void applyPending() {
        Integer previous = initial;
        int change = delta;
        initial = null; baseline = null; delta = 0;
        if (previous != null && change != 0) access.commit(previous + change);
    }
    protected record State(Integer initial, Object baseline, int delta) {}
}
