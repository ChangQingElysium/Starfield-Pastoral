package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.interior.CrossDimensionTeleporter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

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
        boolean male,
        Map<ResourceLocation, String> layoutConfiguration
) implements CustomPacketPayload {
    private static final int MAX_CONFIGURATION_FIELDS = 64;

    public static final Type<FarmSelectionSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "farm_selection_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmSelectionSubmitPayload> STREAM_CODEC =
            StreamCodec.of(
                    FarmSelectionSubmitPayload::encode,
                    FarmSelectionSubmitPayload::decode);

    public FarmSelectionSubmitPayload {
        layoutConfiguration = Map.copyOf(layoutConfiguration);
        if (layoutConfiguration.size() > MAX_CONFIGURATION_FIELDS) {
            throw new IllegalArgumentException(
                    "Farm layout configuration has too many fields");
        }
    }

    /** Source-compatible constructor for clients without typed layout options. */
    public FarmSelectionSubmitPayload(
            String farmTypeId,
            String farmName,
            boolean forceCancelPending,
            String preferredName,
            String favoriteThing,
            boolean male
    ) {
        this(farmTypeId, farmName, forceCancelPending,
                preferredName, favoriteThing, male, Map.of());
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            FarmSelectionSubmitPayload payload
    ) {
        buffer.writeUtf(payload.farmTypeId(), 256);
        buffer.writeUtf(payload.farmName(), 64);
        buffer.writeBoolean(payload.forceCancelPending());
        buffer.writeUtf(payload.preferredName(), 64);
        buffer.writeUtf(payload.favoriteThing(), 96);
        buffer.writeBoolean(payload.male());
        buffer.writeVarInt(payload.layoutConfiguration().size());
        payload.layoutConfiguration().forEach((id, value) -> {
            ResourceLocation.STREAM_CODEC.encode(buffer, id);
            buffer.writeUtf(value, 128);
        });
    }

    private static FarmSelectionSubmitPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        String farmTypeId = buffer.readUtf(256);
        String farmName = buffer.readUtf(64);
        boolean forceCancelPending = buffer.readBoolean();
        String preferredName = buffer.readUtf(64);
        String favoriteThing = buffer.readUtf(96);
        boolean male = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CONFIGURATION_FIELDS) {
            throw new IllegalArgumentException(
                    "Invalid farm layout configuration field count: " + count);
        }
        LinkedHashMap<ResourceLocation, String> configuration =
                new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id =
                    ResourceLocation.STREAM_CODEC.decode(buffer);
            if (configuration.putIfAbsent(id, buffer.readUtf(128)) != null) {
                throw new IllegalArgumentException(
                        "Duplicate farm layout configuration field: " + id);
            }
        }
        return new FarmSelectionSubmitPayload(
                farmTypeId, farmName, forceCancelPending,
                preferredName, favoriteThing, male, configuration);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FarmSelectionSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // This payload is sent immediately before the profile screen closes. Clear only its
            // pause classification so farm initialization can run, but keep guiOpen true until
            // the client actually reports that the screen has closed.
            com.stardew.craft.time.StardewTimePauseService.updateClientState(player, false, true);

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

            // Resolve and revalidate the namespaced layout on the server.
            ResourceLocation requestedLayoutId =
                    normalizeLayoutId(payload.farmTypeId);
            StardewFarmLayout layout = StardewFarmLayouts.find(
                            requestedLayoutId)
                    .filter(StardewFarmLayout::selectable)
                    .orElseGet(() -> StardewFarmLayouts.find(
                            StardewFarmLayoutRegistry.builtinId(
                                    com.stardew.craft.farm.FarmType.STANDARD))
                            .orElseThrow());
            if (!layout.id().equals(requestedLayoutId)) {
                StardewCraft.LOGGER.warn("[FARM_SELECT] {} tried to select locked farm type: {}",
                        player.getName().getString(), payload.farmTypeId);
            }
            Map<ResourceLocation, String> requestedConfiguration =
                    layout.id().equals(requestedLayoutId)
                            ? payload.layoutConfiguration() : Map.of();

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
            FarmInstance farm;
            try {
                farm = registry.createFarm(
                        player.getUUID(),
                        preferredName,
                        name,
                        layout.id(),
                        requestedConfiguration);
            } catch (IllegalArgumentException invalidConfiguration) {
                StardewCraft.LOGGER.warn(
                        "[FARM_SELECT] {} sent invalid configuration for {}: {}",
                        player.getName().getString(),
                        layout.id(),
                        invalidConfiguration.getMessage());
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "stardewcraft.farm_selection.invalid_configuration"));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        player, new OpenFarmSelectionPayload());
                return;
            }

            StardewCraft.LOGGER.info("[FARM_SELECT] {} created farm '{}' (type={})",
                    player.getName().getString(), name, layout.id());

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

    private static ResourceLocation normalizeLayoutId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return StardewFarmLayoutRegistry.builtinId(
                    com.stardew.craft.farm.FarmType.STANDARD);
        }
        String normalized = rawId.trim();
        ResourceLocation id = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(StardewCraft.MODID, normalized);
        return id == null
                ? StardewFarmLayoutRegistry.builtinId(
                        com.stardew.craft.farm.FarmType.STANDARD)
                : id;
    }

    private static String sanitizeProfileText(String value, int maxLength) {
        String sanitized = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength).trim();
    }
}
