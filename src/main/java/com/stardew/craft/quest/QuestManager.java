package com.stardew.craft.quest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.network.DailyQuestSyncPayload;
import com.stardew.craft.quest.network.QuestCompletePayload;
import com.stardew.craft.quest.network.QuestLogSyncPayload;
import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressCauses;
import com.stardew.craft.api.v1.progress.StardewProgressEvent;
import com.stardew.craft.api.v1.progress.StardewProgressEventType;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressOperation;
import com.stardew.craft.api.v1.progress.StardewProgressRequirements;
import com.stardew.craft.api.v1.progress.StardewProgressSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 玩家任务管理器 — 运行时管理 accepted / completed 状态
 * 每个 PlayerStardewData 持有一个实例
 */
@SuppressWarnings("null")
public class QuestManager {

    private final List<StardewQuest> questLog = new ArrayList<>();
    private final Set<String> completedQuestIds = new HashSet<>();
    private final Set<Integer> dailyQuestCompletedDays = new HashSet<>();
    private int questsCompleted;
    @Nullable
    private StardewQuest dailyQuest;
    private int lastDailyQuestDay = -1;
    private int billboardQuestsDone;

    // ─── 静态快捷方法 ───

    @Nullable
    public static QuestManager of(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data != null ? data.getQuestManager() : null;
    }

    // ─── 任务日志 API ───

    public void acceptQuest(String questId, ServerPlayer player) {
        if (hasQuest(questId)) return;
        var normalizedId = QuestDataLoader.normalizeId(questId);
        if (normalizedId == null
                || !StardewProgressRequirements.requirements(
                                player,
                                new StardewProgressKey(
                                        com.stardew.craft.api.v1.progress
                                                .StardewProgressDomains.QUEST,
                                        normalizedId),
                                StardewProgressOperation.ACCEPT)
                        .satisfied()) {
            return;
        }
        StardewQuest quest = QuestDataLoader.createQuest(questId);
        if (quest == null) {
            StardewCraft.LOGGER.warn("[Quest] Unknown quest id: {}", questId);
            return;
        }
        acceptQuest(quest, player);
    }

    public void acceptQuest(StardewQuest quest, ServerPlayer player) {
        if (hasQuest(quest.getId())) return;
        quest.setAccepted(true);
        quest.setShowNew(true);
        // 记录接受任务的绝对天数，用于 daysLeft tick 时跳过接受当天
        if (quest.getDayQuestAccepted() < 0) {
            quest.setDayQuestAccepted(lastDailyQuestDay > 0 ? lastDailyQuestDay : 1);
        }
        questLog.add(quest);
        quest.onAccept(player);
        StardewProgressSnapshot accepted =
                StardewProgressRegistry.questSnapshot(quest, null);
        StardewProgressRegistry.dispatch(new StardewProgressEvent(
                StardewProgressEventType.ACCEPTED,
                player.serverLevel(),
                Optional.of(player.getUUID()),
                Optional.empty(),
                accepted,
                StardewProgressCauses.ACCEPT));
        markOwnerDirty(player);
        syncToClient(player);
        if ("102".equals(quest.getId())) {
            com.stardew.craft.event.LuckyPurpleShortsWorldEvents.syncVisibility(player);
        }
    }

    public boolean debugGrantQuest(String questId, ServerPlayer player) {
        if (hasQuest(questId)) {
            return false;
        }
        StardewQuest quest = QuestDataLoader.createQuest(questId);
        if (quest == null) {
            return false;
        }
        acceptQuest(quest, player);
        return true;
    }

    public void removeQuest(String questId, ServerPlayer player) {
        StardewQuest removed = getQuest(questId);
        questLog.removeIf(q -> QuestDataLoader.idsEqual(q.getId(), questId));
        if (removed != null) {
            StardewProgressSnapshot before =
                    StardewProgressRegistry.questSnapshot(removed, null);
            StardewProgressSnapshot after =
                    StardewProgressRegistry.questSnapshot(
                            removed, StardewProgressPhase.CANCELLED);
            StardewProgressRegistry.dispatch(new StardewProgressEvent(
                    StardewProgressEventType.CANCELLED,
                    player.serverLevel(),
                    Optional.of(player.getUUID()),
                    Optional.of(before),
                    after,
                    StardewProgressCauses.CANCEL));
        }
        markOwnerDirty(player);
        syncToClient(player);
    }

    public boolean hasQuest(String questId) {
        for (StardewQuest q : questLog) {
            if (QuestDataLoader.idsEqual(q.getId(), questId)) return true;
        }
        return false;
    }

    @Nullable
    public StardewQuest getQuest(String questId) {
        for (StardewQuest q : questLog) {
            if (QuestDataLoader.idsEqual(q.getId(), questId)) return q;
        }
        return null;
    }

    public List<StardewQuest> getQuestLog() {
        return questLog;
    }

    void refreshDefinitions(ServerPlayer player) {
        boolean changed = false;
        for (StardewQuest quest : questLog) {
            if (quest instanceof DataDrivenQuest dataDriven) {
                dataDriven.refreshDefinition();
                changed = true;
            }
        }
        if (changed) {
            markOwnerDirty(player);
            syncToClient(player);
        }
    }

    public boolean isQuestCompleted(String questId) {
        return completedQuestIds.stream().anyMatch(id -> QuestDataLoader.idsEqual(id, questId));
    }

    public boolean completeActiveQuest(String questId, ServerPlayer player) {
        StardewQuest quest = getQuest(questId);
        if (quest == null) return false;
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        // Older call sites completed the quest object directly without letting the
        // manager persist, notify and remove it. Treat that state as recoverable.
        if (!quest.isCompleted()) {
            quest.questComplete(player);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
        return true;
    }

    public Set<String> getCompletedQuestIds() {
        return java.util.Collections.unmodifiableSet(completedQuestIds);
    }

    public boolean debugForgetCompletedQuest(String questId, ServerPlayer player) {
        boolean removed = completedQuestIds.removeIf(id -> QuestDataLoader.idsEqual(id, questId));
        if (removed) {
            markOwnerDirty(player);
            syncToClient(player);
        }
        return removed;
    }

    // ─── 每日任务 ───

    @Nullable
    public StardewQuest getDailyQuest() {
        return dailyQuest;
    }

    public void setDailyQuest(@Nullable StardewQuest quest) {
        this.dailyQuest = quest;
    }

    public int getLastDailyQuestDay() {
        return lastDailyQuestDay;
    }

    public void setLastDailyQuestDay(int day) {
        this.lastDailyQuestDay = day;
    }

    public int getBillboardQuestsDone() {
        return billboardQuestsDone;
    }

    public void incrementBillboardQuestsDone() {
        billboardQuestsDone++;
    }

    public Set<Integer> getDailyQuestCompletedDays() {
        return dailyQuestCompletedDays;
    }

    public void markDailyQuestCompletedDay(int dayInSeason) {
        dailyQuestCompletedDays.add(dayInSeason);
    }

    // ─── 事件分发 ───

    public void onMonsterSlain(ServerPlayer player, String monsterType) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onMonsterSlain(player, monsterType);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    public void onFishCaught(ServerPlayer player, String itemId, int count) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onFishCaught(player, itemId, count);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    public void onItemReceived(ServerPlayer player, String itemId, int count) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onItemReceived(player, itemId, count);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    /**
     * @return true if any quest consumed the offered item (SDV: intercepts before gift processing)
     */
    public boolean onItemOfferedToNpc(ServerPlayer player, String npcId, String itemId) {
        return onItemOfferedToNpc(player, npcId, itemId, 1);
    }

    public boolean onItemOfferedToNpc(ServerPlayer player, String npcId, String itemId, int count) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        boolean consumed = false;
        for (StardewQuest q : List.copyOf(questLog)) {
            if (q.onItemOfferedToNpc(player, npcId, itemId, Math.max(1, count))) {
                consumed = true;
                break; // SDV: onlyOneQuest=true — stop after first match
            }
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
        return consumed;
    }

    public void onRecipeCrafted(ServerPlayer player, String recipeId) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onRecipeCrafted(player, recipeId);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    public void onNpcSocialized(ServerPlayer player, String npcId) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onNpcSocialized(player, npcId);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    public void onWarped(ServerPlayer player, String location) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onWarped(player, location);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    public void onBuildingExists(ServerPlayer player, String buildingType) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onBuildingExists(player, buildingType);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        markOwnerDirty(player);
        syncToClient(player);
        cleanupDestroyed(player);
    }

    /**
     * 矿井到达新最深层时触发 — 检查 Location 类型任务（Explore the Mine 系列）
     */
    public void onMineFloorReached(ServerPlayer player, int floor) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        for (StardewQuest q : List.copyOf(questLog)) {
            q.onMineFloorReached(player, floor);
        }
        if (floor >= 145 && hasQuest("20") && !com.stardew.craft.mail.MailService.hasOrWillReceiveMail(player, "QiChallengeComplete")) {
            StardewQuest quest = getQuest("20");
            if (quest != null && !quest.isCompleted()) {
                quest.questComplete(player);
            }
            com.stardew.craft.mail.MailService.addMailForTomorrow(player, "QiChallengeComplete");
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.OBJECTIVE_EVENT);
        cleanupDestroyed(player);
        if (floor >= 5
                && !hasQuest("15")
                && !isQuestCompleted("15")
                && !com.stardew.craft.mail.MailService.hasOrWillReceiveMail(player, "guildQuest")) {
            com.stardew.craft.mail.MailService.addMailForTomorrow(player, "guildQuest");
        }
    }

    // ─── 天数推进 ───

    public void onDayStarted(ServerPlayer player, int gameDay) {
        Map<StardewProgressKey, StardewProgressSnapshot> before =
                captureQuestSnapshots();
        // 减少定时任务天数 (SDV 在天结束时递减，我们在天开始时调用但跳过接受当天)
        for (StardewQuest q : questLog) {
            q.tickDay(gameDay);
        }
        publishQuestChanges(
                player, before, StardewProgressCauses.DAY_TICK);
        cleanupDestroyed(player);

        // ── SDV 故事任务自动触发 ──
        triggerStoryQuests(player, gameDay);

        // 存档迁移：老版 daily quest（legacy 英文 title，未填本地化 key）且尚未被接受 → 强制重生成以走新模板
        if (dailyQuest != null
                && (dailyQuest.getTitleKey() == null || dailyQuest.getTitleKey().isEmpty())
                && !dailyQuest.isAccepted()) {
            dailyQuest = null;
            lastDailyQuestDay = -1; // 触发下面的重生成
        }

        // 刷新每日任务
        if (gameDay != lastDailyQuestDay) {
            dailyQuest = null;
            lastDailyQuestDay = gameDay;
            // 季节切换时清空日历星标（每28天一季）
            if (gameDay > 0 && (gameDay - 1) % 28 == 0) {
                dailyQuestCompletedDays.clear();
            }
            // 生成今日公告栏每日任务（SDV 概率表：约 30% 天数可能返回 null 表示今天没任务）
            long worldSeed = player.level().getServer().overworld().getSeed();
            dailyQuest = DailyQuestGenerator.generate(gameDay, worldSeed, player);
            markOwnerDirty(player);
            // P2 fix: sync new daily quest to client immediately (SDV parity)
            PacketDistributor.sendToPlayer(player, DailyQuestSyncPayload.fromQuest(dailyQuest));
        }
    }

    /**
     * SDV 故事任务自动触发 — 按天数/季节自动接受故事任务。
     * 对应 SDV Game1.checkForQuestComplete / _newDayAfterFade 中的逻辑。
     */
    public void triggerStoryQuests(ServerPlayer player, int gameDay) {
        // gameDay 是绝对天数（从1开始），day-in-season = ((gameDay - 1) % 28) + 1
        int dayInSeason = ((gameDay - 1) % 28) + 1;
        int season = ((gameDay - 1) / 28) % 4; // 0=spring, 1=summer, 2=fall, 3=winter

        StardewCraft.LOGGER.info("[Quest] triggerStoryQuests gameDay={} season={} dayInSeason={}", gameDay, season, dayInSeason);

        // 玩家首次进入星露谷维度时在法师塔内，还未完成法师任务（交付末影之眼）。
        // 此时不应发布任何故事任务，等正式进入维度后再触发。
        PlayerStardewData pData = PlayerDataManager.getPlayerData(player);
        if (!pData.isWizardQuestComplete()) {
            return;
        }

        // 春1日起：自动接受 Quest 6（开始新生活）和 Quest 9（自我介绍）
        // 使用 >= 范围而非 == 精确匹配，防止登录延迟/首次触发失败时遗漏
        if (gameDay >= 1) {
            if (!isQuestCompleted("6") && !hasQuest("6")) {
                acceptQuest("6", player);
            }
            if (!isQuestCompleted("9") && !hasQuest("9")) {
                acceptQuest("9", player);
            }
        }

    }

    // ─── 内部工具 ───

    public void cleanupDestroyed(ServerPlayer player) {
        boolean changed = false;
        List<String> pendingNextQuests = new ArrayList<>();
        Iterator<StardewQuest> it = questLog.iterator();
        while (it.hasNext()) {
            StardewQuest q = it.next();
            if (q.isCompleted() && !q.isDestroy() && !q.isNotifiedComplete()) {
                // 任务刚完成，发送完成通知
                q.setNotifiedComplete(true);
                questsCompleted++;
                PacketDistributor.sendToPlayer(player,
                    new QuestCompletePayload(q.getId(), q.getMoneyReward()));
                // SDV: nextQuests 在 questComplete() 中立即触发（奖励领取前）
                // 延迟到遍历结束后 acceptQuest，避免 ConcurrentModificationException
                pendingNextQuests.addAll(q.getNextQuests());
                // 无奖励的任务标记为销毁
                if (!q.hasReward()) {
                    q.setDestroy(true);
                }
            }
            if (q.isDestroy()) {
                if (q.isCompleted()) {
                    completedQuestIds.add(q.getDefinitionId() == null
                            ? q.getId() : q.getDefinitionId().toString());
                }
                it.remove();
                changed = true;
            }
        }
        // 遍历结束后再添加后续任务
        for (String nextId : pendingNextQuests) {
            acceptQuest(nextId, player);
        }
        if (changed || !pendingNextQuests.isEmpty()) {
            syncToClient(player);
            // 若当前每日任务已完成，同时刷新客户端 dailyQuest 缓存（isCompleted=true）→
            // 公告栏不再显示"接受"按钮。
            if (dailyQuest != null && dailyQuest.isCompleted()) {
                PacketDistributor.sendToPlayer(player, DailyQuestSyncPayload.fromQuest(dailyQuest));
            }
        }
        // 任何状态变更（包括 notifiedComplete）都需要持久化
        markOwnerDirty(player);
    }

    private void markOwnerDirty(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data != null) {
            data.markDirty();
        }
    }

    private Map<StardewProgressKey, StardewProgressSnapshot> captureQuestSnapshots() {
        Map<StardewProgressKey, StardewProgressSnapshot> snapshots =
                new LinkedHashMap<>();
        for (StardewQuest quest : questLog) {
            StardewProgressSnapshot snapshot =
                    StardewProgressRegistry.questSnapshot(quest, null);
            snapshots.put(snapshot.key(), snapshot);
        }
        return snapshots;
    }

    private void publishQuestChanges(
            ServerPlayer player,
            Map<StardewProgressKey, StardewProgressSnapshot> before,
            net.minecraft.resources.ResourceLocation cause
    ) {
        for (StardewQuest quest : questLog) {
            StardewProgressSnapshot after =
                    StardewProgressRegistry.questSnapshot(quest, null);
            StardewProgressSnapshot previous = before.get(after.key());
            if (previous == null || previous.equals(after)) {
                continue;
            }
            if (!previous.metrics().equals(after.metrics())) {
                StardewProgressRegistry.dispatch(new StardewProgressEvent(
                        StardewProgressEventType.PROGRESSED,
                        player.serverLevel(),
                        Optional.of(player.getUUID()),
                        Optional.of(previous),
                        after,
                        cause));
            }
            if (previous.phase() != after.phase()) {
                StardewProgressRegistry.dispatch(new StardewProgressEvent(
                        eventTypeFor(after.phase()),
                        player.serverLevel(),
                        Optional.of(player.getUUID()),
                        Optional.of(previous),
                        after,
                        cause));
            }
        }
    }

    private static StardewProgressEventType eventTypeFor(
            StardewProgressPhase phase
    ) {
        return switch (phase) {
            case REWARD_AVAILABLE ->
                    StardewProgressEventType.REWARD_BECAME_AVAILABLE;
            case COMPLETED -> StardewProgressEventType.COMPLETED;
            case FAILED -> StardewProgressEventType.FAILED;
            case EXPIRED -> StardewProgressEventType.EXPIRED;
            case CANCELLED -> StardewProgressEventType.CANCELLED;
            case UNAVAILABLE ->
                    StardewProgressEventType.BECAME_UNAVAILABLE;
            case AVAILABLE -> StardewProgressEventType.MADE_AVAILABLE;
            case SCHEDULED -> StardewProgressEventType.SCHEDULED;
            case ACTIVE -> StardewProgressEventType.ACCEPTED;
            case NOT_STARTED -> StardewProgressEventType.PROGRESSED;
        };
    }

    /** 同步完整任务日志到客户端 */
    private void syncToClient(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            QuestLogSyncPayload.fromQuests(questLog, billboardQuestsDone, dailyQuestCompletedDays));
    }

    // ─── NBT 持久化 ───

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag questListTag = new ListTag();
        for (StardewQuest q : questLog) {
            questListTag.add(q.saveState());
        }
        tag.put("QuestLog", questListTag);

        if (dailyQuest != null) {
            tag.put("DailyQuest", dailyQuest.save());
        }

        tag.putInt("LastDailyQuestDay", lastDailyQuestDay);
        tag.putInt("BillboardQuestsDone", billboardQuestsDone);
        tag.putInt("QuestsCompleted", questsCompleted);

        ListTag completedTag = new ListTag();
        for (String id : completedQuestIds) {
            completedTag.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        tag.put("CompletedQuestIds", completedTag);

        tag.putIntArray("DailyQuestCompletedDays", dailyQuestCompletedDays.stream().mapToInt(Integer::intValue).toArray());

        return tag;
    }

    public void load(CompoundTag tag) {
        questLog.clear();
        completedQuestIds.clear();

        if (tag.contains("QuestLog", 9)) {
            ListTag list = tag.getList("QuestLog", 10);
            for (int i = 0; i < list.size(); i++) {
                StardewQuest q = StardewQuest.load(list.getCompound(i));
                questLog.add(q);
            }
        }

        if (tag.contains("DailyQuest", 10)) {
            dailyQuest = StardewQuest.load(tag.getCompound("DailyQuest"));
        }

        lastDailyQuestDay = tag.getInt("LastDailyQuestDay");
        billboardQuestsDone = tag.getInt("BillboardQuestsDone");
        questsCompleted = tag.getInt("QuestsCompleted");

        if (tag.contains("CompletedQuestIds", 9)) {
            ListTag list = tag.getList("CompletedQuestIds", 8);
            for (int i = 0; i < list.size(); i++) {
                String savedId = list.getString(i);
                var normalized = QuestDataLoader.normalizeId(savedId);
                completedQuestIds.add(normalized == null ? savedId : normalized.toString());
            }
        }

        dailyQuestCompletedDays.clear();
        if (tag.contains("DailyQuestCompletedDays")) {
            for (int d : tag.getIntArray("DailyQuestCompletedDays")) {
                dailyQuestCompletedDays.add(d);
            }
        }
    }

}
