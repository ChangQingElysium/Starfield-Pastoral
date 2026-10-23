package com.stardew.craft.platform.fabric.network;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NetworkReconfigurationTest {
    @Test
    void configurationResetDropsPartialTransferBeforeOldTimeoutAndNewPlayTraffic() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        AtomicInteger disconnects = new AtomicInteger();
        PacketListener listener = (PacketListener) Proxy.newProxyInstance(PacketListener.class.getClassLoader(),
                new Class<?>[] {PacketListener.class}, (proxy, method, arguments) -> null);
        var context = new FabricPayloadContext(listener, connection, null,
                payload -> fail("Unexpected send"), reason -> disconnects.incrementAndGet(),
                Runnable::run, () -> true, PacketFlow.SERVERBOUND, ConnectionProtocol.PLAY, null);
        try {
            var id = ResourceLocation.parse("test:reconfiguration");
            byte[] bytes = new byte[40_000];
            var oldFrames = FabricFragmentation.split(id, bytes, PacketFlow.SERVERBOUND, 10, 20);
            FragmentAssembler oldState = FabricFragmentation.assembler(context);
            long staleStart = System.nanoTime() - FragmentAssembler.TIMEOUT_NANOS - 1;
            assertNull(oldState.accept((FragmentPayload) oldFrames.getFirst(), staleStart));
            assertEquals(bytes.length, oldState.reservedBytes());
            assertTrue(oldState.isExpired(System.nanoTime()));

            // This is also the BEFORE_CONFIGURE reset. Fabric changes its listener without
            // delivering DISCONNECT, so relying on the disconnect callbacks would leave this state.
            FabricNetwork.forget(connection);
            FabricFragmentation.tick(PacketFlow.SERVERBOUND);
            assertEquals(0, disconnects.get());

            FragmentAssembler newState = FabricFragmentation.assembler(context);
            assertNotSame(oldState, newState);
            assertEquals(0, newState.reservedBytes());
            var newFrames = FabricFragmentation.split(id, bytes, PacketFlow.SERVERBOUND, 11, 21);
            long now = System.nanoTime();
            assertNull(newState.accept((FragmentPayload) newFrames.getFirst(), now));
            assertNotNull(newState.accept((FragmentPayload) newFrames.getLast(), now));
            assertEquals(0, disconnects.get());
        } finally {
            FabricNetwork.forget(connection);
        }
    }
}
