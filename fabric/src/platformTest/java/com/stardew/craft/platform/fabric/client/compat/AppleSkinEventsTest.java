package com.stardew.craft.platform.fabric.client.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import squeek.appleskin.api.event.HUDOverlayEvent;

class AppleSkinEventsTest {
    @Test
    void allFourActualFabricCallbacksReceiveTheOriginalOptionalApiEventAndSetItsRealCancellationFlag() {
        List<HUDOverlayEvent> received = new ArrayList<>();
        java.util.function.Consumer<HUDOverlayEvent> handler = event -> { received.add(event); FabricAppleSkinEvents.setCanceled(event, true); };
        FabricAppleSkinEvents.install(handler::accept, handler::accept, handler::accept, handler::accept);
        var saturation = new HUDOverlayEvent.Saturation(2, 13, 17, null);
        var exhaustion = new HUDOverlayEvent.Exhaustion(1, 13, 17, null);
        var hunger = new HUDOverlayEvent.HungerRestored(10, null, null, 13, 17, null);
        var health = new HUDOverlayEvent.HealthRestored(7, null, null, 13, 17, null);
        HUDOverlayEvent.Saturation.EVENT.invoker().interact(saturation); HUDOverlayEvent.Exhaustion.EVENT.invoker().interact(exhaustion);
        HUDOverlayEvent.HungerRestored.EVENT.invoker().interact(hunger); HUDOverlayEvent.HealthRestored.EVENT.invoker().interact(health);
        assertEquals(List.of(saturation, exhaustion, hunger, health), received);
        for (var event : received) { assertTrue(event.isCanceled); assertEquals(13, event.x); assertEquals(17, event.y); }
        FabricAppleSkinEvents.setCanceled(saturation, false); assertFalse(saturation.isCanceled);
        assertThrows(IllegalStateException.class, () -> FabricAppleSkinEvents.install(handler::accept, handler::accept, handler::accept, handler::accept));
    }
}
