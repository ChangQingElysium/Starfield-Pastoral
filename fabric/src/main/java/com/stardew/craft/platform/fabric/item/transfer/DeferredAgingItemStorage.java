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
import net.minecraft.world.item.ItemStack;

/** Cask state reservations, including immediately collectible silver/gold inputs. */
public final class DeferredAgingItemStorage extends SnapshotParticipant<DeferredAgingItemStorage.Snapshot>
        implements SlottedStorage<ItemVariant> {
    public record Aging(ItemStack product, boolean ready, float days, float rate, int lastDay) {
        public Aging { product = product.copy(); }
        @Override public ItemStack product() { return product.copy(); }
        private boolean matches(Aging other) {
            return ItemStack.matches(product, other.product) && ready == other.ready
                    && Float.compare(days, other.days) == 0 && Float.compare(rate, other.rate) == 0 && lastDay == other.lastDay;
        }
    }
    public record Start(Aging state, BooleanSupplier stillValid) {
        public Start { Objects.requireNonNull(state); Objects.requireNonNull(stillValid); }
    }
    public interface Cask {
        boolean available();
        Aging read();
        Object captureContext();
        boolean matchesContext(Object state);
        Optional<Start> prepare(ItemStack input);
        void start(Aging state);
        ItemStack collect(int count);
    }
    private record Action(BooleanSupplier valid, Runnable commit) {}
    private record Baseline(Aging state, Object context) {}
    private final Cask cask;
    private final List<SingleSlotStorage<ItemVariant>> slots = List.of(new Slot(0), new Slot(1));
    private Aging staged;
    private Baseline baseline;
    private List<Action> actions = new ArrayList<>();

    public DeferredAgingItemStorage(Cask cask) { this.cask = Objects.requireNonNull(cask); }
    private Aging state() { return staged == null ? cask.read() : staged; }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !cask.available() || !state().product.isEmpty()) return 0;
        var plan = cask.prepare(resource.toStack());
        if (plan.isEmpty()) return 0;
        Start start = plan.get();
        if (start.state.product.isEmpty() || start.state.product.getCount() != 1) {
            throw new IllegalStateException("Cask returned an invalid one-item aging plan");
        }
        reserve(transaction);
        staged = start.state;
        actions.add(new Action(start.stillValid, () -> cask.start(start.state)));
        return 1;
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        Aging current = state();
        if (amount == 0 || !cask.available() || !current.ready || !resource.matches(current.product)) return 0;
        int count = (int) Math.min(amount, current.product.getCount());
        if (count == 0) return 0;
        ItemStack expected = current.product.copyWithCount(count);
        ItemStack remainder = current.product.copyWithCount(current.product.getCount() - count);
        reserve(transaction);
        staged = remainder.isEmpty() ? new Aging(ItemStack.EMPTY, false, -1, 1, current.lastDay)
                : new Aging(remainder, current.ready, current.days, current.rate, current.lastDay);
        actions.add(new Action(() -> true, () -> {
            if (!ItemStack.matches(expected, cask.collect(count))) {
                throw new IllegalStateException("Cask violated its committed output reservation");
            }
        }));
        return count;
    }
    private void reserve(TransactionContext transaction) {
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (baseline != null) return;
        Baseline expected = new Baseline(cask.read(), Objects.requireNonNull(cask.captureContext()));
        baseline = expected;
        TransferTransactionGuards.register(transaction, () -> {
            if (baseline != expected) return;
            if (!cask.available() || !expected.state.matches(cask.read()) || !cask.matchesContext(expected.context)
                    || actions.stream().anyMatch(action -> !action.valid.getAsBoolean())) {
                throw new TransferTransactionGuards.Conflict("Cask, aging rules or day changed outside its open transaction");
            }
        });
    }
    @Override public int getSlotCount() { return 2; }
    @Override public SingleSlotStorage<ItemVariant> getSlot(int index) { return slots.get(index); }
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        return List.<StorageView<ItemVariant>>of(slots.get(0), slots.get(1)).iterator();
    }
    @Override protected Snapshot createSnapshot() { return new Snapshot(staged, baseline, List.copyOf(actions)); }
    @Override protected void readSnapshot(Snapshot snapshot) {
        staged = snapshot.staged; baseline = snapshot.baseline; actions = new ArrayList<>(snapshot.actions);
    }
    @Override protected void onFinalCommit() {
        var committed = actions;
        staged = null; baseline = null; actions = new ArrayList<>();
        for (Action action : committed) action.commit.run();
    }
    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int index;
        private Slot(int index) { this.index = index; }
        private ItemStack stack() {
            if (!cask.available()) return ItemStack.EMPTY;
            Aging current = state();
            return current.ready == (index == 1) ? current.product : ItemStack.EMPTY;
        }
        @Override public boolean supportsInsertion() { return index == 0; }
        @Override public boolean supportsExtraction() { return index == 1; }
        @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return index == 0 ? DeferredAgingItemStorage.this.insert(resource, amount, transaction) : 0;
        }
        @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, amount);
            return index == 1 ? DeferredAgingItemStorage.this.extract(resource, amount, transaction) : 0;
        }
        @Override public boolean isResourceBlank() { return stack().isEmpty(); }
        @Override public ItemVariant getResource() { return ItemVariant.of(stack()); }
        @Override public long getAmount() { return stack().getCount(); }
        @Override public long getCapacity() { return index == 0 ? 1 : Math.max(1, stack().getMaxStackSize()); }
    }
    protected record Snapshot(Aging staged, Baseline baseline, List<Action> actions) {}
}
