package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
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
 * /stardew mail send <mailId> [player] — 立即投递邮件
 * /stardew mail list — 列出所有已注册邮件ID
 * /stardew mail diagnostics — 显示最近一次邮件重载诊断
 * /stardew mail check — 查看当前信箱内容
 * /stardew mail clear — 清空信箱
 */
@SuppressWarnings("null")
public class MailDebugCommand {

    private static final EntityArgument PLAYER_TARGET_ARGUMENT = EntityArgument.players();

    private static final SuggestionProvider<CommandSourceStack> MAIL_ID_SUGGESTIONS =
            (ctx, builder) -> {
                String remaining = builder.getRemaining();
                int separator = firstWhitespace(remaining);
                if (separator < 0) {
                    return SharedSuggestionProvider.suggest(
                            MailRegistry.getAll().stream().map(e -> e.getId()),
                            builder);
                }
                int targetStart = separator;
                while (targetStart < remaining.length()
                        && Character.isWhitespace(remaining.charAt(targetStart))) {
                    targetStart++;
                }
                return PLAYER_TARGET_ARGUMENT.listSuggestions(
                        ctx,
                        builder.createOffset(builder.getStart() + targetStart));
            };

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("mail")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("send")
                    .then(Commands.argument("mailAndTargets", StringArgumentType.greedyString())
                        .suggests(MAIL_ID_SUGGESTIONS)
                        .executes(MailDebugCommand::send)
                    )
                )
                .then(Commands.literal("list")
                    .executes(MailDebugCommand::listMails)
                )
                .then(Commands.literal("diagnostics")
                    .executes(MailDebugCommand::showDiagnostics)
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

    private static int send(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ParsedSend parsed = parseSend(StringArgumentType.getString(ctx, "mailAndTargets"));
        String mailId = parsed.mailId();
        if (!MailRegistry.contains(mailId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown mail ID: " + mailId));
            return 0;
        }
        Collection<ServerPlayer> targets;
        if (parsed.targetSelector() == null) {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
                return 0;
            }
            targets = java.util.List.of(player);
        } else {
            targets = parsed.targetSelector().findPlayers(ctx.getSource());
        }
        for (ServerPlayer target : targets) {
            MailService.addMail(target, mailId);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Sent mail '" + mailId + "' to " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    static ParsedSend parseSend(String raw) throws CommandSyntaxException {
        String value = raw == null ? "" : raw.strip();
        int separator = firstWhitespace(value);
        if (separator < 0) {
            if (value.isEmpty()) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                        .dispatcherUnknownArgument().create();
            }
            return new ParsedSend(value, null);
        }
        String mailId = value.substring(0, separator);
        String targetText = value.substring(separator).strip();
        if (targetText.isEmpty()) {
            return new ParsedSend(mailId, null);
        }
        StringReader reader = new StringReader(targetText);
        var selector = PLAYER_TARGET_ARGUMENT.parse(reader);
        reader.skipWhitespace();
        if (reader.canRead()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                    .dispatcherUnknownArgument().createWithContext(reader);
        }
        return new ParsedSend(mailId, selector);
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int listMails(CommandContext<CommandSourceStack> ctx) {
        var all = MailRegistry.getAll();
        ctx.getSource().sendSuccess(() -> Component.literal("§6Registered mails (" + all.size() + "):"), false);
        for (var entry : all) {
            ctx.getSource().sendSuccess(() -> Component.literal("  §7- " + entry.getId()), false);
        }
        if (all.isEmpty() && !MailRegistry.lastReloadDiagnostics().isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§eMail reload reported problems; run /stardew mail diagnostics"),
                    false);
        }
        return all.size();
    }

    private static int showDiagnostics(CommandContext<CommandSourceStack> ctx) {
        var snapshot = MailRegistry.snapshot();
        var diagnostics = MailRegistry.lastReloadDiagnostics();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6Mail snapshot v" + snapshot.version()
                        + ": " + snapshot.definitions().size() + " registered, "
                        + diagnostics.size() + " reload diagnostic(s)"), false);
        if (diagnostics.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("§aNo mail reload problems were reported"), false);
            return 0;
        }
        for (var diagnostic : diagnostics) {
            String color = diagnostic.severity() == DefinitionDiagnostic.Severity.ERROR
                    ? "§c" : "§e";
            String source = diagnostic.source() == null ? "<mail reload>" : diagnostic.source().toString();
            String definition = diagnostic.definitionId() == null
                    ? "" : " [" + diagnostic.definitionId() + "]";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    color + diagnostic.severity() + " " + source + definition + ": "
                            + diagnostic.message()), false);
        }
        return diagnostics.size();
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

    record ParsedSend(
            String mailId,
            net.minecraft.commands.arguments.selector.EntitySelector targetSelector
    ) {
    }
}
