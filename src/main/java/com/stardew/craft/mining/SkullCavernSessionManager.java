package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骷髅矿洞会话管理器
 * <p>
 * SDV 原版：每次离开骷髅矿后，所有楼层(121+)重置，下次进入从 121 重新开始。
 * 本管理器追踪在矿内的玩家，当所有人离开时触发异步清理。
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class SkullCavernSessionManager {

    private static final Set<UUID> playersInSkullCavern = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> craftedStaircasesUsed = new ConcurrentHashMap<>();
    private static int sessionDeepestFloor = 121;

    /** 玩家进入骷髅矿（从入口或 /tp 命令触发） */
    public static void onPlayerEnter(ServerPlayer player) {
        if (playersInSkullCavern.add(player.getUUID())) {
            craftedStaircasesUsed.put(player.getUUID(), 0);
        }
        StardewCraft.LOGGER.info("[SKULL] Player {} entered skull cavern, active={}",
                player.getName().getString(), playersInSkullCavern.size());
    }

    /** 更新当前会话的最深层 */
    public static void updateDeepestFloor(int floor) {
        if (floor > sessionDeepestFloor) {
            sessionDeepestFloor = floor;
        }
    }

    /** 玩家离开骷髅矿 */
    public static void onPlayerLeave(ServerPlayer player, ServerLevel miningLevel) {
        playersInSkullCavern.remove(player.getUUID());
        StardewCraft.LOGGER.info("[SKULL] Player {} left skull cavern, remaining={}",
                player.getName().getString(), playersInSkullCavern.size());

        if (playersInSkullCavern.isEmpty()) {
            resetSession(miningLevel);
            com.stardew.craft.festival.desert.DesertFestivalMineService.resetCurrentRun(miningLevel);
        }
    }

    /** 检查玩家是否在骷髅矿会话中 */
    public static boolean isPlayerInSkullCavern(UUID uuid) {
        return playersInSkullCavern.contains(uuid);
    }

    public static int getCraftedStaircasesUsed(UUID playerId) {
        return craftedStaircasesUsed.getOrDefault(playerId, 0);
    }

    /**
     * 全部玩家离开 → 异步清理所有骷髅矿楼层数据
     * 方块清理不需要做（下次生成时会覆写），只清 MineFloorDataManager 记录即可。
     */
    private static void resetSession(ServerLevel miningLevel) {
        StardewCraft.LOGGER.info("[SKULL] All players left, resetting session (floors 121-{})",
                sessionDeepestFloor);

        MineFloorDataManager manager = MineFloorDataManager.get(miningLevel);
        for (int floor = 121; floor <= sessionDeepestFloor; floor++) {
            manager.clearFloorData(floor);
        }

        sessionDeepestFloor = 121;
        StardewCraft.LOGGER.info("[SKULL] Session reset complete");
    }

    // ═══════════ Event Listeners ═══════════

    @SubscribeEvent
    public static void onLadderPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ModMiningDimensions.STARDEW_MINING
                || !(event.getEntity() instanceof ServerPlayer player)
                || !event.getPlacedBlock().is(ModBlocks.MINE_LADDER.get())) {
            return;
        }
        MiningPlayerData mining = MiningDataManager.getPlayerData(player);
        if (mining.getCurrentFloor() > 120) {
            // SDV Object.cs increments numberOfCraftedStairsUsedThisRun as soon as
            // placement successfully creates the ladder, not when it is clicked.
            craftedStaircasesUsed.merge(player.getUUID(), 1, Integer::sum);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // 离开矿井维度
        if (event.getFrom().equals(ModMiningDimensions.STARDEW_MINING)) {
            if (playersInSkullCavern.contains(sp.getUUID())) {
                ServerLevel miningLevel = sp.server.getLevel(ModMiningDimensions.STARDEW_MINING);
                if (miningLevel != null) {
                    onPlayerLeave(sp, miningLevel);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!playersInSkullCavern.contains(sp.getUUID())) return;

        ServerLevel miningLevel = sp.server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (miningLevel != null) {
            onPlayerLeave(sp, miningLevel);
        }
        craftedStaircasesUsed.remove(sp.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!playersInSkullCavern.contains(sp.getUUID())) return;

        // 死亡复活后离开骷髅矿
        ServerLevel miningLevel = sp.server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (miningLevel != null) {
            onPlayerLeave(sp, miningLevel);
        }
    }
}
