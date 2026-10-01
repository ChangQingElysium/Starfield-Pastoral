package com.stardew.craft.joja;

import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.joja.network.JojaPurchaseResultPayload;
import com.stardew.craft.joja.network.OpenJojaCDMenuPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Joja Community Development Form — 服务端购买逻辑，与 SDV JojaCDMenu.cs:receiveLeftClick 1:1 对齐。
 *
 * <ul>
 *   <li>入会条件：{@link CCStoryFlags#JOJA_MEMBER}</li>
 *   <li>点击某格：已完成 → 无动作；钱不够 → 结果码通知客户端抖动；钱够 → 扣钱 + 寄两封 mail（ccXxx + jojaXxx）+ 播 reward 音</li>
 *   <li>购买标记：记在 {@link #BOUGHT_SOMETHING}，玩家关闭 CDMenu 时若为 true → Morris 播 Morris_JojaCDConfirm</li>
 * </ul>
 */
public final class JojaCDService {

    private JojaCDService() {}

    /** 玩家本次打开 CDMenu 期间是否买过东西 — 对应 SDV boughtSomething 字段 */
    private static final Set<UUID> BOUGHT_SOMETHING = ConcurrentHashMap.newKeySet();

    /**
     * 计算已完成掩码（5 bit，对应 5 个 cc* 区域）。
     * 合并已生效 + 明日生效的 flag —— 对应 SDV {@code doesAnyFarmerHaveOrWillReceiveMail}。
     */
    public static int completedMask(ServerPlayer player) {
        int mask = 0;
        for (int i = 0; i < 5; i++) {
            String cc = CCStoryFlags.jojaButtonToCcFlag(i);
            if (CCStoryFlags.hasFlag(player, cc)
                || com.stardew.craft.mail.MailService.hasMailFlagForTomorrow(player, cc)) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /** 打开 CDMenu（服务端 → 客户端） */
    public static void openMenu(ServerPlayer player) {
        BOUGHT_SOMETHING.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player,
            new OpenJojaCDMenuPayload(completedMask(player), PlayerStardewDataAPI.getMoney(player)));
    }

    /** 处理购买请求 — 返回 {@link JojaConstants#RESULT_OK / NOT_ENOUGH_MONEY / ALREADY_DONE} */
    public static void handlePurchase(ServerPlayer player, int buttonIdx) {
        if (buttonIdx < 0 || buttonIdx > 4) return;

        String ccFlag = CCStoryFlags.jojaButtonToCcFlag(buttonIdx);

        // 已完成 或 同日已下单明日生效 —— 都视为不可再买（与 completedMask 逻辑一致）
        if (CCStoryFlags.hasFlag(player, ccFlag)
            || com.stardew.craft.mail.MailService.hasMailFlagForTomorrow(player, ccFlag)) {
            send(player, buttonIdx, JojaConstants.RESULT_ALREADY_DONE);
            return;
        }

        int price = JojaConstants.priceForButton(buttonIdx);
        // removeMoney 返回 false 说明钱不够；用它而非 addMoney(-price)
        // 因为 addMoney 内部对负数 no-op（只接受正数），会导致"免费购买"的严重 bug。
        if (!PlayerStardewDataAPI.removeMoney(player, price)) {
            // 钱不够 — SDV: dayTimeMoneyBox.moneyShakeTimer = 1000
            send(player, buttonIdx, JojaConstants.RESULT_NOT_ENOUGH_MONEY);
            return;
        }

        // 排队两个次日生效 flag（SDV: addMailForTomorrow("jojaXxx"/"ccXxx", noLetter:true)）
        com.stardew.craft.mail.MailService.addMailFlagForTomorrow(player, CCStoryFlags.jojaAreaFlag(buttonIdx));
        com.stardew.craft.mail.MailService.addMailFlagForTomorrow(player, ccFlag);

        // 播 reward 音 — SDV: Game1.playSound("reward")
        player.level().playSound(null, player.blockPosition(),
            ModSounds.REWARD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        // SDV parity: 买完 5 区域 → 触发 ccIsComplete（走 Joja 路径完成 CC 线）
        // 检查已生效 + 明日待生效 合并 —— 当天买第 5 区域时，头 4 个已经生效 + 这一个明天生效
        if (allFiveCcAreasDoneOrPending(player) && !CCStoryFlags.hasFlag(player, CCStoryFlags.CC_IS_COMPLETE)
            && !com.stardew.craft.mail.MailService.hasMailFlagForTomorrow(player, CCStoryFlags.CC_IS_COMPLETE)) {
            com.stardew.craft.mail.MailService.addMailFlagForTomorrow(player, CCStoryFlags.CC_IS_COMPLETE);
            com.stardew.craft.StardewCraft.LOGGER.info(
                "[JOJA] {} completed all 5 Joja areas — ccIsComplete will fire tomorrow.",
                player.getGameProfile().getName());
        }

        BOUGHT_SOMETHING.add(player.getUUID());
        send(player, buttonIdx, JojaConstants.RESULT_OK);
    }

    private static boolean allFiveCcAreasDoneOrPending(ServerPlayer p) {
        for (int i = 0; i < 5; i++) {
            String cc = CCStoryFlags.jojaButtonToCcFlag(i);
            boolean has = CCStoryFlags.hasFlag(p, cc)
                || com.stardew.craft.mail.MailService.hasMailFlagForTomorrow(p, cc);
            if (!has) return false;
        }
        return true;
    }

    /** 玩家关闭 CDMenu — 如买过东西则 Morris 播 Morris_JojaCDConfirm */
    public static void handleClose(ServerPlayer player, boolean clientBoughtSomething) {
        boolean serverBought = BOUGHT_SOMETHING.remove(player.getUUID());
        MorrisService.notifyCDFormClosed(player, clientBoughtSomething && serverBought);
    }

    private static void send(ServerPlayer player, int buttonIdx, int resultCode) {
        PacketDistributor.sendToPlayer(player, new JojaPurchaseResultPayload(
            buttonIdx,
            resultCode,
            PlayerStardewDataAPI.getMoney(player),
            completedMask(player)
        ));
    }
}
