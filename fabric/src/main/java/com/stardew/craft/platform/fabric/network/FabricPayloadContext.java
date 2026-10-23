package com.stardew.craft.platform.fabric.network;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** No client classes appear in this common context's fields, signatures or implementation. */
public final class FabricPayloadContext implements IPayloadContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("stardewcraft/fabric-network");
    private final PacketListener listener;
    private final Connection connection;
    private final Player player;
    private final Consumer<CustomPacketPayload> sender;
    private final Consumer<Component> disconnect;
    private final Executor mainThread;
    private final BooleanSupplier isMainThread;
    private final PacketFlow flow;
    private final ConnectionProtocol protocol;
    private final Consumer<ConfigurationTask.Type> taskCompletion;

    public FabricPayloadContext(PacketListener listener, Connection connection, Player player,
            Consumer<CustomPacketPayload> sender, Consumer<Component> disconnect,
            Executor mainThread, BooleanSupplier isMainThread, PacketFlow flow,
            ConnectionProtocol protocol, Consumer<ConfigurationTask.Type> taskCompletion) {
        this.listener = Objects.requireNonNull(listener, "Packet listener");
        this.connection = Objects.requireNonNull(connection, "Connection");
        this.player = player;
        this.sender = Objects.requireNonNull(sender, "Reply sender");
        this.disconnect = Objects.requireNonNull(disconnect, "Disconnect handler");
        this.mainThread = Objects.requireNonNull(mainThread, "Main thread executor");
        this.isMainThread = Objects.requireNonNull(isMainThread, "Main thread predicate");
        this.flow = Objects.requireNonNull(flow, "Packet flow");
        this.protocol = Objects.requireNonNull(protocol, "Connection protocol");
        this.taskCompletion = taskCompletion;
    }

    @Override public PacketListener listener() { return listener; }
    @Override public Connection connection() { return connection; }
    @Override public PacketFlow flow() { return flow; }
    @Override public ConnectionProtocol protocol() { return protocol; }

    @Override
    public Player player() {
        if (player == null) {
            throw new UnsupportedOperationException("A player is not available during configuration");
        }
        return player;
    }

    @Override
    public void reply(CustomPacketPayload payload) {
        Objects.requireNonNull(payload, "Reply payload");
        FabricNetwork.requireRegistered(payload.type(), protocol, flow.getOpposite());
        sender.accept(payload);
    }

    @Override
    public void disconnect(Component reason) {
        disconnect.accept(Objects.requireNonNull(reason, "Disconnect reason"));
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        Objects.requireNonNull(task, "Main thread task");
        return enqueueWork(() -> { task.run(); return null; });
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        Objects.requireNonNull(task, "Main thread task");
        // Gameplay handlers routinely ignore this future. A same-thread failure must
        // reach the dispatcher's error handling immediately, as it did in NeoForge.
        if (isMainThread.getAsBoolean()) {
            return CompletableFuture.completedFuture(task.get());
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable action = () -> {
            try {
                result.complete(task.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        };
        try {
            mainThread.execute(action);
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        result.whenComplete((value, failure) -> {
            if (failure != null) LOGGER.error("Exception in queued {} / {} payload work", protocol, flow, failure);
        });
        return result;
    }

    @Override
    public void handle(CustomPacketPayload payload) {
        FabricNetwork.dispatch(payload, this);
    }

    @Override
    public void finishCurrentTask(ConfigurationTask.Type type) {
        if (taskCompletion == null || protocol != ConnectionProtocol.CONFIGURATION
                || flow != PacketFlow.SERVERBOUND) {
            throw new UnsupportedOperationException("Only a server configuration context can finish a task");
        }
        if (!isMainThread.getAsBoolean()) {
            throw new IllegalStateException("Configuration task completion must run on the server thread");
        }
        taskCompletion.accept(Objects.requireNonNull(type, "Configuration task type"));
    }
}
