package com.stardew.craft.platform.fabric.fluid.transfer;

import com.stardew.craft.platform.fabric.fluid.IFluidHandler;
import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

public final class FluidHandlerStorage {
    private FluidHandlerStorage() {}

    public static Storage<FluidVariant> of(IFluidHandler handler) {
        if (handler == null) return Storage.empty();
        if (handler instanceof TransactionalFluidHandler transactional) return transactional.stardewcraft$fluidStorage();
        return new PendingFluidStorage(handler);
    }

    private record PendingFluidStorage(IFluidHandler handler) implements Storage<FluidVariant> {
        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Fluid handler requires a transactional watering plan: " + handler.getClass().getName());
        }
        @Override public long insert(FluidVariant resource, long amount, TransactionContext transaction) { throw unsupported(); }
        @Override public long extract(FluidVariant resource, long amount, TransactionContext transaction) { throw unsupported(); }
        @Override public Iterator<StorageView<FluidVariant>> iterator() { throw unsupported(); }
    }
}
