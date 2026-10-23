package com.stardew.craft.platform.fabric.network;

import com.google.gson.GsonBuilder;
import com.stardew.craft.platform.fabric.datamap.DataMapTransfer;
import com.stardew.craft.platform.fabric.datamap.PeerDataMapAssembly;
import com.stardew.craft.platform.fabric.menu.MenuDataPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ClientInformation;

/**
 * Development wire peer. No Minecraft/ClientLevel or client handlers. World/recipe/item packets
 * are deliberately opaque; the report cannot be used as client gameplay or 435-handler acceptance.
 * Run with ProtocolPeerGuard as -javaagent. No server is started by this tool.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class HeadlessProtocolPeer {
    private static final Set<String> PLAY_CONTROL = Set.of("keep_alive", "ping", "disconnect", "player_position");
    private static final Map<ResourceLocation, StreamCodec> PAYLOAD_CODECS = new LinkedHashMap<>();
    static {
        PAYLOAD_CODECS.put(ProtocolManifest.Offer.TYPE.id(), ProtocolManifest.Offer.CODEC);
        PAYLOAD_CODECS.put(ProtocolManifest.Ack.TYPE.id(), ProtocolManifest.Ack.CODEC);
        PAYLOAD_CODECS.put(DataMapTransfer.Chunk.TYPE.id(), DataMapTransfer.Chunk.CODEC);
        PAYLOAD_CODECS.put(DataMapTransfer.Ack.TYPE.id(), DataMapTransfer.Ack.CODEC);
        PAYLOAD_CODECS.put(FragmentPayload.TYPE.id(), FragmentPayload.CODEC);
        PAYLOAD_CODECS.put(MenuDataPayload.TYPE.id(), MenuDataPayload.STREAM_CODEC);
        PAYLOAD_CODECS.put(CommonVersionPayload.ID.id(), CommonVersionPayload.CODEC);
        PAYLOAD_CODECS.put(CommonRegisterPayload.ID.id(), CommonRegisterPayload.CODEC);
        PAYLOAD_CODECS.put(RegistrationPayload.REGISTER.id(), RegistrationPayload.REGISTER_CODEC);
        PAYLOAD_CODECS.put(RegistrationPayload.UNREGISTER.id(), RegistrationPayload.UNREGISTER_CODEC);
        PAYLOAD_CODECS.put(BrandPayload.TYPE.id(), BrandPayload.STREAM_CODEC);
    }
    private HeadlessProtocolPeer() {}

    public static void main(String[] args) throws Exception {
        if (!"active".equals(System.getProperty("stardew.protocolPeer.guard")))
            throw new IllegalStateException("Mandatory ProtocolPeerGuard -javaagent is absent");
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); registerMainCodecs();
        if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); requireNoClientDefinitions(); return; }
        if (args.length != 6) throw new IllegalArgumentException("127.0.0.1 port uniqueName sessions playSeconds report.json (or --self-test)");
        String host = args[0], name = args[2]; int port = Integer.parseInt(args[1]), sessions = Integer.parseInt(args[3]), playSeconds = Integer.parseInt(args[4]);
        if (!host.equals("127.0.0.1") || !InetAddress.getByName(host).isLoopbackAddress() || port < 1 || port > 65535
                || !name.matches("[A-Za-z0-9_]{1,16}") || sessions < 1 || sessions > 3 || playSeconds < 1 || playSeconds > 30)
            throw new IllegalArgumentException("Peer is restricted to an explicit loopback endpoint and bounded sessions");
        Path output = Path.of(args[5]); var outcomes = new ArrayList<Map<String, Object>>();
        Map<String, Object> report = new LinkedHashMap<>(); report.put("mode", "native Connection + wire-only peer; no Minecraft/client instance");
        report.put("boundary", "Manifest ACK mirrors the declared server manifest; unknown application payloads and game world packets are observed opaque. No 435-handler, registry remap, menu apply or UI claim.");
        report.put("endpoint", host + ":" + port); report.put("name", name); report.put("sessions", outcomes);
        boolean success = true;
        try {
            for (int i = 0; i < sessions; i++) {
                var session = new Session(name, host, port, playSeconds); session.run(); outcomes.add(session.report);
                if (session.failure != null) { success = false; break; }
                if (i + 1 < sessions) Thread.sleep(1000);
            }
            requireNoClientDefinitions(); report.put("clientOrGraphicsClassDefinitions", 0);
        } catch (Throwable failure) { success = false; report.put("failure", failure.toString()); }
        finally {
            report.put("success", success); Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            Connection.NETWORK_WORKER_GROUP.get().shutdownGracefully().syncUninterruptibly();
        }
        System.out.println("Protocol peer success=" + success + "; report=" + output);
        if (!success) System.exit(1);
    }

    private static void registerMainCodecs() throws ReflectiveOperationException {
        for (var spec : List.of(new String[] {"com.stardew.craft.pet.PetCatalogHandshake$Offer", "CODEC"},
                new String[] {"com.stardew.craft.pet.PetCatalogHandshake$Ack", "CODEC"},
                new String[] {"com.stardew.craft.network.CapabilityHelloPayload", "STREAM_CODEC"},
                new String[] {"com.stardew.craft.network.CapabilityAckPayload", "STREAM_CODEC"})) {
            var type = Class.forName(spec[0]);
            var id = (CustomPacketPayload.Type<?>) type.getField("TYPE").get(null);
            PAYLOAD_CODECS.put(id.id(), (StreamCodec) type.getField(spec[1]).get(null));
        }
    }
    private static void requireNoClientDefinitions() {
        if (!System.getProperty("stardew.protocolPeer.blockedClientDefinitions", "0").equals("0"))
            throw new AssertionError("Peer attempted to define client/graphics classes: " + System.getProperty("stardew.protocolPeer.blockedClientDefinitions"));
    }

    static final class Session {
        final String name, host; final int port, playSeconds;
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        final Map<String, Object> report = new ConcurrentHashMap<>();
        final Map<String, Integer> opaquePackets = new ConcurrentHashMap<>(), customPayloads = new ConcurrentHashMap<>();
        final List<Object> dataMaps = java.util.Collections.synchronizedList(new ArrayList<>()), fragments = java.util.Collections.synchronizedList(new ArrayList<>());
        final PeerDataMapAssembly mapAssembly = new PeerDataMapAssembly();
        final FragmentAssembler fragmentAssembly = new FragmentAssembler(FabricFragmentation.CLIENTBOUND_CHUNK_BYTES);
        volatile String failure; volatile boolean expectedClose, completedDwell, playLogin, manifestAck, dataMapAck;
        volatile long playStarted; volatile List<ProtocolManifest.Entry> manifest;
        int commonVersion = -1;
        Session(String name, String host, int port, int playSeconds) {
            this.name = name; this.host = host; this.port = port; this.playSeconds = playSeconds;
            report.put("startedUtc", java.time.Instant.now().toString()); report.put("opaqueGamePackets", opaquePackets);
            report.put("customPayloadCounts", customPayloads); report.put("dataMapSnapshots", dataMaps); report.put("fragmentTransfers", fragments);
        }
        void run() {
            try {
                Connection.connect(new InetSocketAddress(host, port), false, connection).syncUninterruptibly();
                connection.initiateServerboundPlayConnection(host, port, listener(ClientLoginPacketListener.class, ConnectionProtocol.LOGIN));
                UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                connection.send(new ServerboundHelloPacket(name, id));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                while (connection.isConnected() && failure == null && System.nanoTime() < deadline) {
                    connection.tick();
                    if (playLogin && System.nanoTime() - playStarted >= TimeUnit.SECONDS.toNanos(playSeconds)) {
                        expectedClose = true; completedDwell = true; connection.disconnect(Component.literal("StardewCraft development wire peer completed")); break;
                    }
                    Thread.sleep(25);
                }
                if (!playLogin && failure == null) failure = "No PLAY login packet before timeout/disconnect";
                if ((!manifestAck || !dataMapAck) && failure == null) failure = "Configuration did not complete both manifest and data map acknowledgements";
                if (!expectedClose && failure == null) failure = "Unexpected connection termination or timeout";
            } catch (Throwable error) { failure = error.toString(); }
            finally {
                expectedClose = true;
                if (connection.isConnected()) connection.disconnect(Component.literal("Development peer cleanup"));
                connection.handleDisconnection();
                report.put("finishedUtc", java.time.Instant.now().toString()); report.put("playLoginPacketObserved", playLogin);
                report.put("manifestAckSent", manifestAck); report.put("dataMapAckSent", dataMapAck); report.put("completedPlayDwellThenClosed", completedDwell);
                report.put("success", failure == null); if (failure != null) report.put("failure", failure);
            }
        }
        <T extends PacketListener> T listener(Class<T> type, ConnectionProtocol phase) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                try {
                    return switch (method.getName()) {
                        case "flow" -> PacketFlow.CLIENTBOUND;
                        case "protocol" -> phase;
                        case "isAcceptingMessages", "shouldHandleMessage" -> true;
                        case "toString" -> "HeadlessProtocolPeer/" + phase;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "onDisconnect" -> { report.put("disconnect", ((DisconnectionDetails) args[0]).reason().getString()); if (!expectedClose && failure == null) failure = "Server disconnected during " + phase; yield null; }
                        case "onPacketError" -> throw new IllegalStateException("Native packet failed", (Throwable) args[1]);
                        default -> { handle(method.getName(), args == null || args.length == 0 ? null : args[0], phase); yield null; }
                    };
                } catch (Throwable error) {
                    failure = method.getName() + ": " + error; error.printStackTrace(); connection.disconnect(Component.literal("Protocol peer failure")); throw error;
                }
            });
        }
        void handle(String method, Object packet, ConnectionProtocol phase) {
            switch (method) {
                case "handleHello" -> throw new UnsupportedOperationException("Online-mode encryption is outside this isolated offline peer");
                case "handleCompression" -> connection.setupCompression(((ClientboundLoginCompressionPacket) packet).getCompressionThreshold(), true);
                case "handleCustomQuery" -> connection.send(new ServerboundCustomQueryAnswerPacket(((ClientboundCustomQueryPacket) packet).transactionId(), null));
                case "handleGameProfile" -> {
                    var profile = ((ClientboundGameProfilePacket) packet).gameProfile(); report.put("profileUuid", profile.getId().toString());
                    connection.setupInboundProtocol(wire(ConfigurationProtocols.CLIENTBOUND_TEMPLATE, false, this), listener(ClientConfigurationPacketListener.class, ConnectionProtocol.CONFIGURATION));
                    connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
                    connection.setupOutboundProtocol(wire(ConfigurationProtocols.SERVERBOUND_TEMPLATE, false, this));
                    sendPayload(new BrandPayload("stardewcraft-wire-peer"));
                    connection.send(new ServerboundClientInformationPacket(ClientInformation.createDefault()));
                }
                case "handleKeepAlive" -> connection.send(new ServerboundKeepAlivePacket(((ClientboundKeepAlivePacket) packet).getId()));
                case "handlePing" -> connection.send(new ServerboundPongPacket(((ClientboundPingPacket) packet).getId()));
                case "handleRequestCookie" -> connection.send(new ServerboundCookieResponsePacket(((ClientboundCookieRequestPacket) packet).key(), null));
                case "handleDisconnect" -> { report.put("serverDisconnectPacket", packet.toString()); failure = "Server sent disconnect during " + phase; }
                case "handleCustomPayload" -> payload(((ClientboundCustomPayloadPacket) packet).payload(), phase);
                case "handleSelectKnownPacks" -> connection.send(new ServerboundSelectKnownPacks(List.of()));
                case "handleRegistryData" -> report.merge("registryDataPackets", 1, (a, b) -> (Integer) a + (Integer) b);
                case "handleUpdateTags", "handleEnabledFeatures", "handleResetChat", "handleCustomReportDetails", "handleServerLinks", "handleStoreCookie" -> report.merge(method, 1, (a, b) -> (Integer) a + (Integer) b);
                case "handleResourcePackPush", "handleTransfer" -> throw new UnsupportedOperationException("Unexpected required resource/transfer in isolated validation server: " + method);
                case "handleResourcePackPop" -> report.put("resourcePackPopObserved", true);
                case "handleConfigurationFinished" -> {
                    if (!manifestAck || !dataMapAck) throw new IllegalStateException("PLAY began without both custom acknowledgements");
                    connection.setupInboundProtocol(wire(GameProtocols.CLIENTBOUND_TEMPLATE, true, this), listener(ClientGamePacketListener.class, ConnectionProtocol.PLAY));
                    connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
                    connection.setupOutboundProtocol(wire(GameProtocols.SERVERBOUND_TEMPLATE, true, this));
                    playStarted = System.nanoTime(); report.put("configurationFinishedUtc", java.time.Instant.now().toString());
                }
                case "handleMovePlayer" -> connection.send(new ServerboundAcceptTeleportationPacket(((ClientboundPlayerPositionPacket) packet).getId()));
                default -> throw new UnsupportedOperationException("Unhandled native peer control packet: " + method);
            }
        }
        void payload(CustomPacketPayload payload, ConnectionProtocol phase) {
            customPayloads.merge(payload.type().id().toString(), 1, Integer::sum);
            if (payload instanceof RegistrationPayload registration) {
                if (phase == ConnectionProtocol.CONFIGURATION && registration.id().equals(RegistrationPayload.REGISTER)) {
                    sendPayload(new RegistrationPayload(RegistrationPayload.REGISTER, List.of(ProtocolManifest.Offer.TYPE.id(),
                            DataMapTransfer.Chunk.TYPE.id(), CommonVersionPayload.ID.id(), CommonRegisterPayload.ID.id())));
                }
            } else if (payload instanceof ProtocolManifest.Offer offer) {
                if (manifest != null) throw new IllegalStateException("Repeated protocol offer");
                manifest = offer.entries();
                if (manifest.isEmpty()) throw new IllegalStateException("Empty server manifest");
                report.put("manifestEntries", manifest.size()); report.put("manifestNonce", offer.nonce());
                report.put("manifest", manifest.stream().map(entry -> Map.of("id", entry.id().toString(), "protocol", entry.protocol().name(), "flow", entry.flow().name(), "version", entry.version(), "optional", entry.optional())).toList());
                Set<ResourceLocation> configReceives = new LinkedHashSet<>();
                manifest.stream().filter(entry -> entry.protocol() == ConnectionProtocol.CONFIGURATION && entry.flow() == PacketFlow.CLIENTBOUND).forEach(entry -> configReceives.add(entry.id()));
                configReceives.add(CommonVersionPayload.ID.id()); configReceives.add(CommonRegisterPayload.ID.id());
                sendPayload(new RegistrationPayload(RegistrationPayload.REGISTER, new ArrayList<>(configReceives)));
                report.put("announcedConfigurationChannels", configReceives.stream().map(Object::toString).toList());
                sendPayload(new ProtocolManifest.Ack(offer.nonce(), manifest)); manifestAck = true;
            } else if (payload instanceof CommonVersionPayload version) {
                commonVersion = java.util.Arrays.stream(version.versions()).filter(value -> value == 1).findFirst().orElseThrow();
                sendPayload(new CommonVersionPayload(new int[] {commonVersion}));
            } else if (payload instanceof CommonRegisterPayload registration) {
                if (!registration.phase().equals(CommonRegisterPayload.PLAY_PHASE) || manifest == null || commonVersion < 0)
                    throw new IllegalStateException("Unexpected Fabric common-register order/phase");
                Set<ResourceLocation> receives = new LinkedHashSet<>();
                manifest.stream().filter(entry -> entry.protocol() == ConnectionProtocol.PLAY && entry.flow() == PacketFlow.CLIENTBOUND).forEach(entry -> receives.add(entry.id()));
                receives.add(CommonVersionPayload.ID.id()); receives.add(CommonRegisterPayload.ID.id());
                sendPayload(new CommonRegisterPayload(commonVersion, CommonRegisterPayload.PLAY_PHASE, receives));
                report.put("announcedPlayChannels", receives.size());
            } else if (payload.type().id().toString().equals("stardewcraft:pet_catalog_offer")) {
                try {
                    var pets = Class.forName("com.stardew.craft.api.v1.pet.StardewPets"); pets.getMethod("freeze").invoke(null);
                    String local = (String) pets.getMethod("fingerprint").invoke(null);
                    String remote = (String) payload.getClass().getMethod("fingerprint").invoke(payload);
                    if (!local.equals(remote)) throw new IllegalStateException("Independent local pet catalog fingerprint mismatch");
                    sendPayload((CustomPacketPayload) Class.forName("com.stardew.craft.pet.PetCatalogHandshake$Ack").getConstructor(String.class).newInstance(local));
                    report.put("petCatalogFingerprint", local); report.put("petCatalogValidatedLocally", true);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot validate real pet catalog", error); }
            } else if (payload.type().id().toString().equals("stardewcraft:capability_hello_v1")) {
                try {
                    var registry = Class.forName("com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry");
                    var remote = (List<?>) payload.getClass().getMethod("capabilities").invoke(payload);
                    var result = registry.getMethod("accept", Connection.class, List.class).invoke(null, connection, remote);
                    if (!(Boolean) result.getClass().getMethod("accepted").invoke(result)) throw new IllegalStateException("Real capability registry rejected remote capabilities");
                    var local = (List<?>) registry.getMethod("local").invoke(null);
                    sendPayload((CustomPacketPayload) Class.forName("com.stardew.craft.network.CapabilityAckPayload").getConstructor(List.class).newInstance(local));
                    report.put("capabilitiesValidatedLocally", true); report.put("localCapabilityCount", local.size());
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot validate real capabilities", error); }
            } else if (payload instanceof DataMapTransfer.Chunk chunk) {
                var complete = mapAssembly.accept(chunk);
                if (complete != null) {
                    dataMaps.add(Map.of("bytes", complete.bytes(), "sha256", HexFormat.of().formatHex(complete.ack().digest()), "maps", complete.mapEntryCounts(), "phase", phase.name()));
                    if (phase == ConnectionProtocol.CONFIGURATION) { sendPayload(complete.ack()); dataMapAck = true; }
                }
            } else if (payload instanceof FragmentPayload fragment) {
                var complete = fragmentAssembly.accept(fragment, System.nanoTime());
                if (complete != null) fragments.add(Map.of("type", complete.type().toString(), "bytes", complete.bytes().length, "sha256", digest(complete.bytes()), "originalBody", "opaque; production assembler completed"));
            } else if (payload instanceof MenuDataPayload menu) {
                report.merge("menuDataPackets", 1, (a, b) -> (Integer) a + (Integer) b); report.put("lastMenuData", menu.toString());
            } else if (payload instanceof RawPayload raw) {
                report.merge("opaqueCustomPayloadBytes", raw.bytes().length, (a, b) -> (Integer) a + (Integer) b);
            }
        }
        void sendPayload(CustomPacketPayload payload) { connection.send(new ServerboundCustomPayloadPacket(payload)); }
        void observe(PacketType<?> type, int bytes) {
            opaquePackets.merge(type.id().toString(), 1, Integer::sum);
            if (type.id().getPath().equals("login")) { playLogin = true; report.put("playLoginObservedUtc", java.time.Instant.now().toString()); }
            if (type.id().getPath().equals("start_configuration")) throw new UnsupportedOperationException("Mid-play reconfiguration is outside this reconnect probe");
        }
    }

    /** Uses native public packet enumeration/codec; only custom payload envelopes and opaque game bodies differ. */
    static <T extends PacketListener> ProtocolInfo<T> wire(ProtocolInfo.Unbound<T, ? extends ByteBuf> template, boolean game, Session session) {
        java.util.function.Function<ByteBuf, ByteBuf> decorate = buffer -> game ? new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY) : new FriendlyByteBuf(buffer);
        ProtocolInfo<T> nativeProtocol = (ProtocolInfo<T>) ((ProtocolInfo.Unbound) template).bind(decorate);
        Map<Integer, PacketType<?>> packets = new LinkedHashMap<>(); template.listPackets((type, index) -> packets.put(index, type));
        int customId = packets.entrySet().stream().filter(entry -> entry.getValue().id().getPath().equals("custom_payload")).mapToInt(Map.Entry::getKey).findFirst().orElseThrow();
        return new ProtocolInfo<>() {
            public ConnectionProtocol id() { return nativeProtocol.id(); }
            public PacketFlow flow() { return nativeProtocol.flow(); }
            public BundlerInfo bundlerInfo() { return null; } // Bundle delimiters are counted opaque; no ClientLevel is constructed.
            public StreamCodec<ByteBuf, Packet<? super T>> codec() { return new StreamCodec<>() {
                public Packet<? super T> decode(ByteBuf buffer) {
                    int start = buffer.readerIndex(); var friendly = new FriendlyByteBuf(buffer); int index = friendly.readVarInt();
                    PacketType<?> type = packets.get(index); if (type == null) throw new IllegalArgumentException("Unknown native packet id " + index);
                    if (index == customId) return (Packet) new ClientboundCustomPayloadPacket(decodePayload(new RegistryFriendlyByteBuf(buffer, RegistryAccess.EMPTY)));
                    if (game && !PLAY_CONTROL.contains(type.id().getPath())) {
                        int bytes = buffer.readableBytes(); buffer.skipBytes(bytes); return (Packet) new ObservedGamePacket(type, bytes, session);
                    }
                    buffer.readerIndex(start); return nativeProtocol.codec().decode(buffer);
                }
                public void encode(ByteBuf buffer, Packet<? super T> packet) {
                    if (packet instanceof ServerboundCustomPayloadPacket custom) {
                        var friendly = new FriendlyByteBuf(buffer); friendly.writeVarInt(customId); encodePayload(friendly, custom.payload());
                    } else nativeProtocol.codec().encode(buffer, packet);
                }
            }; }
        };
    }
    record RawPayload(Type<RawPayload> type, byte[] bytes) implements CustomPacketPayload {}
    record ObservedGamePacket(PacketType<?> observedType, int bytes, Session session) implements Packet<ClientGamePacketListener> {
        public PacketType<? extends Packet<ClientGamePacketListener>> type() { return (PacketType) observedType; }
        public void handle(ClientGamePacketListener listener) { session.observe(observedType, bytes); }
    }
    static CustomPacketPayload decodePayload(RegistryFriendlyByteBuf buffer) {
        var id = buffer.readResourceLocation(); var codec = PAYLOAD_CODECS.get(id);
        if (codec != null) { var payload = (CustomPacketPayload) codec.decode(buffer); if (buffer.isReadable()) throw new IllegalArgumentException("Trailing payload bytes: " + id); return payload; }
        byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); return new RawPayload(new CustomPacketPayload.Type<>(id), bytes);
    }
    static void encodePayload(FriendlyByteBuf buffer, CustomPacketPayload payload) {
        buffer.writeResourceLocation(payload.type().id()); var codec = PAYLOAD_CODECS.get(payload.type().id());
        if (codec == null) { if (payload instanceof RawPayload raw) buffer.writeBytes(raw.bytes()); else throw new IllegalArgumentException("No peer codec " + payload.type().id()); }
        else codec.encode(buffer, payload);
    }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    static void selfTest() {
        var peer = new Session("FabricPortProbe", "127.0.0.1", 1, 1);
        for (var template : List.of(ConfigurationProtocols.CLIENTBOUND_TEMPLATE, ConfigurationProtocols.SERVERBOUND_TEMPLATE, GameProtocols.CLIENTBOUND_TEMPLATE, GameProtocols.SERVERBOUND_TEMPLATE)) {
            var info = wire((ProtocolInfo.Unbound) template, template.id() == ConnectionProtocol.PLAY, peer);
            var bytes = Unpooled.buffer();
            try {
                if (info.flow() == PacketFlow.SERVERBOUND) {
                    var payload = new ProtocolManifest.Ack(42, List.of());
                    info.codec().encode(bytes, new ServerboundCustomPayloadPacket(payload));
                    new FriendlyByteBuf(bytes).readVarInt();
                    if (!payload.equals(decodePayload(new RegistryFriendlyByteBuf(bytes, RegistryAccess.EMPTY)))) throw new AssertionError("Native envelope/real payload codec mismatch");
                }
            } finally { bytes.release(); }
        }
        peer.listener(ClientLoginPacketListener.class, ConnectionProtocol.LOGIN);
        peer.listener(ClientConfigurationPacketListener.class, ConnectionProtocol.CONFIGURATION);
        peer.listener(ClientGamePacketListener.class, ConnectionProtocol.PLAY);
        System.out.println("SELF-TEST PASS: real vanilla bootstrap, native protocol tables/codecs, custom ACK envelope and all three proxy listener interfaces; no socket opened");
    }
}
