package com.stardew.craft.platform.fabric.client.event.input;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.client.event.InputEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.EventPriority;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

class FabricInputEventsTest {
    @Test
    void installationIsIdempotentAndAllInputProducersPreservePayloadAndCancellation() {
        var bus = new EventBus();
        FabricInputEvents.install(bus);
        FabricInputEvents.install(bus);
        assertThrows(IllegalStateException.class, () -> FabricInputEvents.install(new EventBus()));
        List<String> order = new ArrayList<>();
        bus.addListener(InputEvent.Key.class, event -> {
            assertEquals(65, event.getKey());
            assertEquals(30, event.getScanCode());
            assertEquals(0, event.getAction());
            assertEquals(3, event.getModifiers());
            assertFalse(event.isCancelable());
            order.add("key");
        });
        bus.addListener(InputEvent.MouseButton.Pre.class, event -> {
            assertEquals(1, event.getButton());
            assertEquals(1, event.getAction());
            assertEquals(2, event.getModifiers());
            event.setCanceled(true);
            order.add("mouse-pre");
        });
        bus.addListener(InputEvent.MouseButton.Post.class, event -> {
            assertFalse(event.isCancelable());
            order.add("mouse-post");
        });
        bus.addListener(InputEvent.MouseScrollingEvent.class, event -> {
            assertEquals(-0.5, event.getScrollDeltaX());
            assertEquals(2.5, event.getScrollDeltaY());
            assertTrue(event.isLeftDown());
            assertFalse(event.isMiddleDown());
            assertTrue(event.isRightDown());
            assertEquals(701.25, event.getMouseX());
            assertEquals(450.5, event.getMouseY());
            event.setCanceled(true);
            order.add("scroll");
        });
        bus.addListener(EventPriority.HIGHEST, false, InputEvent.InteractionKeyMappingTriggered.class, event -> {
            assertEquals(InteractionHand.MAIN_HAND, event.getHand());
            assertTrue(event.isUseItem());
            assertFalse(event.isAttack());
            assertFalse(event.isPickBlock());
            event.setCanceled(true);
            event.setSwingHand(false);
            order.add("interaction");
        });
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, event -> fail("ordinary canceled listener ran"));
        FabricInputEvents.key(65, 30, 0, 3);
        assertTrue(FabricInputEvents.mousePre(1, 1, 2));
        FabricInputEvents.mousePost(1, 1, 2);
        assertTrue(FabricInputEvents.scroll(-0.5, 2.5, true, false, true, 701.25, 450.5));
        var interaction = FabricInputEvents.interaction(1, null, InteractionHand.MAIN_HAND);
        assertTrue(interaction.isCanceled());
        assertFalse(interaction.shouldSwingHand());
        assertEquals(List.of("key", "mouse-pre", "mouse-post", "scroll", "interaction"), order);
    }

    @Test
    void canceledHeldAttackRetainsCrackThenSwingUnlessExplicitlySuppressed() {
        var event = new InputEvent.InteractionKeyMappingTriggered(0, null, InteractionHand.MAIN_HAND);
        assertTrue(event.isAttack());
        assertTrue(event.shouldSwingHand());
        event.setCanceled(true);
        List<String> effects = new ArrayList<>();
        assertTrue(FabricInputEvents.consumeCanceled(event, () -> effects.add("crack"), () -> effects.add("swing")));
        assertEquals(List.of("crack", "swing"), effects);
        event.setSwingHand(false);
        assertTrue(FabricInputEvents.consumeCanceled(event, () -> fail("crack suppressed"), () -> fail("swing suppressed")));
    }

    @Test
    void uncanceledActionStillRunsWhenOnlyTheSwingIsSuppressed() {
        var event = new InputEvent.InteractionKeyMappingTriggered(1, null, InteractionHand.OFF_HAND);
        event.setSwingHand(false);
        assertFalse(FabricInputEvents.consumeCanceled(event, () -> fail("no canceled effects"), () -> fail("no canceled effects")));
        assertFalse(FabricInputEvents.shouldSwing(event));
        assertTrue(FabricInputEvents.shouldSwing(null));
        assertEquals(InteractionHand.OFF_HAND, event.getHand());
    }
}
