package com.stardew.craft.platform.fabric.event.client;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.ClientTickEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.junit.jupiter.api.Test;

class FabricClientEventsTest {
    @Test
    void realFabricTickInvokersPublishPreAndPostOncePerTickIncludingTheMenu() {
        EventBus bus = new EventBus();
        List<Class<?>> events = new ArrayList<>();
        bus.addListener(ClientTickEvent.class, event -> events.add(event.getClass()));
        FabricClientEvents.install(bus);
        FabricClientEvents.install(bus);
        assertThrows(IllegalStateException.class, () -> FabricClientEvents.install(new EventBus()));

        // The producer intentionally does not require a loaded world or player.
        // Invoke the actual Fabric callback arrays without starting Minecraft.
        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(null);
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(null);
        ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(null);
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(null);

        assertEquals(List.of(ClientTickEvent.Pre.class, ClientTickEvent.Post.class,
                ClientTickEvent.Pre.class, ClientTickEvent.Post.class), events);
    }

    @Test
    void commonTickMarkerTypesHaveNoClientLinkage() throws IOException {
        for (Class<?> type : List.of(ClientTickEvent.class, ClientTickEvent.Pre.class, ClientTickEvent.Post.class)) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream);
                String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytecode.contains("net/minecraft/client/"), type.getName());
                assertFalse(bytecode.contains("net/fabricmc/fabric/api/client/"), type.getName());
                assertFalse(bytecode.contains("platform/fabric/event/client/"), type.getName());
            }
        }
        assertFalse(new ClientTickEvent.Pre().isCancelable());
        assertFalse(new ClientTickEvent.Post().isCancelable());
    }
}
