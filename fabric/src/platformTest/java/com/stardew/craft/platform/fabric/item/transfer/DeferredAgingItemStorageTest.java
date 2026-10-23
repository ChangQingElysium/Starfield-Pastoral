package com.stardew.craft.platform.fabric.item.transfer;

import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

class DeferredAgingItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void immediateReadyInputCanBeCollectedAndReplacedWithinOneTransaction() {
        var cask = new Cask(); var silver = ItemVariant.of(named());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, cask.storage.insert(silver, 64, transaction));
            assertEquals(0, cask.storage.getSlot(0).getAmount());
            assertEquals(1, cask.storage.getSlot(1).getAmount());
            assertEquals(0, cask.storage.extract(item(Items.DIAMOND), 1, transaction));
            assertEquals(1, cask.storage.extract(silver, 1, transaction));
            assertEquals(1, cask.storage.insert(silver, 1, transaction));
            assertEquals(0, cask.writes.size());
            commit(transaction);
        }
        assertEquals(List.of("start", "collect:1", "start"), cask.writes);
        assertTrue(silver.matches(cask.state.product()));
        assertEquals(1, cask.state.product().getCount());
    }

    @Test void normalInputStaysWorkingAndAbortDoesNotChangeProductOrDay() {
        var cask = new Cask();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, cask.storage.insert(item(Items.DIAMOND), 64, transaction));
            assertEquals(1, cask.storage.getSlot(0).getAmount());
            assertEquals(0, cask.storage.getSlot(1).getAmount());
            assertEquals(0, cask.storage.extract(item(Items.DIAMOND), 1, transaction));
        }
        assertTrue(cask.state.product().isEmpty());
        assertEquals(-1, cask.state.lastDay());
        assertTrue(cask.writes.isEmpty());
    }

    @Test void twoCasksAndNestedRollbackPreserveOriginalOrderWithoutAnOperationQuota() {
        var first = new Cask(); var second = new Cask();
        var silver = ItemVariant.of(named());
        try (var outer = Transaction.openOuter()) {
            assertEquals(1, first.storage.insert(silver, 1, outer));
            try (var nested = outer.openNested()) {
                assertEquals(1, first.storage.extract(silver, 1, nested));
                assertEquals(1, second.storage.insert(silver, 1, nested));
            }
            assertEquals(1, first.storage.getSlot(1).getAmount());
            assertEquals(0, second.storage.getSlot(1).getAmount());
            assertEquals(1, second.storage.insert(silver, 1, outer));
            commit(outer);
        }
        assertEquals(List.of("start"), first.writes);
        assertEquals(List.of("start"), second.writes);
    }

    @Test void changedRulesOrDayAbortAllCasksBeforeAnyDirtyOrOutputWrite() {
        var first = new Cask(); var second = new Cask();
        try (var transaction = Transaction.openOuter()) {
            first.storage.insert(item(Items.DIAMOND), 1, transaction);
            second.storage.insert(item(Items.DIAMOND), 1, transaction);
            second.rule++;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertTrue(first.writes.isEmpty()); assertTrue(second.writes.isEmpty());
        try (var transaction = Transaction.openOuter()) {
            first.storage.insert(item(Items.DIAMOND), 1, transaction);
            first.day++;
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertTrue(first.state.product().isEmpty());
    }

    @Test void discardedNestedStartDoesNotLeaveAnAgingRuleGuardBehind() {
        var cask = new Cask();
        cask.state = new DeferredAgingItemStorage.Aging(named(), true, 42, 2, 3);
        try (var outer = Transaction.openOuter()) {
            assertEquals(1, cask.storage.extract(ItemVariant.of(named()), 1, outer));
            try (var nested = outer.openNested()) {
                assertEquals(1, cask.storage.insert(item(Items.DIAMOND), 1, nested));
            }
            cask.rule++;
            commit(outer);
        }
        assertEquals(List.of("collect:1"), cask.writes);
        assertTrue(cask.state.product().isEmpty());
    }

    @Test void legacyMultiItemProductSupportsPartialCollectionsAndRetainsAgingFields() {
        var cask = new Cask();
        cask.state = new DeferredAgingItemStorage.Aging(new ItemStack(Items.DIAMOND, 5), true, 21, 4, 10);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(2, cask.storage.extract(item(Items.DIAMOND), 2, transaction));
            assertEquals(3, cask.storage.getSlot(1).getAmount());
            commit(transaction);
        }
        assertEquals(3, cask.state.product().getCount());
        assertEquals(21, cask.state.days()); assertEquals(4, cask.state.rate()); assertEquals(10, cask.state.lastDay());
    }

    private static ItemStack named() {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Silver ingredient"));
        return stack;
    }
    private static final class Cask implements DeferredAgingItemStorage.Cask {
        private DeferredAgingItemStorage.Aging state = new DeferredAgingItemStorage.Aging(ItemStack.EMPTY, false, -1, 1, -1);
        private final List<String> writes = new ArrayList<>();
        private int day = 3, rule = 1;
        private final DeferredAgingItemStorage storage = new DeferredAgingItemStorage(this);
        @Override public boolean available() { return true; }
        @Override public DeferredAgingItemStorage.Aging read() { return state; }
        @Override public Object captureContext() { return day; }
        @Override public boolean matchesContext(Object context) { return context.equals(day); }
        @Override public Optional<DeferredAgingItemStorage.Start> prepare(ItemStack input) {
            int expected = rule;
            boolean silver = input.has(DataComponents.CUSTOM_NAME);
            return Optional.of(new DeferredAgingItemStorage.Start(
                    new DeferredAgingItemStorage.Aging(input.copyWithCount(1), silver, silver ? 42 : 56, 2, day),
                    () -> rule == expected));
        }
        @Override public void start(DeferredAgingItemStorage.Aging plan) { state = plan; writes.add("start"); }
        @Override public ItemStack collect(int count) {
            ItemStack output = state.product().copyWithCount(count);
            ItemStack remainder = state.product().copyWithCount(state.product().getCount() - count);
            state = remainder.isEmpty() ? new DeferredAgingItemStorage.Aging(ItemStack.EMPTY, false, -1, 1, state.lastDay())
                    : new DeferredAgingItemStorage.Aging(remainder, true, state.days(), state.rate(), state.lastDay());
            writes.add("collect:" + count);
            return output;
        }
    }
}
