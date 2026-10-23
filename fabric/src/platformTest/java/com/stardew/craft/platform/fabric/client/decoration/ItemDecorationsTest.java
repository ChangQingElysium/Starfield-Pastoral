package com.stardew.craft.platform.fabric.client.decoration;

import static org.junit.jupiter.api.Assertions.*;
import com.stardew.craft.platform.fabric.client.event.RegisterItemDecorationsEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ItemDecorationsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test
    void registrationAppendsOrderedCallbacksAndFreezesTheItemListsAfterTheModEvent() {
        var bus = new EventBus(); IItemDecorator first = (graphics, font, stack, x, y) -> false; IItemDecorator second = (graphics, font, stack, x, y) -> true;
        bus.addListener(RegisterItemDecorationsEvent.class, event -> { event.register(Items.BOW, first); event.register(Items.BOW, second); });
        var registered = FabricItemDecorations.register(bus); assertEquals(List.of(first, second), registered.get(Items.BOW));
        assertThrows(UnsupportedOperationException.class, () -> registered.get(Items.BOW).clear());
        assertThrows(UnsupportedOperationException.class, registered::clear);
    }
    @Test
    void callbacksRunAfterTheNativeCallerWithStateResetRequestsAndFinallyRestoreOnFailure() {
        List<String> order = new ArrayList<>(); var stack = new ItemStack(Items.BOW); var states = states(order);
        List<IItemDecorator> callbacks = List.of((graphics, font, actual, x, y) -> {
            assertSame(stack, actual); assertEquals(9, x); assertEquals(12, y); order.add("first"); return true;
        }, (graphics, font, actual, x, y) -> { order.add("second"); return false; });
        FabricItemDecorations.render(callbacks, null, null, stack, 9, 12, states);
        assertEquals(List.of("backup", "reset", "first", "reset", "second", "restore"), order);
        order.clear();
        assertThrows(IllegalArgumentException.class, () -> FabricItemDecorations.render(List.of((graphics, font, actual, x, y) -> {
            throw new IllegalArgumentException("decorator failed");
        }), null, null, stack, 0, 0, states));
        assertEquals(List.of("backup", "reset", "restore"), order);
        order.clear(); FabricItemDecorations.render(List.of(), null, null, stack, 0, 0, states); assertTrue(order.isEmpty());
    }
    @Test
    void recursiveAmmoDecorationsKeepSeparateStateSnapshotsAndDoNotSuppressOtherCallbacks() {
        List<String> order = new ArrayList<>(); var snapshots = new AtomicInteger();
        var states = new FabricItemDecorations.StateAccess() {
            public Runnable backup() { int id = snapshots.incrementAndGet(); order.add("backup" + id); return () -> order.add("restore" + id); }
            public void reset() { order.add("reset"); }
        };
        IItemDecorator ammo = (graphics, font, stack, x, y) -> { order.add("ammo"); return false; };
        IItemDecorator slingshot = (graphics, font, stack, x, y) -> {
            FabricItemDecorations.render(List.of(ammo), graphics, font, new ItemStack(Items.ARROW), x, y, states); return true;
        };
        FabricItemDecorations.render(List.of(slingshot), null, null, new ItemStack(Items.BOW), 0, 0, states);
        assertEquals(List.of("backup1", "reset", "backup2", "reset", "ammo", "restore2", "reset", "restore1"), order);
    }
    private static FabricItemDecorations.StateAccess states(List<String> order) {
        return new FabricItemDecorations.StateAccess() { public Runnable backup() { order.add("backup"); return () -> order.add("restore"); } public void reset() { order.add("reset"); } };
    }
}
