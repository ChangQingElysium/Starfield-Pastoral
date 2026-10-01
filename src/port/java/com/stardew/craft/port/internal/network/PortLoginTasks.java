package com.stardew.craft.port.internal.network;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.VarInt;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import com.stardew.craft.port.net.minecraft.server.network.ConfigurationTask;
import com.stardew.craft.port.net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;
import com.stardew.craft.port.net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NeoForge configuration tasks mapped onto the Forge 1.20.1 login handshake.
 *
 * <ol>
 *   <li>While Forge gathers the login messages for a new modded connection, the server posts
 *       {@link RegisterConfigurationTasksEvent} on the StardewCraft mod bus and runs every registered
 *       {@link ICustomConfigurationTask}; the payloads a task sends become one login message.</li>
 *   <li>The client runs the {@code configurationToClient} handlers on its main thread and answers
 *       with one login reply holding every payload passed to {@code context.reply}.</li>
 *   <li>The server runs the {@code configurationToServer} handlers on its main thread. The task must
 *       call {@code finishCurrentTask(type)} there, otherwise the connection is dropped (NeoForge
 *       would stall the configuration phase until timeout). Only then is the login index answered,
 *       so Forge keeps the player out of the world until every task finished.</li>
 * </ol>
 *
 * <p>Limits: one request/reply round trip per task; the server cannot {@code reply} from a
 * configuration handler; login messages are not split (each must stay below 1 MB).
 */
final class PortLoginTasks {
    static final String DISCONNECT_TASK = "stardewcraft:port_disconnect";
    private static final int MAX_LOGIN_MESSAGE = 1_000_000;

    private PortLoginTasks() {
    }

    static void register(SimpleChannel channel) {
        BiConsumer<TaskMessage, Supplier<NetworkEvent.Context>> taskConsumer = PortLoginTasks::receiveTask;
        channel.messageBuilder(TaskMessage.class, PortNetwork.INDEX_LOGIN_TASK, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(TaskMessage::getAsInt, TaskMessage::setLoginIndex)
                .encoder(TaskMessage::write)
                .decoder(TaskMessage::read)
                .buildLoginPacketList(PortLoginTasks::gather)
                .consumerNetworkThread(taskConsumer)
                .add();
        BiConsumer<ReplyMessage, Supplier<NetworkEvent.Context>> replyConsumer = PortLoginTasks::receiveReply;
        channel.messageBuilder(ReplyMessage.class, PortNetwork.INDEX_LOGIN_REPLY, NetworkDirection.LOGIN_TO_SERVER)
                .loginIndex(TaskMessage::getAsInt, TaskMessage::setLoginIndex)
                .encoder(TaskMessage::write)
                .decoder(ReplyMessage::readReply)
                .consumerNetworkThread(replyConsumer)
                .add();
    }

    // ------------------------------------------------------------------ server: gather

    /** Called by Forge once per incoming modded connection (memory connections included). */
    private static List<Pair<String, TaskMessage>> gather(boolean isLocal) {
        GatherListener listener = new GatherListener();
        List<Pair<String, TaskMessage>> messages = new ArrayList<>();
        try {
            RegisterConfigurationTasksEvent event = new RegisterConfigurationTasksEvent(listener);
            PortNetwork.modBus().post(event);
            if (listener.reason == null) {
                for (ConfigurationTask task : event.getConfigurationTasks()) {
                    List<CustomPacketPayload> sent = new ArrayList<>();
                    ((ICustomConfigurationTask) task).run(sent::add);
                    String id = task.type().id();
                    messages.add(Pair.of(id, new TaskMessage(id, encodeBatch(sent, true))));
                }
            }
        } catch (RuntimeException e) {
            PortNetwork.LOGGER.error("StardewCraft configuration tasks failed to start", e);
            listener.reason = Component.literal("StardewCraft configuration failed: " + e);
        }
        if (listener.reason != null) {
            messages.clear();
            messages.add(Pair.of(DISCONNECT_TASK, new TaskMessage(DISCONNECT_TASK, encodeReason(listener.reason))));
        }
        return messages;
    }

    /**
     * Listener view at gather time. Forge's channel version check (which covers every payload id,
     * direction and version) rejects any client that lacks the Stardew channel before login
     * completes, so a registered payload is always present on the remote.
     */
    private static final class GatherListener implements ServerConfigurationPacketListener {
        private Component reason;

        @Override
        public boolean hasChannel(ResourceLocation payloadId) {
            PortPayloadRegistry registry = PortNetwork.registry();
            return registry.byId(PortPayloadRegistry.Phase.CONFIGURATION, payloadId) != null
                    || registry.byId(PortPayloadRegistry.Phase.PLAY, payloadId) != null;
        }

        @Override
        public void disconnect(Component reason) {
            if (this.reason == null) {
                this.reason = reason;
            }
        }
    }

    // ------------------------------------------------------------------ client: run task

    private static void receiveTask(TaskMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context forgeContext = contextSupplier.get();
        // Must be marked handled on the network thread, otherwise Forge sends an empty vanilla reply.
        forgeContext.setPacketHandled(true);
        Connection connection = forgeContext.getNetworkManager();
        forgeContext.enqueueWork(() -> {
            if (DISCONNECT_TASK.equals(message.taskId)) {
                PortNetwork.channel().reply(new ReplyMessage(message.taskId, message.body), forgeContext);
                return;
            }
            List<CustomPacketPayload> replies = new ArrayList<>();
            PortPayloadContext context = PortPayloadContext.login(connection, LogicalSide.CLIENT, payload -> {
                requireEntry(payload.type().id(), false);
                replies.add(payload);
            }, null);
            try {
                for (Decoded decoded : decodeBatch(message.body, true)) {
                    decoded.entry.handle(true, decoded.payload, context);
                    if (context.isDisconnected()) {
                        return;
                    }
                }
                PortNetwork.channel().reply(new ReplyMessage(message.taskId, encodeBatch(replies, false)), forgeContext);
            } catch (Throwable t) {
                PortNetwork.LOGGER.error("StardewCraft configuration task {} failed on the client", message.taskId, t);
                context.disconnect(Component.literal("StardewCraft configuration task " + message.taskId + " failed: " + t));
            }
        });
    }

    // ------------------------------------------------------------------ server: handle reply

    private static void receiveReply(TaskMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context forgeContext = contextSupplier.get();
        forgeContext.setPacketHandled(true);
        Connection connection = forgeContext.getNetworkManager();
        forgeContext.enqueueWork(() -> {
            if (DISCONNECT_TASK.equals(message.taskId)) {
                PortNetwork.disconnect(connection, LogicalSide.SERVER, decodeReason(message.body));
                return;
            }
            Set<String> finished = new HashSet<>();
            PortPayloadContext context = PortPayloadContext.login(connection, LogicalSide.SERVER, payload -> {
                throw new UnsupportedOperationException("PORT(1.20.1): configuration tasks run as one login round trip; "
                        + "the server cannot reply to " + payload.type().id());
            }, finished);
            try {
                for (Decoded decoded : decodeBatch(message.body, false)) {
                    decoded.entry.handle(false, decoded.payload, context);
                    if (context.isDisconnected()) {
                        return;
                    }
                }
            } catch (Throwable t) {
                PortNetwork.LOGGER.error("StardewCraft configuration task {} failed on the server", message.taskId, t);
                context.disconnect(Component.literal("StardewCraft configuration task " + message.taskId + " failed: " + t));
                return;
            }
            if (!finished.contains(message.taskId)) {
                context.disconnect(Component.literal("StardewCraft configuration task " + message.taskId + " did not complete"));
                return;
            }
            // Answer the login index only now, so Forge holds the login until the task finished.
            HandshakeHandler.<TaskMessage>indexFirst((handshake, msg, ctx) -> {
            }).accept(message, contextSupplier);
        });
    }

    // ------------------------------------------------------------------ batch codec

    private record Decoded(PortPayloadRegistry.Entry<?> entry, CustomPacketPayload payload) {
    }

    private static PortPayloadRegistry.Entry<?> requireEntry(ResourceLocation id, boolean clientbound) {
        PortPayloadRegistry.Entry<?> entry = PortNetwork.registry().byId(PortPayloadRegistry.Phase.CONFIGURATION, id);
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered configuration payload " + id);
        }
        if (clientbound ? !entry.clientbound() : !entry.serverbound()) {
            throw new IllegalStateException("Configuration payload " + id + " may not be sent to the " + (clientbound ? "client" : "server"));
        }
        return entry;
    }

    private static byte[] encodeBatch(List<CustomPacketPayload> payloads, boolean clientbound) {
        ByteBuf out = Unpooled.buffer();
        try {
            VarInt.write(out, payloads.size());
            for (CustomPacketPayload payload : payloads) {
                PortPayloadRegistry.Entry<?> entry = requireEntry(payload.type().id(), clientbound);
                ByteBuf body = Unpooled.buffer();
                try {
                    entry.encode(new RegistryFriendlyByteBuf(body, PortNetwork.builtinAccess(), ConnectionType.NEOFORGE), payload);
                    VarInt.write(out, entry.wireId());
                    VarInt.write(out, body.readableBytes());
                    out.writeBytes(body);
                } finally {
                    body.release();
                }
            }
            if (out.readableBytes() > MAX_LOGIN_MESSAGE) {
                throw new EncoderException("Configuration payload batch exceeds " + MAX_LOGIN_MESSAGE + " bytes");
            }
            byte[] bytes = new byte[out.readableBytes()];
            out.readBytes(bytes);
            return bytes;
        } finally {
            out.release();
        }
    }

    private static List<Decoded> decodeBatch(byte[] bytes, boolean clientbound) {
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        int count = VarInt.read(in);
        List<Decoded> decoded = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            int wireId = VarInt.read(in);
            int length = VarInt.read(in);
            PortPayloadRegistry.Entry<?> entry = PortNetwork.registry().byWire(PortPayloadRegistry.Phase.CONFIGURATION, wireId);
            if (entry == null) {
                throw new DecoderException("Unknown configuration payload wire id " + wireId);
            }
            requireEntry(entry.id(), clientbound);
            if (length < 0 || length > in.readableBytes()) {
                throw new DecoderException("Truncated configuration payload " + entry.id());
            }
            RegistryFriendlyByteBuf body = new RegistryFriendlyByteBuf(in.readSlice(length), PortNetwork.builtinAccess(), ConnectionType.NEOFORGE);
            CustomPacketPayload payload = entry.decode(body);
            if (body.isReadable()) {
                throw new DecoderException("Configuration payload " + entry.id() + " left " + body.readableBytes() + " unread bytes");
            }
            decoded.add(new Decoded(entry, payload));
        }
        if (in.isReadable()) {
            throw new DecoderException("Trailing bytes after configuration payload batch");
        }
        return decoded;
    }

    private static byte[] encodeReason(Component reason) {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        try {
            out.writeComponent(reason);
            byte[] bytes = new byte[out.readableBytes()];
            out.readBytes(bytes);
            return bytes;
        } finally {
            out.release();
        }
    }

    private static Component decodeReason(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)).readComponent();
    }

    // ------------------------------------------------------------------ messages

    /** Login request; also the base of the reply so both share encoding and login-index plumbing. */
    static class TaskMessage implements IntSupplier {
        private int loginIndex;
        final String taskId;
        final byte[] body;

        TaskMessage(String taskId, byte[] body) {
            this.taskId = taskId;
            this.body = body;
        }

        @Override
        public int getAsInt() {
            return this.loginIndex;
        }

        void setLoginIndex(int loginIndex) {
            this.loginIndex = loginIndex;
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.taskId);
            buffer.writeByteArray(this.body);
        }

        static TaskMessage read(FriendlyByteBuf buffer) {
            return new TaskMessage(buffer.readUtf(), buffer.readByteArray(MAX_LOGIN_MESSAGE + 64));
        }
    }

    static final class ReplyMessage extends TaskMessage {
        ReplyMessage(String taskId, byte[] body) {
            super(taskId, body);
        }

        static ReplyMessage readReply(FriendlyByteBuf buffer) {
            return new ReplyMessage(buffer.readUtf(), buffer.readByteArray(MAX_LOGIN_MESSAGE + 64));
        }
    }
}
