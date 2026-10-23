package com.stardew.craft.platform.fabric.event.enchant;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnchantingEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void anvilSkipsEmptyLeftAndCancellationClearsEveryPreviousResultField() {
        var bus = new EventBus(); var calls = new AtomicInteger();
        var left = new ItemStack(Items.DIAMOND_SWORD); var right = new ItemStack(Items.ENCHANTED_BOOK);
        List<Object> writes = new ArrayList<>();
        bus.addListener(AnvilUpdateEvent.class, event -> {
            calls.incrementAndGet(); assertSame(left, event.getLeft()); assertSame(right, event.getRight());
            assertEquals(0, event.getCost()); assertNull(event.getName());
            event.setOutput(new ItemStack(Items.APPLE)); event.setCost(99); event.setMaterialCost(3); event.setCanceled(true);
        });
        assertTrue(FabricEnchantingEvents.anvil(bus, ItemStack.EMPTY, right, null, null, writes::add, writes::add, writes::add));
        assertEquals(0, calls.get()); assertTrue(writes.isEmpty());
        assertFalse(FabricEnchantingEvents.anvil(bus, left, right, null, null, writes::add, writes::add, writes::add));
        assertEquals(List.of(ItemStack.EMPTY, 0, 0), writes);
    }

    @Test
    void anvilOverridesOnlyNonemptyOutputClampsLongCostAndKeepsMaterialCost() {
        var left = new ItemStack(Items.DIAMOND_SWORD); var right = new ItemStack(Items.ENCHANTED_BOOK);
        for (long cost : new long[] { Long.MAX_VALUE, Long.MIN_VALUE }) {
            var bus = new EventBus(); var output = new ItemStack(Items.APPLE); List<Object> writes = new ArrayList<>();
            bus.addListener(AnvilUpdateEvent.class, event -> {
                assertEquals("name", event.getName()); event.setOutput(output); event.setCost(cost); event.setMaterialCost(-3);
            });
            assertFalse(FabricEnchantingEvents.anvil(bus, left, right, "name", null, writes::add, writes::add, writes::add));
            assertEquals(List.of(output, cost > 0 ? Integer.MAX_VALUE : 0, -3), writes);
        }
        var empty = new EventBus(); empty.addListener(AnvilUpdateEvent.class, event -> event.setCost(99));
        assertTrue(FabricEnchantingEvents.anvil(empty, left, right, null, null, ignored -> fail(), ignored -> fail(), ignored -> fail()));
    }

    @Test
    void grindstonePlacementSeparatesVanillaSentinelCustomXpAndCanceledOutput() {
        var top = new ItemStack(Items.DIAMOND_HOE); var bottom = new ItemStack(Items.DIAMOND_HOE);
        var vanilla = new EventBus(); vanilla.addListener(GrindstoneEvent.class, event -> event.setXp(80));
        var result = FabricEnchantingEvents.place(vanilla, top, bottom);
        assertTrue(result.vanilla()); assertEquals(Integer.MIN_VALUE, result.experience());
        var custom = new EventBus(); var output = top.copy();
        custom.addListener(GrindstoneEvent.OnPlaceItem.class, event -> {
            assertSame(top, event.getTopItem()); assertSame(bottom, event.getBottomItem()); assertEquals(-1, event.getXp());
            event.setOutput(output); event.setXp(0);
        });
        result = FabricEnchantingEvents.place(custom, top, bottom);
        assertFalse(result.vanilla()); assertSame(output, result.output()); assertEquals(0, result.experience());
        custom.addListener(GrindstoneEvent.OnPlaceItem.class, event -> event.setCanceled(true));
        result = FabricEnchantingEvents.place(custom, top, bottom);
        assertFalse(result.vanilla()); assertTrue(result.output().isEmpty()); assertEquals(-1, result.experience());
        assertEquals(Integer.MIN_VALUE, FabricEnchantingEvents.place(vanilla, top, bottom).experience());
    }

    @Test
    void takingUsesPostEventXpAndReplacementsAfterSoundWhileCancelKeepsInputsAndHasNoEffects() {
        List<String> order = new ArrayList<>();
        var inputs = new SimpleContainer(2) { @Override public void setChanged() { order.add("changed"); } };
        var top = new ItemStack(Items.DIAMOND_HOE); var bottom = new ItemStack(Items.DIAMOND_HOE);
        inputs.setItem(0, top); inputs.setItem(1, bottom); order.clear();
        var bus = new EventBus(); var replacement = new ItemStack(Items.APPLE);
        bus.addListener(GrindstoneEvent.OnTakeItem.class, event -> {
            assertSame(top, event.getTopItem()); assertSame(bottom, event.getBottomItem()); assertEquals(9, event.getXp());
            assertSame(ContainerLevelAccess.NULL, event.getContainerAccess()); assertNull(event.getPlayer());
            order.add("event"); event.setXp(0); event.setNewTopItem(replacement);
        });
        FabricEnchantingEvents.take(bus, inputs, ContainerLevelAccess.NULL, null, 9,
                xp -> { assertEquals(0, xp); assertSame(top, inputs.getItem(0)); order.add("xp"); },
                () -> { assertSame(bottom, inputs.getItem(1)); order.add("sound"); });
        assertSame(replacement, inputs.getItem(0)); assertSame(ItemStack.EMPTY, inputs.getItem(1));
        assertEquals(List.of("event", "xp", "sound", "changed", "changed", "changed"), order);
        inputs.setItem(0, top); inputs.setItem(1, bottom); order.clear();
        bus.addListener(GrindstoneEvent.OnTakeItem.class, event -> event.setCanceled(true));
        FabricEnchantingEvents.take(bus, inputs, ContainerLevelAccess.NULL, null, 9, ignored -> fail(), () -> fail());
        assertSame(top, inputs.getItem(0)); assertSame(bottom, inputs.getItem(1)); assertEquals(List.of("event"), order);
    }

    @Test
    void enchantEventExposesTheAlreadyChangedStackAndOriginalMutableListThroughPlayerInheritance() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var player = (ServerPlayer) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ServerPlayer.class);
        var stack = new ItemStack(Items.ENCHANTED_BOOK); stack.set(DataComponents.REPAIR_COST, 7);
        var enchantments = new ArrayList<EnchantmentInstance>(); enchantments.add(null);
        var bus = new EventBus(); List<String> order = new ArrayList<>();
        bus.addListener(PlayerEvent.class, event -> { assertSame(player, event.getEntity()); order.add("player"); });
        bus.addListener(PlayerEnchantItemEvent.class, event -> {
            assertFalse(event.isCancelable()); assertSame(stack, event.getEnchantedItem()); assertEquals(7, stack.get(DataComponents.REPAIR_COST));
            assertSame(enchantments, event.getEnchantments()); event.getEnchantments().clear(); stack.set(DataComponents.REPAIR_COST, 0); order.add("enchant");
        });
        FabricEnchantingEvents.enchanted(bus, player, stack, enchantments);
        assertTrue(enchantments.isEmpty()); assertEquals(0, stack.get(DataComponents.REPAIR_COST)); assertEquals(List.of("player", "enchant"), order);
    }
}
