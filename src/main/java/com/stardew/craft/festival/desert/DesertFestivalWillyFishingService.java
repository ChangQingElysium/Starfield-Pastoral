package com.stardew.craft.festival.desert;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.quest.FishingQuest;
import com.stardew.craft.quest.ItemDeliveryQuest;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.StardewQuest;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class DesertFestivalWillyFishingService {
    public static final String TARGET_ID = "desert_festival_willy_challenge";
    public static final String MARKER_TAG = "sdv_festival_marker:desert_willy_challenge";
    public static final BlockPos INTERACTION_POS = new BlockPos(-233, 65, -195);

    public static final String QUEST_ID = "98765";

    private static final String CONTEXT = "willy_fishing";
    private static final String YES_ID = "yes";
    private static final String LAST_ACCEPTED_DATE_STAT = "desertFestivalWillyFishingQuestDate";
    private static final String LAST_ACCEPTED_DAY_STAT = "desertFestivalWillyFishingQuestFestivalDay";

    private DesertFestivalWillyFishingService() {
    }

    public static void openChallengeBoard(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!DesertFestivalService.isFestivalOpen()) {
            sendDialogue(player, "willy", "stardewcraft.desert_festival.willy.closed");
            return;
        }
        int currentDate = currentDateKey();
        if (PlayerStardewDataAPI.getStat(player, LAST_ACCEPTED_DATE_STAT) == currentDate) {
            sendDialogue(player, "willy", "stardewcraft.desert_festival.willy.already_today");
            return;
        }
        int festivalDay = currentFestivalDay();
        sendQuestion(player, Component.translatable("stardewcraft.desert_festival.willy.offer." + festivalDay), List.of(
            response(YES_ID, Component.translatable("stardewcraft.ui.yes"), player),
            response("no", Component.translatable("stardewcraft.ui.no"), player)
        ));
    }

    public static void handleQuestionResponse(ServerPlayer player, String context, String choiceId) {
        if (player == null || !CONTEXT.equals(context) || !YES_ID.equals(choiceId)) {
            return;
        }
        acceptChallenge(player);
    }

    public static boolean tryCompleteFishingReport(ServerPlayer player, StardewNpcEntity npc, String npcId) {
        if (npc == null || !canCompleteFishingReport(player, npcId)) {
            return false;
        }
        FishingQuest quest = findReadyFishingQuest(player);
        if (quest != null) {
            return completeFishingQuest(player, quest, npc);
        }
        ItemDeliveryQuest goldenBobberQuest = findReadyGoldenBobberQuest(player);
        return goldenBobberQuest != null && completeGoldenBobberQuest(player, goldenBobberQuest, npc);
    }

    public static boolean canCompleteFishingReport(
            ServerPlayer player,
            String npcId
    ) {
        if (player == null || !"willy".equalsIgnoreCase(npcId)) {
            return false;
        }
        return findReadyFishingQuest(player) != null
            || findReadyGoldenBobberQuest(player) != null
                && hasGoldenBobber(player);
    }

    public static boolean tryCompleteChallengeAtBoard(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        FishingQuest fishingQuest = findReadyFishingQuest(player);
        if (fishingQuest != null) {
            return completeFishingQuest(player, fishingQuest, null);
        }
        ItemDeliveryQuest goldenBobberQuest = findReadyGoldenBobberQuest(player);
        return goldenBobberQuest != null && completeGoldenBobberQuest(player, goldenBobberQuest, null);
    }

    private static boolean completeFishingQuest(ServerPlayer player, FishingQuest quest, StardewNpcEntity npc) {
        int festivalDay = Math.max(1, PlayerStardewDataAPI.getStat(player, LAST_ACCEPTED_DAY_STAT));
        QuestManager manager = QuestManager.of(player);
        if (manager != null) {
            manager.completeActiveQuest(quest.getId(), player);
        } else {
            quest.questComplete(player);
        }
        DesertFestivalService.giveEggs(player, rewardEggs(festivalDay));
        if (npc != null) {
            npc.facePlayerTemporarily(player, 60, () -> sendDialogue(player, "willy", returnKey(festivalDay)));
        } else {
            sendDialogue(player, "willy", returnKey(festivalDay));
        }
        return true;
    }

    private static boolean completeGoldenBobberQuest(ServerPlayer player, ItemDeliveryQuest quest, StardewNpcEntity npc) {
        if (!hasGoldenBobber(player)) {
            return false;
        }
        if (!player.getAbilities().instabuild && !consumeGoldenBobber(player)) {
            return false;
        }
        int festivalDay = Math.max(3, PlayerStardewDataAPI.getStat(player, LAST_ACCEPTED_DAY_STAT));
        quest.onItemOfferedToNpc(player, "willy", "stardewcraft:golden_bobber");
        DesertFestivalService.giveEggs(player, rewardEggs(festivalDay));
        QuestManager manager = QuestManager.of(player);
        if (manager != null) {
            manager.cleanupDestroyed(player);
        }
        if (npc != null) {
            npc.facePlayerTemporarily(player, 60, () -> sendDialogue(player, "willy", returnKey(festivalDay)));
        } else {
            sendDialogue(player, "willy", returnKey(festivalDay));
        }
        return true;
    }

    public static boolean isWillyChallengeQuest(StardewQuest quest) {
        return quest != null && QUEST_ID.equals(quest.getId());
    }

    public static void handleDeliveredGoldenBobber(ServerPlayer player, ItemDeliveryQuest quest) {
        if (player == null || !isWillyChallengeQuest(quest)) {
            return;
        }
        int festivalDay = Math.max(3, PlayerStardewDataAPI.getStat(player, LAST_ACCEPTED_DAY_STAT));
        DesertFestivalService.giveEggs(player, rewardEggs(festivalDay));
        QuestManager manager = QuestManager.of(player);
        if (manager != null) {
            manager.cleanupDestroyed(player);
        }
        sendDialogue(player, "willy", returnKey(festivalDay));
    }

    public static boolean shouldForceGoldenBobberTreasure(ServerPlayer player) {
        if (player == null || !DesertFestivalService.isFestivalOpen() || currentFestivalDay() != 3) {
            return false;
        }
        QuestManager manager = QuestManager.of(player);
        if (manager == null || !(manager.getQuest(QUEST_ID) instanceof ItemDeliveryQuest quest)) {
            return false;
        }
        if (!quest.isAccepted() || quest.isCompleted() || quest.isDestroy()) {
            return false;
        }
        return player.getInventory().countItem(ModItems.GOLDEN_BOBBER.get()) <= 0;
    }

    private static void acceptChallenge(ServerPlayer player) {
        if (!DesertFestivalService.isFestivalOpen()) {
            return;
        }
        QuestManager manager = QuestManager.of(player);
        if (manager == null || manager.hasQuest(QUEST_ID)) {
            return;
        }
        int festivalDay = currentFestivalDay();
        StardewQuest quest = festivalDay == 3 ? createGoldenBobberQuest() : createFishingQuest(festivalDay);
        quest.setId(QUEST_ID);
        quest.setDaysLeft(1);
        quest.setCanBeCancelled(false);
        manager.acceptQuest(quest, player);
        PlayerStardewDataAPI.setStat(player, LAST_ACCEPTED_DATE_STAT, currentDateKey());
        PlayerStardewDataAPI.setStat(player, LAST_ACCEPTED_DAY_STAT, festivalDay);
    }

    private static FishingQuest createFishingQuest(int festivalDay) {
        boolean dayOne = festivalDay == 1;
        String itemId = dayOne ? "stardewcraft:sandfish" : "stardewcraft:scorpion_carp";
        String itemKey = dayOne ? "item.stardewcraft.sandfish" : "item.stardewcraft.scorpion_carp";
        int count = dayOne ? 3 : 1;
        FishingQuest quest = new FishingQuest();
        quest.setTargetNpc("willy");
        quest.setItemId(itemId);
        quest.setNumberToFish(count);
        quest.setReward(0);
        quest.setLocalizedTitle("stardewcraft.desert_festival.willy.challenge.title");
        quest.setLocalizedDescription("stardewcraft.desert_festival.willy.challenge.description." + festivalDay);
        quest.setLocalizedObjective("stardewcraft.quest.fishing.objective", Integer.toString(count), itemKey);
        return quest;
    }

    private static ItemDeliveryQuest createGoldenBobberQuest() {
        ItemDeliveryQuest quest = new ItemDeliveryQuest();
        quest.setTargetNpc("willy");
        quest.setItemId("stardewcraft:golden_bobber");
        quest.setNumber(1);
        quest.setTargetMessage("stardewcraft.desert_festival.willy.challenge.return.3");
        quest.setLocalizedTitle("stardewcraft.desert_festival.willy.challenge.title");
        quest.setLocalizedDescription("stardewcraft.desert_festival.willy.challenge.description.3");
        quest.setLocalizedObjective("stardewcraft.desert_festival.willy.golden_bobber");
        return quest;
    }

    private static FishingQuest findReadyFishingQuest(ServerPlayer player) {
        QuestManager manager = QuestManager.of(player);
        if (manager == null || !(manager.getQuest(QUEST_ID) instanceof FishingQuest quest)) {
            return null;
        }
        if (!quest.isAccepted() || quest.isCompleted() || quest.isDestroy()) {
            return null;
        }
        return quest.getNumberFished() >= quest.getNumberToFish() ? quest : null;
    }

    private static ItemDeliveryQuest findReadyGoldenBobberQuest(ServerPlayer player) {
        QuestManager manager = QuestManager.of(player);
        if (manager == null || !(manager.getQuest(QUEST_ID) instanceof ItemDeliveryQuest quest)) {
            return null;
        }
        if (!quest.isAccepted() || quest.isCompleted() || quest.isDestroy()) {
            return null;
        }
        if (!"willy".equalsIgnoreCase(quest.getTargetNpc()) || !"stardewcraft:golden_bobber".equalsIgnoreCase(quest.getItemId())) {
            return null;
        }
        return hasGoldenBobber(player) ? quest : null;
    }

    private static boolean hasGoldenBobber(ServerPlayer player) {
        return player != null && player.getInventory().countItem(ModItems.GOLDEN_BOBBER.get()) > 0;
    }

    private static boolean consumeGoldenBobber(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ModItems.GOLDEN_BOBBER.get())) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            player.getInventory().setChanged();
            return true;
        }
        return false;
    }

    private static int currentFestivalDay() {
        return Math.max(1, Math.min(3, com.stardew.craft.festival.FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID)));
    }

    private static int currentDateKey() {
        StardewTimeManager time = StardewTimeManager.get();
        return time == null ? 0 : time.getAbsoluteDay();
    }

    private static int rewardEggs(int festivalDay) {
        return switch (festivalDay) {
            case 1 -> 25;
            case 2 -> 50;
            default -> 30;
        };
    }

    private static String returnKey(int festivalDay) {
        return "stardewcraft.desert_festival.willy.challenge.return." + Math.max(1, Math.min(3, festivalDay));
    }

    private static void sendQuestion(ServerPlayer player, Component question, List<OpenDesertFestivalQuestionPayload.ResponseOption> responses) {
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            CONTEXT,
            currentFestivalDay(),
            "",
            Component.Serializer.toJson(question, player.registryAccess()),
            responses
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(id, Component.Serializer.toJson(label, player.registryAccess()));
    }

    private static void sendDialogue(ServerPlayer player, String npcId, String key) {
        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(npcId, key, 0));
    }
}
