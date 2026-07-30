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
    private static final String DISMISS_COMMAND = "/stardew announcement dismiss";

    private JoinAnnouncementService() {}

    public static void schedule(ServerPlayer player) {
        var updateCheck = ModUpdateChecker.checkAsync();
        player.server.tell(new TickTask(player.server.getTickCount() + ANNOUNCEMENT_DELAY_TICKS, () -> {
            if (player.isRemoved()) {
                return;
            }
            updateCheck.thenAccept(status -> player.server.execute(() -> {
                if (player.isRemoved()) {
                    return;
                }
                boolean dismissed = PlayerDataManager.getPlayerData(player)
                        .isJoinAnnouncementDismissed();
                if (!dismissed) {
                    send(player, status);
                } else if (shouldSendUpdateNotice(dismissed, status)) {
                    sendUpdateNotice(player, status);
                }
            }));
        }));
    }

    static boolean shouldSendUpdateNotice(
            boolean announcementDismissed,
            ModUpdateChecker.VersionStatus status
    ) {
        return announcementDismissed && status.isOutdated();
    }

    private static void send(ServerPlayer player, ModUpdateChecker.VersionStatus status) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("────── ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Starfield Pastoral").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(" ──────").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("[!] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(Component.translatable("stardewcraft.join_announcement.public_test")
                .withStyle(ChatFormatting.GRAY)));
        sendVersionStatus(player, status);
        player.sendSystemMessage(linkLine(
                "stardewcraft.join_announcement.modrinth", ModUpdateChecker.MODRINTH_URL));
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
        player.sendSystemMessage(Component.literal("  [ ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable("stardewcraft.join_announcement.dismiss")
                .setStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, DISMISS_COMMAND))
                    .withUnderlined(true)
                    .withColor(ChatFormatting.GRAY)))
            .append(Component.literal(" ]").withStyle(ChatFormatting.DARK_GRAY)));
        player.sendSystemMessage(Component.literal("─────────────────────────────")
            .withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal(""));
    }

    private static void sendUpdateNotice(
            ServerPlayer player,
            ModUpdateChecker.VersionStatus status
    ) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("[!] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("stardewcraft.join_announcement.update_available")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        sendVersionStatus(player, status);
        player.sendSystemMessage(linkLine(
                "stardewcraft.join_announcement.modrinth", ModUpdateChecker.MODRINTH_URL));
        player.sendSystemMessage(Component.literal(""));
    }

    private static void sendVersionStatus(
            ServerPlayer player,
            ModUpdateChecker.VersionStatus status
    ) {
        player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable(
                        "stardewcraft.join_announcement.current_version",
                        status.installedVersion()).withStyle(ChatFormatting.GRAY)));
        switch (status.state()) {
            case UP_TO_DATE -> player.sendSystemMessage(Component.literal("  • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("stardewcraft.join_announcement.up_to_date")
                            .withStyle(ChatFormatting.GREEN)));
            case OUTDATED -> {
                player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable(
                                "stardewcraft.join_announcement.latest_version",
                                status.latestVersion()).withStyle(ChatFormatting.YELLOW)));
                player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable(
                                "stardewcraft.join_announcement.update_recommended")
                                .withStyle(ChatFormatting.GOLD)));
            }
            case AHEAD -> {
                player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable(
                                "stardewcraft.join_announcement.latest_version",
                                status.latestVersion()).withStyle(ChatFormatting.GRAY)));
                player.sendSystemMessage(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable(
                                "stardewcraft.join_announcement.ahead_of_public")
                                .withStyle(ChatFormatting.GREEN)));
            }
            case UNAVAILABLE -> player.sendSystemMessage(Component.literal("  • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(
                            "stardewcraft.join_announcement.latest_unavailable")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
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
