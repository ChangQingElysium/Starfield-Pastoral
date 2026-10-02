package com.stardew.craft.port.net.neoforged.neoforge.network;

import com.stardew.craft.port.internal.network.PortDistribution;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * NeoForge 21.1 {@code PacketDistributor} static API on Forge 1.20.1. Several payloads in one call
 * are sent as one {@code ClientboundBundlePacket}, like NeoForge.
 */
public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        PortDistribution.sendToServer(payload, payloads);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        player.connection.send(PortDistribution.clientbound(payload, payloads));
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        level.getServer().getPlayerList().broadcastAll(PortDistribution.clientbound(payload, payloads), level.dimension());
    }

    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z,
            double radius, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        level.getServer().getPlayerList().broadcast(excluded, x, y, z, radius, level.dimension(),
                PortDistribution.clientbound(payload, payloads));
    }

    public static void sendToAllPlayers(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Cannot send clientbound payloads without a running server");
        }
        server.getPlayerList().broadcastAll(PortDistribution.clientbound(payload, payloads));
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        chunkCache(entity).broadcast(entity, PortDistribution.clientbound(payload, payloads));
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        chunkCache(entity).broadcastAndSend(entity, PortDistribution.clientbound(payload, payloads));
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload,
            CustomPacketPayload... payloads) {
        Packet<?> packet = PortDistribution.clientbound(payload, payloads);
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            player.connection.send(packet);
        }
    }

    private static ServerChunkCache chunkCache(Entity entity) {
        if (entity.level().getChunkSource() instanceof ServerChunkCache cache) {
            return cache;
        }
        throw new IllegalStateException("Cannot send clientbound payloads on the client");
    }
}
