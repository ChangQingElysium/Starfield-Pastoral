package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Handles the player-facing opt-out action embedded in the login announcement. */
public final class JoinAnnouncementCommand {

    private JoinAnnouncementCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .then(Commands.literal("announcement")
                .then(Commands.literal("dismiss")
                    .executes(context -> dismiss(context.getSource())))));
    }

    private static int dismiss(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.setJoinAnnouncementDismissed(true);
        player.sendSystemMessage(Component.translatable("stardewcraft.join_announcement.dismissed"));
        return 1;
    }
}
