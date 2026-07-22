package com.stardew.craft.quest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.sewer.SewerStoryFlags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Owns quest 28 completion handoff and repairs saves affected by a missing bundled definition. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DarkTalismanQuestService {
    public static final String OPENING_EVENT_ID = "wizard_dark_talisman_opening";
    public static final String QUEST_ID = "28";

    private DarkTalismanQuestService() {
    }

    public static void onCutsceneCompleted(ServerPlayer player, String eventId) {
        if (OPENING_EVENT_ID.equals(eventId)) {
            ensureQuestAccepted(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            repairPreviouslySeenEvent(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 0) {
            repairPreviouslySeenEvent(player);
        }
    }

    private static void repairPreviouslySeenEvent(ServerPlayer player) {
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null
                || !EventSeenData.get(stardewLevel).hasSeen(player.getUUID(), OPENING_EVENT_ID)) {
            return;
        }
        ensureQuestAccepted(player);
    }

    private static void ensureQuestAccepted(ServerPlayer player) {
        if (PlayerDataManager.getPlayerData(player).hasMailFlag(SewerStoryFlags.KROBUS_UNSEAL)) {
            return;
        }
        QuestManager quests = QuestManager.of(player);
        if (quests == null || quests.hasQuest(QUEST_ID) || quests.isQuestCompleted(QUEST_ID)) {
            return;
        }
        quests.acceptQuest(QUEST_ID, player);
    }
}
