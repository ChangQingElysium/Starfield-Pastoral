package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.interior.CrossDimensionTeleporter;
import com.stardew.craft.network.payload.OpenFarmJoinInvitePayload;
import com.stardew.craft.network.payload.FarmJoinPendingStatePayload;
import com.stardew.craft.player.PlayerDisplayName;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理农场加入请求。
 * 每个请求者同时只能有一个待处理请求。
 * 请求在 5 分钟后自动过期。
 */
public final class FarmJoinManager {

    private FarmJoinManager() {}

    /** 请求者UUID → 请求信息 */
    private static final Map<UUID, JoinRequest> pendingRequests = new ConcurrentHashMap<>();

    private static final long EXPIRE_TICKS = 6000L; // 5 minutes

    public record JoinRequest(UUID requesterUUID, String requesterName,
                              UUID ownerUUID, long createdTick) {}

    /**
     * 创建加入请求。
     * @return true 成功创建, false 失败（已有待处理请求或农场不存在等）
     */
    public static boolean createRequest(ServerPlayer requester, UUID ownerUUID, MinecraftServer server) {
        UUID reqUUID = requester.getUUID();

        // 清除过期请求
        cleanExpired(server);

        // 已有待处理请求
        if (pendingRequests.containsKey(reqUUID)) {
                        syncPendingState(requester, true);
            requester.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.already_pending"));
            return false;
        }

        // 验证目标农场
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarm(ownerUUID);
        if (farm == null || !farm.isInitialized()) {
                        syncPendingState(requester, false);
            requester.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.not_found"));
            return false;
        }

        // 农场已满
        int maxFarmers = ModGameRules.getMaxFarmersPerFarm(server);
        if (farm.getFarmerCount() >= maxFarmers) {
                        syncPendingState(requester, false);
            requester.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.farm_full", maxFarmers));
            return false;
        }

        // 请求者已有农场
        if (registry.hasFarm(reqUUID)) {
                        syncPendingState(requester, false);
            requester.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.already_has_farm"));
            return false;
        }

        // 存储请求
        long tick = server.overworld().getGameTime();
        String requesterName = PlayerDisplayName.get(requester);
        pendingRequests.put(reqUUID, new JoinRequest(reqUUID, requesterName,
                ownerUUID, tick));

        // 向农场主人发送加入申请弹窗
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
        if (owner != null) {
            sendJoinRequestToOwner(owner, requesterName, reqUUID, farm.getFarmName());
        } else {
                        syncPendingState(requester, false);
            requester.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.owner_offline"));
            pendingRequests.remove(reqUUID);
            return false;
        }

                syncPendingState(requester, true);
        requester.sendSystemMessage(
                Component.translatable("stardewcraft.farm.join.request_sent", farm.getFarmName()));
        StardewCraft.LOGGER.info("[FARM_JOIN] {} requested to join {}'s farm",
                requester.getName().getString(), farm.getOwnerName());
        return true;
    }

    /**
     * 处理加入响应（接受/拒绝）。
     */
    public static boolean handleResponse(ServerPlayer owner, UUID requesterUUID, boolean accept,
                                          MinecraftServer server) {
        JoinRequest request = pendingRequests.get(requesterUUID);
        if (request == null) {
            owner.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.no_pending"));
            return false;
        }

        // 验证是否是对应的农场主人
        if (!request.ownerUUID.equals(owner.getUUID())) {
            owner.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.not_your_request"));
            return false;
        }

        pendingRequests.remove(requesterUUID);

        if (!accept) {
            // 拒绝
            ServerPlayer requester = server.getPlayerList().getPlayer(requesterUUID);
            if (requester != null) {
                syncPendingState(requester, false);
                requester.sendSystemMessage(
                        Component.translatable("stardewcraft.farm.join.rejected",
                                PlayerDisplayName.get(owner)));
            }
            owner.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.you_rejected",
                            request.requesterName));
            StardewCraft.LOGGER.info("[FARM_JOIN] {} rejected {}'s join request",
                    owner.getName().getString(), request.requesterName);
            return true;
        }

        // 接受 — 添加成员
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();

        // 再次验证
        if (registry.hasFarm(requesterUUID)) {
            owner.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.already_has_farm"));
            return false;
        }

        if (!registry.addMember(owner.getUUID(), requesterUUID)) {
            int maxFarmers = ModGameRules.getMaxFarmersPerFarm(server);
            owner.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.farm_full", maxFarmers));
            return false;
        }

        // 通知双方
        FarmInstance farm = registry.getFarm(owner.getUUID());
        String farmName = farm != null ? farm.getFarmName() : "???";

        owner.sendSystemMessage(
                Component.translatable("stardewcraft.farm.join.accepted",
                        request.requesterName));

        ServerPlayer requester = server.getPlayerList().getPlayer(requesterUUID);
        if (requester != null) {
                        syncPendingState(requester, false);
            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                    requester, com.stardew.craft.player.PlayerDataManager.getPlayerData(requester));
            requester.sendSystemMessage(
                    Component.translatable("stardewcraft.farm.join.welcome",
                            farmName, PlayerDisplayName.get(owner)));
                        CrossDimensionTeleporter.wizardInteriorToStardewOutdoor(requester, true);
        }

        StardewCraft.LOGGER.info("[FARM_JOIN] {} accepted {}'s join request -> farm '{}'",
                owner.getName().getString(), request.requesterName, farmName);
        return true;
    }

        /**
         * 无参 accept/reject 的兜底入口：处理该农场主最近一条待处理请求。
         * 主要给聊天点击按钮使用，避免客户端在本地预解析带 UUID 的 RUN_COMMAND 时直接拦截。
         */
        public static boolean handleLatestResponse(ServerPlayer owner, boolean accept, MinecraftServer server) {
                cleanExpired(server);

                JoinRequest latest = null;
                for (JoinRequest request : pendingRequests.values()) {
                        if (!request.ownerUUID.equals(owner.getUUID())) {
                                continue;
                        }
                        if (latest == null || request.createdTick() > latest.createdTick()) {
                                latest = request;
                        }
                }

                if (latest == null) {
                        owner.sendSystemMessage(Component.translatable("stardewcraft.farm.join.no_pending"));
                        return false;
                }

                return handleResponse(owner, latest.requesterUUID(), accept, server);
        }

    /**
     * 获取某个请求者的待处理请求。
     */
    public static JoinRequest getPending(UUID requesterUUID) {
        return pendingRequests.get(requesterUUID);
    }

        public static boolean hasPending(UUID requesterUUID) {
                return pendingRequests.containsKey(requesterUUID);
        }

    /**
     * 取消某个请求者的待处理请求。
     */
    public static void cancelRequest(UUID requesterUUID) {
        pendingRequests.remove(requesterUUID);
    }

        public static void cancelRequestForNewFarm(ServerPlayer requester, MinecraftServer server) {
                JoinRequest request = pendingRequests.remove(requester.getUUID());
                syncPendingState(requester, false);
                if (request == null) {
                        return;
                }

                requester.sendSystemMessage(Component.translatable("stardewcraft.farm.join.cancelled_for_new_farm"));

                ServerPlayer owner = server.getPlayerList().getPlayer(request.ownerUUID());
                if (owner != null) {
                        owner.sendSystemMessage(Component.translatable(
                                        "stardewcraft.farm.join.cancelled_by_requester",
                                        request.requesterName()));
                }

                StardewCraft.LOGGER.info("[FARM_JOIN] {} cancelled a pending join request to create a new farm",
                                requester.getName().getString());
        }

    /**
     * 清除过期请求。
     */
    private static void cleanExpired(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        pendingRequests.entrySet().removeIf(e -> now - e.getValue().createdTick > EXPIRE_TICKS);
    }

        public static void syncPendingState(ServerPlayer player, boolean pending) {
                PacketDistributor.sendToPlayer(player, new FarmJoinPendingStatePayload(pending));
        }

    /**
     * 向农场主人发送加入申请弹窗。
     */
    private static void sendJoinRequestToOwner(ServerPlayer owner, String requesterName, UUID requesterUUID, String farmName) {
        owner.sendSystemMessage(Component.translatable("stardewcraft.farm.join.incoming", requesterName));
        PacketDistributor.sendToPlayer(owner, new OpenFarmJoinInvitePayload(requesterUUID, requesterName, farmName));
    }
}
