package com.stardew.craft.platform.fabric.network;

import com.stardew.craft.platform.fabric.network.client.FabricClientNetworking;
import com.stardew.craft.platform.mixin.network.mixin.ServerCommonConnectionAccessor;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric registration and configuration negotiation for the existing Stardew payloads. */
public final class FabricNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("stardewcraft/fabric-network");
    static final ConfigurationTask.Type PROTOCOL_TASK_TYPE =
            new ConfigurationTask.Type("stardewcraft:fabric_protocol_v1");
    private static final Map<ProtocolManifest.Key, Registration<?>> REGISTRATIONS = new LinkedHashMap<>();
    private static final Map<Connection, Map<ProtocolManifest.Key, ProtocolManifest.Entry>> PEERS =
            new ConcurrentHashMap<>();
    private static final Map<Connection, Long> PENDING_PROTOCOL = new ConcurrentHashMap<>();
    private static Consumer<RegisterConfigurationTasksEvent> configurationTasks;
    private static boolean initialized;
    private static boolean clientInitialized;
    private static boolean sealed;

    /** Call exactly once from ModInitializer, after addon payload subscribers are collected. */
    public static synchronized void initialize(Consumer<RegisterPayloadHandlersEvent> registerPayloads,
            Consumer<RegisterConfigurationTasksEvent> registerTasks) {
        if (initialized || sealed) {
            throw new IllegalStateException("Fabric networking has already been initialized");
        }
        configurationTasks = Objects.requireNonNull(registerTasks, "Configuration task registration");
        PayloadRegistrar internal = new PayloadRegistrar("fabric-transport/1", false);
        internal.configurationToClient(ProtocolManifest.Offer.TYPE, ProtocolManifest.Offer.CODEC,
                FabricNetwork::acceptProtocolOffer);
        internal.configurationToServer(ProtocolManifest.Ack.TYPE, ProtocolManifest.Ack.CODEC,
                FabricNetwork::acceptProtocolAck);
        FabricFragmentation.initialize(internal);
        Objects.requireNonNull(registerPayloads, "Payload registration").accept(new RegisterPayloadHandlersEvent());
        sealed = true;
        PacketDistributor.initializeServerLifecycle();
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> {
            Connection connection = connection(handler);
            // Reconfiguration replaces the listener without disconnecting the connection.
            // Reset partial play transfers too, before configuration can outlive their timeout.
            forget(connection);
            if (!ServerConfigurationNetworking.canSend(handler, ProtocolManifest.Offer.TYPE)) {
                fail(handler, "Client does not implement StardewCraft Fabric protocol negotiation", null);
                return;
            }
            ((FabricServerConfigurationNetworkHandler) handler).addTask(new ConfigurationTask() {
                @Override public Type type() { return PROTOCOL_TASK_TYPE; }

                @Override
                public void start(Consumer<net.minecraft.network.protocol.Packet<?>> sender) {
                    long nonce = ThreadLocalRandom.current().nextLong();
                    PENDING_PROTOCOL.put(connection, nonce);
                    sender.accept(ServerConfigurationNetworking.createS2CPacket(
                            new ProtocolManifest.Offer(nonce, manifest())));
                }
            });
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> forget(connection(handler)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(connection(handler)));
        initialized = true;
    }

    /** Call from ClientModInitializer; this is the only common entry into the client implementation. */
    public static synchronized void initializeClient() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("Client networking cannot initialize on a dedicated server");
        }
        if (!initialized || clientInitialized) {
            throw new IllegalStateException("Initialize common networking once before client networking");
        }
        FabricClientNetworking.initialize();
        clientInitialized = true;
    }

    static synchronized <T extends CustomPacketPayload> void registerPlay(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler,
            PacketFlow flow, String version, boolean optional) {
        Registration<T> registration = registration(type, codec, handler, ConnectionProtocol.PLAY,
                flow, version, optional);
        if (flow == PacketFlow.SERVERBOUND) {
            PayloadTypeRegistry.playC2S().register(type, codec);
            if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
                var listener = context.player().connection;
                dispatch(payload, new FabricPayloadContext(listener, connection(listener), context.player(),
                        context.responseSender()::sendPacket, context.responseSender()::disconnect,
                        context.server(), context.server()::isSameThread, PacketFlow.SERVERBOUND,
                        ConnectionProtocol.PLAY, null));
            })) {
                throw new IllegalStateException("Fabric server play receiver already exists: " + type.id());
            }
        } else {
            PayloadTypeRegistry.playS2C().register(type, codec);
        }
        put(registration);
    }

    static synchronized <T extends CustomPacketPayload> void registerConfiguration(
            CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler, PacketFlow flow, String version, boolean optional) {
        Registration<T> registration = registration(type, codec, handler, ConnectionProtocol.CONFIGURATION,
                flow, version, optional);
        if (flow == PacketFlow.SERVERBOUND) {
            PayloadTypeRegistry.configurationC2S().register(type, codec);
            if (!ServerConfigurationNetworking.registerGlobalReceiver(type, (payload, context) -> {
                var listener = context.networkHandler();
                dispatch(payload, new FabricPayloadContext(listener, connection(listener), null,
                        context.responseSender()::sendPacket, context.responseSender()::disconnect,
                        context.server(), context.server()::isSameThread, PacketFlow.SERVERBOUND,
                        ConnectionProtocol.CONFIGURATION,
                        task -> ((FabricServerConfigurationNetworkHandler) listener).completeTask(task)));
            })) {
                throw new IllegalStateException("Fabric server configuration receiver already exists: " + type.id());
            }
        } else {
            PayloadTypeRegistry.configurationS2C().register(type, codec);
        }
        put(registration);
    }

    private static <T extends CustomPacketPayload> Registration<T> registration(
            CustomPacketPayload.Type<T> type, StreamCodec<?, T> codec, IPayloadHandler<T> handler,
            ConnectionProtocol protocol, PacketFlow flow, String version, boolean optional) {
        if (sealed) {
            throw new IllegalStateException("Payload registration is closed before connections may start");
        }
        Objects.requireNonNull(type, "Payload type");
        Objects.requireNonNull(codec, "Payload codec");
        Objects.requireNonNull(handler, "Payload handler");
        ProtocolManifest.Entry entry = new ProtocolManifest.Entry(type.id(), protocol, flow, version, optional);
        if (REGISTRATIONS.containsKey(entry.key())) {
            throw new IllegalArgumentException("Duplicate payload registration: " + entry.key());
        }
        return new Registration<>(type, codec, handler, protocol, flow, version, optional);
    }

    private static void put(Registration<?> registration) {
        REGISTRATIONS.put(new ProtocolManifest.Key(registration.protocol(), registration.flow(),
                registration.type().id()), registration);
    }

    /** The client adapter registers receivers only; codecs were already registered on both sides. */
    public static List<Registration<?>> clientRegistrations() {
        if (!sealed) {
            throw new IllegalStateException("Payload definitions are not complete");
        }
        return REGISTRATIONS.values().stream().filter(r -> r.flow() == PacketFlow.CLIENTBOUND).toList();
    }

    public record Registration<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<?, T> codec, IPayloadHandler<T> handler, ConnectionProtocol protocol, PacketFlow flow,
            String version, boolean optional) {}

    static Registration<?> playRegistration(ResourceLocation id, PacketFlow flow) {
        Registration<?> registration = REGISTRATIONS.get(new ProtocolManifest.Key(ConnectionProtocol.PLAY, flow, id));
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered " + flow + " play payload: " + id);
        }
        return registration;
    }

    public static void requireRegistered(CustomPacketPayload.Type<?> type, ConnectionProtocol protocol,
            PacketFlow flow) {
        if (!REGISTRATIONS.containsKey(new ProtocolManifest.Key(protocol, flow, type.id()))) {
            throw new IllegalArgumentException("Unregistered " + protocol + " / " + flow + " payload: " + type.id());
        }
    }

    /** Dispatch always enters the game thread, including Fabric's Netty configuration callbacks. */
    public static void dispatch(CustomPacketPayload payload, IPayloadContext context) {
        Registration<?> registration = REGISTRATIONS.get(new ProtocolManifest.Key(
                context.protocol(), context.flow(), payload.type().id()));
        if (registration == null) {
            throw new IllegalArgumentException("No handler for " + payload.type().id());
        }
        try {
            context.enqueueWork(() -> {
                if (!context.connection().isConnected()) {
                    return;
                }
                if (!isProtocolPayload(payload) && !PEERS.containsKey(context.connection())) {
                    throw new IllegalStateException("Payload arrived before protocol negotiation: " + payload.type().id());
                }
                invoke(registration, payload, context);
            }).exceptionally(failure -> {
                failPayload(payload, context, failure);
                return null;
            });
        } catch (RuntimeException failure) {
            failPayload(payload, context, failure);
        }
    }

    private static void failPayload(CustomPacketPayload payload, IPayloadContext context, Throwable failure) {
        LOGGER.error("StardewCraft Fabric payload {} failed", payload.type().id(), failure);
        context.disconnect(protocolErrorReason());
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void invoke(Registration<T> registration,
            CustomPacketPayload payload, IPayloadContext context) {
        registration.handler().handle((T) payload, context);
    }

    private static boolean isProtocolPayload(CustomPacketPayload payload) {
        return payload instanceof ProtocolManifest.Offer || payload instanceof ProtocolManifest.Ack;
    }

    private static List<ProtocolManifest.Entry> manifest() {
        return REGISTRATIONS.values().stream().map(r -> new ProtocolManifest.Entry(r.type().id(),
                        r.protocol(), r.flow(), r.version(), r.optional()))
                .sorted(Comparator.comparing(e -> e.protocol().name() + "/" + e.flow().name() + "/" + e.id()))
                .toList();
    }

    private static void acceptProtocolOffer(ProtocolManifest.Offer offer, IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND || context.protocol() != ConnectionProtocol.CONFIGURATION) {
            throw new IllegalStateException("Protocol offer must be clientbound configuration");
        }
        if (PEERS.containsKey(context.connection())) {
            throw new IllegalStateException("Repeated protocol offer in the same configuration phase");
        }
        String mismatch = ProtocolManifest.incompatibility(manifest(), offer.entries());
        if (mismatch != null) {
            LOGGER.warn("StardewCraft Fabric protocol rejected server: {}", mismatch);
            context.disconnect(protocolErrorReason());
            return;
        }
        PEERS.put(context.connection(), ProtocolManifest.indexed(offer.entries()));
        context.reply(new ProtocolManifest.Ack(offer.nonce(), manifest()));
    }

    private static void acceptProtocolAck(ProtocolManifest.Ack ack, IPayloadContext context) {
        Long pending = PENDING_PROTOCOL.get(context.connection());
        if (pending == null || pending.longValue() != ack.nonce()) {
            throw new IllegalStateException("Unsolicited, stale or replayed protocol acknowledgement");
        }
        String mismatch = ProtocolManifest.incompatibility(manifest(), ack.entries());
        if (mismatch != null) {
            LOGGER.warn("StardewCraft Fabric protocol rejected client: {}", mismatch);
            context.disconnect(protocolErrorReason());
            return;
        }
        PENDING_PROTOCOL.remove(context.connection());
        PEERS.put(context.connection(), ProtocolManifest.indexed(ack.entries()));
        var listener = (ServerConfigurationPacketListenerImpl) context.listener();
        // Register the real tasks before finishing this early task. Appending after vanilla's
        // JoinWorld task has started would let a client enter before catalog validation.
        configurationTasks.accept(new RegisterConfigurationTasksEvent(listener,
                ServerConfigurationNetworking.getServer(listener)));
        if (context.connection().isConnected()) {
            context.finishCurrentTask(PROTOCOL_TASK_TYPE);
        }
    }

    static boolean peerHasConfigurationChannel(ServerConfigurationPacketListenerImpl handler,
            ResourceLocation id) {
        Map<ProtocolManifest.Key, ProtocolManifest.Entry> peer = PEERS.get(connection(handler));
        if (peer == null) {
            return false;
        }
        ProtocolManifest.Key clientbound = new ProtocolManifest.Key(ConnectionProtocol.CONFIGURATION,
                PacketFlow.CLIENTBOUND, id);
        ProtocolManifest.Key serverbound = new ProtocolManifest.Key(ConnectionProtocol.CONFIGURATION,
                PacketFlow.SERVERBOUND, id);
        return (peer.containsKey(clientbound) && ServerConfigurationNetworking.canSend(handler, id))
                || peer.containsKey(serverbound);
    }

    static Connection connection(ServerCommonPacketListenerImpl handler) {
        return ((ServerCommonConnectionAccessor) handler).stardewcraft$getConnection();
    }

    /** Also called from client disconnect events to release protocol state after failed logins. */
    public static void forget(Connection connection) {
        PEERS.remove(connection);
        PENDING_PROTOCOL.remove(connection);
        FabricFragmentation.forget(connection);
    }

    static void fail(ServerConfigurationPacketListenerImpl handler, String message, Throwable failure) {
        LOGGER.error(message, failure);
        handler.disconnect(protocolErrorReason());
    }

    static Component protocolErrorReason() {
        return Component.translatable("disconnect.stardewcraft.fabric.protocol_error");
    }

    private FabricNetwork() {}
}
