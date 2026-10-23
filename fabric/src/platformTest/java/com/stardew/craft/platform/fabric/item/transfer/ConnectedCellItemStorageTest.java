package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;

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

class ConnectedCellItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void connectedEntryPointsSharePhysicalReservationsWithoutDuplicatingHay() {
        var a = new Cell();
        var b = new Cell();
        var left = network(List.of(a.storage, b.storage));
        var right = network(List.of(b.storage, a.storage));
        for (boolean apply : new boolean[] {false, true}) {
            try (var transaction = Transaction.openOuter()) {
                assertEquals(1, left.insert(item(Items.WHEAT), 64, transaction));
                assertEquals(1, right.insert(item(Items.WHEAT), 64, transaction));
                assertEquals(0, left.insert(item(Items.WHEAT), 64, transaction));
                assertEquals(2, left.getAmount());
                assertEquals(2, right.getAmount());
                assertEquals(2, left.getCapacity());
                assertEquals(0, a.writes + b.writes);
                if (apply) commit(transaction);
            }
            assertEquals(apply ? 2 : 0, a.writes + b.writes);
        }
        assertEquals(1, a.stack.getCount());
        assertEquals(1, b.stack.getCount());
    }

    @Test
    void oneCallInsertsOneAndExtractionCanCollectSeveralCellsInTheOriginalOrder() {
        var a = new Cell();
        var b = new Cell();
        var aggregate = network(List.of(a.storage, b.storage));
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, aggregate.insert(item(Items.WHEAT), 2, transaction));
            assertEquals(0, b.storage.getAmount());
            assertEquals(1, aggregate.insert(item(Items.WHEAT), 2, transaction));
            assertEquals(2, aggregate.extract(item(Items.WHEAT), Long.MAX_VALUE, transaction));
            commit(transaction);
        }
        assertTrue(a.stack.isEmpty());
        assertTrue(b.stack.isEmpty());
    }

    @Test
    void nestedAbortRestoresTheOuterReservationAndAbortedChangesNeverDirtyOrSend() {
        var a = new Cell();
        var aggregate = network(List.of(a.storage));
        try (var outer = Transaction.openOuter()) {
            aggregate.insert(item(Items.WHEAT), 1, outer);
            try (var inner = outer.openNested()) {
                aggregate.extract(item(Items.WHEAT), 1, inner);
            }
            assertEquals(1, aggregate.getAmount());
        }
        assertTrue(a.stack.isEmpty());
        assertEquals(0, a.writes);
        assertEquals(0, a.dirty);
        assertEquals(0, a.packets);
    }

    @Test
    void normalizedHayIsVisibleInsideTheTransactionAndOtherItemsStayRejected() {
        var a = new Cell();
        var aggregate = network(List.of(a.storage));
        ItemStack named = new ItemStack(Items.WHEAT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named hay"));
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, aggregate.insert(item(Items.DIRT), 1, transaction));
            assertEquals(1, aggregate.insert(ItemVariant.of(named), 1, transaction));
            assertEquals(0, aggregate.extract(ItemVariant.of(named), 1, transaction));
            assertEquals(1, aggregate.extract(item(Items.WHEAT), 1, transaction));
        }
        assertEquals(0, a.writes);
    }

    @Test
    void topologyOrCellChangesAbortBeforeAnyCellCommit() {
        var a = new Cell();
        var b = new Cell();
        var current = new ArrayList<>(List.of(a.storage, b.storage));
        var aggregate = new ConnectedCellItemStorage(() -> List.copyOf(current), () -> item(Items.WHEAT));
        try (var transaction = Transaction.openOuter()) {
            aggregate.insert(item(Items.WHEAT), 1, transaction);
            current.removeLast();
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertEquals(0, a.writes + b.writes);
        current.add(b.storage);
        try (var transaction = Transaction.openOuter()) {
            aggregate.insert(item(Items.WHEAT), 1, transaction);
            a.version++;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertEquals(0, a.writes + b.writes);
    }

    private static ConnectedCellItemStorage network(List<DeferredItemCellStorage> cells) {
        return new ConnectedCellItemStorage(() -> cells, () -> item(Items.WHEAT));
    }
    private static final class Cell implements DeferredItemCellStorage.Cell {
        private ItemStack stack = ItemStack.EMPTY;
        private int version, writes, dirty, packets;
        private final DeferredItemCellStorage storage = new DeferredItemCellStorage(this);
        @Override public ItemStack read() { return stack; }
        @Override public boolean accepts(ItemVariant resource) { return resource.isOf(Items.WHEAT); }
        @Override public ItemStack normalizeInsertion(ItemStack value) { return new ItemStack(Items.WHEAT, value.getCount()); }
        @Override public int capacity() { return 1; }
        @Override public Object captureState() { return version; }
        @Override public boolean matchesState(Object state) { return state.equals(version); }
        @Override public void commit(ItemStack value) { stack = value; version++; writes++; dirty++; packets++; }
    }
}
