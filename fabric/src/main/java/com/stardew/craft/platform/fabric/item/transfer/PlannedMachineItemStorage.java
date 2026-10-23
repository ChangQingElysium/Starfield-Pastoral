package com.stardew.craft.platform.fabric.item.transfer;

import java.util.Iterator;
import java.util.ArrayList;
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

/** Two real machine slots, with one whole production batch per available cycle. */
public final class PlannedMachineItemStorage extends SnapshotParticipant<PlannedMachineItemStorage.Reservation>
        implements SlottedStorage<ItemVariant> {
    private final PlannedItemMachine machine;
    private final List<SingleSlotStorage<ItemVariant>> slots = List.of(new Slot(0), new Slot(1));
    private Object baseline;
    private PlannedItemMachine.Insertion insertion;
    private int extracted;
    private Runnable commitDependency;
    private List<Integer> extractionSteps = new ArrayList<>();

    public PlannedMachineItemStorage(PlannedItemMachine machine) { this.machine = Objects.requireNonNull(machine, "machine"); }
    /** Configure once during construction; dependency commits precede all machine events. */
    public PlannedMachineItemStorage withCommitDependency(Runnable dependency) {
        if (commitDependency != null) throw new IllegalStateException("Machine commit dependency already configured");
        commitDependency = Objects.requireNonNull(dependency, "dependency");
        return this;
    }
    public boolean hasPendingInsertion() { return insertion != null; }
    public boolean hasCollectedAllOutput() {
        ItemStack output = machine.output();
        return !output.isEmpty() && extracted == output.getCount();
    }
    @Override public boolean supportsInsertion() { return machine.canInsert(); }
    @Override public int getSlotCount() { return 2; }
    @Override public SingleSlotStorage<ItemVariant> getSlot(int slot) { return slots.get(slot); }
    @Override public long insert(ItemVariant variant, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, amount);
        if (amount == 0 || insertion != null || !machine.canInsert()) return 0;
        int offeredCount = (int) Math.min(amount, Math.min(machine.inputLimit(), ItemVariantImpl.getMaxStackSize(variant)));
        if (offeredCount <= 0) return 0;
        ItemStack output = machine.output();
        boolean collecting = !output.isEmpty() && extracted == output.getCount();
        // Reserve a snapshot before the planner's shared RNG participant. A veto
        // must also abort its nested RNG reservation rather than spend a roll.
        try (var planning = transaction.openNested()) {
            reserve(planning);
            var plan = machine.planInsertion(variant.toStack(offeredCount), collecting, planning);
            if (plan.isEmpty()) return 0;
            var prepared = plan.get();
            if (prepared.consumed() > offeredCount || prepared.trackedInput().getCount() != prepared.consumed()
                    || !variant.matches(prepared.trackedInput())) {
                throw new IllegalStateException("Machine returned an invalid input plan");
            }
            insertion = prepared;
            planning.commit();
            return prepared.consumed();
        }
    }
    @Override public long extract(ItemVariant variant, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, amount);
        if (amount == 0) return 0;
        ItemStack output = machine.output();
        if (!variant.matches(output)) return 0;
        int count = (int) Math.min(amount, Math.max(0, output.getCount() - extracted));
        if (count == 0) return 0;
        reserve(transaction);
        extracted += count;
        extractionSteps.add(count);
        return count;
    }
    private void reserve(TransactionContext transaction) {
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (baseline != null) return;
        baseline = Objects.requireNonNull(machine.captureState(), "machine state");
        TransferTransactionGuards.register(transaction, () -> {
            if (baseline != null && (!machine.matchesState(baseline)
                    || (insertion != null && !insertion.stillValid().getAsBoolean()))) {
                throw new TransferTransactionGuards.Conflict("Machine changed outside its open item transaction");
            }
        });
    }
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        return List.<StorageView<ItemVariant>>of(slots.get(0), slots.get(1)).iterator();
    }
    @Override protected Reservation createSnapshot() { return new Reservation(baseline, insertion, extracted, List.copyOf(extractionSteps)); }
    @Override protected void readSnapshot(Reservation snapshot) {
        baseline = snapshot.baseline; insertion = snapshot.insertion; extracted = snapshot.extracted;
        extractionSteps = new ArrayList<>(snapshot.extractionSteps);
    }
    @Override protected void onFinalCommit() {
        // FAPI callback ordering changes with nested snapshot propagation. Do
        // not depend on another participant being finalized before this one.
        if (commitDependency != null) commitDependency.run();
        var collections = extractionSteps;
        var start = insertion;
        baseline = null; insertion = null; extracted = 0; extractionSteps = new ArrayList<>();
        // Preserve partial-extract and final COLLECTED transition semantics.
        for (int collection : collections) machine.commitExtraction(collection);
        if (start != null) start.commit().run();
    }
    private ItemStack stack(int slot) {
        if (slot == 0) return insertion != null ? insertion.trackedInput() : machine.input();
        ItemStack output = machine.output();
        return output.copyWithCount(Math.max(0, output.getCount() - extracted));
    }
    private final class Slot implements SingleSlotStorage<ItemVariant> {
        private final int slot;
        private Slot(int slot) { this.slot = slot; }
            @Override public boolean supportsInsertion() { return slot == 0 && machine.canInsert(); }
        @Override public boolean supportsExtraction() { return slot == 1; }
        @Override public long insert(ItemVariant variant, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(variant, amount);
            return slot == 0 ? PlannedMachineItemStorage.this.insert(variant, amount, transaction) : 0;
        }
        @Override public long extract(ItemVariant variant, long amount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(variant, amount);
            return slot == 1 ? PlannedMachineItemStorage.this.extract(variant, amount, transaction) : 0;
        }
        @Override public boolean isResourceBlank() { return stack(slot).isEmpty(); }
        @Override public ItemVariant getResource() { return ItemVariant.of(stack(slot)); }
        @Override public long getAmount() { return stack(slot).getCount(); }
        @Override public long getCapacity() {
            ItemStack value = stack(slot);
            return Math.min(slot == 0 ? machine.inputLimit() : 64, value.isEmpty() ? 64 : value.getMaxStackSize());
        }
    }
    protected record Reservation(Object baseline, PlannedItemMachine.Insertion insertion, int extracted, List<Integer> extractionSteps) {}
}
