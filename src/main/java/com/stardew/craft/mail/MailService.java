package com.stardew.craft.mail;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActionContext;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.network.payload.OpenMailPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件系统服务层（服务端）。
 * <p>
 * 对齐 SDV 的邮件 API：
 * - {@link #addMail(ServerPlayer, String)} — 立即投递到 mailbox（SDV Game1.addMail）
 * - {@link #addMailForTomorrow(ServerPlayer, String)} — 次日投递（SDV Game1.addMailForTomorrow）
 * - {@link #hasOrWillReceiveMail(ServerPlayer, String)} — 是否已收过/将收到
 * - {@link #openNextMail(ServerPlayer)} — 从 mailbox 弹出并发送到客户端
 */
@SuppressWarnings("null")
public class MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);

    /**
     * 立即投递邮件到玩家信箱。如果玩家已收过（mailFlags）或信箱中已有，则忽略。
     */
    public static void addMail(ServerPlayer player, String mailId) {
        if (!canQueueReadableMail(player, mailId, "mailbox")) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(mailId)) return;  // 已读过
        int before = data.getMailbox().size();
        data.addToMailbox(mailId);
        if (data.getMailbox().size() != before) {
            executeActions(player, MailRegistry.get(mailId), MailPhase.DELIVERY);
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    /** Annual calendar mail: the vanilla ID repeats, while read history remains recorded. */
    public static void addRecurringMail(ServerPlayer player, String mailId) {
        if (!canQueueReadableMail(player, mailId, "recurringMailbox")) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        String deliveryFlag = "__recurring_mail_" + mailId + "_year_"
            + com.stardew.craft.time.StardewTimeManager.get().getCurrentYear();
        if (data.hasMailFlag(deliveryFlag)) return;
        int before = data.getMailbox().size();
        data.addToMailbox(mailId);
        data.addMailFlag(deliveryFlag);
        if (data.getMailbox().size() != before || data.hasMailFlag(deliveryFlag)) {
            if (data.getMailbox().size() != before) {
                executeActions(player, MailRegistry.get(mailId), MailPhase.DELIVERY);
            }
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    /**
     * 安排邮件于次日投递。如果已收过或已安排，则忽略。
     */
    public static void addMailForTomorrow(ServerPlayer player, String mailId) {
        if (!canQueueReadableMail(player, mailId, "mailForTomorrow")) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(mailId)) return;
        int before = data.getMailForTomorrow().size();
        data.addMailForTomorrow(mailId);
        if (data.getMailForTomorrow().size() != before) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
    }

    /**
     * 安排标志位于次日生效（SDV {@code addMailForTomorrow(noLetter: true)} 对等）。
     * flush 时直接进 mailFlags —— 不产生可读信件。队列记录 queue 时的 absoluteDay，
     * 之后 currentDay &gt; queuedDay 才会 flush（多人跨日登录安全）。
     */
    public static void addMailFlagForTomorrow(ServerPlayer player, String flag) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(flag)) return;
        int today = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        data.addMailFlagForTomorrow(flag, today);
    }

    /** 是否已在明天生效的标志位队列里（尚未 flush）。 */
    public static boolean hasMailFlagForTomorrow(ServerPlayer player, String flag) {
        return PlayerDataManager.getPlayerData(player).hasMailFlagForTomorrow(flag);
    }

    /**
     * 检查玩家是否已经收过、正在信箱中、或已安排明天投递此邮件。
     */
    public static boolean hasOrWillReceiveMail(ServerPlayer player, String mailId) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.hasMailFlag(mailId)
                || data.getMailbox().contains(mailId)
                || data.getMailForTomorrow().contains(mailId);
    }

    /**
     * 从信箱弹出下一封邮件并发送到客户端显示。
     * 如果信箱为空，发送聊天消息提示。
     */
    public static void openNextMail(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasMailInMailbox()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.mailbox.empty"));
            return;
        }

        String mailId;
        MailEntry entry;
        while (true) {
            mailId = data.popMailFromMailbox();
            if (mailId == null) {
                PlayerDataEventHandler.syncPlayerData(player, data);
                player.sendSystemMessage(Component.translatable("stardewcraft.mailbox.empty"));
                return;
            }

            entry = MailRegistry.get(mailId);
            if (entry != null) break;

            data.addMailFlag(mailId);
            LOGGER.warn("Discarded unreadable queued mail '{}' for {} because no mail entry is registered",
                    mailId, player.getName().getString());
        }

        // 标记为已读
        data.addMailFlag(mailId);
        if ("winter_18".equals(mailId)) {
            // Vanilla LetterViewerMenu records this only when the actual letter is opened.
            data.addMailFlag("sawSecretSanta" + com.stardew.craft.time.StardewTimeManager.get().getCurrentYear());
        }

        // 特殊信件打开时触发附加效果（SDV parity: LetterViewerMenu 内联逻辑）
        if (com.stardew.craft.communitycenter.reward.BulletinReward.isBulletinThankYouMail(mailId)) {
            if (player.level() instanceof net.minecraft.server.level.ServerLevel lvl) {
                com.stardew.craft.communitycenter.reward.BulletinReward.onThankYouLetterOpened(player, lvl);
            }
        }

        String text = entry.getText();
        String secretFriendId = "winter_18".equals(mailId)
            ? com.stardew.craft.festival.WinterStarFestivalService.getSecretFriendId(player)
            : "";
        String secretSantaNameKey = secretFriendId.isBlank()
            ? ""
            : "entity.stardewcraft.npc." + secretFriendId;

        // 构建附件列表 & 发放物品到玩家背包
        List<OpenMailPayload.ItemAttachment> items = new ArrayList<>();
        for (MailEntry.AttachedItem ai : entry.getAttachedItems()) {
            items.add(new OpenMailPayload.ItemAttachment(ai.id(), ai.count()));
            // 服务端直接给玩家物品
            net.minecraft.resources.ResourceLocation itemRL = net.minecraft.resources.ResourceLocation.parse(ai.id());
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemRL);
            if (item != net.minecraft.world.item.Items.AIR) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, ai.count());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }

        // 处理金钱附件（服务端直接加钱 + 同步客户端）
        int money = entry.getMoney();
        if (money > 0) {
            com.stardew.craft.player.PlayerStardewDataAPI.addMoney(player, money);
        }

        // 配方学习
        String learnedRecipe = entry.getLearnedRecipe() != null ? entry.getLearnedRecipe() : "";
        String cookingOrCrafting = "";
        if (!learnedRecipe.isEmpty()) {
            cookingOrCrafting = entry.isRecipeIsCooking() ? "cooking" : "crafting";
            data.unlockRecipe(learnedRecipe);
        }

        boolean hasQuest = entry.getQuestId() != null || entry.getSpecialOrderId() != null;

        // 邮件附带任务时，服务端接受该任务
        if (entry.getQuestId() != null && !entry.getQuestId().isEmpty()) {
            com.stardew.craft.quest.QuestManager qm = com.stardew.craft.quest.QuestManager.of(player);
            if (qm != null) {
                qm.acceptQuest(entry.getQuestId(), player);
            }
        }

        executeActions(player, entry, MailPhase.READ);

        int remaining = data.getMailbox().size();
        PlayerDataEventHandler.syncPlayerData(player, data);

        OpenMailPayload payload = new OpenMailPayload(
                mailId, text, secretSantaNameKey, entry.getBackground(),
                entry.getTextColor() != null ? entry.getTextColor() : "",
                items, money, learnedRecipe, cookingOrCrafting,
                hasQuest, remaining
        );

        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Reopens an already received letter with vanilla {@code fromCollection} semantics. */
    public static void openSeenMail(ServerPlayer player, String mailId) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (mailId == null || !data.hasMailFlag(mailId)) return;
        MailEntry entry = MailRegistry.get(mailId);
        if (entry == null) return;

        String secretFriendId = "winter_18".equals(mailId)
                ? com.stardew.craft.festival.WinterStarFestivalService.getSecretFriendId(player)
                : "";
        String secretSantaNameKey = secretFriendId.isBlank()
                ? ""
                : "entity.stardewcraft.npc." + secretFriendId;
        PacketDistributor.sendToPlayer(player, new OpenMailPayload(
                mailId,
                entry.getText(),
                secretSantaNameKey,
                entry.getBackground(),
                entry.getTextColor() != null ? entry.getTextColor() : "",
                List.of(),
                0,
                "",
                "",
                false,
                0
        ));
    }

    private static boolean canQueueReadableMail(ServerPlayer player, String mailId, String queueName) {
        if (mailId == null || mailId.isBlank()) {
            LOGGER.warn("Ignoring blank readable mail id for {} queue on {}",
                    queueName, player.getName().getString());
            return false;
        }
        if (!MailRegistry.contains(mailId)) {
            LOGGER.warn("Ignoring unregistered readable mail '{}' for {} queue on {}",
                    mailId, queueName, player.getName().getString());
            return false;
        }
        MailEntry entry = MailRegistry.get(mailId);
        if (entry == null) return false;
        for (var condition : entry.availableWhen()) {
            boolean allowed = StardewConditions.test(condition, StardewConditionContext.forPlayer(player))
                    .resultOrPartial(message -> LOGGER.error(
                            "[Mail] Availability condition failed for {}: {}", entry.definitionId(), message))
                    .orElse(false);
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * 夜间结算：将所有玩家的 mailForTomorrow 移入 mailbox。
     * 应在新一天开始时调用。
     *
     * <p>额外：对刚 flush 的 {@code ccXxx} flag（由 Joja CD form 次日生效）
     * 触发 {@link com.stardew.craft.communitycenter.reward.AreaCompletionService}，
     * 让温室、鱼塘淘金、公告板奖励等和 CC 路径一样生效。
     */
    public static void deliverAllTomorrowMail(net.minecraft.server.MinecraftServer server) {
        int today = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        PlayerDataManager manager = PlayerDataManager.get();
        net.minecraft.server.level.ServerLevel stardewLevel =
            server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerStardewData data = manager.getOrCreateData(player.getUUID());
            int beforeMailbox = data.getMailbox().size();
            int beforeTomorrow = data.getMailForTomorrow().size();
            java.util.List<String> flushed = data.deliverTomorrowMail(today);
            if (data.getMailbox().size() != beforeMailbox
                    || data.getMailForTomorrow().size() != beforeTomorrow
                    || !flushed.isEmpty()) {
                PlayerDataEventHandler.syncPlayerData(player, data);
            }
            dispatchFlushedFlags(player, stardewLevel, flushed);
        }
    }

    /**
     * 单个玩家登录时调用 —— 对跨日离线玩家补 flush。
     * 如果没到期就什么也不做。
     */
    public static void flushOnLogin(ServerPlayer player) {
        int today = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int beforeMailbox = data.getMailbox().size();
        int beforeTomorrow = data.getMailForTomorrow().size();
        java.util.List<String> flushed = data.deliverTomorrowMail(today);
        if (data.getMailbox().size() != beforeMailbox
                || data.getMailForTomorrow().size() != beforeTomorrow
                || !flushed.isEmpty()) {
            PlayerDataEventHandler.syncPlayerData(player, data);
        }
        if (flushed.isEmpty()) return;
        net.minecraft.server.level.ServerLevel stardewLevel =
            player.getServer() == null ? null
            : player.getServer().getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        dispatchFlushedFlags(player, stardewLevel, flushed);
    }

    private static void dispatchFlushedFlags(ServerPlayer player,
                                             net.minecraft.server.level.ServerLevel stardewLevel,
                                             java.util.List<String> flushed) {
        if (flushed.isEmpty()) return;
        for (String flag : flushed) {
            MailEntry mail = MailRegistry.get(flag);
            if (mail != null) {
                executeActions(player, mail, MailPhase.DELIVERY);
            }
            int areaId = ccFlagToAreaId(flag);
            if (areaId >= 0 && stardewLevel != null) {
                com.stardew.craft.communitycenter.reward.AreaCompletionService.onAreaComplete(
                    player, areaId, stardewLevel, /*jojaPath=*/true);
            }
        }
    }

    private static void executeActions(ServerPlayer player, MailEntry entry, MailPhase phase) {
        if (entry == null) return;
        List<StardewAction> actions = phase == MailPhase.DELIVERY ? entry.onDelivery() : entry.onRead();
        StardewActionContext context = StardewActionContext.forPlayer(player);
        for (StardewAction action : actions) {
            StardewActions.execute(action, context).resultOrPartial(message -> StardewCraft.LOGGER.error(
                    "[Mail] {} action failed for {}: {}", phase.logName, entry.definitionId(), message));
        }
    }

    private enum MailPhase {
        DELIVERY("delivery"),
        READ("read");

        private final String logName;

        MailPhase(String logName) {
            this.logName = logName;
        }
    }

    private static int ccFlagToAreaId(String flag) {
        if (flag == null) return -1;
        return switch (flag) {
            case "ccPantry"     -> 0;
            case "ccCraftsRoom" -> 1;
            case "ccFishTank"   -> 2;
            case "ccBoilerRoom" -> 3;
            case "ccVault"      -> 4;
            case "ccBulletin"   -> 5;
            default -> -1;
        };
    }
}
