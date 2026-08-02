package com.stardew.craft.quest;

import net.minecraft.server.level.ServerPlayer;

/**
 * Quest 事件分发 facade — 从各游戏系统调用，转发给 QuestManager
 */
public final class StardewQuestEvents {

    private StardewQuestEvents() {}

    public static void fireMonsterSlain(ServerPlayer player, String monsterType) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onMonsterSlain(player, monsterType);
    }

    public static void fireFishCaught(ServerPlayer player, String itemId, int count) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onFishCaught(player, itemId, count);
    }

    public static void fireItemReceived(ServerPlayer player, String itemId, int count) {
        com.stardew.craft.museum.MuseumQuestService.onItemReceived(player, itemId);
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onItemReceived(player, itemId, count);
    }

    /**
     * @return true if a quest consumed the offered item (delivery quest matched).
     *         Caller should skip gift taste processing when true.
     */
    public static boolean fireItemOfferedToNpc(ServerPlayer player, String npcId, String itemId) {
        return fireItemOfferedToNpc(player, npcId, itemId, 1);
    }

    public static boolean fireItemOfferedToNpc(
            ServerPlayer player, String npcId, String itemId, int count) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) return qm.onItemOfferedToNpc(player, npcId, itemId, Math.max(1, count));
        return false;
    }

    public static void fireRecipeCrafted(ServerPlayer player, String recipeId) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onRecipeCrafted(player, recipeId);
    }

    public static void fireNpcSocialized(ServerPlayer player, String npcId) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onNpcSocialized(player, npcId);
    }

    public static void fireWarped(ServerPlayer player, String location) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onWarped(player, location);
    }

    public static void fireBuildingExists(ServerPlayer player, String buildingType) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onBuildingExists(player, buildingType);
    }

    public static void fireMineFloorReached(ServerPlayer player, int floor) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onMineFloorReached(player, floor);
    }

    public static void fireDayStarted(ServerPlayer player, int gameDay) {
        QuestManager qm = QuestManager.of(player);
        if (qm != null) qm.onDayStarted(player, gameDay);
    }
}
