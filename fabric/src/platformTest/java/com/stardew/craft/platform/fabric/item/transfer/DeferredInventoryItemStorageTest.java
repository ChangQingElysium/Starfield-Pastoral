package com.stardew.craft.platform.fabric.item.transfer;

import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeferredInventoryItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void pureReadsAndAbortDoNotCallRecoveryOrWriteInventory() {
        var collector = new Collector();
        collector.items.set(1, new ItemStack(Items.EGG, 10));
        try (var transaction = Transaction.openOuter()) {
            assertEquals(10, collector.storage.getSlot(1).getAmount());
            assertEquals(6, collector.storage.extract(item(Items.EGG), 6, transaction));
            assertEquals(4, collector.storage.getSlot(1).getAmount());
            assertEquals(10, collector.items.get(1).getCount());
            assertEquals(0, collector.writes);
        }
        assertEquals(10, collector.items.get(1).getCount());
        assertEquals(0, collector.writes);
    }

    @Test void matchingStacksAreFilledBeforeEarlierEmptySlotsAndComponentsSurvive() {
        var collector = new Collector();
        ItemStack named = new ItemStack(Items.EGG, 10);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named produce"));
        collector.items.set(2, named);
        var variant = ItemVariant.of(named);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(40, collector.storage.insert(variant, 40, transaction));
            commit(transaction);
        }
        assertEquals(16, collector.items.get(2).getCount());
        assertEquals(16, collector.items.get(0).getCount());
        assertEquals(16, collector.items.get(1).getCount());
        assertEquals(2, collector.items.get(3).getCount());
        assertTrue(collector.items.stream().allMatch(variant::matches));
        assertEquals(1, collector.writes);
    }

    @Test void onlyTheFirstOccupiedStackIsExtractablePerCall() {
        var collector = new Collector();
        collector.items.set(0, new ItemStack(Items.EGG, 3));
        collector.items.set(1, new ItemStack(Items.DIAMOND, 2));
        collector.items.set(2, new ItemStack(Items.EGG, 4));
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, collector.storage.extract(item(Items.DIAMOND), 64, transaction));
            assertEquals(3, collector.storage.extract(item(Items.EGG), 64, transaction));
            assertEquals(0, collector.storage.extract(item(Items.EGG), 64, transaction));
            assertEquals(2, collector.storage.extract(item(Items.DIAMOND), 64, transaction));
            assertEquals(4, collector.storage.extract(item(Items.EGG), 64, transaction));
            commit(transaction);
        }
        assertTrue(collector.items.stream().allMatch(ItemStack::isEmpty));
        assertEquals(1, collector.writes);
    }

    @Test void nestedAbortRetainsOuterInsertAndTwoCollectorsCommitTogether() {
        var first = new Collector(); var second = new Collector();
        try (var outer = Transaction.openOuter()) {
            assertEquals(10, first.storage.insert(item(Items.DIAMOND), 10, outer));
            try (var nested = outer.openNested()) {
                assertEquals(5, first.storage.extract(item(Items.DIAMOND), 5, nested));
                assertEquals(20, second.storage.insert(item(Items.DIAMOND), 20, nested));
            }
            assertEquals(10, first.storage.getSlot(1).getAmount());
            assertEquals(0, second.storage.getSlot(1).getAmount());
            assertEquals(30, second.storage.insert(item(Items.DIAMOND), 30, outer));
            commit(outer);
        }
        assertEquals(10, first.items.getFirst().getCount());
        assertEquals(30, second.items.getFirst().getCount());
        assertEquals(1, first.writes); assertEquals(1, second.writes);
    }

    @Test void pendingJournalDisablesTheViewWithoutTryingToRecover() {
        var collector = new Collector();
        collector.items.set(0, new ItemStack(Items.EGG, 4));
        collector.pending = true;
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, collector.storage.getSlot(1).getAmount());
            assertEquals(0, collector.storage.insert(item(Items.DIAMOND), 10, transaction));
            assertEquals(0, collector.storage.extract(item(Items.EGG), 4, transaction));
            commit(transaction);
        }
        assertEquals(4, collector.items.getFirst().getCount());
        assertEquals(0, collector.writes);
    }

    @Test void journalAppearingBeforeCommitAbortsEveryCollectorWithoutWorldWrites() {
        var first = new Collector(); var second = new Collector();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(10, first.storage.insert(item(Items.DIAMOND), 10, transaction));
            assertEquals(10, second.storage.insert(item(Items.DIAMOND), 10, transaction));
            second.pending = true;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertTrue(first.items.stream().allMatch(ItemStack::isEmpty));
        assertTrue(second.items.stream().allMatch(ItemStack::isEmpty));
        assertEquals(0, first.writes + second.writes);
    }

    @Test void externalInventoryEditOrBlockReplacementCannotBeOverwritten() {
        var collector = new Collector();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(10, collector.storage.insert(item(Items.DIAMOND), 10, transaction));
            collector.items.set(3, new ItemStack(Items.EMERALD));
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertTrue(collector.items.get(3).is(Items.EMERALD));
        assertTrue(collector.items.getFirst().isEmpty());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(10, collector.storage.insert(item(Items.DIAMOND), 10, transaction));
            collector.identity = new Object();
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertEquals(0, collector.writes);
    }

    @Test void abortedNestedReservationDoesNotValidateAgainstAStaleInventorySnapshot() {
        var collector = new Collector();
        try (var outer = Transaction.openOuter()) {
            try (var nested = outer.openNested()) {
                assertEquals(5, collector.storage.insert(item(Items.DIAMOND), 5, nested));
            }
            collector.items.set(3, new ItemStack(Items.EMERALD));
            assertEquals(10, collector.storage.insert(item(Items.DIAMOND), 10, outer));
            commit(outer);
        }
        assertEquals(10, collector.items.getFirst().getCount());
        assertTrue(collector.items.get(3).is(Items.EMERALD));
        assertEquals(1, collector.writes);
    }

    private static final class Collector implements DeferredInventoryItemStorage.Inventory {
        private List<ItemStack> items = new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        private Object identity = new Object();
        private boolean pending;
        private int writes;
        private final DeferredInventoryItemStorage storage = new DeferredInventoryItemStorage(this);
        @Override public boolean available() { return !pending; }
        @Override public List<ItemStack> read() { return items; }
        @Override public Object captureState() { return identity; }
        @Override public boolean matchesState(Object state) { return identity == state; }
        @Override public void commit(List<ItemStack> next) { items = new ArrayList<>(next); writes++; }
    }
}
