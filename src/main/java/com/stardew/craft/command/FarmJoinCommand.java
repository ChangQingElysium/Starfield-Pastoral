package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmJoinManager;
import com.stardew.craft.network.payload.FarmListSyncPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /stardew farm accept <uuid>  — 接受加入请求
 * /stardew farm reject <uuid>  — 拒绝加入请求
 *
 * 调试子命令（需要 OP 2）：
 * /stardew farm debug selectionscreen  — 打开农场选择界面（含"加入"按钮）
 * /stardew farm debug joinscreen       — 打开加入农场列表（模拟数据）
 * /stardew farm debug request          — 模拟收到一条加入请求聊天消息
 * /stardew farm debug addmember        — 给自己农场添加一个假成员
 * /stardew farm debug members          — 列出当前农场所有成员
 * /stardew farm debug clearmembers     — 清除所有假成员
 */
@SuppressWarnings("null")
public class FarmJoinCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("farm")
                    .then(Commands.literal("accept")
                        .executes(ctx -> handleLatestResponse(ctx, true))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                            .executes(ctx -> handleResponse(ctx, true))
                        )
                    )
                    .then(Commands.literal("reject")
                        .executes(ctx -> handleLatestResponse(ctx, false))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                            .executes(ctx -> handleResponse(ctx, false))
                        )
                    )
                    .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("selectionscreen")
                            .executes(FarmJoinCommand::debugSelectionScreen)
                        )
                        .then(Commands.literal("joinscreen")
                            .executes(FarmJoinCommand::debugJoinScreen)
                        )
                        .then(Commands.literal("request")
                            .executes(FarmJoinCommand::debugRequest)
                        )
                        .then(Commands.literal("addmember")
                            .executes(FarmJoinCommand::debugAddMember)
                        )
                        .then(Commands.literal("members")
                            .executes(FarmJoinCommand::debugListMembers)
                        )
                        .then(Commands.literal("clearmembers")
                            .executes(FarmJoinCommand::debugClearMembers)
                        )
                    )
                )
        );
    }

    private static int handleResponse(CommandContext<CommandSourceStack> ctx, boolean accept) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("stardewcraft.command.player_only"));
            return 0;
        }

        UUID requesterUUID = UuidArgument.getUuid(ctx, "uuid");

        boolean success = FarmJoinManager.handleResponse(player, requesterUUID, accept, player.server);
        return success ? 1 : 0;
    }

    private static int handleLatestResponse(CommandContext<CommandSourceStack> ctx, boolean accept) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        boolean success = FarmJoinManager.handleLatestResponse(player, accept, player.server);
        return success ? 1 : 0;
    }

    // ══════════════════════════════════════════
    //  调试子命令
    // ══════════════════════════════════════════

    /** 打开农场选择界面（含"加入别人的农场"按钮） */
    private static int debugSelectionScreen(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        FarmJoinManager.syncPendingState(player, FarmJoinManager.hasPending(player.getUUID()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.stardew.craft.network.payload.OpenFarmSelectionPayload());
        player.sendSystemMessage(Component.translatable("stardewcraft.command.farm_join.selection_opened"));
        return 1;
    }

    /** 打开加入农场列表界面，填入模拟数据 */
    private static int debugJoinScreen(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        // 构建模拟农场列表
        List<FarmListSyncPayload.FarmEntry> mockFarms = new ArrayList<>();
        mockFarms.add(new FarmListSyncPayload.FarmEntry(
                UUID.randomUUID(), "Robin", "Sunrise Farm", "standard", 0, false));
        mockFarms.add(new FarmListSyncPayload.FarmEntry(
                UUID.randomUUID(), "Ruby", "Starlight Garden", "riverland", 0, false));
        mockFarms.add(new FarmListSyncPayload.FarmEntry(
                UUID.randomUUID(), "Alex", "Harvest Manor", "forest", 0, false));
        mockFarms.add(new FarmListSyncPayload.FarmEntry(
                UUID.randomUUID(), "MoonHunter", "Bluewater Farm", "hilltop", 0, false));
        mockFarms.add(new FarmListSyncPayload.FarmEntry(
                UUID.randomUUID(), "StarFarmer", "Lucky Homestead", "wilderness", 0, false));

        // 也加入真实已有的农场（如果有的话）
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        int maxFarmers = ModGameRules.getMaxFarmersPerFarm(player.server);
        for (FarmInstance farm : registry.getAllFarms()) {
            if (farm.isInitialized() && farm.getFarmerCount() < maxFarmers) {
                mockFarms.add(new FarmListSyncPayload.FarmEntry(
                        farm.getOwnerUUID(), com.stardew.craft.player.PlayerDisplayName.get(
                                player.server, farm.getOwnerUUID()), farm.getFarmName(),
                        farm.getFarmLayoutId().toString(), 0, false));
            }
        }

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new FarmListSyncPayload(mockFarms, "farm_join"));
        player.sendSystemMessage(Component.translatable(
                "stardewcraft.command.farm_join.list_opened", mockFarms.size()));
        return 1;
    }

    /** 模拟收到一条加入请求的聊天消息（带 [接受] [拒绝] 按钮） */
    private static int debugRequest(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        UUID fakeRequester = UUID.randomUUID();
        String fakeName = "TestPlayer";

        MutableComponent msg = Component.translatable("stardewcraft.farm.join.incoming", fakeName);
        msg.append(Component.literal(" "));

        MutableComponent acceptBtn = Component.literal("[")
                .append(Component.translatable("stardewcraft.farm.join.accept"))
                .append("]");
        acceptBtn.setStyle(Style.EMPTY
                .withColor(0x2E7D32)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/stardew farm accept " + fakeRequester))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("stardewcraft.farm.join.accept.hover"))));
        msg.append(acceptBtn);
        msg.append(Component.literal(" "));

        MutableComponent rejectBtn = Component.literal("[")
                .append(Component.translatable("stardewcraft.farm.join.reject"))
                .append("]");
        rejectBtn.setStyle(Style.EMPTY
                .withColor(0xC62828)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/stardew farm reject " + fakeRequester))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("stardewcraft.farm.join.reject.hover"))));
        msg.append(rejectBtn);

        player.sendSystemMessage(msg);
        player.sendSystemMessage(Component.translatable("stardewcraft.command.farm_join.request_sent"));
        return 1;
    }

    /** 给自己农场添加一个假成员 */
    private static int debugAddMember(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarm(player.getUUID());
        if (farm == null) {
            player.sendSystemMessage(Component.translatable("stardewcraft.command.farm_join.no_farm"));
            return 0;
        }

        UUID fakeUUID = UUID.randomUUID();
        if (!registry.addMember(player.getUUID(), fakeUUID)) {
            int maxFarmers = ModGameRules.getMaxFarmersPerFarm(player.server);
            player.sendSystemMessage(Component.translatable("stardewcraft.command.farm_join.full", maxFarmers));
            return 0;
        }

        int maxFarmers = ModGameRules.getMaxFarmersPerFarm(player.server);
        player.sendSystemMessage(Component.translatable(
                "stardewcraft.command.farm_join.member_added",
                fakeUUID.toString().substring(0, 8), farm.getFarmerCount(), maxFarmers));
        return 1;
    }

    /** 列出当前农场所有成员 */
    private static int debugListMembers(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
        if (farm == null) {
            player.sendSystemMessage(Component.translatable("stardewcraft.command.farm_join.no_farm"));
            return 0;
        }

        int maxFarmers = ModGameRules.getMaxFarmersPerFarm(player.server);
        player.sendSystemMessage(Component.translatable(
                "stardewcraft.command.farm_join.members_header", farm.getFarmerCount(), maxFarmers));
        player.sendSystemMessage(Component.translatable(
                "stardewcraft.command.farm_join.owner",
                com.stardew.craft.player.PlayerDisplayName.get(player.server, farm.getOwnerUUID()),
                farm.getOwnerUUID().toString().substring(0, 8)));
        int i = 1;
        for (UUID member : farm.getMembers()) {
            // 尝试查找在线玩家名
            ServerPlayer mp = player.server.getPlayerList().getPlayer(member);
            Component name = mp != null
                    ? Component.literal(com.stardew.craft.player.PlayerDisplayName.get(mp))
                    : Component.translatable("stardewcraft.command.farm_join.offline_member");
            player.sendSystemMessage(Component.translatable(
                    "stardewcraft.command.farm_join.member", i, name,
                    member.toString().substring(0, 8)));
            i++;
        }
        return 1;
    }

    /** 清除所有假成员 */
    private static int debugClearMembers(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarm(player.getUUID());
        if (farm == null) {
            player.sendSystemMessage(Component.translatable("stardewcraft.command.farm_join.no_farm"));
            return 0;
        }

        List<UUID> toRemove = new ArrayList<>(farm.getMembers());
        for (UUID m : toRemove) {
            registry.removeMember(player.getUUID(), m);
        }
        player.sendSystemMessage(Component.translatable(
                "stardewcraft.command.farm_join.members_cleared", toRemove.size()));
        return 1;
    }
}
