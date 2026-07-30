package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stardew.craft.combat.CombatDamageHistory;
import com.stardew.craft.combat.DamageOutcome;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prints the latest authoritative combat-damage trace for the executing player.
 */
public final class CombatExplainCommand {
    private CombatExplainCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
                .then(Commands.literal("combat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("explain")
                                .executes(context -> explain(context.getSource())))));
    }

    private static int explain(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        CombatDamageHistory.Entry entry = CombatDamageHistory.latest(player.getUUID()).orElse(null);
        if (entry == null) {
            source.sendFailure(Component.literal("No Stardew combat damage has been recorded yet."));
            return 0;
        }

        DamageOutcome outcome = entry.outcome();
        source.sendSuccess(
                () -> Component.literal("Latest Stardew damage trace (tick " + entry.gameTick() + "):"),
                false
        );
        for (String line : outcome.toExplainLines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
