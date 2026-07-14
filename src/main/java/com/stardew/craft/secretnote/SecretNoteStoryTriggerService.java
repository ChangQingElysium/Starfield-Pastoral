package com.stardew.craft.secretnote;

import com.stardew.craft.mining.SkullCavernSessionManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Records source-faithful story eligibility without choosing any camera, actor,
 * coordinate or animation. The pending flags are consumed by the future agreed
 * 3D cutscene implementation.
 */
public final class SecretNoteStoryTriggerService {
    public static final int SKULL_CAVERN_FLOOR_100 = 220;

    private SecretNoteStoryTriggerService() {
    }

    public static boolean markQiCaveScenePendingIfEligible(ServerPlayer player, int internalFloor) {
        if (internalFloor != SKULL_CAVERN_FLOOR_100) return false;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        QuestManager quests = QuestManager.of(player);
        if (quests == null
                || !data.hasSeenSecretNote("stardewcraft:10")
                || !quests.hasQuest("30")
                || data.hasMailFlag(SecretNoteStoryFlags.QI_CAVE)
                || data.hasMailFlag(SecretNoteStoryFlags.QI_CAVE_SCENE_PENDING)) {
            return false;
        }
        data.addMailFlag(SecretNoteStoryFlags.QI_CAVE_SCENE_PENDING);
        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
        return true;
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
