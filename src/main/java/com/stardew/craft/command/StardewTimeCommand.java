package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.stardew.craft.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Server-authoritative Stardew clock controls. */
public final class StardewTimeCommand {
    private StardewTimeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .then(Commands.literal("timespeed")
                .executes(context -> showCurrent(context.getSource()))
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1D, 100.0D))
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> setMultiplier(
                        context.getSource(),
                        DoubleArgumentType.getDouble(context, "multiplier")
                    )))));
    }

    private static int showCurrent(CommandSourceStack source) {
        source.sendSuccess(
            () -> Component.translatable(
                "stardewcraft.command.timespeed.current",
                formatMultiplier(Config.TIME_SPEED_MULTIPLIER.get())
            ),
            false
        );
        return 1;
    }

    private static int setMultiplier(CommandSourceStack source, double multiplier) {
        Config.TIME_SPEED_MULTIPLIER.set(multiplier);
        source.sendSuccess(
            () -> Component.translatable(
                "stardewcraft.command.timespeed.set",
                formatMultiplier(multiplier)
            ),
            true
        );
        return 1;
    }

    static String formatMultiplier(double multiplier) {
        return java.math.BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString();
    }
}
