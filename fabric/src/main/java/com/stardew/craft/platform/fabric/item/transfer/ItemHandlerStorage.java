package com.stardew.craft.platform.fabric.item.transfer;

import com.stardew.craft.platform.fabric.item.IItemHandler;
import com.stardew.craft.platform.fabric.item.wrapper.InvWrapper;
import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public final class ItemHandlerStorage {
    private ItemHandlerStorage() {}

    public static Storage<ItemVariant> of(IItemHandler handler) {
        if (handler == null || handler.getSlots() == 0) return Storage.empty();
        if (handler instanceof TransactionalItemHandler transactional) return transactional.stardewcraft$itemStorage();
        if (handler.getClass() == InvWrapper.class) {
            return ContainerItemStorage.of(((InvWrapper) handler).getInv());
        }
        return unplanned(handler);
    }

    public static Storage<ItemVariant> unplanned(IItemHandler handler) {
        return new PendingMachineStorage(handler);
    }

    /** An explicit incomplete port boundary, never a silent empty machine inventory. */
    private record PendingMachineStorage(IItemHandler handler) implements Storage<ItemVariant> {
        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Machine requires a transactional item plan: " + handler.getClass().getName());
        }
        @Override public long insert(ItemVariant resource, long amount, TransactionContext transaction) { throw unsupported(); }
        @Override public long extract(ItemVariant resource, long amount, TransactionContext transaction) { throw unsupported(); }
        @Override public Iterator<StorageView<ItemVariant>> iterator() { throw unsupported(); }
    }
}
