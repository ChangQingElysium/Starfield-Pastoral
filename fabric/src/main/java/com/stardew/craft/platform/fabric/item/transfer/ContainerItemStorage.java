package com.stardew.craft.platform.fabric.item.transfer;

import com.google.common.collect.MapMaker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * FAPI slot transactions with commit-only Container writes. Project chest
 * setItem methods send packets and dirty shared SavedData, so they cannot be
 * called by a speculative slot write or rollback.
 */
public final class ContainerItemStorage extends CombinedSlottedStorage<ItemVariant, SingleStackStorage> {
    private static final Map<Object, ContainerItemStorage> STORAGES =
            new MapMaker().weakKeys().weakValues().makeMap();
    private Container container;

    private ContainerItemStorage(Container container) {
        super(List.of());
        this.container = container;
        resize();
    }

    public static ContainerItemStorage of(Container container) {
        Object identity = container instanceof ContainerStorageIdentity shared
                ? shared.stardewcraft$storageIdentity() : container;
        ContainerItemStorage storage = STORAGES.computeIfAbsent(identity, ignored -> new ContainerItemStorage(container));
        // A shared Junimo inventory may be reached through another, still loaded chest.
        storage.container = container;
        storage.resize();
        return storage;
    }

    private void resize() {
        if (parts.size() == container.getContainerSize()) return;
        List<SingleStackStorage> slots = new ArrayList<>();
        for (int index = 0; index < container.getContainerSize(); index++) slots.add(new Slot(index));
        parts = List.copyOf(slots);
    }

    private boolean available() {
        return !(container instanceof BlockEntity blockEntity) || !blockEntity.isRemoved();
    }

    private final class Slot extends SingleStackStorage {
        private final int index;
        private ItemStack staged;
        private final SnapshotParticipant<Snapshot> changes = new SnapshotParticipant<>() {
            @Override protected Snapshot createSnapshot() {
                return new Snapshot(staged != null, staged == null ? ItemStack.EMPTY : staged.copy());
            }
            @Override protected void readSnapshot(Snapshot snapshot) {
                staged = snapshot.present ? snapshot.stack.copy() : null;
            }
            @Override protected void onFinalCommit() {
                ItemStack committed = staged;
                staged = null;
                if (committed != null) {
                    container.setItem(index, committed);
                    container.setChanged();
                }
            }
        };

        private Slot(int index) { this.index = index; }
        @Override protected ItemStack getStack() { return staged == null ? container.getItem(index) : staged; }
        @Override protected void setStack(ItemStack stack) { staged = stack; }
        @Override protected boolean canInsert(ItemVariant variant) {
            return available() && container.canPlaceItem(index, variant.toStack());
        }
        @Override protected boolean canExtract(ItemVariant variant) { return available(); }
        @Override protected int getCapacity(ItemVariant variant) {
            return Math.min(container.getMaxStackSize(), super.getCapacity(variant));
        }
        @Override public void updateSnapshots(TransactionContext transaction) {
            TransferTransactionGuards.checkCanReserve(transaction);
            changes.updateSnapshots(transaction);
            if (staged == null) staged = container.getItem(index).copy();
        }
    }

    private record Snapshot(boolean present, ItemStack stack) {}
}
