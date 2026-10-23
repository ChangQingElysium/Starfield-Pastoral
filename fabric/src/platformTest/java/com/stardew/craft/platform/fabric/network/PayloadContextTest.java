package com.stardew.craft.platform.fabric.network;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ConfigurationTask;
import org.junit.jupiter.api.Test;

class PayloadContextTest {
    @Test
    void workRunsImmediatelyOnTheGameThreadAndReturnsItsValue() {
        List<Runnable> queue = new ArrayList<>();
        var context = context(true, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND, queue, null);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(1, context.enqueueWork((java.util.function.Supplier<Integer>) calls::incrementAndGet).join());
        assertTrue(queue.isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void nettyWorkIsQueuedWithoutBlockingAndExceptionsReachTheFuture() {
        List<Runnable> queue = new ArrayList<>();
        var context = context(false, ConnectionProtocol.CONFIGURATION, PacketFlow.SERVERBOUND, queue, null);
        var future = context.enqueueWork(() -> { throw new IllegalArgumentException("fixture"); });
        assertFalse(future.isDone());
        assertEquals(1, queue.size());
        queue.removeFirst().run();
        assertEquals("fixture", assertThrows(CompletionException.class, future::join).getCause().getMessage());
    }

    @Test
    void sameThreadFailurePropagatesImmediatelyEvenWhenACallerIgnoresTheFuture() {
        var context = context(true, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND, new ArrayList<>(), null);
        assertThrows(IllegalArgumentException.class,
                () -> context.enqueueWork(() -> { throw new IllegalArgumentException("fixture"); }));
    }

    @Test
    void configurationPlayerAccessIsExplicitlyUnsupported() {
        var context = context(true, ConnectionProtocol.CONFIGURATION, PacketFlow.SERVERBOUND,
                new ArrayList<>(), null);
        assertThrows(UnsupportedOperationException.class, context::player);
    }

    @Test
    void onlyServerConfigurationMayCompleteATaskOnTheGameThread() {
        ConfigurationTask.Type type = new ConfigurationTask.Type("test:task");
        AtomicReference<ConfigurationTask.Type> finished = new AtomicReference<>();
        var server = context(true, ConnectionProtocol.CONFIGURATION, PacketFlow.SERVERBOUND,
                new ArrayList<>(), finished::set);
        server.finishCurrentTask(new ICustomConfigurationTask.Type("test:task"));
        assertEquals(type, finished.get());

        var play = context(true, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND,
                new ArrayList<>(), finished::set);
        assertThrows(UnsupportedOperationException.class, () -> play.finishCurrentTask(type));
        var client = context(true, ConnectionProtocol.CONFIGURATION, PacketFlow.CLIENTBOUND,
                new ArrayList<>(), finished::set);
        assertThrows(UnsupportedOperationException.class, () -> client.finishCurrentTask(type));
        var netty = context(false, ConnectionProtocol.CONFIGURATION, PacketFlow.SERVERBOUND,
                new ArrayList<>(), finished::set);
        assertThrows(IllegalStateException.class, () -> netty.finishCurrentTask(type));
    }

    private static FabricPayloadContext context(boolean onMainThread, ConnectionProtocol protocol,
            PacketFlow flow, List<Runnable> queued, Consumer<ConfigurationTask.Type> finish) {
        PacketListener listener = (PacketListener) Proxy.newProxyInstance(PacketListener.class.getClassLoader(),
                new Class<?>[] {PacketListener.class}, (proxy, method, arguments) -> null);
        return new FabricPayloadContext(listener, new Connection(flow), null,
                payload -> fail("Unexpected send"), reason -> fail("Unexpected disconnect"), queued::add,
                () -> onMainThread, flow, protocol, finish);
    }
}
