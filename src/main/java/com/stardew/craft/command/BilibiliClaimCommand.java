package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 内部命令 /stardew bilibili_claim — 由公告中的点击事件触发，
 * 领取 B 站关注奖励（彩虹猫之刃，一人一把）并打开 B 站主页。
 */
@SuppressWarnings("null")
public class BilibiliClaimCommand {

    private static final String BILIBILI_URL = "https://space.bilibili.com/259427053";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("bilibili_claim")
                    .executes(ctx -> claimReward(ctx.getSource()))
                )
        );
    }

    private static int claimReward(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.isBilibiliRewardClaimed()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.bilibili.reward.already_claimed"));
            return 0;
        }

        // 发放彩虹猫之刃
        ItemStack meowmere = new ItemStack(ModItems.MEOWMERE.get());
        if (!player.getInventory().add(meowmere)) {
            player.drop(meowmere, false);
        }
        data.setBilibiliRewardClaimed(true);

        // 发送奖励消息 + B 站链接
        MutableComponent urlMsg = Component.translatable("stardewcraft.bilibili.author_link", BILIBILI_URL.toString())
            .setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, BILIBILI_URL))
                .withUnderlined(true)
                .withColor(ChatFormatting.AQUA));

        player.sendSystemMessage(Component.translatable("stardewcraft.bilibili.reward.claimed",
                meowmere.getHoverName()));
        player.sendSystemMessage(urlMsg);

        return 1;
    }
}
