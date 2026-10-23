package com.stardew.craft.platform.fabric.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemStackHandlerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void insertionRespectsTheSlotAndItemLimitsWithoutMutatingItsArgument() {
        RecordingHandler inventory = new RecordingHandler(1, 8);
        ItemStack incoming = new ItemStack(Items.DIAMOND, 12);
        ItemStack remainder = inventory.insertItem(0, incoming, false);
        assertEquals(12, incoming.getCount());
        assertEquals(8, inventory.getStackInSlot(0).getCount());
        assertEquals(4, remainder.getCount());
        assertNotSame(incoming, remainder);
        assertEquals(List.of(0), inventory.changed);

        ItemStackHandler ordinary = new ItemStackHandler(1);
        assertEquals(Item.ABSOLUTE_MAX_STACK_SIZE, ordinary.getSlotLimit(0));
        assertEquals(6, ordinary.insertItem(0, new ItemStack(Items.DIAMOND, 70), false).getCount());
        assertEquals(64, ordinary.getStackInSlot(0).getCount());
    }

    @Test
    void simulationReturnsTheSameRemainderWithoutChangingCountsOrOwnerNotifications() {
        RecordingHandler inventory = new RecordingHandler(1, 8);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 6));
        inventory.changed.clear();
        ItemStack incoming = new ItemStack(Items.DIAMOND, 5);
        ItemStack remainder = inventory.insertItem(0, incoming, true);
        assertEquals(3, remainder.getCount());
        assertEquals(5, incoming.getCount());
        assertEquals(6, inventory.getStackInSlot(0).getCount());
        assertTrue(inventory.changed.isEmpty());
        remainder.setCount(1);
        assertEquals(6, inventory.getStackInSlot(0).getCount());
    }

    @Test
    void componentDifferencesCannotBeMergedAndRejectedInputRetainsItsIdentity() {
        ItemStackHandler inventory = new ItemStackHandler(1);
        ItemStack stored = new ItemStack(Items.DIAMOND, 4);
        stored.set(DataComponents.CUSTOM_NAME, Component.literal("Gold quality"));
        inventory.setStackInSlot(0, stored);
        ItemStack incoming = new ItemStack(Items.DIAMOND, 2);
        incoming.set(DataComponents.CUSTOM_NAME, Component.literal("Silver quality"));
        assertSame(incoming, inventory.insertItem(0, incoming, false));
        assertEquals(4, stored.getCount());
        assertEquals(2, incoming.getCount());
    }

    @Test
    void slotValidationIsIndependentOfFullness() {
        RecordingHandler inventory = new RecordingHandler(1, 8);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        assertTrue(inventory.isItemValid(0, new ItemStack(Items.DIAMOND)));
        ItemStack incoming = new ItemStack(Items.DIAMOND);
        assertSame(incoming, inventory.insertItem(0, incoming, true));
        ItemStack invalid = new ItemStack(Items.DIRT);
        assertFalse(inventory.isItemValid(0, invalid));
        assertSame(invalid, inventory.insertItem(0, invalid, false));
    }

    @Test
    void extractionCapsEachReturnedStackAndSimulationDoesNotAliasStoredContents() {
        RecordingHandler inventory = new RecordingHandler(1, 200);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 100));
        inventory.changed.clear();
        ItemStack predicted = inventory.extractItem(0, 90, true);
        assertEquals(64, predicted.getCount());
        predicted.setCount(1);
        assertEquals(100, inventory.getStackInSlot(0).getCount());
        assertTrue(inventory.changed.isEmpty());
        ItemStack extracted = inventory.extractItem(0, 90, false);
        assertEquals(64, extracted.getCount());
        assertEquals(36, inventory.getStackInSlot(0).getCount());
        assertEquals(List.of(0), inventory.changed);
    }

    @Test
    void extractingAWholeSlotTransfersOwnershipAndNonpositiveRequestsCannotCreateItems() {
        ItemStackHandler inventory = new ItemStackHandler(1);
        ItemStack owned = new ItemStack(Items.DIAMOND, 5);
        inventory.setStackInSlot(0, owned);
        assertTrue(inventory.extractItem(0, -2, false).isEmpty());
        assertTrue(inventory.extractItem(0, 0, false).isEmpty());
        assertEquals(5, owned.getCount());
        assertSame(owned, inventory.extractItem(0, 99, false));
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        owned.setCount(2);
        assertTrue(inventory.getStackInSlot(0).isEmpty());
    }

    @Test
    void directReplacementMayRestoreOverLimitContentsAndSizeResetDiscardsOldSlots() {
        RecordingHandler inventory = new RecordingHandler(2, 8);
        ItemStack restored = new ItemStack(Items.DIAMOND, 70);
        inventory.setStackInSlot(1, restored);
        assertSame(restored, inventory.getStackInSlot(1));
        assertEquals(List.of(1), inventory.changed);
        inventory.setSize(3);
        assertEquals(3, inventory.getSlots());
        assertTrue(inventory.getStackInSlot(1).isEmpty());
    }

    @Test
    void callerSuppliedBackingListIsPreservedAndInvalidSlotsAreRejected() {
        NonNullList<ItemStack> backing = NonNullList.withSize(2, ItemStack.EMPTY);
        ItemStackHandler inventory = new ItemStackHandler(backing);
        ItemStack item = new ItemStack(Items.DIAMOND);
        backing.set(1, item);
        assertSame(item, inventory.getStackInSlot(1));
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.getStackInSlot(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.setStackInSlot(2, item));
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.insertItem(2, item, false));
        assertThrows(IndexOutOfBoundsException.class, () -> inventory.extractItem(2, 1, false));
    }

    @Test
    void nbtRoundTripUsesNativeComponentsSlotNumbersAndSizeWithoutDirtyingLoadedContents() {
        HolderLookup.Provider registries = registries();
        RecordingHandler original = new RecordingHandler(3, 99);
        ItemStack stack = new ItemStack(Items.DIAMOND, 17);
        CompoundTag quality = new CompoundTag();
        quality.putString("quality", "gold");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(quality));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Stored component name"));
        original.setStackInSlot(2, stack);
        CompoundTag saved = original.serializeNBT(registries);
        assertEquals(3, saved.getInt("Size"));
        ListTag entries = saved.getList("Items", 10);
        assertEquals(1, entries.size());
        assertEquals(2, entries.getCompound(0).getInt("Slot"));
        assertTrue(entries.getCompound(0).contains("components"));

        RecordingHandler restored = new RecordingHandler(1, 99);
        restored.deserializeNBT(registries, saved);
        assertEquals(3, restored.getSlots());
        assertEquals(17, restored.getStackInSlot(2).getCount());
        assertTrue(ItemStack.isSameItemSameComponents(stack, restored.getStackInSlot(2)));
        assertTrue(restored.getStackInSlot(0).isEmpty());
        assertEquals(1, restored.loaded);
        assertTrue(restored.changed.isEmpty());
    }

    @Test
    void nbtWithoutSizeKeepsTheExistingSlotCountAndSkipsOutOfRangeEntries() {
        HolderLookup.Provider registries = registries();
        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag negative = new CompoundTag();
        negative.putInt("Slot", -1);
        items.add(new ItemStack(Items.DIAMOND, 2).save(registries, negative));
        CompoundTag tooHigh = new CompoundTag();
        tooHigh.putInt("Slot", 2);
        items.add(new ItemStack(Items.DIAMOND, 3).save(registries, tooHigh));
        tag.put("Items", items);
        RecordingHandler restored = new RecordingHandler(2, 99);
        restored.setStackInSlot(0, new ItemStack(Items.DIAMOND, 9));
        restored.changed.clear();
        restored.deserializeNBT(registries, tag);
        assertEquals(2, restored.getSlots());
        assertTrue(restored.getStackInSlot(0).isEmpty());
        assertTrue(restored.getStackInSlot(1).isEmpty());
        assertEquals(1, restored.loaded);
        assertTrue(restored.changed.isEmpty());
    }

    private static HolderLookup.Provider registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static final class RecordingHandler extends ItemStackHandler {
        final List<Integer> changed = new ArrayList<>();
        final int limit;
        int loaded;

        RecordingHandler(int size, int limit) { super(size); this.limit = limit; }
        @Override public int getSlotLimit(int slot) { return limit; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(Items.DIAMOND); }
        @Override protected void onContentsChanged(int slot) { changed.add(slot); }
        @Override protected void onLoad() { loaded++; }
    }
}
