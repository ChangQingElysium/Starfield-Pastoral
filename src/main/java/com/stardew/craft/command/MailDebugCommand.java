package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * /stardewmail send <mailId> [player] — 立即投递邮件
 * /stardewmail list — 列出所有已注册邮件ID
 * /stardewmail check — 查看当前信箱内容
 * /stardewmail clear — 清空信箱
 */
@SuppressWarnings("null")
public class MailDebugCommand {

    private static final SuggestionProvider<CommandSourceStack> MAIL_ID_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    MailRegistry.getAll().stream().map(e -> e.getId()),
                    builder);

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("mail")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("send")
                    .then(Commands.argument("mailId", StringArgumentType.word())
                        .suggests(MAIL_ID_SUGGESTIONS)
                        .executes(MailDebugCommand::sendToSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(MailDebugCommand::sendToTargets)
                        )
                    )
                )
                .then(Commands.literal("list")
                    .executes(MailDebugCommand::listMails)
                )
                .then(Commands.literal("check")
                    .executes(MailDebugCommand::checkMailbox)
                )
                .then(Commands.literal("clear")
                    .executes(MailDebugCommand::clearMailbox)
                )
            )
        );
    }

    private static int sendToSelf(CommandContext<CommandSourceStack> ctx) {
        String mailId = StringArgumentType.getString(ctx, "mailId");
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }
        if (!MailRegistry.contains(mailId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown mail ID: " + mailId));
            return 0;
        }
        MailService.addMail(player, mailId);
        ctx.getSource().sendSuccess(() -> Component.literal("Sent mail '" + mailId + "' to "
                + com.stardew.craft.player.PlayerDisplayName.get(player)), true);
        return 1;
    }

    private static int sendToTargets(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String mailId = StringArgumentType.getString(ctx, "mailId");
        if (!MailRegistry.contains(mailId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown mail ID: " + mailId));
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer target : targets) {
            MailService.addMail(target, mailId);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Sent mail '" + mailId + "' to " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int listMails(CommandContext<CommandSourceStack> ctx) {
        var all = MailRegistry.getAll();
        ctx.getSource().sendSuccess(() -> Component.literal("§6Registered mails (" + all.size() + "):"), false);
        for (var entry : all) {
            ctx.getSource().sendSuccess(() -> Component.literal("  §7- " + entry.getId()), false);
        }
        return all.size();
    }

    private static int checkMailbox(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        var mailbox = data.getMailbox();
        if (mailbox.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Mailbox is empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6Mailbox (" + mailbox.size() + " mails):"), false);
        for (String mid : mailbox) {
            ctx.getSource().sendSuccess(() -> Component.literal("  §7- " + mid), false);
        }
        return mailbox.size();
    }

    private static int clearMailbox(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        var mailbox = data.getMailbox();
        int count = mailbox.size();
        // Clear by popping all
        while (data.hasMailInMailbox()) {
            data.popMailFromMailbox();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aCleared " + count + " mails from mailbox"), true);
        return count;
    }
}
