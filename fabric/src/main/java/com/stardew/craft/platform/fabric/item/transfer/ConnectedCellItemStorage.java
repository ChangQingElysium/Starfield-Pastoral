package com.stardew.craft.platform.fabric.item.transfer;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Original feed-trough aggregate slot: one hay inserted per call, many extracted. */
public final class ConnectedCellItemStorage implements SingleSlotStorage<ItemVariant> {
    private final Supplier<List<DeferredItemCellStorage>> connected;
    private final Supplier<ItemVariant> resource;
    public ConnectedCellItemStorage(Supplier<List<DeferredItemCellStorage>> connected, Supplier<ItemVariant> resource) {
        this.connected = Objects.requireNonNull(connected, "connected");
        this.resource = Objects.requireNonNull(resource, "resource");
    }
    private void guardTopology(TransactionContext transaction, List<DeferredItemCellStorage> cells) {
        var snapshot = List.copyOf(cells);
        TransferTransactionGuards.register(transaction, () -> {
            if (!snapshot.equals(connected.get())) {
                throw new TransferTransactionGuards.Conflict("Connected machine topology changed outside its open transaction");
            }
        });
    }
    @Override public long insert(ItemVariant variant, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, amount);
        if (amount == 0) return 0;
        var cells = connected.get();
        for (var cell : cells) {
            if (cell.insert(variant, 1, transaction) > 0) {
                guardTopology(transaction, cells);
                return 1;
            }
        }
        return 0;
    }
    @Override public long extract(ItemVariant variant, long amount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(variant, amount);
        if (amount == 0) return 0;
        var cells = connected.get();
        long count = 0;
        for (var cell : cells) {
            count += cell.extract(variant, amount - count, transaction);
            if (count == amount) break;
        }
        if (count > 0) guardTopology(transaction, cells);
        return count;
    }
    @Override public boolean isResourceBlank() { return getAmount() == 0; }
    @Override public ItemVariant getResource() { return isResourceBlank() ? ItemVariant.blank() : resource.get(); }
    @Override public long getAmount() { return connected.get().stream().mapToLong(DeferredItemCellStorage::getAmount).sum(); }
    @Override public long getCapacity() { return connected.get().stream().mapToLong(DeferredItemCellStorage::getCapacity).sum(); }
}
