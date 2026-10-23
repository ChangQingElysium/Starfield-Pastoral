package com.stardew.craft.platform.fabric.fluid.transfer;

import com.stardew.craft.platform.fabric.fluid.FluidStack;
import com.stardew.craft.platform.fabric.fluid.IFluidHandler;
import com.stardew.craft.platform.fabric.item.transfer.TransferTransactionGuards;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.material.Fluids;

/**
 * The garden pot's insert-only watering transaction. Reservations are visible
 * to Transfer; water(), block writes, packets and dirty marks happen on commit.
 * Its gameplay reservoir stores whole millibuckets, so fractional mB stay at
 * the caller rather than being truncated and lost.
 */
public final class GardenPotWaterStorage extends SnapshotParticipant<GardenPotWaterStorage.Reservation>
        implements SingleSlotStorage<FluidVariant> {
    public static final long DROPLETS_PER_MILLIBUCKET = FluidConstants.BUCKET / 1000;
    private final IFluidHandler handler;
    private final Supplier<Object> captureContext;
    private final Predicate<Object> matchesContext;
    private int pendingMillibuckets;
    private FluidVariant acceptedVariant;
    private Baseline baseline;

    public GardenPotWaterStorage(IFluidHandler handler) {
        this(handler, () -> handler, previous -> previous == handler);
    }
    public GardenPotWaterStorage(IFluidHandler handler, Supplier<Object> captureContext, Predicate<Object> matchesContext) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.captureContext = Objects.requireNonNull(captureContext, "captureContext");
        this.matchesContext = Objects.requireNonNull(matchesContext, "matchesContext");
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        int requested = (int) Math.min(Integer.MAX_VALUE, maxAmount / DROPLETS_PER_MILLIBUCKET);
        if (requested == 0) return 0;
        int available = handler.fill(stack(resource, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
        int accepted = Math.min(requested, Math.max(0, available - pendingMillibuckets));
        if (accepted == 0) return 0;
        TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
        if (baseline == null) {
            FluidStack fluid = handler.getFluidInTank(0);
            Baseline expected = new Baseline(captureContext.get(), available, handler.getTankCapacity(0), fluid);
            baseline = expected;
            TransferTransactionGuards.register(transaction, () -> {
                if (baseline != expected) return;
                FluidStack actual = handler.getFluidInTank(0);
                if (!matchesContext.test(expected.context) || expected.capacity != handler.getTankCapacity(0)
                        || expected.available != handler.fill(stack(resource, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE)
                        || actual.getFluid() != expected.fluid.getFluid() || actual.getAmount() != expected.fluid.getAmount()
                        || !actual.getComponentsPatch().equals(expected.fluid.getComponentsPatch())) {
                    throw new TransferTransactionGuards.Conflict("Garden pot or moisture changed outside its open fluid transaction");
                }
            });
        }
        pendingMillibuckets += accepted;
        if (acceptedVariant == null) acceptedVariant = resource;
        return accepted * DROPLETS_PER_MILLIBUCKET;
    }

    @Override public boolean supportsExtraction() { return false; }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        return 0; // The original WaterHandler deliberately has no drain operation.
    }

    @Override public boolean isResourceBlank() { return getAmount() == 0; }
    @Override public FluidVariant getResource() {
        // WaterHandler normalizes all accepted WATER-tag fluids into ordinary water.
        return isResourceBlank() ? FluidVariant.blank() : FluidVariant.of(Fluids.WATER);
    }

    @Override
    public long getAmount() {
        long water = (long) handler.getFluidInTank(0).getAmount() + pendingMillibuckets;
        return water >= handler.getTankCapacity(0) ? 0 : water * DROPLETS_PER_MILLIBUCKET;
    }

    @Override public long getCapacity() { return (long) handler.getTankCapacity(0) * DROPLETS_PER_MILLIBUCKET; }
    @Override protected Reservation createSnapshot() { return new Reservation(pendingMillibuckets, acceptedVariant, baseline); }
    @Override protected void readSnapshot(Reservation snapshot) {
        pendingMillibuckets = snapshot.millibuckets;
        acceptedVariant = snapshot.variant;
        baseline = snapshot.baseline;
    }

    @Override
    protected void onFinalCommit() {
        int amount = pendingMillibuckets;
        FluidVariant resource = acceptedVariant;
        pendingMillibuckets = 0;
        acceptedVariant = null;
        baseline = null;
        if (amount == 0) return;
        int filled = handler.fill(stack(resource, amount), IFluidHandler.FluidAction.EXECUTE);
        if (filled != amount) {
            throw new IllegalStateException("Garden pot changed outside its open fluid transaction: reserved "
                    + amount + " mB, accepted " + filled + " mB");
        }
    }

    private static FluidStack stack(FluidVariant variant, int amount) {
        return new FluidStack(variant.getFluid(), amount, variant.getComponents());
    }

    private record Baseline(Object context, int available, int capacity, FluidStack fluid) {}
    protected record Reservation(int millibuckets, FluidVariant variant, Baseline baseline) {}
}
