package com.stardew.craft.museum;

import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Quest 23/24 hooks shared by every museum-item acquisition path. */
public final class MuseumQuestService {

    public static final String INTRO_EVENT_ID = "0";
    public static final String FIRST_ARTIFACT_FLAG = "artifactFound";
    private static final String LOST_BOOK_ID = "stardewcraft:lost_book";

    private MuseumQuestService() {}

    public static void onItemReceived(ServerPlayer player, String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item != Items.AIR) {
                onItemReceived(player, new ItemStack(item));
            }
        } catch (Exception ignored) {
        }
    }

    public static void onItemReceived(ServerPlayer player, ItemStack stack) {
        if (!MuseumDonationItems.isArtifact(stack)) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (LOST_BOOK_ID.equals(itemId)) return;

        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        if (playerData.hasMailFlag(FIRST_ARTIFACT_FLAG)) return;

        QuestManager quests = QuestManager.of(player);
        boolean sawIntro = EventSeenData.get(player.serverLevel()).hasSeen(player.getUUID(), INTRO_EVENT_ID);
        if (!sawIntro && quests != null && !quests.hasQuest("23") && !quests.isQuestCompleted("23")) {
            quests.acceptQuest("23", player);
        }
        playerData.addMailFlag(FIRST_ARTIFACT_FLAG);
    }

    public static void onDonationPlaced(ServerPlayer player) {
        QuestManager quests = QuestManager.of(player);
        if (quests != null) {
            quests.completeActiveQuest("24", player);
        }
    }
}
