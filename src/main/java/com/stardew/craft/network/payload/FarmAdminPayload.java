package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.command.FarmAdminCommand;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.farm.FarmInstance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C→S: GUI 中执行管理操作。
 * action:
 *   0 — 请求刷新农场列表
 *   1 — 删除农场 (targetUUID)
 *   2 — 重命名农场 (targetUUID, extraString=新名称)
 *   3 — 传送到农场 (targetUUID)
 *   4 — 转移农场 (targetUUID=原主人, extraString=目标玩家名)
 */
@SuppressWarnings("null")
public record FarmAdminPayload(
        int action,
        UUID targetUUID,
        String extraString
) implements CustomPacketPayload {

    public static final Type<FarmAdminPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_admin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmAdminPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmAdminPayload decode(RegistryFriendlyByteBuf buf) {
                    int action = buf.readByte();
                    UUID uuid = buf.readUUID();
                    String extra = buf.readUtf(256);
                    return new FarmAdminPayload(action, uuid, extra);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmAdminPayload payload) {
                    buf.writeByte(payload.action);
                    buf.writeUUID(payload.targetUUID);
                    buf.writeUtf(payload.extraString, 256);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmAdminPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!FarmAdminCommand.isStardewOp(player)) return; // 星露谷 OP 权限检查

            FarmInstanceRegistry registry = FarmInstanceRegistry.get();

            switch (payload.action) {
                case 0 -> { // 刷新列表
                    PacketDistributor.sendToPlayer(player, FarmAdminSyncPayload.fromRegistry(registry));
                }
                case 1 -> { // 删除农场
                    String ownerDisplayName = com.stardew.craft.player.PlayerDisplayName.get(
                            player.server, payload.targetUUID);
                    FarmInstance farm = registry.deleteFarm(payload.targetUUID);
                    if (farm != null) {
                        FarmPermissionManager.get().clearAllForOwner(payload.targetUUID);
                        player.displayClientMessage(Component.translatable(
                                "stardewcraft.farm.admin.deleted", ownerDisplayName), false);
                    } else {
                        player.displayClientMessage(Component.translatable("stardewcraft.farm.admin.not_found"), false);
                    }
                    // 刷新列表
                    PacketDistributor.sendToPlayer(player, FarmAdminSyncPayload.fromRegistry(registry));
                }
                case 2 -> { // 重命名
                    boolean ok = registry.renameFarm(payload.targetUUID, payload.extraString);
                    if (ok) {
                        player.displayClientMessage(Component.translatable(
                                "stardewcraft.farm.admin.renamed", payload.extraString), false);
                    } else {
                        player.displayClientMessage(Component.translatable("stardewcraft.farm.admin.not_found"), false);
                    }
                    PacketDistributor.sendToPlayer(player, FarmAdminSyncPayload.fromRegistry(registry));
                }
                case 3 -> { // 传送
                    FarmInstance farm = registry.getFarm(payload.targetUUID);
                    if (farm != null) {
                        net.minecraft.core.BlockPos tp = farm.getOrigin().offset(128, 10, 128);
                        player.teleportTo(
                                player.serverLevel(),
                                tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5,
                                0, 0);
                        player.displayClientMessage(Component.translatable(
                                "stardewcraft.farm.admin.teleported",
                                com.stardew.craft.player.PlayerDisplayName.get(
                                        player.server, farm.getOwnerUUID())), true);
                    } else {
                        player.displayClientMessage(Component.translatable("stardewcraft.farm.admin.not_found"), false);
                    }
                }
                case 4 -> { // 转移农场
                    String targetName = payload.extraString.trim();
                    if (targetName.isEmpty()) {
                        player.displayClientMessage(Component.translatable("stardewcraft.farm.admin.target_required"), false);
                        return;
                    }
                    ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayerByName(targetName);
                    if (targetPlayer == null) {
                        player.displayClientMessage(Component.translatable(
                                "stardewcraft.farm.admin.player_offline", targetName), false);
                        return;
                    }
                    String targetDisplayName = com.stardew.craft.player.PlayerDisplayName.get(targetPlayer);
                    boolean ok = registry.transferFarm(payload.targetUUID, targetPlayer.getUUID(), targetDisplayName);
                    if (ok) {
                        FarmPermissionManager.get().transferPermissions(payload.targetUUID, targetPlayer.getUUID());
                        player.displayClientMessage(Component.translatable(
                                "stardewcraft.farm.admin.transferred", targetDisplayName), false);
                    } else {
                        player.displayClientMessage(Component.translatable(
                                "stardewcraft.farm.admin.transfer_failed"), false);
                    }
                    PacketDistributor.sendToPlayer(player, FarmAdminSyncPayload.fromRegistry(registry));
                }
            }
        });
    }
}
