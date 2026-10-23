package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void abortChangesNeitherContentsNorDirtyOrPacketProducingContainerMethods() {
        RecordingContainer inventory = new RecordingContainer(2);
        var storage = ContainerItemStorage.of(inventory);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(12, storage.insert(item(Items.DIAMOND), 12, transaction));
            assertEquals(12, storage.getSlot(0).getAmount());
            assertTrue(inventory.getItem(0).isEmpty());
            assertEquals(0, inventory.writes);
            assertEquals(0, inventory.dirty);
        }
        assertTrue(inventory.isEmpty());
        assertEquals(0, storage.getSlot(0).getAmount());
        assertEquals(0, inventory.writes);
        assertEquals(0, inventory.dirty);
    }

    @Test
    void commitPreservesComponentsAndRespectsSlotAndItemCapacity() {
        RecordingContainer inventory = new RecordingContainer(1);
        var storage = ContainerItemStorage.of(inventory);
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Transfer fixture"));
        ItemVariant variant = ItemVariant.of(named);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(64, storage.insert(variant, Long.MAX_VALUE, transaction));
            transaction.commit();
        }
        assertEquals(64, inventory.getItem(0).getCount());
        assertTrue(variant.matches(inventory.getItem(0)));
        assertEquals(1, named.getCount());
        assertEquals(1, inventory.writes);
        assertTrue(inventory.dirty > 0);
    }

    @Test
    void nestedAbortRestoresOuterViewAndOuterAbortCancelsCommittedChildren() {
        RecordingContainer inventory = new RecordingContainer(1);
        var storage = ContainerItemStorage.of(inventory);
        var diamond = item(Items.DIAMOND);
        try (var outer = Transaction.openOuter()) {
            storage.insert(diamond, 10, outer);
            try (var inner = outer.openNested()) {
                storage.extract(diamond, 3, inner);
            }
            assertEquals(10, storage.getSlot(0).getAmount());
            try (var inner = outer.openNested()) {
                storage.extract(diamond, 4, inner);
                inner.commit();
            }
            assertEquals(6, storage.getSlot(0).getAmount());
        }
        assertTrue(inventory.isEmpty());
        assertEquals(0, inventory.writes);
        assertEquals(0, inventory.dirty);
    }

    @Test
    void twoContainerExchangeCanAbortOrCommitWithoutLosingItems() {
        RecordingContainer source = new RecordingContainer(1);
        source.setItem(0, new ItemStack(Items.DIAMOND, 10));
        source.writes = source.dirty = 0;
        RecordingContainer destination = new RecordingContainer(1);
        var from = ContainerItemStorage.of(source);
        var to = ContainerItemStorage.of(destination);
        var diamond = item(Items.DIAMOND);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(4, from.extract(diamond, 4, transaction));
            assertEquals(4, to.insert(diamond, 4, transaction));
        }
        assertEquals(10, source.getItem(0).getCount());
        assertTrue(destination.isEmpty());
        assertEquals(0, source.writes + destination.writes);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(4, from.extract(diamond, 4, transaction));
            assertEquals(4, to.insert(diamond, 4, transaction));
            transaction.commit();
        }
        assertEquals(6, source.getItem(0).getCount());
        assertEquals(4, destination.getItem(0).getCount());
    }

    @Test
    void sharedInventoryAliasesUseOneParticipantAndSeeEachOthersReservations() {
        List<ItemStack> shared = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 10)));
        SharedContainer first = new SharedContainer(shared);
        SharedContainer second = new SharedContainer(shared);
        var from = ContainerItemStorage.of(first);
        var to = ContainerItemStorage.of(second);
        assertSame(from, to);
        var diamond = item(Items.DIAMOND);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(4, from.extract(diamond, 4, transaction));
            assertEquals(6, to.getSlot(0).getAmount());
            assertEquals(4, to.insert(diamond, 4, transaction));
            transaction.commit();
        }
        assertEquals(10, shared.getFirst().getCount());
        assertEquals(0, first.writes);
        assertEquals(1, second.writes);
    }

    @Test
    void placementRulesAndComponentVariantsAreNotBypassed() {
        RecordingContainer inventory = new RecordingContainer(2) {
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && stack.is(Items.DIAMOND); }
        };
        var storage = ContainerItemStorage.of(inventory);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(item(Items.DIRT), 1, transaction));
            assertEquals(0, storage.getSlot(1).insert(item(Items.DIAMOND), 1, transaction));
            storage.insert(item(Items.DIAMOND), 5, transaction);
            ItemStack named = new ItemStack(Items.DIAMOND);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Different variant"));
            assertEquals(0, storage.insert(ItemVariant.of(named), 1, transaction));
            transaction.commit();
        }
        assertEquals(5, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isEmpty());
    }

    @Test
    void nativeVariantsRespectComponentStackLimitsAndComponentEquality() {
        RecordingContainer inventory = new RecordingContainer(1);
        var storage = ContainerItemStorage.of(inventory);
        ItemStack limited = new ItemStack(Items.DIAMOND);
        limited.set(DataComponents.MAX_STACK_SIZE, 16);
        var variant = ItemVariant.of(limited);
        ItemStack equal = limited.copyWithCount(7);
        assertEquals(variant, ItemVariant.of(equal));
        assertEquals(variant.hashCode(), ItemVariant.of(equal).hashCode());
        assertNotEquals(variant, item(Items.DIAMOND));
        assertTrue(variant.matches(equal));
        try (var transaction = Transaction.openOuter()) {
            assertEquals(16, storage.insert(variant, Long.MAX_VALUE, transaction));
            assertEquals(0, storage.insert(variant, 1, transaction));
            assertEquals(16, storage.getSlot(0).getCapacity());
            transaction.commit();
        }
        assertEquals(16, inventory.getItem(0).getCount());
        assertEquals(16, inventory.getItem(0).getMaxStackSize());
    }

    private static class RecordingContainer extends SimpleContainer {
        private int writes;
        private int dirty;
        RecordingContainer(int size) { super(size); }
        @Override public void setItem(int slot, ItemStack stack) { writes++; super.setItem(slot, stack); }
        @Override public void setChanged() { dirty++; }
    }

    private static final class SharedContainer extends SimpleContainer implements ContainerStorageIdentity {
        private final List<ItemStack> shared;
        private int writes;
        SharedContainer(List<ItemStack> shared) { super(shared.size()); this.shared = shared; }
        @Override public Object stardewcraft$storageIdentity() { return shared; }
        @Override public int getContainerSize() { return shared.size(); }
        @Override public ItemStack getItem(int slot) { return shared.get(slot); }
        @Override public void setItem(int slot, ItemStack stack) { writes++; shared.set(slot, stack); }
    }
}
