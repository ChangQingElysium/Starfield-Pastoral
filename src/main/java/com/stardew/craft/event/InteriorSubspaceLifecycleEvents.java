package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.desert.DesertMapBootstrap;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Interaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public class InteriorSubspaceLifecycleEvents {

    private static final String PLAYER_FIRST_INTERIOR_ENTERED = "stardewcraft_interior_region_first_entered";
    private static int tickCounter = 0;
    private static int fallbackRetryCounter = 0;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel stardew = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardew == null) {
            return;
        }

        InteriorSubspaceManager.ensureLoaded(stardew, "server_started");
        // 兜底：已初始化的存档不会触发 batch 流程，主动重放一次所有传送触发方块，
        // 防止任何原因（region 覆盖、玩家误挖、旧版本遗漏）导致的丢失。
        InteriorSubspaceManager.replaceAllPortalsIfReady(stardew, "server_started_safety_net");

        // 沙漠区域 schem 放置
        DesertMapBootstrap.ensureLoaded(stardew, "server_started");
        com.stardew.craft.desert.DesertGalaxyPillarBootstrap.ensurePlaced(stardew, "server_started");
    }

    /**
     * 拦截旧版 Interaction 传送实体加入世界——无论何时加载区块，
     * 只要实体带有 sdv_portal_marker: 或 sdv_portal_target: 标签就直接取消加载。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Interaction interaction)) return;

        for (String tag : interaction.getTags()) {
            if (tag.startsWith("sdv_portal_marker:") || tag.startsWith("sdv_portal_target:")) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }

        InteriorSubspaceManager.ensureLoaded(player.serverLevel(), "enter_stardew_dimension");
        InteriorSubspaceManager.replaceAllPortalsIfReady(player.serverLevel(), "enter_stardew_dimension_safety_net");

        // 沙漠区域 schem 放置（老存档首次进入时触发）
        DesertMapBootstrap.ensureLoaded(player.serverLevel(), "enter_stardew_dimension");
        com.stardew.craft.desert.DesertGalaxyPillarBootstrap.ensurePlaced(player.serverLevel(), "enter_stardew_dimension");
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        // 驱动分批建筑放置（每 tick 放一个，避免 watchdog 超时）
        InteriorSubspaceManager.tickBatchPlacement(level);

        // 驱动沙漠 schem 放置
        DesertMapBootstrap.tick(level);
        com.stardew.craft.desert.DesertGalaxyPillarBootstrap.tick(level);

        tickCounter++;
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        // 兜底触发：老存档/特殊进服时序下可能错过 server_started 与 dimension_changed。
        // 仅在布局尚未初始化时重试，避免每秒重复触发昂贵的自愈路径。
        fallbackRetryCounter++;
        if (!InteriorSubspaceManager.isLayoutInitialized(level) && !level.players().isEmpty() && fallbackRetryCounter >= 30) {
            fallbackRetryCounter = 0;
            InteriorSubspaceManager.ensureLoaded(level, "stardew_player_present");
        }

        for (ServerPlayer player : level.players()) {
            if (player.getPersistentData().getBoolean(PLAYER_FIRST_INTERIOR_ENTERED)) {
                continue;
            }

            if (InteriorSubspaceManager.isInteriorRegion(level, player.blockPosition())) {
                InteriorSubspaceManager.ensureLoaded(level, "first_enter_interior_region");
                player.getPersistentData().putBoolean(PLAYER_FIRST_INTERIOR_ENTERED, true);
            }
        }
    }
}
