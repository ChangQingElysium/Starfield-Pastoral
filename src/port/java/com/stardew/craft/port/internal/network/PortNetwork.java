package com.stardew.craft.port.internal.network;

import com.mojang.logging.LogUtils;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.VarInt;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;
import com.stardew.craft.port.net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.registration.HandlerThread;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.AttributeKey;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * NeoForge 21.1 payload networking rebuilt on one Forge 1.20.1 {@link SimpleChannel}.
 *
 * <p><b>Wire format.</b> SimpleChannel only offers 256 discriminators, the mod registers ~430 payload
 * types, so every play payload travels in one envelope message: {@code kind byte} then either a
 * complete message ({@code VarInt wireId + payload codec bytes}) or one fragment of it. Messages
 * larger than the vanilla custom-payload limits (1 MiB clientbound, 32 KiB serverbound) are split
 * and reassembled per connection, standing in for NeoForge's automatic payload splitting.
 *
 * <p><b>Threading.</b> Like NeoForge's default ({@code HandlerThread.MAIN}), decoding happens on
 * the network thread and handlers are invoked on the receiving side's main thread; a registrar
 * with {@code executesOn(NETWORK)} keeps the handler on the network thread.
 *
 * <p><b>Direction.</b> A payload arriving on a side that has no handler for it (e.g. a
 * {@code playToClient} payload sent by a client) disconnects the sender, as NeoForge does.
 */
public final class PortNetwork {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation("stardewcraft", "payloads");
    private static final String PROTOCOL_PREFIX = "stardew-port-1:";

    static final int INDEX_PLAY = 0;
    static final int INDEX_LOGIN_TASK = 1;
    static final int INDEX_LOGIN_REPLY = 2;

    private static final int KIND_COMPLETE = 0;
    private static final int KIND_PART = 1;
    private static final int KIND_LAST_PART = 2;
    /** Vanilla limits: clientbound custom payload 1048576 bytes, serverbound 32767 bytes. */
    private static final int CLIENTBOUND_PART_BYTES = 1_000_000;
    private static final int SERVERBOUND_PART_BYTES = 30_000;
    private static final int MAX_CLIENTBOUND_MESSAGE = 64 * 1024 * 1024;
    private static final int MAX_SERVERBOUND_MESSAGE = 8 * 1024 * 1024;

    private static final AttributeKey<ByteArrayOutputStream> REASSEMBLY = AttributeKey.valueOf("stardewcraft:payload_reassembly");
    private static final Object SERVERBOUND_SPLIT_LOCK = new Object();
    /** Last fragment packet -> original payload, so {@code ClientboundCustomPayloadPacket.unwrap} sees split payloads. */
    private static final Map<Packet<?>, CustomPacketPayload> SPLIT_ORIGINS = Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile PortPayloadRegistry registry;
    private static volatile SimpleChannel channel;
    private static volatile IEventBus modBus;
    private static volatile RegistryAccess builtinAccess;

    private PortNetwork() {
    }

    /** Posts {@link RegisterPayloadHandlersEvent} on the mod bus and creates the channel. Idempotent. */
    public static synchronized void initialize(IEventBus bus) {
        if (channel != null) {
            return;
        }
        modBus = bus;
        PortPayloadRegistry.Builder builder = new PortPayloadRegistry.Builder();
        bus.post(new RegisterPayloadHandlersEvent(builder));
        PortPayloadRegistry built = builder.build();
        registry = built;
        String protocol = PROTOCOL_PREFIX + built.fingerprint();
        SimpleChannel created = NetworkRegistry.newSimpleChannel(CHANNEL_ID, () -> protocol, protocol::equals, protocol::equals);
        BiConsumer<PlayMessage, Supplier<NetworkEvent.Context>> consumer = PortNetwork::receivePlay;
        created.messageBuilder(PlayMessage.class, INDEX_PLAY)
                .encoder(PlayMessage::write)
                .decoder(PlayMessage::read)
                .consumerNetworkThread(consumer)
                .add();
        PortLoginTasks.register(created);
        channel = created;
        LOGGER.info("StardewCraft payload channel {} ready: {} play / {} configuration payloads, protocol {}",
                CHANNEL_ID, built.size(PortPayloadRegistry.Phase.PLAY), built.size(PortPayloadRegistry.Phase.CONFIGURATION), protocol);
    }

    static PortPayloadRegistry registry() {
        PortPayloadRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException("StardewCraft payloads are not registered yet (FMLCommonSetupEvent has not run)");
        }
        return current;
    }

    static SimpleChannel channel() {
        SimpleChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("StardewCraft payload channel is not initialised yet");
        }
        return current;
    }

    static IEventBus modBus() {
        return modBus;
    }

    // ------------------------------------------------------------------ registry access

    static RegistryAccess builtinAccess() {
        RegistryAccess access = builtinAccess;
        if (access == null) {
            access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            builtinAccess = access;
        }
        return access;
    }

    static RegistryAccess serverAccess() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : builtinAccess();
    }

    // ------------------------------------------------------------------ encoding

    @SuppressWarnings("unchecked")
    private static List<Packet<?>> encodePlay(CustomPacketPayload payload, boolean clientbound, RegistryAccess access) {
        PortPayloadRegistry.Entry<?> entry = registry().byId(PortPayloadRegistry.Phase.PLAY, payload.type().id());
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered play payload " + payload.type().id());
        }
        if (clientbound ? !entry.clientbound() : !entry.serverbound()) {
            throw new IllegalStateException("Payload " + entry.id() + " may not be sent to the " + (clientbound ? "client" : "server"));
        }
        ByteBuf body = Unpooled.buffer();
        try {
            VarInt.write(body, entry.wireId());
            entry.encode(new RegistryFriendlyByteBuf(body, access, ConnectionType.NEOFORGE), payload);
            int size = body.readableBytes();
            int limit = clientbound ? MAX_CLIENTBOUND_MESSAGE : MAX_SERVERBOUND_MESSAGE;
            if (size > limit) {
                throw new EncoderException("Payload " + entry.id() + " is " + size + " bytes, above the " + limit + " byte limit");
            }
            NetworkDirection direction = clientbound ? NetworkDirection.PLAY_TO_CLIENT : NetworkDirection.PLAY_TO_SERVER;
            int partBytes = clientbound ? CLIENTBOUND_PART_BYTES : SERVERBOUND_PART_BYTES;
            byte[] bytes = new byte[size];
            body.readBytes(bytes);
            if (size <= partBytes) {
                return List.of(channel().toVanillaPacket(new PlayMessage(KIND_COMPLETE, bytes, 0, size), direction));
            }
            List<Packet<?>> packets = new ArrayList<>();
            for (int offset = 0; offset < size; offset += partBytes) {
                int length = Math.min(partBytes, size - offset);
                int kind = offset + length >= size ? KIND_LAST_PART : KIND_PART;
                packets.add(channel().toVanillaPacket(new PlayMessage(kind, bytes, offset, length), direction));
            }
            if (clientbound) {
                SPLIT_ORIGINS.put(packets.get(packets.size() - 1), payload);
            }
            return packets;
        } finally {
            body.release();
        }
    }

    /** Clientbound packet for one or more payloads; several packets become one bundle (as NeoForge does). */
    @SuppressWarnings("unchecked")
    static Packet<?> clientboundPacket(CustomPacketPayload first, CustomPacketPayload... rest) {
        RegistryAccess access = serverAccess();
        List<Packet<?>> packets = new ArrayList<>(encodePlay(first, true, access));
        for (CustomPacketPayload payload : rest) {
            packets.addAll(encodePlay(payload, true, access));
        }
        if (packets.size() == 1) {
            return packets.get(0);
        }
        List<Packet<ClientGamePacketListener>> bundled = new ArrayList<>(packets.size());
        for (Packet<?> packet : packets) {
            bundled.add((Packet<ClientGamePacketListener>) packet);
        }
        return new ClientboundBundlePacket(bundled);
    }

    static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(clientboundPacket(payload));
    }

    static void sendToPlayers(Collection<ServerPlayer> players, Packet<?> packet) {
        for (ServerPlayer player : players) {
            player.connection.send(packet);
        }
    }

    /** Client -> server. Only callable on the logical client. */
    static void sendToServer(CustomPacketPayload payload) {
        List<Packet<?>> packets = encodePlay(payload, false, PortClientNetwork.registryAccess());
        if (packets.size() == 1) {
            PortClientNetwork.send(packets.get(0));
            return;
        }
        synchronized (SERVERBOUND_SPLIT_LOCK) {
            for (Packet<?> packet : packets) {
                PortClientNetwork.send(packet);
            }
        }
    }

    /** Decodes a clientbound vanilla packet produced by this channel (gametest packet inspection). */
    @Nullable
    public static CustomPacketPayload decodeClientbound(Packet<?> packet) {
        if (!(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket custom)
                || !CHANNEL_ID.equals(custom.getIdentifier())) {
            return null;
        }
        CustomPacketPayload origin = SPLIT_ORIGINS.get(packet);
        if (origin != null) {
            return origin;
        }
        FriendlyByteBuf data = custom.getData();
        try {
            if (data.readUnsignedByte() != INDEX_PLAY || data.readUnsignedByte() != KIND_COMPLETE) {
                return null;
            }
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(data, serverAccess(), ConnectionType.NEOFORGE);
            PortPayloadRegistry.Entry<?> entry = registry().byWire(PortPayloadRegistry.Phase.PLAY, VarInt.read(buffer));
            return entry == null ? null : entry.decode(buffer);
        } finally {
            data.release();
        }
    }

    /** Lookup by id for the GAMEPLAY_STREAM_CODEC shim. */
    static PortPayloadRegistry.Entry<?> playEntry(ResourceLocation id) {
        PortPayloadRegistry.Entry<?> entry = registry().byId(PortPayloadRegistry.Phase.PLAY, id);
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered play payload " + id);
        }
        return entry;
    }

    public static void encodeWithId(RegistryFriendlyByteBuf buffer, CustomPacketPayload payload) {
        PortPayloadRegistry.Entry<?> entry = playEntry(payload.type().id());
        buffer.writeResourceLocation(entry.id());
        entry.encode(buffer, payload);
    }

    public static CustomPacketPayload decodeWithId(RegistryFriendlyByteBuf buffer) {
        return playEntry(buffer.readResourceLocation()).decode(buffer);
    }

    // ------------------------------------------------------------------ receiving

    private static void receivePlay(PlayMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context forgeContext = contextSupplier.get();
        forgeContext.setPacketHandled(true);
        Connection connection = forgeContext.getNetworkManager();
        LogicalSide side = forgeContext.getDirection().getReceptionSide();
        boolean onClient = side.isClient();
        byte[] complete;
        try {
            complete = reassemble(connection, message, onClient ? MAX_CLIENTBOUND_MESSAGE : MAX_SERVERBOUND_MESSAGE);
        } catch (RuntimeException e) {
            fail(connection, side, "Invalid StardewCraft payload fragment", e);
            return;
        }
        if (complete == null) {
            return;
        }
        PortPayloadRegistry.Entry<?> entry;
        CustomPacketPayload payload;
        try {
            RegistryAccess access = onClient ? PortClientNetwork.registryAccess() : serverAccess();
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(complete), access, ConnectionType.NEOFORGE);
            int wireId = VarInt.read(buffer);
            entry = registry().byWire(PortPayloadRegistry.Phase.PLAY, wireId);
            if (entry == null) {
                throw new DecoderException("Unknown StardewCraft payload wire id " + wireId);
            }
            if (onClient ? !entry.clientbound() : !entry.serverbound()) {
                disconnect(connection, side, Component.literal("Payload " + entry.id() + " may not be sent to the "
                        + (onClient ? "client" : "server")));
                return;
            }
            payload = entry.decode(buffer);
            if (buffer.isReadable()) {
                throw new DecoderException("Payload " + entry.id() + " left " + buffer.readableBytes() + " unread bytes");
            }
        } catch (RuntimeException e) {
            fail(connection, side, "Failed to decode StardewCraft payload", e);
            return;
        }
        PortPayloadContext context = onClient
                ? PortPayloadContext.clientPlay(connection)
                : PortPayloadContext.serverPlay(connection, forgeContext.getSender());
        PortPayloadRegistry.Entry<?> target = entry;
        Runnable invoke = () -> {
            try {
                target.handle(onClient, payload, context);
            } catch (Throwable t) {
                fail(connection, side, "Failed to handle StardewCraft payload " + target.id(), t);
            }
        };
        if (target.thread() == HandlerThread.NETWORK) {
            invoke.run();
        } else {
            forgeContext.enqueueWork(invoke);
        }
    }

    @Nullable
    private static byte[] reassemble(Connection connection, PlayMessage message, int limit) {
        if (message.kind == KIND_COMPLETE) {
            return message.copy();
        }
        if (message.kind != KIND_PART && message.kind != KIND_LAST_PART) {
            throw new DecoderException("Unknown payload fragment kind " + message.kind);
        }
        var attribute = connection.channel().attr(REASSEMBLY);
        ByteArrayOutputStream pending = attribute.get();
        if (pending == null) {
            pending = new ByteArrayOutputStream();
            attribute.set(pending);
        }
        if (pending.size() + message.length > limit) {
            attribute.set(null);
            throw new DecoderException("Split payload exceeds " + limit + " bytes");
        }
        pending.write(message.data, message.offset, message.length);
        if (message.kind == KIND_PART) {
            return null;
        }
        attribute.set(null);
        return pending.toByteArray();
    }

    private static void fail(Connection connection, LogicalSide side, String what, Throwable failure) {
        LOGGER.error("{} ({} side)", what, side, failure);
        disconnect(connection, side, Component.literal(what + ": " + failure));
    }

    /** Disconnects with the right packet for the connection's current phase. */
    static void disconnect(Connection connection, LogicalSide side, Component reason) {
        if (side.isClient()) {
            connection.disconnect(reason);
            return;
        }
        if (connection.getPacketListener() instanceof ServerGamePacketListenerImpl game) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && !server.isSameThread()) {
                server.execute(() -> game.disconnect(reason));
            } else {
                game.disconnect(reason);
            }
        } else if (connection.getPacketListener() instanceof ServerLoginPacketListenerImpl login) {
            login.disconnect(reason);
        } else {
            connection.disconnect(reason);
        }
    }

    // ------------------------------------------------------------------ envelope

    static final class PlayMessage {
        final int kind;
        final byte[] data;
        final int offset;
        final int length;

        PlayMessage(int kind, byte[] data, int offset, int length) {
            this.kind = kind;
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeByte(this.kind);
            buffer.writeBytes(this.data, this.offset, this.length);
        }

        static PlayMessage read(FriendlyByteBuf buffer) {
            int kind = buffer.readUnsignedByte();
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new PlayMessage(kind, bytes, 0, bytes.length);
        }

        byte[] copy() {
            if (this.offset == 0 && this.length == this.data.length) {
                return this.data;
            }
            byte[] out = new byte[this.length];
            System.arraycopy(this.data, this.offset, out, 0, this.length);
            return out;
        }
    }
}
