package com.stardew.craft.npc.runtime;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.network.payload.OpenFarmSelectionPayload;
import com.stardew.craft.network.payload.OpenLewisConfirmPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmCancellationService {
    private static final Map<UUID, PendingCancellation> PENDING = new ConcurrentHashMap<>();

    private FarmCancellationService() {
    }

    public static void requestCancellation(ServerPlayer requester) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        UUID ownerId = registry.getOwnerForPlayer(requester.getUUID());
        FarmInstance farm = ownerId == null ? null : registry.getFarm(ownerId);
        if (farm == null) {
            PacketDistributor.sendToPlayer(requester, new OpenFarmSelectionPayload());
            return;
        }

        Set<UUID> members = new LinkedHashSet<>(farm.getAllFarmers());
        Set<ServerPlayer> onlineMembers = new LinkedHashSet<>();
        for (UUID memberId : members) {
            ServerPlayer online = requester.server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                requester.displayClientMessage(Component.translatable("stardewcraft.lewis.farm_cancel.offline_blocked"), false);
                return;
            }
            onlineMembers.add(online);
        }

        UUID requestId = UUID.randomUUID();
        PendingCancellation pending = new PendingCancellation(requestId, ownerId, members, new LinkedHashSet<>());
        PENDING.put(requestId, pending);
        for (ServerPlayer member : onlineMembers) {
            PacketDistributor.sendToPlayer(member, new OpenLewisConfirmPayload(
                requestId,
                OpenLewisConfirmPayload.KIND_FARM_CANCEL,
                "stardewcraft.lewis.farm_cancel.question",
                java.util.List.of(farm.getFarmName()),
                "stardewcraft.dialog.yes",
                "stardewcraft.dialog.no"));
        }
    }

    public static void handleConfirm(ServerPlayer responder, UUID requestId, boolean accepted) {
        PendingCancellation pending = PENDING.get(requestId);
        if (pending == null || !pending.required().contains(responder.getUUID())) {
            return;
        }
        if (!accepted) {
            PENDING.remove(requestId);
            notifyMembers(responder.server, pending, Component.translatable(
                    "stardewcraft.lewis.farm_cancel.rejected",
                    com.stardew.craft.player.PlayerDisplayName.get(responder)));
            return;
        }
        PendingCancellation next = pending.withAccepted(responder.getUUID());
        PENDING.put(requestId, next);
        if (next.accepted().containsAll(next.required())) {
            PENDING.remove(requestId);
            executeCancellation(responder, next.ownerId());
        }
    }

    private static void executeCancellation(ServerPlayer actor, UUID ownerId) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarm(ownerId);
        if (farm == null) {
            return;
        }
        ServerLevel stardewLevel = actor.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            actor.displayClientMessage(Component.translatable("stardewcraft.farm.not_found"), false);
            return;
        }

        Set<UUID> members = new LinkedHashSet<>(farm.getAllFarmers());
        clearFarmArea(stardewLevel, farm);
        GreenhouseManager.get(stardewLevel).clearForOwner(ownerId);
        FarmPermissionManager.get().clearAllForOwner(ownerId);
        registry.deleteFarm(ownerId);

        for (UUID memberId : members) {
            ServerPlayer member = actor.server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                    member, com.stardew.craft.player.PlayerDataManager.getPlayerData(member));
                PacketDistributor.sendToPlayer(member, new OpenFarmSelectionPayload());
                member.displayClientMessage(Component.translatable("stardewcraft.lewis.farm_cancel.completed"), false);
            }
        }
    }

    private static void clearFarmArea(ServerLevel level, FarmInstance farm) {
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }

        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        for (Entity entity : level.getEntities((Entity) null, bounds, entity -> !(entity instanceof ServerPlayer))) {
            entity.discard();
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                    }
                }
            }
        }
    }

    private static void notifyMembers(net.minecraft.server.MinecraftServer server, PendingCancellation pending, Component message) {
        for (UUID memberId : pending.required()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.displayClientMessage(message, false);
            }
        }
    }

    private record PendingCancellation(UUID requestId, UUID ownerId, Set<UUID> required, Set<UUID> accepted) {
        PendingCancellation withAccepted(UUID playerId) {
            Set<UUID> next = new LinkedHashSet<>(accepted);
            next.add(playerId);
            return new PendingCancellation(requestId, ownerId, required, next);
        }
    }
}
