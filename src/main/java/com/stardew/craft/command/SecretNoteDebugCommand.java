package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.secretnote.SecretNoteRegistry;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Operator-only staging and verification commands for the secret-note system. */
public final class SecretNoteDebugCommand {
    private SecretNoteDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
                .then(Commands.literal("secretnote")
                        .requires(source -> source.hasPermission(2))
                        .then(CommandTargets.executesWithTarget(
                                Commands.literal("status"), SecretNoteDebugCommand::status))
                        .then(CommandTargets.executesWithTarget(
                                Commands.literal("grant_magnifying_glass"), SecretNoteDebugCommand::grantMagnifyingGlass))
                        .then(Commands.literal("give")
                                .then(CommandTargets.executesWithTarget(
                                        Commands.argument("count", IntegerArgumentType.integer(1, 999)),
                                        SecretNoteDebugCommand::give)))
                        .then(Commands.literal("read")
                                .then(CommandTargets.executesWithTarget(
                                        Commands.argument("number", IntegerArgumentType.integer(1)),
                                        SecretNoteDebugCommand::read)))
                        .then(Commands.literal("forget")
                                .then(CommandTargets.executesWithTarget(
                                        Commands.argument("number", IntegerArgumentType.integer(1)),
                                        SecretNoteDebugCommand::forget)))
                        .then(CommandTargets.executesWithTarget(
                                Commands.literal("reset_read"), SecretNoteDebugCommand::resetRead))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int total = SecretNoteRegistry.orderedNotes().size();
        int seen = (int) SecretNoteRegistry.orderedNotes().stream()
                .filter(entry -> data.hasSeenSecretNote(entry.getKey().toString()))
                .count();
        context.getSource().sendSuccess(() -> Component.literal(
                "Secret notes for " + player.getName().getString()
                        + ": magnifyingGlass=" + SecretNoteService.hasMagnifyingGlass(data)
                        + ", seen=" + seen + "/" + total
                        + ", loose=" + player.getInventory().countItem(ModItems.SECRET_NOTE.get())), false);
        return seen;
    }

    private static int grantMagnifyingGlass(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        ItemStack stack = new ItemStack(ModItems.MAGNIFYING_GLASS.get());
        if (!player.addItem(stack)) player.drop(stack, false);
        SecretNoteService.grantMagnifyingGlass(player);
        context.getSource().sendSuccess(() -> Component.literal("Granted Magnifying Glass to "
                + player.getName().getString()), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        int count = IntegerArgumentType.getInteger(context, "count");
        ItemStack stack = new ItemStack(ModItems.SECRET_NOTE.get(), count);
        if (!player.addItem(stack)) player.drop(stack, false);
        return count;
    }

    private static int read(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        int number = IntegerArgumentType.getInteger(context, "number");
        ResourceLocation id = SecretNoteRegistry.byDisplayNumber(number);
        return id != null && SecretNoteService.debugDiscover(player, id) ? 1 : 0;
    }

    private static int forget(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        int number = IntegerArgumentType.getInteger(context, "number");
        ResourceLocation id = SecretNoteRegistry.byDisplayNumber(number);
        if (id == null) return 0;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.forgetSecretNote(id.toString())) return 0;
        saveAndSync(player, data);
        return 1;
    }

    private static int resetRead(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandTargets.resolve(context);
        if (player == null) return 0;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int oldCount = data.getSecretNotesSeen().size();
        data.clearSecretNotesSeen();
        saveAndSync(player, data);
        return oldCount;
    }

    private static void saveAndSync(ServerPlayer player, PlayerStardewData data) {
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }
}
