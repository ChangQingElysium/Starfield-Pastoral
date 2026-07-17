package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.interior.CrossDimensionTeleporter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S: 玩家在农场选择 GUI 中确认了农场类型和名称。
 * 服务端创建农场实例并传送玩家。
 */
@SuppressWarnings("null")
public record FarmSelectionSubmitPayload(
        String farmTypeId,
        String farmName,
        boolean forceCancelPending,
        String preferredName,
        String favoriteThing,
        boolean male
) implements CustomPacketPayload {

    public static final Type<FarmSelectionSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_selection_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmSelectionSubmitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FarmSelectionSubmitPayload::farmTypeId,
                    ByteBufCodecs.STRING_UTF8, FarmSelectionSubmitPayload::farmName,
                    ByteBufCodecs.BOOL, FarmSelectionSubmitPayload::forceCancelPending,
                    ByteBufCodecs.STRING_UTF8, FarmSelectionSubmitPayload::preferredName,
                    ByteBufCodecs.STRING_UTF8, FarmSelectionSubmitPayload::favoriteThing,
                    ByteBufCodecs.BOOL, FarmSelectionSubmitPayload::male,
                    FarmSelectionSubmitPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmSelectionSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // This payload is sent immediately before the profile screen closes. Clear the cached
            // menu state now so the next server tick cannot keep the Stardew world paused while
            // the farm initialization task is running.
            com.stardew.craft.time.StardewTimePauseService.updateClientState(player, false);

            String preferredName = sanitizeProfileText(payload.preferredName(), 48);
            String favoriteThing = sanitizeProfileText(payload.favoriteThing(), 64);
            if (preferredName.isBlank() || favoriteThing.isBlank()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "stardewcraft.player_profile.validation.required"));
                return;
            }

            FarmInstanceRegistry registry = FarmInstanceRegistry.get();

            if (com.stardew.craft.farm.FarmJoinManager.hasPending(player.getUUID())) {
                if (!payload.forceCancelPending()) {
                    com.stardew.craft.farm.FarmJoinManager.syncPendingState(player, true);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new OpenFarmSelectionPayload());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "stardewcraft.farm.join.confirm_cancel_before_create"));
                    return;
                }
                com.stardew.craft.farm.FarmJoinManager.cancelRequestForNewFarm(player, player.server);
            }

            // 防止重复创建
            if (registry.hasFarm(player.getUUID())) {
                StardewCraft.LOGGER.warn("[FARM_SELECT] {} already has a farm, skipping creation",
                        player.getName().getString());
                // 已有农场，直接传送
                CrossDimensionTeleporter.wizardInteriorToStardewOutdoor(player);
                return;
            }

            // 验证农场类型
            FarmType farmType = FarmType.fromId(payload.farmTypeId);
            if (!farmType.isUnlocked()) {
                StardewCraft.LOGGER.warn("[FARM_SELECT] {} tried to select locked farm type: {}",
                        player.getName().getString(), payload.farmTypeId);
                farmType = FarmType.STANDARD;
            }

            // 验证名称
            String name = payload.farmName;
            if (name == null || name.isBlank()) {
                name = preferredName;
            }
            if (name.length() > 48) {
                name = name.substring(0, 48);
            }
            name = name.trim();

            // 创建农场实例
            com.stardew.craft.player.PlayerStardewData playerData =
                    com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            playerData.setProfile(preferredName, favoriteThing, payload.male() ? 0 : 1);
            com.stardew.craft.player.PlayerDataManager.get().setDirty();
            FarmInstance farm = registry.createFarm(player.getUUID(), preferredName, name, farmType);

            StardewCraft.LOGGER.info("[FARM_SELECT] {} created farm '{}' (type={})",
                    player.getName().getString(), name, farmType.getId());

            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                    player, playerData);

            // 获取星露谷维度并初始化农场（分帧异步放置 schematic，减少卡顿）
            ServerLevel stardewLevel = player.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if (stardewLevel != null && farm != null) {
                // 发送"正在准备农场"标题给玩家
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        net.minecraft.network.chat.Component.translatable("stardewcraft.farm.loading.title")));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        net.minecraft.network.chat.Component.translatable("stardewcraft.farm.loading.subtitle")));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                        10, 200, 20)); // fadeIn=0.5s, stay=10s, fadeOut=1s

                // 真正跨 tick 执行初始化，让标题包先在当前 tick 结束时发到客户端。
                final FarmInstance farmRef = farm;
                var server = stardewLevel.getServer();
                server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 2, () -> {
                    com.stardew.craft.farm.FarmInstanceInitializer.initializeFarm(stardewLevel, farmRef);
                    // 再跨一个 tick 清除标题并传送，保证客户端至少渲染一帧完成状态。
                    server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
                        CrossDimensionTeleporter.wizardInteriorToStardewOutdoorAfterFarmInitialization(player);
                    }));
                }));
            } else {
                CrossDimensionTeleporter.wizardInteriorToStardewOutdoor(player);
            }
        });
    }

    private static String sanitizeProfileText(String value, int maxLength) {
        String sanitized = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength).trim();
    }
}
