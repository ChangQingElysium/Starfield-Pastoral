package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FuelledMachineItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void multipleCoalInputsAndOneHeavyBatchCanAbortOrCommitTogether() throws ReflectiveOperationException {
        var machine = new Machine(false, 3);
        long initial = machine.seed.get();
        for (boolean apply : new boolean[] {false, true}) {
            try (var outer = Transaction.openOuter()) {
                assertEquals(2, machine.storage.insert(item(Items.COAL), 2, outer));
                assertEquals(2, machine.storage.insert(item(Items.COAL), 2, outer));
                assertEquals(5, machine.storage.insert(item(Items.IRON_ORE), 5, outer));
                assertEquals(1, machine.buffer.amount());
                assertEquals(0, machine.coal);
                assertEquals(0, machine.fuelWrites + machine.productionWrites);
                assertEquals(initial, machine.seed.get());
                if (apply) commit(outer);
            }
            assertEquals(apply ? 1 : 0, machine.coal);
            assertEquals(apply ? 1 : 0, machine.productionWrites);
        }
        assertEquals(5, machine.input.getCount());
        assertEquals(1, machine.startedFuel);
        assertEquals(2, machine.providerCalls); // One plan per attempt, never rerun at commit.
    }

    @Test
    void fuelCapacityAndTheOriginalNoFuelExtractionRuleArePreserved() throws ReflectiveOperationException {
        var machine = new Machine(false, 1);
        machine.coal = 62;
        try (var outer = Transaction.openOuter()) {
            assertEquals(2, machine.storage.insert(item(Items.COAL), Long.MAX_VALUE, outer));
            assertEquals(0, machine.storage.insert(item(Items.COAL), 1, outer));
            assertEquals(0, machine.storage.extract(item(Items.COAL), 64, outer));
            commit(outer);
        }
        assertEquals(64, machine.coal);
        assertEquals(1, machine.fuelWrites);
    }

    @Test
    void nestedAbortRestoresBothFuelAndPreparedProductionToTheOuterReservation() throws ReflectiveOperationException {
        var machine = new Machine(false, 3);
        long initial = machine.seed.get();
        try (var outer = Transaction.openOuter()) {
            machine.storage.insert(item(Items.COAL), 1, outer);
            try (var inner = outer.openNested()) {
                machine.storage.insert(item(Items.COAL), 3, inner);
                machine.storage.insert(item(Items.IRON_ORE), 5, inner);
            }
            assertEquals(1, machine.buffer.amount());
            assertFalse(machine.production.hasPendingInsertion());
            commit(outer);
        }
        assertEquals(1, machine.coal);
        assertTrue(machine.input.isEmpty());
        assertEquals(initial, machine.seed.get());
    }

    @Test
    void providerVetoDoesNotConsumeReservedFuelOrSpendTheRolledInputOutput() throws ReflectiveOperationException {
        var machine = new Machine(false, 3);
        machine.veto = true;
        long initial = machine.seed.get();
        try (var outer = Transaction.openOuter()) {
            machine.storage.insert(item(Items.COAL), 3, outer);
            assertEquals(0, machine.storage.insert(item(Items.IRON_ORE), 5, outer));
            assertEquals(3, machine.buffer.amount());
            commit(outer);
        }
        assertEquals(3, machine.coal);
        assertEquals(0, machine.productionWrites);
        assertEquals(initial, machine.seed.get());
        assertEquals(1, machine.providerCalls);
    }

    @Test
    void externalFuelChangeRejectsBeforeAnyFinalWriteAndKeepsTheExternalEdit() throws ReflectiveOperationException {
        var machine = new Machine(false, 1);
        long initial = machine.seed.get();
        try (var outer = Transaction.openOuter()) {
            machine.storage.insert(item(Items.COAL), 2, outer);
            machine.storage.insert(item(Items.IRON_ORE), 5, outer);
            machine.coal = 1;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(1, machine.coal);
        assertEquals(0, machine.fuelWrites + machine.productionWrites);
        assertEquals(initial, machine.seed.get());
    }

    @Test
    void twoFuelledMachinesParticipateInTheSameAbortOrCommitWithoutAnOperationQuota() throws ReflectiveOperationException {
        var first = new Machine(false, 1);
        var second = new Machine(false, 3);
        for (boolean apply : new boolean[] {false, true}) {
            try (var outer = Transaction.openOuter()) {
                first.storage.insert(item(Items.COAL), 3, outer);
                second.storage.insert(item(Items.COAL), 3, outer);
                assertEquals(5, first.storage.insert(item(Items.IRON_ORE), 5, outer));
                assertEquals(5, second.storage.insert(item(Items.IRON_ORE), 5, outer));
                if (apply) commit(outer);
            }
            assertEquals(apply ? 1 : 0, first.productionWrites);
            assertEquals(apply ? 1 : 0, second.productionWrites);
        }
        assertEquals(2, first.coal);
        assertEquals(0, second.coal);
        assertEquals(2, first.startedFuel);
        assertEquals(0, second.startedFuel);
    }

    @Test
    void onlyTheSmokerVariantAcceptsFuelWhileBusy() throws ReflectiveOperationException {
        for (boolean smoker : new boolean[] {false, true}) {
            var machine = new Machine(smoker, 1);
            machine.working = true;
            machine.product = new ItemStack(Items.GOLD_INGOT, 2);
            try (var outer = Transaction.openOuter()) {
                assertEquals(smoker ? 4 : 0, machine.storage.insert(item(Items.COAL), 4, outer));
                commit(outer);
            }
            assertEquals(smoker ? 4 : 0, machine.coal);
            assertEquals(0, machine.productionWrites);
        }
    }

    @Test
    void ordinaryFurnaceRejectsMoreFuelAfterItsVirtualCycleStartsButSmokerKeepsAccepting() throws ReflectiveOperationException {
        for (boolean smoker : new boolean[] {false, true}) {
            var machine = new Machine(smoker, 1);
            machine.coal = 3;
            try (var outer = Transaction.openOuter()) {
                assertEquals(5, machine.storage.insert(item(Items.IRON_ORE), 5, outer));
                assertEquals(smoker ? 2 : 0, machine.storage.insert(item(Items.COAL), 2, outer));
                commit(outer);
            }
            assertEquals(smoker ? 4 : 2, machine.coal);
            assertEquals(machine.coal, machine.startedFuel);
        }
    }

    @Test
    void fullyCollectedOutputAllowsRefuellingAndTheNextBatchInTheSameTransaction() throws ReflectiveOperationException {
        var machine = new Machine(false, 1);
        machine.working = true;
        machine.ready = true;
        machine.input = new ItemStack(Items.IRON_ORE, 5);
        machine.product = new ItemStack(Items.GOLD_INGOT, 3);
        try (var outer = Transaction.openOuter()) {
            assertEquals(3, machine.storage.extract(item(Items.GOLD_INGOT), 3, outer));
            assertEquals(2, machine.storage.insert(item(Items.COAL), 2, outer));
            assertEquals(5, machine.storage.insert(item(Items.IRON_ORE), 5, outer));
            commit(outer);
        }
        assertEquals(1, machine.coal);
        assertEquals(3, machine.collected);
        assertEquals(5, machine.input.getCount());
        assertTrue(machine.working);
    }

    @Test
    void fuelDependencyIsFlushedBeforeStartedObserversAtEveryNativeNestingDepth() throws ReflectiveOperationException {
        for (int depth : new int[] {0, 1, 2, 3}) {
            var machine = new Machine(false, 1);
            try (var outer = Transaction.openOuter()) {
                var nested = new ArrayList<Transaction>();
                Transaction current = outer;
                for (int i = 0; i < depth; i++) { current = current.openNested(); nested.add(current); }
                try {
                    machine.storage.insert(item(Items.COAL), 4, current);
                    machine.storage.insert(item(Items.IRON_ORE), 5, current);
                    for (int i = nested.size() - 1; i >= 0; i--) commit(nested.get(i));
                    commit(outer);
                } finally {
                    for (int i = nested.size() - 1; i >= 0; i--) nested.get(i).close();
                }
            }
            assertEquals(3, machine.startedFuel, "nesting depth " + depth);
            assertEquals(3, machine.coal);
            assertEquals(1, machine.productionWrites);
        }
    }

    @Test
    void dependencyCannotWriteFuelOutsideTheOuterFinalCommitLifecycle() throws ReflectiveOperationException {
        var machine = new Machine(false, 1);
        assertThrows(IllegalStateException.class, machine.buffer::commitBeforeProduction);
        try (var outer = Transaction.openOuter()) {
            machine.storage.insert(item(Items.COAL), 2, outer);
            assertThrows(IllegalStateException.class, machine.buffer::commitBeforeProduction);
        }
        assertEquals(0, machine.coal + machine.fuelWrites);
    }

    @Test
    void changedBatchFuelCostAbortsTheEntirePlanBeforeFuelOrRngCommit() throws ReflectiveOperationException {
        var machine = new Machine(false, 3);
        long initial = machine.seed.get();
        try (var outer = Transaction.openOuter()) {
            machine.storage.insert(item(Items.COAL), 3, outer);
            machine.storage.insert(item(Items.IRON_ORE), 5, outer);
            machine.cost = 5;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(0, machine.coal + machine.fuelWrites + machine.productionWrites);
        assertEquals(initial, machine.seed.get());
    }

    private static final class Machine implements PlannedItemMachine {
        private int coal, cost, version, providerCalls, fuelWrites, productionWrites, startedFuel = -1, collected;
        private boolean working, ready, veto;
        private ItemStack input = ItemStack.EMPTY, product = ItemStack.EMPTY;
        private final AtomicLong seed;
        private final DeferredFuelBuffer buffer;
        private final PlannedMachineItemStorage production;
        private final FuelledMachineItemStorage storage;
        private Machine(boolean smoker, int cost) throws ReflectiveOperationException {
            this.cost = cost;
            seed = TransactionalRandomTest.seed(new LegacyRandomSource(51));
            buffer = new DeferredFuelBuffer(new DeferredFuelBuffer.Access() {
                @Override public int amount() { return coal; }
                @Override public int capacity() { return 64; }
                @Override public boolean accepts(ItemVariant item) { return item.isOf(Items.COAL); }
                @Override public Object captureState() { return version; }
                @Override public boolean matchesState(Object snapshot) { return snapshot.equals(version); }
                @Override public void commit(int amount) { coal = amount; fuelWrites++; }
            });
            production = new PlannedMachineItemStorage(this).withCommitDependency(buffer::commitBeforeProduction);
            storage = new FuelledMachineItemStorage(buffer, production, () -> smoker ||
                    (!production.hasPendingInsertion() && (!working || production.hasCollectedAllOutput())));
        }
        @Override public ItemStack input() { return input; }
        @Override public ItemStack output() { return ready ? product : ItemStack.EMPTY; }
        @Override public Object captureState() { return version; }
        @Override public boolean matchesState(Object state) { return state.equals(version); }
        @Override public Optional<Insertion> planInsertion(ItemStack offered, boolean collecting, TransactionContext transaction) {
            if ((!collecting && working) || !offered.is(Items.IRON_ORE) || offered.getCount() < 5 || buffer.amount() < cost) return Optional.empty();
            providerCalls++;
            TransactionalRandom.nextInt(seed, 5, transaction);
            if (veto) return Optional.empty();
            int fuelCost = cost;
            if (!buffer.consume(fuelCost, transaction)) throw new IllegalStateException("Fixture fuel disappeared");
            return Optional.of(new Insertion(offered.copyWithCount(5), 5, () -> cost == fuelCost && !veto, () -> {
                startedFuel = coal; input = offered.copyWithCount(5); working = true; ready = false;
                product = new ItemStack(Items.GOLD_INGOT, 1); productionWrites++; version++;
            }));
        }
        @Override public void commitExtraction(int count) {
            product.shrink(count); collected += count; version++;
            if (product.isEmpty()) { ready = false; working = false; input = ItemStack.EMPTY; }
        }
    }
}
