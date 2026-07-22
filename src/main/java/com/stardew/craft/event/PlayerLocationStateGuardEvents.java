package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.network.MiningFloorSyncPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PlayerLocationStateGuardEvents {
    private static final double TELEPORT_DISTANCE_SQR = 64.0D * 64.0D;
    private static final Map<UUID, LocationSnapshot> LAST_LOCATIONS = new ConcurrentHashMap<>();

    private PlayerLocationStateGuardEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        LocationSnapshot current = LocationSnapshot.of(player);
        LocationSnapshot previous = LAST_LOCATIONS.put(player.getUUID(), current);
        boolean dimensionChanged = previous != null && !previous.dimension.equals(current.dimension);
        boolean teleported = previous != null && previous.distanceToSqr(current) > TELEPORT_DISTANCE_SQR;

        if (dimensionChanged || teleported || player.tickCount % 40 == 0) {
            reconcileLocationState(player, dimensionChanged || teleported);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var server = player.server;
        UUID playerId = player.getUUID();
        server.tell(new net.minecraft.server.TickTask(
            server.getTickCount() + 1,
            () -> {
                if (server.getPlayerList().getPlayer(playerId) == player) {
                    reconcileLocationState(player, true);
                }
            }));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_LOCATIONS.remove(player.getUUID());
        }
    }

    public static void reconcileLocationState(ServerPlayer player, boolean justTeleported) {
        com.stardew.craft.farm.FarmChunkManager.get().reconcilePlayerOccupancy(player);
        ResourceKey<Level> dimension = player.serverLevel().dimension();

        if (!ModMiningDimensions.STARDEW_MINING.equals(dimension)) {
            clearMineFloorIfNeeded(player);
        }

        if (!InteriorPortalInteractionEvents.isPlayerInInteriorSpace(player)) {
            return;
        }

        if (!ModDimensions.STARDEW_VALLEY.equals(dimension)) {
            InteriorPortalInteractionEvents.clearInteriorState(player);
            return;
        }

        if (justTeleported && !InteriorPortalInteractionEvents.isRecentPortalTeleport(player, 2L)) {
            InteriorPortalInteractionEvents.clearInteriorState(player);
        }
    }

    private static void clearMineFloorIfNeeded(ServerPlayer player) {
        ServerLevel miningLevel = player.server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (miningLevel == null) {
            return;
        }

        MiningPlayerData miningData = MiningDataManager.getPlayerData(player);
        if (miningData.getCurrentFloor() == 0) {
            return;
        }

        miningData.setCurrentFloor(0);
        MiningDataManager.savePlayerData(player, miningData);
        PacketDistributor.sendToPlayer(player, new MiningFloorSyncPacket(0));

        if (com.stardew.craft.mining.SkullCavernSessionManager.isPlayerInSkullCavern(player.getUUID())) {
            com.stardew.craft.mining.SkullCavernSessionManager.onPlayerLeave(player, miningLevel);
        }
    }

    private record LocationSnapshot(ResourceKey<Level> dimension, double x, double y, double z) {
        static LocationSnapshot of(ServerPlayer player) {
            return new LocationSnapshot(
                player.serverLevel().dimension(),
                player.getX(),
                player.getY(),
                player.getZ());
        }

        double distanceToSqr(LocationSnapshot other) {
            if (!dimension.equals(other.dimension)) {
                return Double.POSITIVE_INFINITY;
            }
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
