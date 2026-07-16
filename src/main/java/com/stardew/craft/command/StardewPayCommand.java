package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class StardewPayCommand {
    private StardewPayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .then(Commands.literal("pay")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                            .executes(StardewPayCommand::payThenRun))))));
    }

    private static int payThenRun(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        String command = StringArgumentType.getString(context, "command").trim();
        if (command.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Command is empty."));
            return 0;
        }

        int currentMoney = PlayerStardewDataAPI.getMoney(player);
        String playerName = com.stardew.craft.player.PlayerDisplayName.get(player);
        if (currentMoney < amount) {
            context.getSource().sendFailure(Component.literal(
                playerName + " only has " + currentMoney + "g; required " + amount + "g."));
            return 0;
        }

        if (!PlayerStardewDataAPI.removeMoney(player, amount)) {
            context.getSource().sendFailure(Component.literal(
                playerName + " does not have enough money."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal(
            "Charged " + amount + "g from " + playerName + "."), true);
        context.getSource().getServer().getCommands().performPrefixedCommand(context.getSource(), command);
        return 1;
    }
}
