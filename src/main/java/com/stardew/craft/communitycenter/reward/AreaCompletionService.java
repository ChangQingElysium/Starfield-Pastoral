package com.stardew.craft.communitycenter.reward;

import com.stardew.craft.communitycenter.JunimoNotePlacer;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.mail.MailService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Unified "a CC area just completed" hook — shared by both routes:
 *
 * <ul>
 *   <li><b>Bundle path</b>: Junimo bundles all filled. Plays AreaRestoreCutscene
 *       (Junimo dance + interior block swap).</li>
 *   <li><b>Joja path</b>: Morris CD form purchase flushed the ccXxx flag next day.
 *       No Junimo cutscene (wrong tone); just mail + overworld rewards.</li>
 * </ul>
 *
 * <p>Both paths fire the same downstream consequences: area-complete mail letter,
 * per-area rewards (FishTank/Bulletin/Greenhouse), and {@code ccIsComplete}
 * escalation when the 5th area lands. SDV does this uniformly too.
 */
public final class AreaCompletionService {

    private AreaCompletionService() {}

    /**
     * @param player   the player who completed the area
     * @param areaId   0=Pantry, 1=CraftsRoom, 2=FishTank, 3=BoilerRoom, 4=Vault, 5=Bulletin
     * @param level    server level (must be stardew_valley)
     * @param jojaPath true if this was purchased via Joja CD form (skip interior Junimo cutscene)
     */
    public static void onAreaComplete(ServerPlayer player, int areaId, ServerLevel level, boolean jojaPath) {
        // area 已标记完成后立刻重算，确保该房间的 Junimo Note 当场消失。
        if (!jojaPath) {
            JunimoNotePlacer.ensureJunimoNotes(level, player.getUUID(),
                    PlayerInteriorAllocator.get(level).getCCOrigin(player.getUUID()));
        }

        // 1) 可读邮件 —— SDV: CommunityCenter.cs 的 onAreaCompletion 触发 ccXxx 邮件
        MailService.addMail(player, "cc_area_complete_" + areaId);

        // 2) 核心奖励派发（鱼塘淘金 / 公告板人缘 等）
        AreaRewardDispatcher.onAreaComplete(player, areaId, level);
        if (areaId == 2) {
            com.stardew.craft.npc.runtime.NpcDialogueTopicService
                    .onCommunityBoulderRemoved(player);
        }

        // 3) Pantry 完成 → 修好温室（两个路径都一样）
        if (areaId == 0) {
            GreenhouseManager.get(level).repairForPlayer(level, player.getUUID());
            com.stardew.craft.npc.runtime.NpcDialogueTopicService.onGreenhouseRepaired(player);
        }

        // 4) Junimo 内饰修复过场 —— 只给 CC 路径玩家用；Joja 玩家看到的是仓库外观
        //    （他们连 CC 门都进不了，修不修内饰不影响他们）
        if (!jojaPath) {
            com.stardew.craft.communitycenter.cutscene.AreaRestoreCutscene.start(
                level, areaId, player.getUUID());
        }

        // 5) 5 区域全完 → CC_IS_COMPLETE（仅做 bundle 侧旧逻辑复查；Joja 侧由 JojaCDService 已排队）
        if (!jojaPath) {
            CommunityCenterSavedData data = CommunityCenterSavedData.get();
            if (data.areAllAreasComplete(player.getUUID())) {
                CCStoryFlags.addFlag(player, CCStoryFlags.CC_IS_COMPLETE);
            }
        }
    }
}
