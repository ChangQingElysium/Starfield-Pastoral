package com.stardew.craft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.museum.MuseumDonationItems;
import com.stardew.craft.network.MuseumDonationSyncPacket;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 博物馆调试命令（用于模拟捐赠）
 */
@SuppressWarnings("null")
public class MuseumDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("stardew")
                .then(Commands.literal("museum")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(MuseumDebugCommand::startDonationMode))
                        .then(Commands.literal("end")
                                .executes(MuseumDebugCommand::endDonationMode))
                        .then(Commands.literal("status")
                                .executes(MuseumDebugCommand::statusDonationMode))
                        .then(Commands.literal("lostbooks")
                                .then(Commands.literal("status")
                                        .executes(MuseumDebugCommand::lostBookStatus))
                                .then(Commands.literal("find")
                                        .executes(MuseumDebugCommand::findLostBook))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                .executes(MuseumDebugCommand::setLostBookCount)))
                                .then(Commands.literal("read")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(MuseumDebugCommand::readLostBook)))
                                .then(Commands.literal("reset_read")
                                        .executes(MuseumDebugCommand::resetLostBookReadFlags)))
                        .then(Commands.literal("donate")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                                        .executes(MuseumDebugCommand::donateItem)))));
    }

    private static int startDonationMode(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        java.util.UUID playerId = player.getUUID();
        if (data.isDonationModeActive(playerId)) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.museum.mode.already_on"), false);
            return 1;
        }

        data.startDonationMode(playerId);
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.museum.mode.started"), false);
        return 1;
    }

    private static int endDonationMode(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        java.util.UUID playerId = player.getUUID();
        if (!data.isDonationModeActive(playerId)) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.museum.mode.already_off"), false);
            return 1;
        }

        MuseumDonationData.EndSessionResult result = data.endDonationMode(playerId);
        if (!result.success()) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.museum.mode.end_blocked", result.missingItems().size()));
            return 0;
        }

        syncAll(data, player);
        context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.museum.mode.ended"), false);
        return 1;
    }

    private static int statusDonationMode(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        java.util.UUID playerId = player.getUUID();
        context.getSource().sendSuccess(() -> Component.translatable(
                data.isDonationModeActive(playerId) ? "stardewcraft.command.museum.mode.status_on" : "stardewcraft.command.museum.mode.status_off"
        ), false);
        return 1;
    }

    private static int donateItem(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        Item item = ItemArgument.getItem(context, "item").getItem();
        if (!MuseumDonationItems.isDonatable(new ItemStack(item))) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.museum.donate.invalid"));
            return 0;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        MuseumDonationData data = MuseumDonationData.get(player.serverLevel());
        java.util.UUID playerId = player.getUUID();
        boolean added = data.donate(playerId, id.toString());
        ItemStack stack = new ItemStack(item);
        Component itemName = stack.getHoverName();

    syncAll(data, player);

    if (added) {
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.museum.donate.success", itemName
        ), false);
    } else {
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.museum.donate.already", itemName
        ), false);
    }

        return 1;
    }

    private static int lostBookStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        int found = com.stardew.craft.museum.LostBookService.foundCount(player);
        int maximum = com.stardew.craft.museum.LostBookRegistry.discoveryMaximum();
        long read = com.stardew.craft.museum.LostBookRegistry.orderedBooks().stream()
                .map(java.util.Map.Entry::getKey)
                .filter(id -> com.stardew.craft.player.PlayerDataManager.getPlayerData(player)
                        .hasMailFlag(com.stardew.craft.museum.LostBookService.readFlag(id)))
                .count();
        context.getSource().sendSuccess(() -> Component.literal(
                "Lost books: found=" + found + "/" + maximum
                        + ", registered=" + com.stardew.craft.museum.LostBookRegistry.snapshot().definitions().size()
                        + ", read=" + read), false);
        return 1;
    }

    private static int findLostBook(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        boolean found = com.stardew.craft.museum.LostBookService.find(player);
        if (!found) {
            context.getSource().sendFailure(Component.literal("No undiscovered lost books remain."));
            return 0;
        }
        return lostBookStatus(context);
    }

    private static int setLostBookCount(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        int maximum = com.stardew.craft.museum.LostBookRegistry.discoveryMaximum();
        int requested = IntegerArgumentType.getInteger(context, "count");
        com.stardew.craft.museum.LostBookWorldData.get(player.server)
                .setFoundCount(requested, maximum);
        return lostBookStatus(context);
    }

    private static int readLostBook(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        String raw = StringArgumentType.getString(context, "id");
        ResourceLocation id = raw.indexOf(':') >= 0
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.tryBuild("stardewcraft", raw.matches("\\d+") ? "book_" + raw : raw);
        if (id == null || com.stardew.craft.museum.LostBookRegistry.get(id) == null) {
            context.getSource().sendFailure(Component.literal("Unknown lost book: " + raw));
            return 0;
        }
        return com.stardew.craft.museum.LostBookService.read(player, id) ? 1 : 0;
    }

    private static int resetLostBookReadFlags(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        var data = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        int removed = 0;
        for (String flag : new java.util.ArrayList<>(data.getMailFlags())) {
            if (flag.matches("lb_\\d+") || flag.startsWith("lost_book_read:")) {
                data.removeMailFlag(flag);
                removed++;
            }
        }
        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, data);
        int result = removed;
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + result + " lost-book read flags."), false);
        return 1;
    }

    private static void syncAll(MuseumDonationData data, ServerPlayer player) {
        data.ensureManagedStandLayout(player.serverLevel(), player.getUUID());
        PacketDistributor.sendToPlayer(player, new MuseumDonationSyncPacket(List.copyOf(data.getDonatedItems(player.getUUID()))));
        com.stardew.craft.block.utility.MuseumExhibitStandBlock.syncStands(player.serverLevel(), data, player);
    }
}
