package com.stardew.craft.platform.fabric.client.event.screen;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.client.event.ScreenEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ScreenEventsTest {
    @Test
    void canceledRenderStillPostsAfterEventAndKeepsTheCallerCoordinatesAndPartialTick() {
        var bus = new EventBus(); var screen = new TestScreen(); List<String> order = new ArrayList<>();
        bus.addListener(ScreenEvent.Render.Pre.class, event -> {
            assertSame(screen, event.getScreen()); assertEquals(-3, event.getMouseX()); assertEquals(27, event.getMouseY());
            assertEquals(.375f, event.getPartialTick()); event.setCanceled(true); order.add("pre");
        });
        bus.addListener(ScreenEvent.Render.Post.class, event -> { order.add("post"); assertFalse(event.isCancelable()); });
        FabricScreenEvents.render(bus, screen, null, -3, 27, .375f, () -> fail("canceled screen rendered"));
        assertEquals(List.of("pre", "post"), order);
        var normal = new EventBus(); order.clear();
        normal.addListener(ScreenEvent.Render.Pre.class, event -> order.add("pre"));
        normal.addListener(ScreenEvent.Render.Post.class, event -> order.add("post"));
        FabricScreenEvents.render(normal, screen, null, 0, 0, 0, () -> order.add("native"));
        assertEquals(List.of("pre", "native", "post"), order);
    }

    @Test
    void openingMayCancelReplaceOrCloseWithoutRemovingTheOldScreenPrematurely() {
        var old = new TestScreen(); var requested = new TestScreen(); var replacement = new TestScreen();
        var cancel = new EventBus();
        cancel.addListener(ScreenEvent.Opening.class, event -> { assertEquals(0, old.removed); assertSame(old, event.getCurrentScreen()); event.setCanceled(true); });
        var unchanged = FabricScreenEvents.transition(cancel, old, requested);
        assertTrue(unchanged.canceled()); assertSame(old, unchanged.target()); assertEquals(0, old.removed);
        var replace = new EventBus(); List<String> order = new ArrayList<>();
        replace.addListener(ScreenEvent.Opening.class, event -> { assertSame(requested, event.getScreen()); event.setNewScreen(replacement); order.add("opening"); });
        replace.addListener(ScreenEvent.Closing.class, event -> { assertSame(old, event.getScreen()); assertEquals(0, old.removed); order.add("closing"); });
        var changed = FabricScreenEvents.transition(replace, old, requested);
        assertFalse(changed.canceled()); assertSame(replacement, changed.target()); assertEquals(1, old.removed);
        assertEquals(List.of("opening", "closing"), order);
        var same = new EventBus(); FabricScreenEvents.transition(same, replacement, replacement); assertEquals(0, replacement.removed);
        same.addListener(ScreenEvent.Opening.class, event -> event.setNewScreen(null));
        assertNull(FabricScreenEvents.transition(same, replacement, requested).target()); assertEquals(1, replacement.removed);
    }

    @Test
    void mousePreCancellationSkipsNativeAndPostWhilePostPreservesOrOverridesNativeHandledResult() {
        var screen = new TestScreen(); var cancel = new EventBus(); var calls = new AtomicInteger();
        cancel.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> {
            assertEquals(12.375, event.getMouseX()); assertEquals(-4.25, event.getMouseY()); assertEquals(2, event.getButton()); event.setCanceled(true);
        });
        cancel.addListener(ScreenEvent.MouseButtonPressed.Post.class, event -> fail("post after canceled pre"));
        assertTrue(FabricScreenEvents.click(cancel, screen, 12.375, -4.25, 2, () -> { calls.incrementAndGet(); return false; }));
        assertEquals(0, calls.get());
        var handled = new EventBus();
        handled.addListener(ScreenEvent.MouseButtonPressed.Post.class, event -> {
            assertTrue(event.wasClickHandled()); assertSame(ScreenEvent.MouseButtonPressed.Post.Result.DEFAULT, event.getResult());
            event.setResult(ScreenEvent.MouseButtonPressed.Post.Result.FORCE_UNHANDLED);
        });
        assertFalse(FabricScreenEvents.click(handled, screen, 0, 0, 0, () -> true));
        var release = new EventBus();
        release.addListener(ScreenEvent.MouseButtonReleased.Post.class, event -> { assertFalse(event.wasReleaseHandled()); event.setResult(ScreenEvent.MouseButtonReleased.Post.Result.FORCE_HANDLED); });
        assertTrue(FabricScreenEvents.release(release, screen, 0, 0, 0, () -> false));
    }

    @Test
    void dragScrollAndKeyPostOnlyFollowAnUnhandledNativeCallback() {
        var bus = new EventBus(); var screen = new TestScreen(); List<String> order = new ArrayList<>();
        bus.addListener(ScreenEvent.MouseDragged.Post.class, event -> { assertEquals(.125, event.getDragX()); assertEquals(-.75, event.getDragY()); order.add("dragPost"); });
        assertTrue(FabricScreenEvents.drag(bus, screen, 1, 2, 0, .125, -.75, () -> true));
        assertTrue(order.isEmpty(), "Neo does not post after a handled native drag");
        assertFalse(FabricScreenEvents.drag(bus, screen, 1, 2, 0, .125, -.75, () -> false));
        bus.addListener(ScreenEvent.MouseScrolled.Pre.class, event -> { assertEquals(-.5, event.getScrollDeltaX()); assertEquals(1.75, event.getScrollDeltaY()); order.add("scrollPre"); });
        bus.addListener(ScreenEvent.MouseScrolled.Post.class, event -> order.add("scrollPost"));
        assertTrue(FabricScreenEvents.scroll(bus, screen, 1, 2, -.5, 1.75, () -> true));
        assertFalse(FabricScreenEvents.scroll(bus, screen, 1, 2, -.5, 1.75, () -> false));
        bus.addListener(ScreenEvent.KeyPressed.Post.class, event -> { assertEquals(72, event.getScanCode()); assertEquals(4, event.getModifiers()); event.setCanceled(true); order.add("keyPost"); });
        assertTrue(FabricScreenEvents.key(bus, screen, -1, 72, 4, true, () -> false));
        assertTrue(FabricScreenEvents.key(bus, screen, -1, 72, 4, true, () -> true));
        bus.addListener(ScreenEvent.KeyReleased.Pre.class, event -> { assertEquals(8, event.getKeyCode()); event.setCanceled(true); });
        assertTrue(FabricScreenEvents.key(bus, screen, 8, 0, 0, false, () -> { fail("canceled key forwarded"); return false; }));
        assertEquals(List.of("dragPost", "scrollPre", "scrollPre", "scrollPost", "keyPost"), order);
    }

    @Test
    void canceledDragPreSkipsBothTheOriginalOperationAndPost() {
        var bus = new EventBus();
        bus.addListener(ScreenEvent.MouseDragged.Pre.class, event -> event.setCanceled(true));
        bus.addListener(ScreenEvent.MouseDragged.Post.class, event -> fail("post after canceled drag pre"));
        assertTrue(FabricScreenEvents.drag(bus, new TestScreen(), 1, 2, 0, .5, -.25,
                () -> { fail("canceled drag forwarded"); return false; }));
    }
    private static final class TestScreen extends Screen {
        int removed;
        TestScreen() { super(Component.literal("test")); }
        @Override public void removed() { removed++; }
    }
}
