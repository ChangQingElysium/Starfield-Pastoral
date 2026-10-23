package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;

import java.util.Optional;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExternalOutputItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void pureReadsAndAbortedCollectionDoNotDirtyTheExternalRecordOrUpdateBlocks() {
        var pond = new Pond();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(10, pond.storage.getSlot(1).getAmount());
            assertEquals(6, pond.storage.extract(item(Items.COD), 6, transaction));
            assertEquals(4, pond.storage.getSlot(1).getAmount());
            assertEquals(10, pond.active.output.getCount());
            assertEquals(0, pond.dirty + pond.blockWrites + pond.packets);
        }
        assertEquals(10, pond.active.output.getCount());
        assertEquals(0, pond.dirty + pond.blockWrites + pond.packets);
    }

    @Test
    void twoPondRecordsCanCommitPartialAndFullCollectionsTogether() {
        var first = new Pond();
        var second = new Pond();
        assertFalse(first.storage.supportsInsertion());
        assertFalse(first.storage.getSlot(0).supportsInsertion());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(6, first.storage.extract(item(Items.COD), 6, transaction));
            assertEquals(10, second.storage.extract(item(Items.COD), Long.MAX_VALUE, transaction));
            assertEquals(0, first.storage.insert(item(Items.DIAMOND), 1, transaction));
            commit(transaction);
        }
        assertEquals(4, first.active.output.getCount());
        assertTrue(second.active.output.isEmpty());
        assertEquals(1, first.dirty);
        assertEquals(1, second.dirty);
    }

    @Test
    void recordReplacementAbortsAndRestoresTheDestinationBeforeAnySavedDataWrite() {
        var pond = new Pond();
        var destination = new SimpleContainer(1);
        var to = ContainerItemStorage.of(destination);
        Record previous = pond.active;
        try (var transaction = Transaction.openOuter()) {
            assertEquals(5, pond.storage.extract(item(Items.COD), 5, transaction));
            assertEquals(5, to.insert(item(Items.COD), 5, transaction));
            pond.active = new Record();
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertEquals(10, previous.output.getCount());
        assertEquals(10, pond.active.output.getCount());
        assertTrue(destination.isEmpty());
        assertEquals(0, pond.dirty + pond.blockWrites + pond.packets);
    }

    @Test
    void outputComponentsAndNestedCollectionReservationsArePreserved() {
        var pond = new Pond();
        pond.active.output.set(DataComponents.CUSTOM_NAME, Component.literal("Flavored roe fixture"));
        var roe = ItemVariant.of(pond.active.output);
        try (var outer = Transaction.openOuter()) {
            assertEquals(0, pond.storage.extract(item(Items.COD), 10, outer));
            assertEquals(3, pond.storage.extract(roe, 3, outer));
            try (var inner = outer.openNested()) {
                assertEquals(4, pond.storage.extract(roe, 4, inner));
            }
            assertEquals(7, pond.storage.getSlot(1).getAmount());
            commit(outer);
        }
        assertEquals(7, pond.active.output.getCount());
        assertTrue(roe.matches(pond.active.output));
        assertEquals(1, pond.dirty);
    }

    @Test
    void changedEnvironmentFlavorRejectsBeforeCollectionAndKeepsTheExternalEdit() {
        var pond = new Pond();
        pond.active.output.set(DataComponents.CUSTOM_NAME, Component.literal("Initial flavor"));
        var promised = ItemVariant.of(pond.active.output);
        try (var outer = Transaction.openOuter()) {
            assertEquals(4, pond.storage.extract(promised, 4, outer));
            pond.active.output.set(DataComponents.CUSTOM_NAME, Component.literal("Changed flavor"));
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(10, pond.active.output.getCount());
        assertEquals("Changed flavor", pond.active.output.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(0, pond.dirty + pond.blockWrites + pond.packets);
    }

    private static final class Record { private ItemStack output = new ItemStack(Items.COD, 10); }
    /** The platform adapter's external-record contract, with observable persistence/visual writes. */
    private static final class Pond implements PlannedItemMachine {
        private Record active = new Record();
        private int dirty, blockWrites, packets;
        private final PlannedMachineItemStorage storage = OutputOnlyItemStorage.of(
                this::output, this::captureState, this::matchesState, count -> {
                    ItemStack collected = output().copyWithCount(count);
                    commitExtraction(count);
                    return collected;
                });
        @Override public ItemStack input() { return ItemStack.EMPTY; }
        @Override public ItemStack output() { return active.output.copy(); }
        @Override public Object captureState() { return new State(active, output()); }
        @Override public boolean matchesState(Object snapshot) {
            return snapshot instanceof State state && state.record == active && ItemStack.matches(state.output, active.output);
        }
        @Override public Optional<Insertion> planInsertion(ItemStack input, boolean collecting, TransactionContext transaction) { return Optional.empty(); }
        @Override public void commitExtraction(int count) { active.output.shrink(count); dirty++; blockWrites++; packets++; }
        private record State(Record record, ItemStack output) {}
    }
}
