package com.stardew.craft.port.net.neoforged.neoforge.network.handling;

import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.minecraft.server.network.ConfigurationTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;

/**
 * NeoForge 21.1 payload handling context, implemented on Forge 1.20.1 by
 * {@code com.stardew.craft.port.internal.network.PortPayloadContext}.
 *
 * <p>Handlers registered through {@code PayloadRegistrar} already run on the receiving side's main
 * thread (NeoForge default); {@link #enqueueWork} then runs the task immediately, otherwise it is
 * scheduled on that main thread.
 */
public interface IPayloadContext {
    /** The connection the payload arrived on. */
    Connection connection();

    /**
     * The player: the sender on the server, the local player on the client.
     *
     * @throws UnsupportedOperationException during the login/configuration handshake
     */
    Player player();

    /** Sends a payload back over the same connection, in the same phase. */
    void reply(CustomPacketPayload payload);

    /** Disconnects the connection with the given reason. */
    void disconnect(Component reason);

    CompletableFuture<Void> enqueueWork(Runnable task);

    <T> CompletableFuture<T> enqueueWork(Supplier<T> task);

    /** Marks a configuration task as completed. Only valid for server-side configuration payloads. */
    void finishCurrentTask(ConfigurationTask.Type type);

    /** Direction the payload travelled in. */
    PacketFlow flow();
}
