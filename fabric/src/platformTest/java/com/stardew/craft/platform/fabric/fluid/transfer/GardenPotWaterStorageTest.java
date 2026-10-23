package com.stardew.craft.platform.fabric.fluid.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.fluid;

import com.stardew.craft.platform.fabric.fluid.FluidStack;
import com.stardew.craft.platform.fabric.fluid.IFluidHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GardenPotWaterStorageTest {
    private static final long MB = GardenPotWaterStorage.DROPLETS_PER_MILLIBUCKET;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static void commit(Transaction transaction) {
        com.stardew.craft.platform.fabric.item.transfer.TransferTransactionGuards.beforeCommit(transaction);
        transaction.commit();
    }

    @Test
    void abortedWateringDoesNotTouchTheReservoirMoistureDirtyMarkOrPackets() {
        Pot pot = new Pot();
        var storage = pot.storage;
        try (var transaction = Transaction.openOuter()) {
            assertEquals(100 * MB, storage.insert(fluid(Fluids.WATER), Long.MAX_VALUE, transaction));
            assertEquals(0, storage.getAmount());
            assertEquals(0, pot.water);
            assertFalse(pot.wet);
            assertEquals(0, pot.executions);
            assertEquals(0, pot.dirty);
        }
        assertEquals(0, pot.water);
        assertFalse(pot.wet);
        assertEquals(0, pot.executions);
        assertEquals(0, pot.dirty);
    }

    @Test
    void multipleReservationsCommitOneWateringAndCannotOverfill() {
        Pot pot = new Pot();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(25 * MB, pot.storage.insert(fluid(Fluids.WATER), 25 * MB, transaction));
            assertEquals(75 * MB, pot.storage.insert(fluid(Fluids.WATER), 90 * MB, transaction));
            assertEquals(0, pot.storage.insert(fluid(Fluids.WATER), MB, transaction));
            commit(transaction);
        }
        assertEquals(0, pot.water);
        assertTrue(pot.wet);
        assertEquals(1, pot.executions);
        assertEquals(1, pot.dirty);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, pot.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction));
        }
    }

    @Test
    void partialReservoirPersistsOnlyOnCommitAndCompletesInALaterTransaction() {
        Pot pot = new Pot();
        try (var transaction = Transaction.openOuter()) {
            pot.storage.insert(fluid(Fluids.WATER), 40 * MB, transaction);
            assertEquals(40 * MB, pot.storage.getAmount());
            assertEquals(0, pot.water);
            commit(transaction);
        }
        assertEquals(40, pot.water);
        assertFalse(pot.wet);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(60 * MB, pot.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction));
            commit(transaction);
        }
        assertTrue(pot.wet);
        assertEquals(0, pot.water);
    }

    @Test
    void nestedCommitCanBeAbortedByItsOuterTransaction() {
        Pot pot = new Pot();
        try (var outer = Transaction.openOuter()) {
            pot.storage.insert(fluid(Fluids.WATER), 20 * MB, outer);
            try (var inner = outer.openNested()) {
                pot.storage.insert(fluid(Fluids.WATER), 80 * MB, inner);
                commit(inner);
            }
        }
        assertEquals(0, pot.water);
        assertFalse(pot.wet);
        assertEquals(0, pot.executions);
        assertEquals(0, pot.storage.getAmount());
    }

    @Test
    void twoPotsCanParticipateInOneAbortOrCommitWithoutAnOpaqueOperationQuota() {
        Pot first = new Pot();
        Pot second = new Pot();
        for (boolean commit : new boolean[] {false, true}) {
            try (var transaction = Transaction.openOuter()) {
                assertEquals(100 * MB, first.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction));
                assertEquals(100 * MB, second.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction));
                if (commit) commit(transaction);
            }
            assertEquals(commit, first.wet);
            assertEquals(commit, second.wet);
            assertEquals(commit ? 1 : 0, first.executions);
            assertEquals(commit ? 1 : 0, second.executions);
        }
    }

    @Test
    void fractionalMillibucketsStayWithTheCallerAndUnitConversionDoesNotOverflow() {
        assertEquals(81, MB);
        Pot pot = new Pot();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, pot.storage.insert(fluid(Fluids.WATER), MB - 1, transaction));
            assertEquals(MB, pot.storage.insert(fluid(Fluids.WATER), MB + 1, transaction));
            assertEquals(99 * MB, pot.storage.insert(fluid(Fluids.WATER), Long.MAX_VALUE, transaction));
            assertEquals(100 * MB, pot.storage.getCapacity());
            commit(transaction);
        }
        assertTrue(pot.wet);
    }

    @Test
    void insertionKeepsComponentArgumentsAndThePotStoresCanonicalWater() {
        Pot pot = new Pot();
        DataComponentPatch patch = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Water fixture")).build();
        FluidVariant input = FluidVariant.of(Fluids.WATER, patch);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(30 * MB, pot.storage.insert(input, 30 * MB, transaction));
            commit(transaction);
        }
        assertEquals(patch, pot.lastComponents);
        assertEquals(30, pot.water);
        // getResource() uses FAPI's injected native WATER/EMPTY caches and needs
        // the later headless-server Mixin load gate. This plain-JVM fixture
        // checks the actual committed handler's canonical fluid and components.
        assertSame(Fluids.WATER, pot.getFluidInTank(0).getFluid());
    }

    @Test
    void nonWaterAndExtractionStayRejectedAndTheProviderReturnsTheRealStorage() {
        Pot pot = new Pot();
        assertSame(pot.storage, FluidHandlerStorage.of(pot));
        assertFalse(pot.storage.supportsExtraction());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, pot.storage.insert(fluid(Fluids.LAVA), 100 * MB, transaction));
            assertEquals(0, pot.storage.extract(fluid(Fluids.WATER), 100 * MB, transaction));
        }
        assertEquals(0, pot.executions);
    }

    @Test
    void nativeFluidVariantNormalizesFlowingWaterAndComparesAllComponentChanges() {
        DataComponentPatch first = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Water fixture")).build();
        DataComponentPatch same = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Water fixture")).build();
        DataComponentPatch different = DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("Different water")).build();
        var source = FluidVariant.of(Fluids.WATER, first);
        assertEquals(source, FluidVariant.of(Fluids.FLOWING_WATER, same));
        assertEquals(source.hashCode(), FluidVariant.of(Fluids.WATER, same).hashCode());
        assertNotEquals(source, FluidVariant.of(Fluids.WATER, different));
        assertNotEquals(source, FluidVariant.of(Fluids.LAVA, first));
        assertSame(Fluids.WATER, source.getFluid());
        assertEquals(first, source.getComponents());
        assertEquals(fluid(Fluids.WATER), fluid(Fluids.WATER));
    }

    @Test
    void externalMoistureChangeAbortsBothPotsBeforeAnyWateringOrDirtyMark() {
        Pot first = new Pot(), second = new Pot();
        try (var transaction = Transaction.openOuter()) {
            first.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction);
            second.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction);
            second.wet = true;
            assertThrows(com.stardew.craft.platform.fabric.item.transfer.TransferTransactionGuards.Conflict.class,
                    () -> commit(transaction));
        }
        assertFalse(first.wet); assertTrue(second.wet);
        assertEquals(0, first.water + second.water + first.executions + second.executions + first.dirty + second.dirty);
        assertEquals(0, first.storage.getAmount());
    }

    @Test
    void replacedPotIdentityCannotReceiveAPreviouslyReservedFluid() {
        Pot pot = new Pot();
        try (var transaction = Transaction.openOuter()) {
            pot.storage.insert(fluid(Fluids.WATER), 100 * MB, transaction);
            pot.identity = new Object();
            assertThrows(com.stardew.craft.platform.fabric.item.transfer.TransferTransactionGuards.Conflict.class,
                    () -> commit(transaction));
        }
        assertFalse(pot.wet); assertEquals(0, pot.executions + pot.dirty);
    }

    @Test
    void rolledBackNestedWaterReservationDoesNotHoldAnObsoleteReservoirBaseline() {
        Pot pot = new Pot();
        try (var outer = Transaction.openOuter()) {
            try (var nested = outer.openNested()) { pot.storage.insert(fluid(Fluids.WATER), 20 * MB, nested); }
            pot.water = 40;
            assertEquals(60 * MB, pot.storage.insert(fluid(Fluids.WATER), 60 * MB, outer));
            commit(outer);
        }
        assertTrue(pot.wet); assertEquals(1, pot.executions);
    }

    /** Mirrors the source WaterHandler's 100 mB reservoir and one-way watering rule. */
    private static final class Pot implements IFluidHandler, TransactionalFluidHandler {
        private int water;
        private boolean wet;
        private int executions;
        private int dirty;
        private DataComponentPatch lastComponents;
        private Object identity = new Object();
        private final GardenPotWaterStorage storage = new GardenPotWaterStorage(this, () -> identity, previous -> previous == identity);
        @Override public Storage<FluidVariant> stardewcraft$fluidStorage() { return storage; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return water == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, water); }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? 100 : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && stack.is(Fluids.WATER); }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (wet || resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            int accepted = Math.min(resource.getAmount(), 100 - water);
            if (action.execute() && accepted > 0) {
                executions++;
                dirty++;
                lastComponents = resource.getComponentsPatch();
                water += accepted;
                if (water == 100) { water = 0; wet = true; }
            }
            return accepted;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }
}
