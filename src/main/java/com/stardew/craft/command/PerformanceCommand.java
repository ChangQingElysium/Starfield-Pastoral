package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stardew.craft.server.performance.PerformanceReportFormatter;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class PerformanceCommand {
    private PerformanceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
                .then(Commands.literal("perf")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(context -> start(context.getSource())))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> reset(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        for (String line : PerformanceReportFormatter.format(ServerPerformanceRecorder.snapshot())) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int start(CommandSourceStack source) {
        ServerPerformanceRecorder.enable();
        source.sendSuccess(() -> Component.literal("Stardew performance profiling started"), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerPerformanceRecorder.disable();
        source.sendSuccess(() -> Component.literal("Stardew performance profiling stopped"), false);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ServerPerformanceRecorder.reset();
        source.sendSuccess(() -> Component.literal("Stardew performance metrics reset"), false);
        return 1;
    }
}
