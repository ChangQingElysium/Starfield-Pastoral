package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.stardew.craft.farm.FarmCaveAPI;
import com.stardew.craft.farm.FarmCaveChoice;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 农场洞穴选择命令：
 * <pre>
 *   /stardew farmcave get
 *   /stardew farmcave set &lt;none|fruit_bats|mushrooms&gt;
 *   /stardew farmcave admin set &lt;player&gt; &lt;choice&gt;   (需要 OP 2)
 * </pre>
 */
public class FarmCaveCommand {

    private static final SuggestionProvider<CommandSourceStack> CHOICE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"none", "fruit_bats", "mushrooms"}, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("farmcave")
                    .then(Commands.literal("get")
                        .executes(FarmCaveCommand::runGet))
                    .then(Commands.literal("set")
                        .then(Commands.argument("choice", StringArgumentType.word())
                            .suggests(CHOICE_SUGGESTIONS)
                            .executes(FarmCaveCommand::runSet)))
                    .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("choice", StringArgumentType.word())
                                    .suggests(CHOICE_SUGGESTIONS)
                                    .executes(FarmCaveCommand::runAdminSet)))))
                    .then(Commands.literal("rebuild")
                        .requires(src -> src.hasPermission(2))
                        .executes(FarmCaveCommand::runRebuildSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FarmCaveCommand::runRebuildTarget)))
                )
        );
    }

    private static int runGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        if (farm == null) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.farm_cave.no_farm"));
            return 0;
        }
        FarmCaveChoice c = farm.getCaveChoice();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.farm_cave.current", caveChoiceName(c)), false);
        return 1;
    }

    private static int runSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "choice");
        FarmCaveChoice choice = FarmCaveChoice.fromName(name);
        if (choice == null) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.unknown_option", name));
            return 0;
        }
        FarmInstanceRegistry reg = FarmInstanceRegistry.get();
        FarmInstance farm = reg.getFarmForPlayer(player.getUUID());
        if (farm == null) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.farm_cave.not_member"));
            return 0;
        }
        boolean ok = FarmCaveAPI.setCaveChoice(player, choice);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.farm_cave.set_failed"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.farm_cave.updated", caveChoiceName(choice)), true);
        return 1;
    }

    private static int runAdminSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "choice");
        FarmCaveChoice choice = FarmCaveChoice.fromName(name);
        if (choice == null) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.unknown_option", name));
            return 0;
        }
        boolean ok = FarmCaveAPI.setCaveChoice(target.getUUID(), choice);
        if (!ok) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.farm_cave.target_no_farm"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.farm_cave.admin_updated",
                com.stardew.craft.player.PlayerDisplayName.get(target), caveChoiceName(choice)), true);
        return 1;
    }

    private static int runRebuildSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return doRebuild(ctx, player);
    }

    private static int runRebuildTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return doRebuild(ctx, target);
    }

    private static int doRebuild(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(target.getUUID());
        if (farm == null) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.farm_cave.target_no_farm"));
            return 0;
        }
        net.minecraft.server.level.ServerLevel stardewLevel =
                ctx.getSource().getServer().getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.dimension_not_loaded"));
            return 0;
        }
        boolean placed = com.stardew.craft.farm.FarmInstanceInitializer.backfillFarmCaveIfMissing(stardewLevel, farm);
        if (placed) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.farm_cave.rebuilt",
                    com.stardew.craft.player.PlayerDisplayName.get(target)), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "stardewcraft.command.farm_cave.already_exists",
                    com.stardew.craft.player.PlayerDisplayName.get(target)), false);
        }
        return 1;
    }

    private static Component caveChoiceName(FarmCaveChoice choice) {
        return Component.translatable("stardewcraft.farm_cave.choice." + choice.getName());
    }
}
