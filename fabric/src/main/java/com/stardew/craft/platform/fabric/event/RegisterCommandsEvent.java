package com.stardew.craft.platform.fabric.event;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Objects;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class RegisterCommandsEvent extends Event {
    private final CommandDispatcher<CommandSourceStack> dispatcher;
    private final Commands.CommandSelection selection;
    private final CommandBuildContext context;

    public RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection selection,
            CommandBuildContext context) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.context = Objects.requireNonNull(context, "context");
    }
    public CommandDispatcher<CommandSourceStack> getDispatcher() { return dispatcher; }
    public Commands.CommandSelection getCommandSelection() { return selection; }
    public CommandBuildContext getBuildContext() { return context; }
}
