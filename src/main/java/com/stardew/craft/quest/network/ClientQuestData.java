package com.stardew.craft.quest.network;

import com.stardew.craft.quest.StardewQuest;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端任务数据缓存 — 由 S→C 同步包更新，UI 读取此处数据
 */
@OnlyIn(Dist.CLIENT)
public final class ClientQuestData {

    private static final List<StardewQuest> questLog = new ArrayList<>();
    @Nullable
    private static StardewQuest dailyQuest;
    private static int billboardQuestsDone;
    private static final Set<Integer> dailyQuestCompletedDays = new HashSet<>();

    private ClientQuestData() {}

    // ─── 写入（由网络包 handler 调用） ───

    public static void setQuestLog(List<StardewQuest> quests) {
        // 保留客户端已设置的 showNew=false 状态，避免服务端同步覆盖
        java.util.Set<String> viewedIds = new java.util.HashSet<>();
        for (StardewQuest old : questLog) {
            if (!old.isShowNew()) {
                viewedIds.add(old.getId());
            }
        }
        questLog.clear();
        for (StardewQuest q : quests) {
            if (viewedIds.contains(q.getId())) {
                q.setShowNew(false);
            }
            questLog.add(q);
        }
    }

    public static void setDailyQuest(@Nullable StardewQuest quest) {
        dailyQuest = quest;
    }

    public static void setBillboardQuestsDone(int count) {
        billboardQuestsDone = count;
    }

    public static void setDailyQuestCompletedDays(Set<Integer> days) {
        dailyQuestCompletedDays.clear();
        dailyQuestCompletedDays.addAll(days);
    }

    /** 标记某个任务完成（收到 QuestCompletePayload 时调用） */
    public static void markCompleted(String questId) {
        for (StardewQuest q : questLog) {
            if (q.getId().equals(questId)) {
                q.setCompleted(true);
                break;
            }
        }
    }

    /** 清空（断线/登出时） */
    public static void clear() {
        questLog.clear();
        dailyQuest = null;
        billboardQuestsDone = 0;
        dailyQuestCompletedDays.clear();
    }

    // ─── 读取（UI 调用） ───

    public static List<StardewQuest> getQuestLog() {
        return Collections.unmodifiableList(questLog);
    }

    @Nullable
    public static StardewQuest getDailyQuest() {
        return dailyQuest;
    }

    public static int getBillboardQuestsDone() {
        return billboardQuestsDone;
    }

    public static boolean isDailyQuestCompletedOnDay(int day) {
        return dailyQuestCompletedDays.contains(day);
    }

    public static boolean hasQuest(String questId) {
        for (StardewQuest q : questLog) {
            if (q.getId().equals(questId)) return true;
        }
        return false;
    }

    /**
     * SDV parity: 等价于 <c>Game1.CanAcceptDailyQuest()</c> —
     * 当前有每日任务、本地玩家还没接、也没完成，则世界内公告栏头顶浮 "!" 提示。
     */
    public static boolean hasUnclaimedDailyQuest() {
        StardewQuest q = dailyQuest;
        if (q == null) return false;
        // 已在 questLog 里表示已接受；dailyQuest 本身的 accepted 也可能被 sync 更新
        if (q.isAccepted() || q.isCompleted()) return false;
        if (hasQuest(q.getId())) return false;
        return true;
    }
}
