package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

import com.stardew.craft.network.payload.SleepVoteUpdatePayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 多人睡眠投票追踪器。
 * <p>
 * 单人时行为不变：一人投票即推进。
 * 多人时需要达到 gamerule stardewSleepingPercentage 设定的比例才推进。
 * AFK 玩家（超过 stardewAfkTimeout 秒无操作）不计入分母。
 */
public final class SleepVoteTracker {

    /** playerUUID → sleepMinute at vote time */
    private static final Map<UUID, Integer> votes = new LinkedHashMap<>();
    /** Forced exhaustion pass-outs share the ready check but cannot be revoked as sleep. */
    private static final Set<UUID> passOutVotes = new HashSet<>();
    /** Server tick when each forced collapse began, used to finish animation 293 before warp. */
    private static final Map<UUID, Integer> passOutStartedAtTick = new HashMap<>();

    /** playerUUID → 最后活动的 System.currentTimeMillis() */
    private static final Map<UUID, Long> lastActivityTime = new LinkedHashMap<>();

    /** 睡眠等待时能量回复的 tick 计数器 */
    private static int sleepRegenTickCounter = 0;

    /**
     * 获取当前所有已投票玩家的 UUID 快照（用于在 clearVotes 之前保存状态）。
     */
    public static Set<UUID> getVotedPlayerSnapshot() {
        return new HashSet<>(votes.keySet());
    }

    public static Set<UUID> getSleepingPlayerSnapshot() {
        Set<UUID> sleeping = new HashSet<>(votes.keySet());
        sleeping.removeAll(passOutVotes);
        return sleeping;
    }

    public static Set<UUID> getPassOutPlayerSnapshot() {
        return new HashSet<>(passOutVotes);
    }

    private SleepVoteTracker() {}

    // ═══════════════════════════════════════════════════════════
    // AFK 跟踪
    // ═══════════════════════════════════════════════════════════

    /**
     * 标记玩家为活跃状态。应在玩家移动、交互、破坏/放置方块时调用。
     */
    public static void markActive(ServerPlayer player) {
        lastActivityTime.put(player.getUUID(), System.currentTimeMillis());
    }

    /**
     * 判断玩家是否处于 AFK 状态。
     */
    public static boolean isAfk(ServerPlayer player, int afkTimeoutSeconds) {
        if (afkTimeoutSeconds <= 0) return false; // AFK 检测已禁用
        Long lastActive = lastActivityTime.get(player.getUUID());
        if (lastActive == null) return false; // 没有记录 → 视为活跃
        long elapsedMs = System.currentTimeMillis() - lastActive;
        return elapsedMs > (long) afkTimeoutSeconds * 1000L;
    }

    // ═══════════════════════════════════════════════════════════
    // 投票
    // ═══════════════════════════════════════════════════════════

    /**
     * 玩家投票想睡觉。
     * @return true 如果达到阈值（应该推进了）
     */
    public static boolean castVote(ServerPlayer player, int sleepMinute) {
        passOutVotes.remove(player.getUUID());
        return castVoteInternal(player, sleepMinute, true);
    }

    public static boolean castPassOutVote(ServerPlayer player, int sleepMinute) {
        passOutVotes.add(player.getUUID());
        return castVoteInternal(player, sleepMinute, false);
    }

    /**
     * Records a pre-2AM multiplayer pass-out as ready without allowing that
     * pass-out itself to advance the shared day. A later real sleep vote or
     * the 2AM hard boundary may advance it.
     */
    public static boolean registerEarlyPassOut(ServerPlayer player, int sleepMinute) {
        passOutVotes.add(player.getUUID());
        votes.put(player.getUUID(), sleepMinute);
        passOutStartedAtTick.putIfAbsent(player.getUUID(), player.server.getTickCount());
        markActive(player);
        return hasReachedThreshold(player.server);
    }

    public static int remainingPassOutAnimationTicks(
            MinecraftServer server,
            Collection<UUID> playerIds,
            int totalTicks
    ) {
        int remaining = 0;
        int now = server.getTickCount();
        for (UUID playerId : playerIds) {
            Integer started = passOutStartedAtTick.get(playerId);
            if (started != null) {
                remaining = Math.max(remaining, Math.max(0, totalTicks - (now - started)));
            }
        }
        return remaining;
    }

    public static int countStardewPlayersOnline(MinecraftServer server) {
        return countStardewPlayers(server);
    }

    private static boolean castVoteInternal(ServerPlayer player, int sleepMinute, boolean broadcastProgress) {
        votes.put(player.getUUID(), sleepMinute);
        // 投票本身也算一次活动
        markActive(player);

        MinecraftServer server = player.server;
        int totalStardewPlayers = countStardewPlayers(server);

        if (totalStardewPlayers <= 1) {
            // 单人直接推进
            return true;
        }

        int afkTimeout = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_AFK_TIMEOUT);
        int activeCount = countActiveStardewPlayers(server, afkTimeout);
        int votedCount = countCurrentVotes(server);
        int sleepPct = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_SLEEPING_PERCENTAGE);
        int required = computeRequired(activeCount, sleepPct);

        StardewCraft.LOGGER.info("[SleepVote] {}/{} voted, need {} ({}% of {} active, {} total, afk={}s)",
                votedCount, activeCount, required, sleepPct, activeCount, totalStardewPlayers, afkTimeout);

        if (!broadcastProgress) {
            return votedCount >= required;
        }

        // 广播投票进度给所有星露谷维度玩家（原版床界面下通过 action bar 可见）
        broadcastVoteProgress(server, votedCount, required);

        // 通知所有人投票进度
        Component progressMsg = Component.translatable("stardewcraft.sleep.vote.progress",
                        votedCount, required)
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (isInStardewDimension(sp)) {
                sp.displayClientMessage(progressMsg, true);
            }
        }

        return votedCount >= required;
    }

    /**
     * 获取已投票玩家中最晚的 sleepMinute（用于计算睡眠时间惩罚）。
     */
    public static int getLatestSleepMinute() {
        int latest = 0;
        for (int m : votes.values()) {
            if (m > latest) latest = m;
        }
        return latest;
    }

    /**
     * 推进完成后清空投票。
     */
    public static void clearVotes() {
        votes.clear();
        passOutVotes.clear();
        passOutStartedAtTick.clear();
    }

    /**
     * 玩家登出时移除投票并重新检查。
     * @return true 如果移除后剩余玩家达到阈值（应推进）
     */
    public static boolean onPlayerLogout(ServerPlayer player) {
        votes.remove(player.getUUID());
        passOutVotes.remove(player.getUUID());
        passOutStartedAtTick.remove(player.getUUID());
        lastActivityTime.remove(player.getUUID());
        MinecraftServer server = player.server;

        // 减去即将离开的这个人
        int totalAfter = countStardewPlayersExcluding(server, player.getUUID());
        if (totalAfter <= 0) {
            clearVotes();
            return false;
        }
        int afkTimeout = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_AFK_TIMEOUT);
        int activeAfter = countActiveStardewPlayersExcluding(server, afkTimeout, player.getUUID());
        if (activeAfter <= 0) {
            // 全是 AFK → 不推进
            clearVotes();
            return false;
        }
        int votedAfter = countCurrentVotes(server);
        int sleepPct = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_SLEEPING_PERCENTAGE);
        int required = computeRequired(activeAfter, sleepPct);
        return votedAfter >= required;
    }

    /**
     * 玩家取消睡眠投票（按 ESC 退出等待界面）。
     */
    public static void revokeVote(ServerPlayer player) {
        if (passOutVotes.contains(player.getUUID())) {
            return;
        }
        votes.remove(player.getUUID());
    }

    /**
     * 玩家取消睡眠投票并广播更新。
     */
    public static void revokeVoteAndBroadcast(ServerPlayer player) {
        if (passOutVotes.contains(player.getUUID())) {
            return;
        }
        votes.remove(player.getUUID());
        MinecraftServer server = player.server;
        int afkTimeout = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_AFK_TIMEOUT);
        int activeCount = countActiveStardewPlayers(server, afkTimeout);
        int votedCount = countCurrentVotes(server);
        int sleepPct = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_SLEEPING_PERCENTAGE);
        int required = computeRequired(activeCount, sleepPct);
        broadcastVoteProgress(server, votedCount, required);
    }

    public static boolean hasVoted(ServerPlayer player) {
        return votes.containsKey(player.getUUID());
    }

    public static boolean hasAnyVotes() {
        return !votes.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════
    // 内部方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 根据活跃人数和百分比计算需要多少人投票。
     * 向上取整，最少1人，最多不超过活跃人数。
     */
    private static int computeRequired(int activeCount, int sleepPercentage) {
        if (sleepPercentage <= 0) return 1;
        if (sleepPercentage >= 100) return activeCount;
        return Math.max(1, (int) Math.ceil(activeCount * sleepPercentage / 100.0));
    }

    public static boolean isInStardewDimension(ServerPlayer player) {
        return player.level().dimension() == ModDimensions.STARDEW_VALLEY
                || player.level().dimension() == ModMiningDimensions.STARDEW_MINING;
    }

    private static int countStardewPlayers(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (isInStardewDimension(sp)) count++;
        }
        return count;
    }

    private static int countStardewPlayersExcluding(MinecraftServer server, UUID exclude) {
        int count = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.getUUID().equals(exclude) && isInStardewDimension(sp)) count++;
        }
        return count;
    }

    private static int countActiveStardewPlayers(MinecraftServer server, int afkTimeoutSeconds) {
        int count = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (isInStardewDimension(sp) && !isAfk(sp, afkTimeoutSeconds)) count++;
        }
        return count;
    }

    private static int countActiveStardewPlayersExcluding(MinecraftServer server, int afkTimeoutSeconds, UUID exclude) {
        int count = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.getUUID().equals(exclude) && isInStardewDimension(sp) && !isAfk(sp, afkTimeoutSeconds)) count++;
        }
        return count;
    }

    private static int countCurrentVotes(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (isInStardewDimension(sp) && votes.containsKey(sp.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasReachedThreshold(MinecraftServer server) {
        int afkTimeout = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_AFK_TIMEOUT);
        int activeCount = countActiveStardewPlayers(server, afkTimeout);
        int sleepPct = server.getGameRules().getInt(ModGameRules.RULE_STARDEW_SLEEPING_PERCENTAGE);
        return countCurrentVotes(server) >= computeRequired(activeCount, sleepPct);
    }

    /**
     * 每 tick 调用：等待其他人睡觉期间，已投票且仍在床上的玩家每秒恢复 1 点能量。
     */
    public static void tickSleepEnergyRegen(MinecraftServer server) {
        if (votes.isEmpty()) {
            sleepRegenTickCounter = 0;
            return;
        }
        sleepRegenTickCounter++;
        if (sleepRegenTickCounter < 20) return;
        sleepRegenTickCounter = 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (votes.containsKey(sp.getUUID()) && sp.isSleeping() && isInStardewDimension(sp)) {
                PlayerStardewDataAPI.restoreEnergy(sp, 1.0f);
            }
        }
    }

    /**
     * 广播睡眠投票进度给所有星露谷维度玩家。
     */
    private static void broadcastVoteProgress(MinecraftServer server, int votedCount, int requiredCount) {
        SleepVoteUpdatePayload payload = new SleepVoteUpdatePayload(votedCount, requiredCount);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (isInStardewDimension(sp)) {
                PacketDistributor.sendToPlayer(sp, payload);
            }
        }
    }
}
