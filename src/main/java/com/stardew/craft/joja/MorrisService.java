package com.stardew.craft.joja;

import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Morris（Joja 超市经理）对话与入会流程。
 *
 * <p>决策树（对齐 SDV {@code JojaMart.cs:checkAction}）：
 * <pre>
 *   未入会：
 *     首次见面（未设 JOJA_GREETING）      → greeting
 *     CC 已完成                           → greetingBase_CommunityCenterComplete
 *     其他                                → greetingBase_MembershipAvailable (Yes/No)
 *       Yes → signup_confirm (Yes/No)
 *         Yes → 扣 5000g + 设 JOJA_MEMBER → signed_up
 *   已入会：
 *     5 区域全买完（hasCompletedAllJojaAreas）→ no_more_cd
 *     其他                                → cd_form_offer (Yes/No)
 *       Yes → openMenu(JojaCDMenu)
 *         购买后 close → cd_confirm（由 {@link JojaCDService#handleClose} 触发）
 * </pre>
 */
public final class MorrisService {

    public static final String NODE_JOIN_YES    = "morris_join_yes";
    public static final String NODE_SIGNUP_YES  = "morris_signup_yes";
    public static final String NODE_CD_YES      = "morris_cd_yes";
    /** 关闭对话不做任何事；所有 No / Cancel 分支都用这个 sentinel。 */
    public static final String NODE_DISMISS     = "morris_dismiss";

    private MorrisService() {}

    private static final String I18N_GREETING          = "stardewcraft.npc.morris.greeting";
    private static final String I18N_FIRST_MEMBERSHIP  = "stardewcraft.npc.morris.first_membership";
    private static final String I18N_WEEKEND_MEMBERSHIP= "stardewcraft.npc.morris.weekend_membership";
    private static final String I18N_FIRST_CC_DONE     = "stardewcraft.npc.morris.first_cc_done";
    private static final String I18N_WEEKEND_CC_DONE   = "stardewcraft.npc.morris.weekend_cc_done";
    private static final String I18N_SIGNUP_CONFIRM    = "stardewcraft.npc.morris.signup_confirm";
    private static final String I18N_SIGNED_UP         = "stardewcraft.npc.morris.signed_up";
    private static final String I18N_COME_BACK_LATER   = "stardewcraft.npc.morris.come_back_later";
    private static final String I18N_PROCESSING        = "stardewcraft.npc.morris.processing";
    private static final String I18N_CD_FORM_OFFER     = "stardewcraft.npc.morris.cd_form_offer";
    private static final String I18N_CD_CONFIRM        = "stardewcraft.npc.morris.cd_confirm";
    private static final String I18N_NO_MORE_CD        = "stardewcraft.npc.morris.no_more_cd";

    /** NpcInteractionService.onInteract 的 morris 分支入口。 */
    public static InteractionResult handle(ServerPlayer player, StardewNpcEntity npc) {
        npc.setYRot(90f);        // 朝 -X（与 default_spawns 一致）
        npc.setYHeadRot(90f);

        // ── 未入会分支 ──
        if (!CCStoryFlags.isJojaMember(player)) {
            // SDV: 今天刚签了 JojaMember（尚未 flush 到 mailFlags）→ Morris_ComeBackLater
            if (com.stardew.craft.mail.MailService.hasMailFlagForTomorrow(player, CCStoryFlags.JOJA_MEMBER)) {
                speak(player, I18N_COME_BACK_LATER);
                return InteractionResult.SUCCESS;
            }
            if (!CCStoryFlags.hasFlag(player, CCStoryFlags.JOJA_GREETING)) {
                CCStoryFlags.addFlag(player, CCStoryFlags.JOJA_GREETING);
                speak(player, I18N_GREETING);
                return InteractionResult.SUCCESS;
            }
            boolean ccComplete = CCStoryFlags.hasFlag(player, CCStoryFlags.CC_IS_COMPLETE);
            boolean weekend = isWeekend();
            if (ccComplete) {
                speak(player, weekend ? I18N_WEEKEND_CC_DONE : I18N_FIRST_CC_DONE);
            } else {
                speak(player, weekend ? I18N_WEEKEND_MEMBERSHIP : I18N_FIRST_MEMBERSHIP);
            }
            return InteractionResult.SUCCESS;
        }

        // ── 已入会分支 ──
        // SDV: 有任一 jojaXxx 还在明日待生效队列 → Morris_StillProcessingOrder
        if (hasAnyJojaAreaPending(player)) {
            speak(player, I18N_PROCESSING);
            return InteractionResult.SUCCESS;
        }
        if (hasAllJojaAreas(player)) {
            speak(player, I18N_NO_MORE_CD);
            return InteractionResult.SUCCESS;
        }
        speak(player, I18N_CD_FORM_OFFER);
        return InteractionResult.SUCCESS;
    }

    /** NpcInteractionService.handleClientQuestionAnswer 的 morris 分支入口。 */
    public static boolean handleAnswer(ServerPlayer player, String nextDialogueNode) {
        if (nextDialogueNode == null) return false;
        return switch (nextDialogueNode) {
            case NODE_JOIN_YES -> {
                // 第一次 Yes → 二次确认
                speak(player, I18N_SIGNUP_CONFIRM);
                yield true;
            }
            case NODE_SIGNUP_YES -> {
                // 二次确认 Yes → 扣 5000g + 次日生效会员（SDV addMailForTomorrow("JojaMember")）
                // 用 removeMoney（原子扣款+充足校验）而非 addMoney(-price)
                // —— addMoney 内部对负数 no-op，曾导致"免费入会"的严重 bug。
                if (!PlayerStardewDataAPI.removeMoney(player, JojaConstants.JOJA_MEMBERSHIP_PRICE)) {
                    com.stardew.craft.StardewCraft.LOGGER.info(
                        "[MORRIS] {} declined join (insufficient funds < {})",
                        player.getGameProfile().getName(), JojaConstants.JOJA_MEMBERSHIP_PRICE);
                    yield true;
                }
                com.stardew.craft.mail.MailService.addMailFlagForTomorrow(player, CCStoryFlags.JOJA_MEMBER);
                com.stardew.craft.quest.QuestManager quests = com.stardew.craft.quest.QuestManager.of(player);
                if (quests != null) {
                    quests.removeQuest("26", player);
                }
                speak(player, I18N_SIGNED_UP);
                yield true;
            }
            case NODE_CD_YES -> {
                // 打开 CD form
                com.stardew.craft.npc.runtime.NpcDialogueTopicService
                        .onJojaDevelopmentFormViewed(player);
                JojaCDService.openMenu(player);
                yield true;
            }
            case NODE_DISMISS -> true;
            default -> false;
        };
    }

    /**
     * {@link JojaCDService#handleClose} 在购买后关闭 menu 时调用，让 Morris 播 Morris_JojaCDConfirm。
     */
    public static void notifyCDFormClosed(ServerPlayer player, boolean boughtSomething) {
        if (boughtSomething) {
            speak(player, I18N_CD_CONFIRM);
        }
    }

    // ────────────────────────────────────────

    /**
     * 检查 5 区域是否已经全部完成 —— CC 路径 / Joja 路径都算。
     * 用 ccXxx 而非 jojaXxx 以确保玩家通过 CC bundle 完成后 Morris 也会说 NoMoreCD，
     * 而不是继续显示已 greyed-out 的 CD form。
     */
    private static boolean hasAllJojaAreas(ServerPlayer p) {
        for (int i = 0; i < 5; i++) {
            if (!CCStoryFlags.hasFlag(p, CCStoryFlags.jojaButtonToCcFlag(i))) return false;
        }
        return true;
    }

    private static boolean hasAnyJojaAreaPending(ServerPlayer p) {
        for (String flag : new String[]{
                CCStoryFlags.JOJA_VAULT, CCStoryFlags.JOJA_BOILER_ROOM,
                CCStoryFlags.JOJA_CRAFTS_ROOM, CCStoryFlags.JOJA_PANTRY,
                CCStoryFlags.JOJA_FISH_TANK}) {
            if (com.stardew.craft.mail.MailService.hasMailFlagForTomorrow(p, flag)) return true;
        }
        return false;
    }

    private static boolean isWeekend() {
        int d = StardewTimeManager.get().getCurrentDay();
        int dow = ((d - 1) % 7 + 7) % 7;
        return dow == 5 || dow == 6; // Sat / Sun
    }

    private static void speak(ServerPlayer player, String translateKey) {
        PacketDistributor.sendToPlayer(player,
            new OpenNpcDialogueScreenPayload("morris", translateKey, 0));
    }
}
