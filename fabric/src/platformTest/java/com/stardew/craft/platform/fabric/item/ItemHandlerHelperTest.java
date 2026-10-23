package com.stardew.craft.platform.fabric.item;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemHandlerHelperTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void stackedInsertionFillsMatchingStacksBeforeEarlierEmptySlots() {
        ItemStackHandler inventory = new ItemStackHandler(3);
        inventory.setStackInSlot(1, new ItemStack(Items.DIAMOND, 60));
        ItemStack incoming = new ItemStack(Items.DIAMOND, 10);
        assertTrue(ItemHandlerHelper.insertItemStacked(inventory, incoming, true).isEmpty());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertEquals(60, inventory.getStackInSlot(1).getCount());
        assertTrue(ItemHandlerHelper.insertItemStacked(inventory, incoming, false).isEmpty());
        assertEquals(64, inventory.getStackInSlot(1).getCount());
        assertEquals(6, inventory.getStackInSlot(0).getCount());
        assertEquals(10, incoming.getCount());
    }

    @Test
    void insertionDoesNotMergeDifferentComponentVariants() {
        ItemStackHandler inventory = new ItemStackHandler(2);
        ItemStack existing = new ItemStack(Items.DIAMOND, 60);
        existing.set(DataComponents.CUSTOM_NAME, Component.literal("One"));
        inventory.setStackInSlot(1, existing);
        ItemStack incoming = new ItemStack(Items.DIAMOND, 10);
        incoming.set(DataComponents.CUSTOM_NAME, Component.literal("Two"));
        assertTrue(ItemHandlerHelper.insertItemStacked(inventory, incoming, false).isEmpty());
        assertEquals(60, inventory.getStackInSlot(1).getCount());
        assertEquals(10, inventory.getStackInSlot(0).getCount());
    }

    @Test
    void unstackableItemsUseSeparateSlotsAndSequentialInsertionDoesNotPrioritizeMerges() {
        ItemStackHandler inventory = new ItemStackHandler(2);
        ItemStack swords = new ItemStack(Items.DIAMOND_SWORD, 2);
        assertTrue(ItemHandlerHelper.insertItemStacked(inventory, swords, false).isEmpty());
        assertEquals(1, inventory.getStackInSlot(0).getCount());
        assertEquals(1, inventory.getStackInSlot(1).getCount());
        assertEquals(2, swords.getCount());

        inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(1, new ItemStack(Items.DIAMOND, 60));
        ItemHandlerHelper.insertItem(inventory, new ItemStack(Items.DIAMOND, 10), false);
        assertEquals(10, inventory.getStackInSlot(0).getCount());
        assertEquals(60, inventory.getStackInSlot(1).getCount());
    }

    @Test
    void fullAndAbsentInventoriesReturnTheUnacceptedInputWithoutLosingItems() {
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 62));
        ItemStack incoming = new ItemStack(Items.DIAMOND, 5);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, incoming, false);
        assertEquals(3, remainder.getCount());
        assertEquals(5, incoming.getCount());
        assertEquals(64, inventory.getStackInSlot(0).getCount());
        assertSame(incoming, ItemHandlerHelper.insertItemStacked(null, incoming, false));
        assertSame(incoming, ItemHandlerHelper.insertItem(null, incoming, false));
        assertTrue(ItemHandlerHelper.insertItemStacked(inventory, ItemStack.EMPTY, false).isEmpty());
    }
}
