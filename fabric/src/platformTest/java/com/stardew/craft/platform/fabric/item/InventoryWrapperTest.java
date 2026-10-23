package com.stardew.craft.platform.fabric.item;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.item.wrapper.InvWrapper;
import com.stardew.craft.platform.fabric.item.wrapper.PlayerMainInvWrapper;
import com.stardew.craft.platform.fabric.item.wrapper.RangedWrapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryWrapperTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void wrapperRespectsContainerRulesCapacityAndSimulationWithoutDirtyNotifications() {
        TrackingContainer container = new TrackingContainer(2, 8);
        InvWrapper inventory = new InvWrapper(container);
        ItemStack incoming = new ItemStack(Items.DIAMOND, 12);
        assertEquals(4, inventory.insertItem(0, incoming, true).getCount());
        assertTrue(container.getItem(0).isEmpty());
        assertEquals(0, container.dirty);
        assertFalse(inventory.isItemValid(0, new ItemStack(Items.DIRT)));
        assertTrue(inventory.isItemValid(0, incoming));
        assertSame(incoming, inventory.insertItem(1, incoming, false));
        ItemStack remainder = inventory.insertItem(0, incoming, false);
        assertEquals(4, remainder.getCount());
        assertEquals(12, incoming.getCount());
        assertEquals(8, container.getItem(0).getCount());
        assertEquals(2, container.dirty); // native setItem notification plus the wrapper notification
        assertEquals(8, inventory.getSlotLimit(0));
    }

    @Test
    void wrapperExtractionUsesTheContainerOperationRatherThanOwnedHandlerPerItemCap() {
        TrackingContainer container = new TrackingContainer(1, 200);
        container.setItem(0, new ItemStack(Items.DIAMOND, 64));
        container.getItem(0).setCount(100); // owner restores an overstack directly
        container.dirty = 0;
        InvWrapper inventory = new InvWrapper(container);
        ItemStack simulated = inventory.extractItem(0, 90, true);
        assertEquals(90, simulated.getCount());
        simulated.setCount(1);
        assertEquals(100, container.getItem(0).getCount());
        assertEquals(0, container.dirty);
        ItemStack extracted = inventory.extractItem(0, 90, false);
        assertEquals(90, extracted.getCount());
        assertEquals(10, container.getItem(0).getCount());
        assertEquals(2, container.dirty);
        assertTrue(inventory.extractItem(0, -1, false).isEmpty());
        assertEquals(10, container.getItem(0).getCount());
    }

    @Test
    void directReplacementUsesNativeSetItemAndWrappersCompareByTheirExactTypeAndOwner() {
        TrackingContainer owner = new TrackingContainer(1, 8);
        InvWrapper first = new InvWrapper(owner);
        InvWrapper second = new InvWrapper(owner);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new InvWrapper(new TrackingContainer(1, 8)));
        assertSame(owner, first.getInv());
        first.setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
        assertEquals(8, owner.getItem(0).getCount()); // vanilla container's own replacement policy
        assertEquals(1, owner.dirty);
    }

    @Test
    void rangeMapsOnlyItsOwnedSlotsAndRejectsWritesOutsideTheView() {
        ItemStackHandler owner = new ItemStackHandler(4);
        RangedWrapper view = new RangedWrapper(owner, 1, 3);
        ItemStack incoming = new ItemStack(Items.DIAMOND, 2);
        assertEquals(2, view.getSlots());
        assertTrue(view.insertItem(0, incoming, false).isEmpty());
        assertEquals(2, owner.getStackInSlot(1).getCount());
        assertSame(incoming, view.insertItem(-1, incoming, false));
        assertSame(incoming, view.insertItem(2, incoming, false));
        view.setStackInSlot(-1, new ItemStack(Items.DIRT));
        view.setStackInSlot(2, new ItemStack(Items.DIRT));
        assertTrue(owner.getStackInSlot(0).isEmpty());
        assertTrue(owner.getStackInSlot(3).isEmpty());
        assertTrue(view.getStackInSlot(-1).isEmpty());
        assertTrue(view.extractItem(2, 1, false).isEmpty());
        assertEquals(0, view.getSlotLimit(2));
        assertFalse(view.isItemValid(-1, incoming));
    }

    @Test
    void playerMainViewExposesThirtySixSlotsAndCannotModifyArmorOrOffhand() {
        Inventory owner = new Inventory(null);
        owner.armor.set(0, new ItemStack(Items.DIAMOND_BOOTS));
        owner.offhand.set(0, new ItemStack(Items.SHIELD));
        PlayerMainInvWrapper view = new PlayerMainInvWrapper(owner);
        assertSame(owner, view.getInventoryPlayer());
        assertEquals(36, view.getSlots());
        view.setStackInSlot(35, new ItemStack(Items.DIAMOND, 2));
        assertEquals(2, owner.items.get(35).getCount());
        ItemStack incoming = new ItemStack(Items.DIRT);
        assertSame(incoming, view.insertItem(36, incoming, false));
        view.setStackInSlot(36, incoming);
        view.setStackInSlot(40, incoming);
        assertTrue(owner.armor.getFirst().is(Items.DIAMOND_BOOTS));
        assertTrue(owner.offhand.getFirst().is(Items.SHIELD));
        assertTrue(view.getStackInSlot(36).isEmpty());
    }

    private static final class TrackingContainer extends SimpleContainer {
        private final int maximum;
        int dirty;

        TrackingContainer(int slots, int maximum) { super(slots); this.maximum = maximum; }
        @Override public int getMaxStackSize() { return maximum; }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && stack.is(Items.DIAMOND); }
        @Override public void setChanged() { super.setChanged(); dirty++; }
    }
}
