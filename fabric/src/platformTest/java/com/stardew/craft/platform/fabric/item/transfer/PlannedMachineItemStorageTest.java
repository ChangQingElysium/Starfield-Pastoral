package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlannedMachineItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void twoMachinesCanAbortOrCommitWithOneSharedRollStreamAndNoWorldWritesBeforeCommit() throws ReflectiveOperationException {
        var nativeRandom = new LegacyRandomSource(101);
        var reference = new LegacyRandomSource(101);
        AtomicLong seed = TransactionalRandomTest.seed(nativeRandom);
        long original = seed.get();
        var first = new Machine(seed);
        var second = new Machine(seed);
        int one = reference.nextInt(999), two = reference.nextInt(999);
        for (boolean apply : new boolean[] {false, true}) {
            try (var transaction = Transaction.openOuter()) {
                assertEquals(1, first.storage.insert(item(Items.DIAMOND), 64, transaction));
                assertEquals(1, second.storage.insert(item(Items.DIAMOND), 64, transaction));
                assertEquals(0, first.storage.insert(item(Items.DIAMOND), 64, transaction));
                assertEquals(0, first.writes + second.writes);
                assertEquals(original, seed.get());
                if (apply) commit(transaction);
            }
            assertEquals(apply ? 1 : 0, first.writes);
            assertEquals(apply ? 1 : 0, second.writes);
        }
        assertEquals(one, first.selected);
        assertEquals(two, second.selected);
        assertEquals(2, first.providerCalls);
        assertEquals(2, second.providerCalls);
        assertEquals(2, first.generatedPlans);
        assertEquals(2, second.generatedPlans);
        assertEquals(1, first.input.getCount());
        assertEquals(seed.get(), TransactionalRandomTest.seed(reference).get());
    }

    @Test
    void recipeVetoRestoresItsRollAndNeverConsumesTheOfferedStack() throws ReflectiveOperationException {
        AtomicLong seed = TransactionalRandomTest.seed(new LegacyRandomSource(13));
        long original = seed.get();
        var denied = new Machine(seed);
        denied.veto = true;
        var accepted = new Machine(seed);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, denied.storage.insert(item(Items.DIAMOND), 1, transaction));
            assertEquals(1, accepted.storage.insert(item(Items.DIAMOND), 1, transaction));
            commit(transaction);
        }
        assertEquals(0, denied.writes);
        assertEquals(1, denied.providerCalls);
        assertEquals(new LegacyRandomSource(original ^ 0x5DEECE66DL).nextInt(999), accepted.selected);
    }

    @Test
    void dynamicWorldChangeRejectsBeforeEitherMachineOrSourceInventoryCommits() throws ReflectiveOperationException {
        AtomicLong seed = TransactionalRandomTest.seed(new LegacyRandomSource(17));
        long initial = seed.get();
        var machine = new Machine(seed);
        var inventory = new SimpleContainer(new ItemStack(Items.DIAMOND, 5));
        var source = ContainerItemStorage.of(inventory);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, source.extract(item(Items.DIAMOND), 1, transaction));
            assertEquals(1, machine.storage.insert(item(Items.DIAMOND), 1, transaction));
            machine.version++;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertEquals(5, inventory.getItem(0).getCount());
        assertEquals(0, machine.writes);
        assertEquals(initial, seed.get());
    }

    @Test
    void changedRecipeVetoAbortsBothMachineAndRandomBeforeCommitWithoutRecallingTheProvider() throws ReflectiveOperationException {
        AtomicLong seed = TransactionalRandomTest.seed(new LegacyRandomSource(18));
        long initial = seed.get();
        var machine = new Machine(seed);
        try (var outer = Transaction.openOuter()) {
            assertEquals(1, machine.storage.insert(item(Items.DIAMOND), 1, outer));
            machine.veto = true;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(1, machine.providerCalls);
        assertEquals(1, machine.generatedPlans);
        assertEquals(0, machine.writes);
        assertEquals(initial, seed.get());
    }

    @Test
    void nestedAbortRestoresMachineAndRandomReservations() throws ReflectiveOperationException {
        AtomicLong seed = TransactionalRandomTest.seed(new LegacyRandomSource(19));
        var machine = new Machine(seed);
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                assertEquals(1, machine.storage.insert(item(Items.DIAMOND), 1, inner));
            }
            assertEquals(0, machine.storage.getSlot(0).getAmount());
            assertEquals(1, machine.storage.insert(item(Items.DIAMOND), 1, outer));
            commit(outer);
        }
        assertEquals(1, machine.writes);
    }

    @Test
    void fullOutputCollectionCanPrecedeNextBatchAndPartialCollectionCannotStartOne() throws ReflectiveOperationException {
        var machine = new Machine(TransactionalRandomTest.seed(new LegacyRandomSource(23)));
        machine.output = new ItemStack(Items.EMERALD, 5);
        try (var outer = Transaction.openOuter()) {
            assertEquals(2, machine.storage.extract(item(Items.EMERALD), 2, outer));
            assertEquals(0, machine.storage.insert(item(Items.DIAMOND), 1, outer));
            assertEquals(3, machine.storage.getSlot(1).getAmount());
            assertEquals(3, machine.storage.extract(item(Items.EMERALD), 64, outer));
            assertEquals(1, machine.storage.insert(item(Items.DIAMOND), 1, outer));
            assertEquals(5, machine.output.getCount());
            commit(outer);
        }
        assertTrue(machine.output.isEmpty());
        assertEquals(5, machine.collected);
        assertEquals(java.util.List.of(2, 3), machine.collectionSteps);
        assertEquals(1, machine.input.getCount());
    }

    private static final class Machine implements PlannedItemMachine {
        private final AtomicLong seed;
        private final PlannedMachineItemStorage storage = new PlannedMachineItemStorage(this);
        private ItemStack input = ItemStack.EMPTY;
        private ItemStack output = ItemStack.EMPTY;
        private int version, providerCalls, generatedPlans, selected = -1, writes, collected;
        private boolean veto;
        private final java.util.List<Integer> collectionSteps = new java.util.ArrayList<>();
        private Machine(AtomicLong seed) { this.seed = seed; }
        @Override public ItemStack input() { return input; }
        @Override public ItemStack output() { return output; }
        @Override public Object captureState() { return version; }
        @Override public boolean matchesState(Object state) { return state.equals(version); }
        @Override public Optional<Insertion> planInsertion(ItemStack offered, boolean collecting, TransactionContext transaction) {
            if (!input.isEmpty() || (!output.isEmpty() && !collecting)) return Optional.empty();
            providerCalls++;
            int roll = TransactionalRandom.nextInt(seed, 999, transaction);
            if (veto) return Optional.empty();
            generatedPlans++;
            return Optional.of(new Insertion(offered.copyWithCount(1), 1, () -> !veto, () -> {
                input = offered.copyWithCount(1); selected = roll; writes++; version++;
            }));
        }
        @Override public void commitExtraction(int count) { output.shrink(count); collected += count; collectionSteps.add(count); version++; }
    }
}
