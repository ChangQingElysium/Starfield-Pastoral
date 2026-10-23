package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RestartingMachineItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void twoProducersOnlyRestartOnCommitAndTheListenerDrawPrecedesTheNextCycleDraw() {
        var random = new LegacyRandomSource(71);
        var expected = new LegacyRandomSource(71);
        var first = new Producer(random);
        var second = new Producer(random);
        try (var outer = Transaction.openOuter()) {
            assertEquals(10, first.storage.extract(item(Items.COD), 10, outer));
            assertEquals(10, second.storage.extract(item(Items.COD), 10, outer));
            assertEquals(10, first.product.getCount());
            assertEquals(0, first.writes + second.writes + first.providerCalls + second.providerCalls);
            commit(outer);
        }
        assertEquals(expected.nextInt(31), first.listenerRoll);
        assertEquals(4 + expected.nextInt(2), first.product.getCount());
        assertEquals(expected.nextInt(31), second.listenerRoll);
        assertEquals(4 + expected.nextInt(2), second.product.getCount());
        assertEquals(expected.nextInt(), random.nextInt());
        assertFalse(first.ready);
        assertFalse(second.ready);
        assertEquals(1, first.providerCalls);
        assertEquals(1, second.providerCalls);
        assertEquals(1, first.collectedEvents);
        assertEquals(1, second.startedEvents);
    }

    @Test
    void abortDoesNotClearOutputRestartCyclesEmitEventsResolveProvidersOrAdvanceRng() {
        var random = new LegacyRandomSource(72);
        var expected = new LegacyRandomSource(72);
        var machine = new Producer(random);
        try (var outer = Transaction.openOuter()) {
            assertEquals(10, machine.storage.extract(item(Items.COD), Long.MAX_VALUE, outer));
        }
        assertTrue(machine.ready);
        assertEquals(10, machine.product.getCount());
        assertEquals(0, machine.collectedEvents + machine.startedEvents + machine.writes + machine.providerCalls);
        assertEquals(expected.nextInt(), random.nextInt());
    }

    @Test
    void partialCollectionDoesNotRestartAndFinalCollectionPreservesTheLastEventAmount() {
        var machine = new Producer(new LegacyRandomSource(73));
        try (var outer = Transaction.openOuter()) {
            assertEquals(3, machine.storage.extract(item(Items.COD), 3, outer));
            assertEquals(0, machine.providerCalls);
            assertEquals(7, machine.storage.extract(item(Items.COD), 7, outer));
            assertEquals(0, machine.providerCalls);
            commit(outer);
        }
        assertEquals(List.of(3, 7), machine.steps);
        assertEquals(7, machine.collectedCount);
        assertEquals(1, machine.startedEvents);
        assertEquals(1, machine.providerCalls);
    }

    @Test
    void nestedAbortCancelsOnlyItsProducerAndDoesNotSpendSiblingCycleRandomness() {
        var random = new LegacyRandomSource(74);
        var expected = new LegacyRandomSource(74);
        var cancelled = new Producer(random);
        var sibling = new Producer(random);
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) { cancelled.storage.extract(item(Items.COD), 10, inner); }
            sibling.storage.extract(item(Items.COD), 10, outer);
            commit(outer);
        }
        assertEquals(expected.nextInt(31), sibling.listenerRoll);
        assertEquals(4 + expected.nextInt(2), sibling.product.getCount());
        assertEquals(10, cancelled.product.getCount());
        assertEquals(0, cancelled.writes + cancelled.providerCalls);
        assertEquals(expected.nextInt(), random.nextInt());
    }

    @Test
    void restartVetoStillCollectsCurrentOutputAndRetainsTheOriginalRetryPause() {
        var random = new LegacyRandomSource(75);
        var expected = new LegacyRandomSource(75);
        var machine = new Producer(random);
        machine.veto = true;
        try (var outer = Transaction.openOuter()) {
            assertEquals(10, machine.storage.extract(item(Items.COD), 10, outer));
            commit(outer);
        }
        assertEquals(expected.nextInt(31), machine.listenerRoll);
        expected.nextInt(2); // Original startCycle rolls its output before the provider can veto.
        assertTrue(machine.product.isEmpty());
        assertFalse(machine.ready);
        assertEquals(1600, machine.retryDelay);
        assertEquals(1, machine.collectedEvents);
        assertEquals(0, machine.startedEvents);
        assertEquals(1, machine.providerCalls);
        assertEquals(expected.nextInt(), random.nextInt());
    }

    @Test
    void existingOutputDoesNotReserveFutureRngAndOrdinaryDrawsAreNeverRewound() {
        var random = new LegacyRandomSource(76);
        var expected = new LegacyRandomSource(76);
        var machine = new Producer(random);
        try (var outer = Transaction.openOuter()) {
            machine.storage.extract(item(Items.COD), 10, outer);
            assertEquals(expected.nextInt(), random.nextInt());
            commit(outer);
        }
        assertEquals(expected.nextInt(31), machine.listenerRoll);
        assertEquals(4 + expected.nextInt(2), machine.product.getCount());
        assertEquals(expected.nextInt(), random.nextInt());
    }

    @Test
    void stagedInputRecipeRollsCommitBeforeTheNaturalCollectedListenerAndNewCycleDraws() throws ReflectiveOperationException {
        var random = new LegacyRandomSource(77);
        var expected = new LegacyRandomSource(77);
        var machine = new Producer(random);
        try (var outer = Transaction.openOuter()) {
            machine.storage.extract(item(Items.COD), 10, outer);
            int selectedInputCount = TransactionalRandom.nextInt(TransactionalRandomTest.seed(random), 999, outer);
            assertEquals(expected.nextInt(999), selectedInputCount);
            commit(outer);
        }
        assertEquals(expected.nextInt(31), machine.listenerRoll);
        assertEquals(4 + expected.nextInt(2), machine.product.getCount());
        assertEquals(expected.nextInt(), random.nextInt());
    }

    private static final class Producer {
        private final LegacyRandomSource random;
        private final PlannedMachineItemStorage storage;
        private ItemStack product = new ItemStack(Items.COD, 10);
        private boolean ready = true, veto;
        private int version, providerCalls, collectedEvents, startedEvents, writes, collectedCount, retryDelay, listenerRoll;
        private final List<Integer> steps = new ArrayList<>();
        private Producer(LegacyRandomSource random) {
            this.random = random;
            storage = OutputOnlyItemStorage.of(() -> ready ? product : ItemStack.EMPTY,
                    () -> version, state -> state.equals(version), this::collect);
        }
        private ItemStack collect(int count) {
            ItemStack result = product.copyWithCount(count);
            steps.add(count); product.shrink(count); version++; writes++;
            if (product.isEmpty()) {
                collectedEvents++; collectedCount = count;
                listenerRoll = random.nextInt(31); // An actual COLLECTED observer may use the same source.
                ready = false;
                int selected = 4 + random.nextInt(2);
                assertTrue(product.isEmpty(), "Restart provider must observe the collected machine");
                providerCalls++;
                if (veto) { retryDelay = 1600; }
                else { product = new ItemStack(Items.COD, selected); startedEvents++; }
                writes++;
            }
            return result;
        }
    }
}
