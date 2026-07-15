package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.payload.FarmAdminSyncPayload;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 星露谷 OP 权限 + 管理面板命令。
 *
 * /stardew op <player>        — MC OP 授予某玩家星露谷管理权限
 * /stardew op remove <player> — MC OP 撤销某玩家星露谷管理权限
 * /stardew admin              — 拥有星露谷 OP 权限的玩家打开管理面板
 */
@SuppressWarnings("null")
public class FarmAdminCommand {

    /** mailFlag 键名 — 用于标记星露谷 OP 权限 */
    public static final String STARDEW_OP_FLAG = "stardew_op";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /stardew op <player> — 授予权限（需要 MC OP 2）
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("op")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(FarmAdminCommand::grantOp)
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(FarmAdminCommand::revokeOp)
                        )
                    )
                )
        );

        // /stardew admin — 打开管理面板（需要星露谷 OP 权限）
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("admin")
                    .requires(FarmAdminCommand::canOpenAdmin)
                    .executes(FarmAdminCommand::openAdmin)
                )
        );
    }

    private static boolean canOpenAdmin(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player && isStardewOp(player);
    }

    /** 检查玩家是否拥有星露谷 OP 权限 */
    public static boolean isStardewOp(ServerPlayer player) {
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        return data.hasMailFlag(STARDEW_OP_FLAG);
    }

    private static int grantOp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PlayerStardewData data = PlayerStardewDataAPI.getData(target);
        data.addMailFlag(STARDEW_OP_FLAG);

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.admin.granted_target", target.getGameProfile().getName()), true);
        target.displayClientMessage(Component.translatable("stardewcraft.command.admin.granted"), false);
        return 1;
    }

    private static int revokeOp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PlayerStardewData data = PlayerStardewDataAPI.getData(target);
        data.removeMailFlag(STARDEW_OP_FLAG);

        ctx.getSource().sendSuccess(() -> Component.translatable(
                "stardewcraft.command.admin.revoked_target", target.getGameProfile().getName()), true);
        target.displayClientMessage(Component.translatable("stardewcraft.command.admin.revoked"), false);
        return 1;
    }

    private static int openAdmin(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.player_required"));
            return 0;
        }

        if (!isStardewOp(player)) {
            ctx.getSource().sendFailure(Component.translatable("stardewcraft.command.admin.no_permission"));
            return 0;
        }

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        PacketDistributor.sendToPlayer(player, FarmAdminSyncPayload.fromRegistry(registry));
        return 1;
    }
}
