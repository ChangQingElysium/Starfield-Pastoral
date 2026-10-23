package com.stardew.craft.platform.fabric.client.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientPlayerNetworkEventTest {
    @Test
    void nullableLogoutIsPublishedOnceEvenWithoutAnActivePlayerOrConnection() throws ReflectiveOperationException {
        var installed = FabricClientPlayerEvents.class.getDeclaredField("installedBus");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, null);
        try {
            var bus = new EventBus();
            List<ClientPlayerNetworkEvent.LoggingOut> seen = new ArrayList<>();
            bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, seen::add);
            FabricClientPlayerEvents.install(bus);
            FabricClientPlayerEvents.install(bus);
            assertThrows(IllegalStateException.class, () -> FabricClientPlayerEvents.install(new EventBus()));
            FabricClientPlayerEvents.postLoggingOut(null, null, null);
            assertEquals(1, seen.size());
            assertNull(seen.getFirst().getPlayer());
            assertNull(seen.getFirst().getMultiPlayerGameMode());
            assertNull(seen.getFirst().getConnection());
            assertFalse(seen.getFirst().isCancelable());
        } finally {
            installed.set(null, previous);
        }
    }
}
