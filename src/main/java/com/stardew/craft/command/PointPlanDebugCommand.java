package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.item.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PointPlanDebugCommand {
    private PointPlanDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("debug")
                .then(Commands.literal("tools")
                    .then(Commands.literal("point_plan")
                        .then(Commands.literal("give")
                            .executes(PointPlanDebugCommand::give)))
                    .then(Commands.literal("map_interaction_points")
                        .then(Commands.literal("give")
                            .executes(PointPlanDebugCommand::giveMapInteraction))))));
    }

    private static int give(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        ItemStack stack = new ItemStack(ModItems.POINT_PLAN_WAND.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Gave point plan wand."), false);
        return 1;
    }

    private static int giveMapInteraction(
            CommandContext<CommandSourceStack> context
    ) {
        if (!(context.getSource().getEntity()
                instanceof ServerPlayer player)) {
            context.getSource().sendFailure(
                    Component.literal("Must be a player."));
            return 0;
        }
        ItemStack stack = new ItemStack(
                ModItems.MAP_INTERACTION_POINT_WAND.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Gave map interaction point wand."),
                false);
        return 1;
    }
}
