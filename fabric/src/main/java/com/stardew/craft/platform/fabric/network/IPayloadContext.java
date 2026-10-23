package com.stardew.craft.platform.fabric.network;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;

/** The platform-owned subset of the payload context used by StardewCraft. */
public interface IPayloadContext {
    PacketListener listener();

    Connection connection();

    /** @throws UnsupportedOperationException during configuration, before a player exists. */
    Player player();

    void reply(CustomPacketPayload payload);

    void disconnect(Component reason);

    CompletableFuture<Void> enqueueWork(Runnable task);

    <T> CompletableFuture<T> enqueueWork(Supplier<T> task);

    PacketFlow flow();

    ConnectionProtocol protocol();

    void handle(CustomPacketPayload payload);

    /** Only the active server configuration task may be completed. */
    void finishCurrentTask(ConfigurationTask.Type type);

    default void finishCurrentTask(ICustomConfigurationTask.Type type) {
        finishCurrentTask(type.vanillaType());
    }
}
