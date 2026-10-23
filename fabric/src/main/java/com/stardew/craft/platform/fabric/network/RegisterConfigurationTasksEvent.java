package com.stardew.craft.platform.fabric.network;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/** Runs after protocol negotiation, while Fabric's early configuration task queue is still active. */
public final class RegisterConfigurationTasksEvent extends com.stardew.craft.platform.fabric.event.Event implements ModBusEvent {
    private final ServerConfigurationPacketListenerImpl handler;
    private final MinecraftServer server;
    private final Listener listener;
    private final Set<ConfigurationTask.Type> taskTypes = new HashSet<>();

    RegisterConfigurationTasksEvent(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        this.handler = Objects.requireNonNull(handler, "Configuration handler");
        this.server = Objects.requireNonNull(server, "Server");
        this.listener = new Listener(handler);
    }

    public Listener getListener() { return listener; }

    public MinecraftServer getServer() { return server; }

    public void register(ICustomConfigurationTask task) {
        Objects.requireNonNull(task, "Configuration task");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Configuration tasks must be registered on the server thread");
        }
        ConfigurationTask.Type type = task.type().vanillaType();
        if (type.equals(FabricNetwork.PROTOCOL_TASK_TYPE) || !taskTypes.add(type)) {
            throw new IllegalArgumentException("Duplicate/reserved configuration task type: " + type);
        }
        ((FabricServerConfigurationNetworkHandler) handler).addTask(new ConfigurationTask() {
            @Override public Type type() { return type; }

            @Override
            public void start(Consumer<Packet<?>> sender) {
                Runnable run = () -> {
                    if (!FabricNetwork.connection(handler).isConnected()) {
                        return;
                    }
                    try {
                        task.run(payload -> {
                            FabricNetwork.requireRegistered(payload.type(),
                                    ConnectionProtocol.CONFIGURATION, PacketFlow.CLIENTBOUND);
                            if (!listener.hasChannel(payload.type())) {
                                throw new IllegalStateException("Client cannot receive configuration payload "
                                        + payload.type().id());
                            }
                            sender.accept(ServerConfigurationNetworking.createS2CPacket(payload));
                        });
                    } catch (RuntimeException failure) {
                        FabricNetwork.fail(handler, "Configuration task " + type + " failed", failure);
                    }
                };
                if (server.isSameThread()) {
                    run.run();
                } else {
                    server.execute(run);
                }
            }
        });
    }

    public static final class Listener {
        private final ServerConfigurationPacketListenerImpl handler;

        private Listener(ServerConfigurationPacketListenerImpl handler) { this.handler = handler; }

        public Connection getConnection() { return FabricNetwork.connection(handler); }

        public ServerConfigurationPacketListenerImpl getNetworkHandler() { return handler; }

        public boolean hasChannel(CustomPacketPayload.Type<?> type) { return hasChannel(type.id()); }

        /** Includes acknowledged C2S channels, which Fabric's canSend alone cannot describe. */
        public boolean hasChannel(ResourceLocation id) {
            return FabricNetwork.peerHasConfigurationChannel(handler, id);
        }

        public void disconnect(Component reason) { handler.disconnect(reason); }

        public void send(CustomPacketPayload payload) {
            FabricNetwork.requireRegistered(payload.type(), ConnectionProtocol.CONFIGURATION,
                    PacketFlow.CLIENTBOUND);
            if (!hasChannel(payload.type())) {
                throw new IllegalStateException("Client cannot receive configuration payload " + payload.type().id());
            }
            ServerConfigurationNetworking.send(handler, payload);
        }
    }
}
