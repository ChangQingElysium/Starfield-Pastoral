package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.SkullCavernSessionManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles secret note 10's floor-100 checkpoint. The current reward presentation
 * is intentionally temporary until Mr. Qi's source-faithful 3D cutscene exists.
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNoteStoryTriggerService {
    public static final int SKULL_CAVERN_FLOOR_100 = 220;

    private SecretNoteStoryTriggerService() {
    }

    /** Also covers debug teleports and players loading directly on the target floor. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 10 != 0
                || !ModMiningDimensions.STARDEW_MINING.equals(player.serverLevel().dimension())) {
            return;
        }
        int floor = MiningDataManager.getPlayerData(player).getCurrentFloor();
        if (floor == SKULL_CAVERN_FLOOR_100) {
            grantTemporaryQiCaveRewardIfEligible(player, floor);
        }
    }

    public static boolean grantTemporaryQiCaveRewardIfEligible(ServerPlayer player, int internalFloor) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        QuestManager quests = QuestManager.of(player);
        if (quests == null || !canGrantTemporaryReward(data, quests.hasQuest("30"), internalFloor)) {
            return false;
        }

        // TODO: Replace this temporary item award and notice with the full Mr. Qi
        // floor-100 cutscene. The authored camera/actor positions must be approved first.
        data.removeMailFlag(SecretNoteStoryFlags.QI_CAVE_SCENE_PENDING);
        data.addMailFlag(SecretNoteStoryFlags.QI_CAVE_TEMP_REWARD_GRANTED);
        quests.removeQuest("30", player);
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);

        ItemStack reward = new ItemStack(ModItems.IRIDIUM_MILK.get());
        ItemStack animationStack = reward.copy();
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        HoldUpItemPayload.sendTo(player, animationStack);
        player.sendSystemMessage(Component.translatable("stardewcraft.secret_note.10.reward"));
        player.sendSystemMessage(Component.translatable("stardewcraft.secret_note.10.cutscene_todo")
                .withStyle(ChatFormatting.GRAY));
        return true;
    }

    static boolean canGrantTemporaryReward(PlayerStardewData data, boolean hasQuest, int internalFloor) {
        return data != null
                && internalFloor == SKULL_CAVERN_FLOOR_100
                && hasQuest
                && data.hasSeenSecretNote("stardewcraft:10")
                && !data.hasMailFlag(SecretNoteStoryFlags.QI_CAVE)
                && !data.hasMailFlag(SecretNoteStoryFlags.QI_CAVE_TEMP_REWARD_GRANTED);
    }

    public static QiCaveRunResult qiCaveRunResult(ServerPlayer player) {
        int craftedStairs = SkullCavernSessionManager.getCraftedStaircasesUsed(player.getUUID());
        return new QiCaveRunResult(craftedStairs, isHonorableQiCaveRun(craftedStairs));
    }

    static boolean isHonorableQiCaveRun(int craftedStaircasesUsed) {
        return craftedStaircasesUsed <= 10;
    }

    public record QiCaveRunResult(int craftedStaircasesUsed, boolean honorable) {
    }
}
