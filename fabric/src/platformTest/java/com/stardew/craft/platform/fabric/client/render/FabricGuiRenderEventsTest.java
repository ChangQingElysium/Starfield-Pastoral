package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.client.event.RenderGuiEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import org.junit.jupiter.api.Test;

class FabricGuiRenderEventsTest {
    @Test
    void preVanillaAndPostRunInOrderWithTheActualFrameTimer() {
        var bus = new EventBus();
        List<String> order = new ArrayList<>();
        bus.addListener(RenderGuiEvent.Pre.class, event -> {
            assertSame(DeltaTracker.ONE, event.getPartialTick());
            assertTrue(event.isCancelable());
            order.add("pre");
        });
        bus.addListener(RenderGuiEvent.Post.class, event -> {
            assertSame(DeltaTracker.ONE, event.getPartialTick());
            assertFalse(event.isCancelable());
            order.add("post");
        });
        FabricGuiRenderEvents.render(bus, null, DeltaTracker.ONE, () -> order.add("vanilla"));
        assertEquals(List.of("pre", "vanilla", "post"), order);
    }

    @Test
    void canceledPreSuppressesBothTheHudBodyAndPost() {
        var bus = new EventBus();
        bus.addListener(RenderGuiEvent.Pre.class, event -> event.setCanceled(true));
        bus.addListener(RenderGuiEvent.Post.class, event -> fail("post fired after cancellation"));
        FabricGuiRenderEvents.render(bus, null, DeltaTracker.ONE, () -> fail("canceled HUD rendered"));
    }

    @Test
    void failedRenderingDoesNotProduceASuccessfulPost() {
        var bus = new EventBus();
        bus.addListener(RenderGuiEvent.Post.class, event -> fail("post fired after renderer failure"));
        var failure = new IllegalStateException("broken renderer");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> FabricGuiRenderEvents.render(
                bus, null, DeltaTracker.ONE, () -> { throw failure; })));
    }
}
