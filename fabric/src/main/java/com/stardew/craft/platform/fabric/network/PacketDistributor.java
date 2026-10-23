package com.stardew.craft.platform.fabric.network;

import com.stardew.craft.platform.fabric.network.client.FabricClientNetworking;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/** Server recipients use Fabric tracking lookups; payload bundles retain their atomic ordering. */
public final class PacketDistributor {
    private static volatile MinecraftServer currentServer;

    static void initializeServerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (currentServer == server) {
                currentServer = null;
            }
        });
    }

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... additional) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("A dedicated server cannot send a serverbound payload");
        }
        FabricClientNetworking.sendToServer(payload, additional);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload... additional) {
        Objects.requireNonNull(player, "Recipient");
        // Automated/test actors have no receiving client. Match the original
        // dummy listener before eagerly encoding/fragmenting a human payload.
        if (player instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
        requireServerThread(player.serverLevel().getServer());
        player.connection.send(clientboundPacket(player.registryAccess(), payload, additional));
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload,
            CustomPacketPayload... additional) {
        Objects.requireNonNull(level, "Level");
        requireServerThread(level.getServer());
        level.getServer().getPlayerList()
                .broadcastAll(clientboundPacket(level.registryAccess(), payload, additional), level.dimension());
    }

    public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded,
            double x, double y, double z, double radius, CustomPacketPayload payload,
            CustomPacketPayload... additional) {
        Objects.requireNonNull(level, "Level");
        requireServerThread(level.getServer());
        level.getServer().getPlayerList().broadcast(excluded, x, y, z, radius, level.dimension(),
                clientboundPacket(level.registryAccess(), payload, additional));
    }

    public static void sendToAllPlayers(CustomPacketPayload payload, CustomPacketPayload... additional) {
        MinecraftServer server = Objects.requireNonNull(currentServer,
                "There is no running Fabric server for clientbound distribution");
        requireServerThread(server);
        server.getPlayerList().broadcastAll(clientboundPacket(server.registryAccess(), payload, additional));
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload,
            CustomPacketPayload... additional) {
        ServerLevel level = serverLevel(entity);
        requireServerThread(level.getServer());
        sendTo(PlayerLookup.tracking(entity), clientboundPacket(level.registryAccess(), payload, additional));
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload,
            CustomPacketPayload... additional) {
        ServerLevel level = serverLevel(entity);
        requireServerThread(level.getServer());
        Collection<ServerPlayer> tracking = PlayerLookup.tracking(entity);
        LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(tracking);
        if (entity instanceof ServerPlayer player) {
            recipients.add(player);
        }
        sendTo(recipients, clientboundPacket(level.registryAccess(), payload, additional));
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos position,
            CustomPacketPayload payload, CustomPacketPayload... additional) {
        Objects.requireNonNull(level, "Level");
        requireServerThread(level.getServer());
        sendTo(PlayerLookup.tracking(level, Objects.requireNonNull(position, "Chunk position")),
                clientboundPacket(level.registryAccess(), payload, additional));
    }

    private static void sendTo(Collection<ServerPlayer> recipients, Packet<?> packet) {
        for (ServerPlayer player : recipients) {
            player.connection.send(packet);
        }
    }

    private static Packet<?> clientboundPacket(RegistryAccess registries, CustomPacketPayload payload,
            CustomPacketPayload... additional) {
        Objects.requireNonNull(payload, "Payload");
        Objects.requireNonNull(additional, "Additional payloads");
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(additional.length + 1);
        addPackets(packets, registries, payload);
        for (CustomPacketPayload next : additional) {
            addPackets(packets, registries, Objects.requireNonNull(next, "Additional payload"));
        }
        return packets.size() == 1 ? packets.getFirst() : new ClientboundBundlePacket(packets);
    }

    private static void addPackets(List<Packet<? super ClientGamePacketListener>> packets,
            RegistryAccess registries, CustomPacketPayload payload) {
        for (CustomPacketPayload frame : FabricFragmentation.prepare(payload, registries, PacketFlow.CLIENTBOUND)) {
            packets.add(new ClientboundCustomPayloadPacket(frame));
        }
    }

    private static ServerLevel serverLevel(Entity entity) {
        if (!(Objects.requireNonNull(entity, "Entity").level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Clientbound distribution requires a server-level entity");
        }
        return level;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Payload distribution must run on the server thread");
        }
    }

    private PacketDistributor() {}
}
