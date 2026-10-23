package com.stardew.craft.platform.fabric.datamap;

import com.stardew.craft.platform.fabric.network.ICustomConfigurationTask;
import com.stardew.craft.platform.fabric.network.IPayloadContext;
import com.stardew.craft.platform.fabric.network.PacketDistributor;
import com.stardew.craft.platform.fabric.network.RegisterConfigurationTasksEvent;
import com.stardew.craft.platform.fabric.network.RegisterPayloadHandlersEvent;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration ACK gates entry; resource reloads replace the client snapshot atomically in play. */
public final class DataMapSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("stardewcraft/fabric-data-map-sync");
    private static final ICustomConfigurationTask.Type TASK = new ICustomConfigurationTask.Type("stardewcraft:fabric_data_maps");
    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(30);
    private static final DataMapAcknowledgements PENDING = new DataMapAcknowledgements();
    private static final Map<Connection, DataMapTransfer.Assembly> INBOUND = new ConcurrentHashMap<>();
    private static boolean installed;

    private DataMapSync() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("fabric-data-maps/1").optional();
        registrar.configurationToClient(DataMapTransfer.Chunk.TYPE, DataMapTransfer.Chunk.CODEC, DataMapSync::acceptChunk);
        registrar.configurationToServer(DataMapTransfer.Ack.TYPE, DataMapTransfer.Ack.CODEC, DataMapSync::acceptAck);
        registrar.playToClient(DataMapTransfer.Chunk.TYPE, DataMapTransfer.Chunk.CODEC, DataMapSync::acceptChunk);
    }

    public static synchronized void installServerLifecycle() {
        if (installed) return;
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((handler, server) -> forget(
                ((com.stardew.craft.platform.mixin.network.mixin.ServerCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> forget(
                ((com.stardew.craft.platform.mixin.network.mixin.ServerCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(
                ((com.stardew.craft.platform.mixin.network.mixin.ServerCommonConnectionAccessor) handler).stardewcraft$getConnection()));
        ServerTickEvents.END_SERVER_TICK.register(server -> PENDING.expire(System.nanoTime()));
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            if (joined || !ServerPlayNetworking.canSend(player, DataMapTransfer.Chunk.TYPE)) return;
            byte[] snapshot = DataMapSnapshot.encode(player.server.registryAccess(), DataMaps.types());
            for (var chunk : DataMapTransfer.split(ThreadLocalRandom.current().nextLong(), snapshot)) PacketDistributor.sendToPlayer(player, chunk);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (var type : DataMaps.types()) {
                var registry = server.registryAccess().registry(type.registryKey());
                registry.ifPresent(value -> DataMaps.clearServer(value.holderOwner()));
            }
        });
        installed = true;
    }

    public static void registerConfigurationTask(RegisterConfigurationTasksEvent event) {
        var listener = event.getListener();
        if (!listener.hasChannel(DataMapTransfer.Chunk.TYPE) || !listener.hasChannel(DataMapTransfer.Ack.TYPE)) {
            if (DataMaps.types().stream().anyMatch(DataMapType::mandatorySync)) listener.disconnect(error());
            return;
        }
        event.register(new ICustomConfigurationTask() {
            @Override public Type type() { return TASK; }
            @Override public void run(Consumer<CustomPacketPayload> sender) {
                byte[] snapshot = DataMapSnapshot.encode(event.getServer().registryAccess(), DataMaps.types());
                long nonce = ThreadLocalRandom.current().nextLong();
                PENDING.begin(listener.getConnection(), nonce, digest(snapshot), System.nanoTime() + TIMEOUT, () -> {
                    listener.disconnect(error());
                    LOGGER.error("Timed out waiting for data map configuration acknowledgement");
                });
                for (var chunk : DataMapTransfer.split(nonce, snapshot)) sender.accept(chunk);
            }
        });
    }

    private static void acceptChunk(DataMapTransfer.Chunk chunk, IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND || (context.protocol() != ConnectionProtocol.CONFIGURATION
                && context.protocol() != ConnectionProtocol.PLAY)) throw new IllegalStateException("Invalid data map transfer direction");
        var assembly = INBOUND.computeIfAbsent(context.connection(), ignored -> new DataMapTransfer.Assembly(chunk));
        byte[] snapshot;
        try { snapshot = assembly.accept(chunk); }
        catch (RuntimeException failure) { INBOUND.remove(context.connection(), assembly); throw failure; }
        if (snapshot == null) return;
        INBOUND.remove(context.connection(), assembly);
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var decoded = DataMapSnapshot.decode(snapshot, registries, DataMaps.types());
        DataMaps.publishClient(decoded);
        if (context.protocol() == ConnectionProtocol.CONFIGURATION) context.reply(new DataMapTransfer.Ack(chunk.nonce(), digest(snapshot)));
    }

    private static void acceptAck(DataMapTransfer.Ack ack, IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND || context.protocol() != ConnectionProtocol.CONFIGURATION) {
            throw new IllegalStateException("Data map ACK must arrive during server configuration");
        }
        PENDING.acknowledge(context.connection(), ack);
        context.finishCurrentTask(TASK);
    }

    public static void forget(Connection connection) { PENDING.forget(connection); INBOUND.remove(connection); }

    public static void clearClientValues() {
        for (var type : DataMaps.types()) if (type.networkCodec() != null) {
            var registry = BuiltInRegistries.REGISTRY.get(type.registryKey().location());
            if (registry != null) DataMaps.clearClient(registry.holderOwner());
        }
    }

    static byte[] digest(byte[] snapshot) {
        try { return MessageDigest.getInstance("SHA-256").digest(snapshot); }
        catch (NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }

    private static Component error() { return Component.translatable("disconnect.stardewcraft.fabric.protocol_error"); }
}
