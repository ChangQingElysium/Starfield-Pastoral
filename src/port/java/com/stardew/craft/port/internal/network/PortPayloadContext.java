package com.stardew.craft.port.internal.network;

import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.minecraft.server.network.ConfigurationTask;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.fml.LogicalSide;

/**
 * {@link IPayloadContext} for both logical sides. Contains no client classes: the client player and
 * the client send path are reached through {@link PortClientNetwork} only when the receiving side
 * is the client, so a dedicated server never loads them.
 */
public final class PortPayloadContext implements IPayloadContext {
    private final Connection connection;
    private final LogicalSide side;
    private final boolean login;
    @Nullable
    private final ServerPlayer serverPlayer;
    private final Consumer<CustomPacketPayload> replySink;
    @Nullable
    private final Set<String> finishedTasks;
    private volatile boolean disconnected;

    private PortPayloadContext(Connection connection, LogicalSide side, boolean login, @Nullable ServerPlayer serverPlayer,
            Consumer<CustomPacketPayload> replySink, @Nullable Set<String> finishedTasks) {
        this.connection = connection;
        this.side = side;
        this.login = login;
        this.serverPlayer = serverPlayer;
        this.replySink = replySink;
        this.finishedTasks = finishedTasks;
    }

    /** Play-phase payload received by the server from {@code sender}. */
    static PortPayloadContext serverPlay(Connection connection, @Nullable ServerPlayer sender) {
        return new PortPayloadContext(connection, LogicalSide.SERVER, false, sender, payload -> {
            if (sender == null) {
                throw new IllegalStateException("Cannot reply: no player bound to this connection");
            }
            PortNetwork.sendToPlayer(sender, payload);
        }, null);
    }

    /** Play-phase payload received by the client. */
    static PortPayloadContext clientPlay(Connection connection) {
        return new PortPayloadContext(connection, LogicalSide.CLIENT, false, null, PortNetwork::sendToServer, null);
    }

    /** Payload exchanged during the Forge login handshake (NeoForge configuration phase). */
    static PortPayloadContext login(Connection connection, LogicalSide side, Consumer<CustomPacketPayload> replySink,
            @Nullable Set<String> finishedTasks) {
        return new PortPayloadContext(connection, side, true, null, replySink, finishedTasks);
    }

    boolean isDisconnected() {
        return this.disconnected || !this.connection.isConnected();
    }

    @Override
    public Connection connection() {
        return this.connection;
    }

    @Override
    public Player player() {
        if (this.login) {
            throw new UnsupportedOperationException("No player is available during the login/configuration handshake");
        }
        return this.side.isClient() ? PortClientNetwork.localPlayer() : this.serverPlayer;
    }

    @Override
    public void reply(CustomPacketPayload payload) {
        this.replySink.accept(payload);
    }

    @Override
    public void disconnect(Component reason) {
        this.disconnected = true;
        PortNetwork.disconnect(this.connection, this.side, reason);
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        BlockableEventLoop<?> executor = this.executor();
        if (executor.isSameThread()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        return executor.submitAsync(task).exceptionally(this::onTaskFailure);
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        BlockableEventLoop<?> executor = this.executor();
        if (executor.isSameThread()) {
            return CompletableFuture.completedFuture(task.get());
        }
        return executor.submit(task).exceptionally(this::onTaskFailure);
    }

    private <T> T onTaskFailure(Throwable failure) {
        PortNetwork.LOGGER.error("Failed to process a main-thread payload task", failure);
        this.disconnect(Component.literal("StardewCraft failed to process a network task: " + failure));
        return null;
    }

    private BlockableEventLoop<?> executor() {
        return LogicalSidedProvider.WORKQUEUE.get(this.side);
    }

    @Override
    public void finishCurrentTask(ConfigurationTask.Type type) {
        if (!this.login || this.side.isClient() || this.finishedTasks == null) {
            throw new UnsupportedOperationException("Configuration tasks can only be finished by the server during login");
        }
        this.finishedTasks.add(type.id());
    }

    @Override
    public PacketFlow flow() {
        return this.side.isClient() ? PacketFlow.CLIENTBOUND : PacketFlow.SERVERBOUND;
    }
}
