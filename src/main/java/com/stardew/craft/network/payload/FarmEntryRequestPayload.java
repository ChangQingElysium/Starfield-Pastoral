package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * C→S: 玩家在农场入口 GUI 中选择了一个农场进入。
 */
@SuppressWarnings("null")
public record FarmEntryRequestPayload(
        UUID targetOwner,
        String entryTag // farm_entry_south / east / west
) implements CustomPacketPayload {

    public static final Type<FarmEntryRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_entry_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmEntryRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public FarmEntryRequestPayload decode(RegistryFriendlyByteBuf buf) {
                    return new FarmEntryRequestPayload(buf.readUUID(), buf.readUtf());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, FarmEntryRequestPayload payload) {
                    buf.writeUUID(payload.targetOwner);
                    buf.writeUtf(payload.entryTag);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmEntryRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            FarmInstanceRegistry registry = FarmInstanceRegistry.get();
            FarmInstance farm = registry.getFarm(payload.targetOwner);
            if (farm == null || !farm.isInitialized()) {
                player.displayClientMessage(Component.translatable("stardewcraft.farm.not_found"), true);
                return;
            }

            // 权限检查：成员可直接进入
            if (!farm.isFarmer(player.getUUID())) {
                FarmPermissionManager permMgr = FarmPermissionManager.get();
                if (!permMgr.canVisit(farm.getOwnerUUID(), player.getUUID())) {
                    player.displayClientMessage(Component.translatable("stardewcraft.farm.no_access"), true);
                    return;
                }
            }

            // 触发区块加载
            ServerLevel stardewLevel = player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if (stardewLevel == null) {
                player.displayClientMessage(Component.translatable("stardewcraft.farm.not_found"), true);
                return;
            }
            // 根据入口方向路由
            BlockPos targetPos;
            float yaw;
            switch (payload.entryTag) {
                case "farm_entry_east" -> {
                    targetPos = farm.getEastEntryPos();
                    yaw = farm.getEastEntryYaw();
                }
                case "farm_entry_west" -> {
                    targetPos = farm.getWestEntryPos();
                    yaw = farm.getWestEntryYaw();
                }
                default -> {
                    targetPos = farm.getSouthEntryPos();
                    yaw = farm.getSouthEntryYaw();
                }
            }

            ModTeleport.to(player, stardewLevel,
                    targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5,
                    yaw, 0.0F);

            // 首次进入农场：给予新手工具（成员加入后首次进入也适用）
            com.stardew.craft.interior.CrossDimensionTeleporter.giveStarterToolsIfNeeded(player);

            StardewCraft.LOGGER.info("[FARM_ENTRY] {} entered {}'s farm via {}",
                    player.getName().getString(), farm.getOwnerName(), payload.entryTag);
        });
    }
}
