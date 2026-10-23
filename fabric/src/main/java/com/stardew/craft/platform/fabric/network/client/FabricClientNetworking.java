package com.stardew.craft.platform.fabric.network.client;

import com.stardew.craft.platform.mixin.network.client.ClientCommonConnectionAccessor;

import com.stardew.craft.platform.fabric.network.FabricNetwork;
import com.stardew.craft.platform.fabric.network.FabricPayloadContext;
import com.stardew.craft.platform.fabric.network.FabricFragmentation;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-only linkage is confined here and to the client-only connection accessor. */
@Environment(EnvType.CLIENT)
public final class FabricClientNetworking {
    public static void initialize() {
        for (FabricNetwork.Registration<?> registration : FabricNetwork.clientRegistrations()) {
            register(registration);
        }
        ClientConfigurationConnectionEvents.INIT.register((handler, client) ->
                FabricNetwork.forget(((ClientCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) ->
                FabricNetwork.forget(((ClientCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                FabricNetwork.forget(((ClientCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> FabricFragmentation.tick(PacketFlow.CLIENTBOUND));
    }

    private static <T extends CustomPacketPayload> void register(FabricNetwork.Registration<T> registration) {
        boolean registered;
        if (registration.protocol() == ConnectionProtocol.PLAY) {
            registered = ClientPlayNetworking.registerGlobalReceiver(registration.type(), playReceiver(
                    context -> context.client().getConnection() == null ? null : ClientPlayNetworking.getSender(),
                    (payload, context) -> {
                        var player = context.player();
                        var listener = player.connection;
                        FabricNetwork.dispatch(payload, new FabricPayloadContext(listener,
                                ((ClientCommonConnectionAccessor) listener).stardewcraft$getConnection(), player,
                                context.responseSender()::sendPacket, context.responseSender()::disconnect,
                                context.client(), context.client()::isSameThread, PacketFlow.CLIENTBOUND,
                                ConnectionProtocol.PLAY, null));
                    }));
        } else if (registration.protocol() == ConnectionProtocol.CONFIGURATION) {
            registered = ClientConfigurationNetworking.registerGlobalReceiver(registration.type(), (payload, context) -> {
                var listener = context.networkHandler();
                FabricNetwork.dispatch(payload, new FabricPayloadContext(listener,
                        ((ClientCommonConnectionAccessor) listener).stardewcraft$getConnection(), null,
                        context.responseSender()::sendPacket, context.responseSender()::disconnect,
                        context.client(), context.client()::isSameThread, PacketFlow.CLIENTBOUND,
                        ConnectionProtocol.CONFIGURATION, null));
            });
        } else {
            throw new IllegalArgumentException("Unsupported payload protocol: " + registration.protocol());
        }
        if (!registered) {
            throw new IllegalStateException("Fabric client receiver already exists: " + registration.type().id());
        }
    }

    /** FAPI queues play receivers, but Context.player() resolves the player's global value at execution time. */
    static <T extends CustomPacketPayload> ClientPlayNetworking.PlayPayloadHandler<T> playReceiver(
            Function<ClientPlayNetworking.Context, PacketSender> currentSender, ClientPlayNetworking.PlayPayloadHandler<T> work) {
        // The sender belongs to the receiving addon. Check it before reading the player:
        // queued work may outlive logout or a transition to another play listener.
        return (payload, context) -> {
            PacketSender current = currentSender.apply(context);
            if (current == null || current != context.responseSender()) return;
            work.receive(payload, context);
        };
    }

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... additional) {
        send(payload);
        for (CustomPacketPayload next : additional) {
            send(next);
        }
    }

    private static void send(CustomPacketPayload payload) {
        FabricNetwork.requireRegistered(payload.type(), ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND);
        var client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            throw new IllegalStateException("Serverbound payload distribution must run on the client thread");
        }
        if (!ClientPlayNetworking.canSend(payload.type())) {
            throw new IllegalStateException("Server has not announced payload " + payload.type().id());
        }
        var listener = java.util.Objects.requireNonNull(client.getConnection(), "Play connection");
        for (CustomPacketPayload frame : FabricFragmentation.prepare(payload, listener.registryAccess(),
                PacketFlow.SERVERBOUND)) {
            ClientPlayNetworking.send(frame);
        }
    }

    private FabricClientNetworking() {}
}
