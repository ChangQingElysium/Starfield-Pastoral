package com.stardew.craft.platform.fabric.menu;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MenuDataSyncTest {
    private static final ResourceLocation TYPE = ResourceLocation.parse("stardewcraft:animal_query");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecPreservesSignedFullIntValuesBeyondTheVanillaShortRange() {
        for (int value : new int[] {Integer.MIN_VALUE, -32769, -1, 0, 32767, 32768, 65535, 1_000_000,
                Integer.MAX_VALUE}) {
            MenuDataPayload original = new MenuDataPayload(100, TYPE, 18, value);
            ByteBuf buffer = Unpooled.buffer();
            try {
                MenuDataPayload.STREAM_CODEC.encode(buffer, original);
                assertEquals(original, MenuDataPayload.STREAM_CODEC.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void initialSyncKeepsNativeItemSnapshotAndOnlySendsFullIntDataAfterIt() {
        int[] values = {Integer.MIN_VALUE, 32768, 65_536, 1_000_000, Integer.MAX_VALUE};
        TestMenu menu = new TestMenu(42, values.length);
        RecordingSynchronizer vanilla = new RecordingSynchronizer();
        List<MenuDataPayload> sent = new ArrayList<>();
        FullIntMenuSynchronizer bridge = new FullIntMenuSynchronizer(menu, TYPE, vanilla, payload -> {
            assertEquals(List.of("initial"), vanilla.operations);
            sent.add(payload);
        });
        NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
        ItemStack carried = new ItemStack(Items.DIAMOND, 3);
        bridge.sendInitialData(menu, items, carried, values);
        assertSame(items, vanilla.initialItems);
        assertSame(carried, vanilla.initialCarried);
        assertArrayEquals(new int[0], vanilla.initialData);
        assertEquals(values.length, sent.size());
        for (int index = 0; index < values.length; index++) {
            MenuDataPayload payload = sent.get(index);
            assertEquals(index, payload.dataSlot());
            assertTrue(payload.matches(menu.containerId, TYPE, menu.values.length));
            menu.setData(payload.dataSlot(), payload.value());
        }
        assertArrayEquals(values, menu.values);
    }

    @Test
    void changingDataNeverUsesTheShortDelegateAndKeepsAnimalAndSiloValues() {
        TestMenu menu = new TestMenu(17, 4);
        RecordingSynchronizer vanilla = new RecordingSynchronizer();
        List<MenuDataPayload> sent = new ArrayList<>();
        FullIntMenuSynchronizer bridge = new FullIntMenuSynchronizer(menu, TYPE, vanilla, sent::add);
        int[] animalIdHayCapacitySellValue = {100_001, 80_000, 120_000, 2_000_000};
        for (int index = 0; index < animalIdHayCapacitySellValue.length; index++) {
            bridge.sendDataChange(menu, index, animalIdHayCapacitySellValue[index]);
        }
        assertTrue(vanilla.operations.isEmpty());
        assertEquals(4, sent.size());
        for (int index = 0; index < sent.size(); index++) {
            assertEquals(animalIdHayCapacitySellValue[index], sent.get(index).value());
        }
    }

    @Test
    void itemSlotAndCarriedUpdatesKeepTheirOriginalDelegationAndStackIdentity() {
        TestMenu menu = new TestMenu(5, 2);
        RecordingSynchronizer vanilla = new RecordingSynchronizer();
        List<MenuDataPayload> sent = new ArrayList<>();
        FullIntMenuSynchronizer bridge = new FullIntMenuSynchronizer(menu, TYPE, vanilla, sent::add);
        ItemStack slot = new ItemStack(Items.DIAMOND, 2);
        ItemStack carried = new ItemStack(Items.DIRT, 4);
        bridge.sendSlotChange(menu, 7, slot);
        bridge.sendCarriedChange(menu, carried);
        assertEquals(List.of("slot:7", "carried"), vanilla.operations);
        assertSame(slot, vanilla.changedSlot);
        assertSame(carried, vanilla.changedCarried);
        assertTrue(sent.isEmpty());
    }

    @Test
    void staleContainerDifferentMenuTypeAndOutOfRangeDataCannotMatchTheCurrentMenu() {
        MenuDataPayload payload = new MenuDataPayload(5, TYPE, 2, 100_000);
        assertTrue(payload.matches(5, TYPE, 3));
        assertFalse(payload.matches(6, TYPE, 3));
        assertFalse(payload.matches(5, ResourceLocation.parse("stardewcraft:silo_manager"), 3));
        assertFalse(payload.matches(5, TYPE, 2));
        assertFalse(payload.matches(5, TYPE, 0));
        assertFalse(payload.matches(5, null, 3));
    }

    @Test
    void onlyTheModNamespaceUsesThisAdapterAndEachMenuRetainsItsOwnDataShape() {
        assertTrue(FabricMenuDataSync.needsAdapter(TYPE));
        assertFalse(FabricMenuDataSync.needsAdapter(ResourceLocation.parse("minecraft:generic_9x3")));
        assertFalse(FabricMenuDataSync.needsAdapter(ResourceLocation.parse("other_mod:menu")));
        assertFalse(FabricMenuDataSync.needsAdapter(null));
        MenuDataPayload lastAnimalField = new MenuDataPayload(10, TYPE, 18, 99999);
        assertTrue(lastAnimalField.matches(10, TYPE, 19));
        assertFalse(lastAnimalField.matches(10, TYPE, 5));
        MenuDataPayload silo = new MenuDataPayload(10, ResourceLocation.parse("stardewcraft:silo_manager"), 4, 120000);
        assertTrue(silo.matches(10, ResourceLocation.parse("stardewcraft:silo_manager"), 5));
    }

    @Test
    void aSynchronizerCannotBeReusedForAnotherMenuInstanceWithTheSameNumericId() {
        TestMenu first = new TestMenu(5, 1);
        TestMenu second = new TestMenu(5, 1);
        RecordingSynchronizer vanilla = new RecordingSynchronizer();
        FullIntMenuSynchronizer bridge = new FullIntMenuSynchronizer(first, TYPE, vanilla,
                payload -> fail("A different menu must not send data"));
        assertTrue(bridge.isFor(first));
        assertFalse(bridge.isFor(second));
        assertThrows(IllegalArgumentException.class, () -> bridge.sendDataChange(second, 0, 7));
        assertThrows(IllegalArgumentException.class,
                () -> bridge.sendInitialData(second, NonNullList.create(), ItemStack.EMPTY, new int[] {1}));
        assertTrue(vanilla.operations.isEmpty());
    }

    @Test
    void malformedDataAddressesAreRejectedBeforeAccessingClientSlots() {
        assertThrows(IllegalArgumentException.class, () -> new MenuDataPayload(-1, TYPE, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MenuDataPayload(1, TYPE, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MenuDataPayload(1, TYPE, 32768, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuDataPayload(1, ResourceLocation.parse("minecraft:generic_9x3"), 0, 1));
    }

    private static final class TestMenu extends AbstractContainerMenu {
        final int[] values;

        TestMenu(int id, int fields) {
            super(MenuType.GENERIC_9x1, id);
            values = new int[fields];
            for (int index = 0; index < fields; index++) {
                int field = index;
                addDataSlot(new DataSlot() {
                    @Override public int get() { return values[field]; }
                    @Override public void set(int value) { values[field] = value; }
                });
            }
        }

        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }

    private static final class RecordingSynchronizer implements ContainerSynchronizer {
        final List<String> operations = new ArrayList<>();
        NonNullList<ItemStack> initialItems;
        ItemStack initialCarried;
        int[] initialData;
        ItemStack changedSlot;
        ItemStack changedCarried;

        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                ItemStack carried, int[] data) {
            operations.add("initial");
            initialItems = items;
            initialCarried = carried;
            initialData = data.clone();
        }

        @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
            operations.add("slot:" + slot);
            changedSlot = stack;
        }
        @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
            operations.add("carried");
            changedCarried = stack;
        }
        @Override public void sendDataChange(AbstractContainerMenu menu, int slot, int value) {
            fail("The short-value data delegate must not be used for mod menu fields");
        }
    }
}
