package com.stardew.craft.port.internal.network;

import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadHandler;
import com.stardew.craft.port.net.neoforged.neoforge.network.registration.HandlerThread;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Frozen table of every payload registered through the NeoForge-shaped {@code PayloadRegistrar}.
 *
 * <p>Wire ids are assigned by sorting payload ids per phase, never by registration order, so they
 * are identical on client and server even if mod-bus listener order differs. The fingerprint of
 * the table (ids, directions, versions, optional flags) is the Stardew channel protocol version.
 */
public final class PortPayloadRegistry {
    public enum Phase {
        PLAY,
        CONFIGURATION
    }

    public static final class Entry<T extends CustomPacketPayload> {
        private final Phase phase;
        private final CustomPacketPayload.Type<T> type;
        private final StreamCodec<? super RegistryFriendlyByteBuf, T> codec;
        @Nullable
        private final IPayloadHandler<T> toClient;
        @Nullable
        private final IPayloadHandler<T> toServer;
        private final String version;
        private final boolean optional;
        private final HandlerThread thread;
        private int wireId = -1;

        private Entry(Phase phase, CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                @Nullable IPayloadHandler<T> toClient, @Nullable IPayloadHandler<T> toServer,
                String version, boolean optional, HandlerThread thread) {
            this.phase = phase;
            this.type = type;
            this.codec = codec;
            this.toClient = toClient;
            this.toServer = toServer;
            this.version = version;
            this.optional = optional;
            this.thread = thread;
        }

        public ResourceLocation id() {
            return this.type.id();
        }

        public int wireId() {
            return this.wireId;
        }

        public HandlerThread thread() {
            return this.thread;
        }

        public boolean clientbound() {
            return this.toClient != null;
        }

        public boolean serverbound() {
            return this.toServer != null;
        }

        @SuppressWarnings("unchecked")
        void encode(RegistryFriendlyByteBuf buffer, CustomPacketPayload payload) {
            ((StreamCodec<RegistryFriendlyByteBuf, T>) this.codec).encode(buffer, (T) payload);
        }

        T decode(RegistryFriendlyByteBuf buffer) {
            return this.codec.decode(buffer);
        }

        /** Runs the handler registered for the receiving side. */
        @SuppressWarnings("unchecked")
        void handle(boolean onClient, CustomPacketPayload payload,
                com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext context) {
            IPayloadHandler<T> handler = onClient ? this.toClient : this.toServer;
            if (handler == null) {
                throw new IllegalStateException("Payload " + this.id() + " may not be handled on the "
                        + (onClient ? "client" : "server"));
            }
            handler.handle((T) payload, context);
        }

        private String signature() {
            return this.phase + "|" + this.id() + "|" + (this.clientbound() ? "c" : "") + (this.serverbound() ? "s" : "")
                    + "|" + this.version + "|" + (this.optional ? "optional" : "required");
        }
    }

    /** Mutable collector handed to {@code RegisterPayloadHandlersEvent}. */
    public static final class Builder {
        private final Map<Phase, Map<ResourceLocation, Entry<?>>> entries = new EnumMap<>(Phase.class);
        private boolean built;

        public Builder() {
            for (Phase phase : Phase.values()) {
                this.entries.put(phase, new LinkedHashMap<>());
            }
        }

        public synchronized <T extends CustomPacketPayload> void register(Phase phase, CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, @Nullable IPayloadHandler<T> toClient,
                @Nullable IPayloadHandler<T> toServer, String version, boolean optional, HandlerThread thread) {
            if (this.built) {
                throw new IllegalStateException("Payload registration is closed; " + type.id() + " registered too late");
            }
            Map<ResourceLocation, Entry<?>> table = this.entries.get(phase);
            if (table.containsKey(type.id())) {
                throw new IllegalStateException("Duplicate " + phase + " payload registration: " + type.id());
            }
            table.put(type.id(), new Entry<>(phase, type, codec, toClient, toServer, version, optional, thread));
        }

        synchronized PortPayloadRegistry build() {
            this.built = true;
            return new PortPayloadRegistry(this.entries);
        }
    }

    private final Map<Phase, Map<ResourceLocation, Entry<?>>> byId = new EnumMap<>(Phase.class);
    private final Map<Phase, List<Entry<?>>> byWire = new EnumMap<>(Phase.class);
    private final String fingerprint;

    private PortPayloadRegistry(Map<Phase, Map<ResourceLocation, Entry<?>>> source) {
        StringBuilder signature = new StringBuilder();
        for (Phase phase : Phase.values()) {
            List<Entry<?>> sorted = new ArrayList<>(source.get(phase).values());
            sorted.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
            Map<ResourceLocation, Entry<?>> ids = new LinkedHashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                Entry<?> entry = sorted.get(i);
                entry.wireId = i;
                ids.put(entry.id(), entry);
                signature.append(entry.signature()).append('\n');
            }
            this.byId.put(phase, Collections.unmodifiableMap(ids));
            this.byWire.put(phase, List.copyOf(sorted));
        }
        this.fingerprint = sha1(signature.toString());
    }

    @Nullable
    public Entry<?> byId(Phase phase, ResourceLocation id) {
        return this.byId.get(phase).get(id);
    }

    @Nullable
    public Entry<?> byWire(Phase phase, int wireId) {
        List<Entry<?>> list = this.byWire.get(phase);
        return wireId >= 0 && wireId < list.size() ? list.get(wireId) : null;
    }

    public int size(Phase phase) {
        return this.byWire.get(phase).size();
    }

    public String fingerprint() {
        return this.fingerprint;
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
