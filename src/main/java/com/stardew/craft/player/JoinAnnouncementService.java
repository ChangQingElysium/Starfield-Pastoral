package com.stardew.craft.player;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

public final class JoinAnnouncementService {

    private static final int ANNOUNCEMENT_DELAY_TICKS = 20;
    private static final String DISCORD_URL = "https://discord.gg/cnG3eE58Au";
    private static final String QQ_GROUP = "961767762";
    private static final String BILIBILI_URL = "https://space.bilibili.com/259427053";
    private static final String REWARD_COMMAND = "/stardew bilibili_claim";

    private JoinAnnouncementService() {}

    public static void schedule(ServerPlayer player) {
        player.server.tell(new TickTask(player.server.getTickCount() + ANNOUNCEMENT_DELAY_TICKS, () -> {
            if (!player.isRemoved()) {
                send(player);
            }
        }));
    }

    private static void send(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("────── ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Starfield Pastoral").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(" ──────").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("[!] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.translatable("stardewcraft.join_announcement.public_test")
                .withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(linkLine("stardewcraft.join_announcement.discord", DISCORD_URL));
        player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable("stardewcraft.join_announcement.qq")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(QQ_GROUP).withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(linkLine("stardewcraft.join_announcement.bilibili", BILIBILI_URL));
        player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable("stardewcraft.welcome.menu_hint")
                .withStyle(ChatFormatting.GRAY)));
        if (!PlayerDataManager.getPlayerData(player).isBilibiliRewardClaimed()) {
            player.sendSystemMessage(Component.literal("  → ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("stardewcraft.join_announcement.reward")
                    .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, REWARD_COMMAND))
                        .withUnderlined(true)
                        .withColor(ChatFormatting.GOLD))));
        }
        player.sendSystemMessage(Component.literal("─────────────────────────────")
            .withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal(""));
    }

    private static MutableComponent linkLine(String labelKey, String url) {
        return Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(url).setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withUnderlined(true)
                .withColor(ChatFormatting.AQUA)));
    }
}
