package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.menu.BundleMenu;
import com.stardew.craft.communitycenter.network.BundleSyncPayload;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.communitycenter.state.CommunityCenterProgress;
import com.stardew.craft.communitycenter.state.CommunityCenterSavedData;
import com.stardew.craft.greenhouse.GreenhouseManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.Collection;

/**
 * Debug commands for Community Center.
 * Registered under both /cc and /stardew cc.
 *
 * /stardew cc open [areaId]          — open the bundle GUI for a given area
 * /stardew cc status                 — print progress summary
 * /stardew cc reset                  — reset all progress
 * /stardew cc complete               — complete all bundles + trigger side-effects
 * /stardew cc complete_area <areaId> — complete all bundles in one area + trigger side-effects
 * /stardew cc complete_bundle <id>   — complete a specific bundle (mark all slots)
 */
@SuppressWarnings("null")
public class CommunityCenterCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register under /stardew cc
        dispatcher.register(Commands.literal("stardew")
                .then(buildTree(Commands.literal("cc"))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTree(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.requires(source -> source.hasPermission(2))
                .then(Commands.literal("open")
                        .executes(ctx -> openMenu(ctx, 1))
                        .then(Commands.argument("area", IntegerArgumentType.integer(0, 6))
                                .executes(ctx -> openMenu(ctx, IntegerArgumentType.getInteger(ctx, "area")))))
                .then(Commands.literal("status")
                        .executes(CommunityCenterCommand::showStatus))
                .then(Commands.literal("reset")
                        .executes(CommunityCenterCommand::resetAll))
                .then(Commands.literal("complete")
                        .executes(CommunityCenterCommand::completeAllWithEffects))
                .then(Commands.literal("complete_area")
                        .then(Commands.argument("area", IntegerArgumentType.integer(0, 6))
                                .executes(ctx -> completeArea(ctx, IntegerArgumentType.getInteger(ctx, "area")))))
                .then(Commands.literal("complete_bundle")
                        .then(Commands.argument("bundleId", IntegerArgumentType.integer(0))
                                .executes(ctx -> completeBundle(ctx, IntegerArgumentType.getInteger(ctx, "bundleId")))))
                .then(Commands.literal("unlock_junimo_text")
                        .executes(CommunityCenterCommand::unlockJunimoText));
    }

    private static int unlockJunimoText(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }
        CCStoryFlags.addFlag(player, CCStoryFlags.CAN_READ_JUNIMO);
        BundleSyncPayload.sendFullSync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Granted canReadJunimoText — junimo note icon will now show in GameMenu."),
                false);
        return 1;
    }

    private static int openMenu(CommandContext<CommandSourceStack> ctx, int areaId) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        player.openMenu(new SimpleMenuProvider(
                (menuId, inv, p) -> new BundleMenu(menuId, inv, areaId),
                Component.translatable("stardewcraft.menu.community_center")
        ));

        BundleSyncPayload.sendFullSync(player);

        ctx.getSource().sendSuccess(
                () -> Component.literal("Opened Community Center GUI for area " + areaId),
                false);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        java.util.UUID uuid = player != null ? player.getUUID() : new java.util.UUID(0L, 0L);
        String summary = CommunityCenterProgress.getDebugSummary(uuid);
        ctx.getSource().sendSuccess(() -> Component.literal(summary), false);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        java.util.UUID uuid = player != null ? player.getUUID() : new java.util.UUID(0L, 0L);
        CommunityCenterSavedData.get().resetAll(uuid);
        if (player != null) {
            BundleSyncPayload.sendFullSync(player);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("Community Center progress reset."), false);
        return 1;
    }

    // ── Complete all areas with side-effects ──

    private static int completeAllWithEffects(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        CommunityCenterSavedData data = CommunityCenterSavedData.get();

        // Mark all bundle slots complete
        java.util.UUID uuid = player != null ? player.getUUID() : new java.util.UUID(0L, 0L);
        data.completeAll(uuid);

        // Trigger side-effects for each area
        if (player != null) {
            for (int areaId = 0; areaId <= 5; areaId++) {
                applyAreaSideEffects(player, areaId);
            }
            BundleSyncPayload.sendFullSync(player);
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("All Community Center bundles and areas completed (with side-effects)."), false);
        return 1;
    }

    // ── Complete a single area ──

    private static int completeArea(CommandContext<CommandSourceStack> ctx, int areaId) {
        CommunityCenterSavedData data = CommunityCenterSavedData.get();

        // Complete all bundles belonging to this area
        Collection<BundleDefinition> bundles = BundleDataManager.getBundlesForArea(areaId);
        if (bundles == null || bundles.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No bundles found for area " + areaId));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        java.util.UUID uuid = player != null ? player.getUUID() : new java.util.UUID(0L, 0L);
        for (BundleDefinition def : bundles) {
            data.markBundleAllSlotsComplete(uuid, def.bundleId());
        }
        data.markAreaComplete(uuid, areaId);

        // Side effects
        if (player != null) {
            applyAreaSideEffects(player, areaId);
            BundleSyncPayload.sendFullSync(player);
        }

        String areaName = BundleDataManager.getAreaName(areaId);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Area " + areaId + " (" + areaName + ") completed."), false);
        return 1;
    }

    // ── Complete a single bundle ──

    private static int completeBundle(CommandContext<CommandSourceStack> ctx, int bundleId) {
        BundleDefinition def = BundleDataManager.getBundle(bundleId);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("Bundle " + bundleId + " not found."));
            return 0;
        }

        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        ServerPlayer player = ctx.getSource().getPlayer();
        java.util.UUID uuid = player != null ? player.getUUID() : new java.util.UUID(0L, 0L);
        data.markBundleAllSlotsComplete(uuid, bundleId);
        if (player != null) {
            BundleSyncPayload.sendFullSync(player);
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("Bundle " + bundleId + " (" + def.internalName() + ") completed."), false);
        return 1;
    }

    // ── Side-effect helper ──

    /**
     * Apply the side-effects that normally happen when an area is completed
     * through gameplay (story flags, mail, greenhouse repair, cutscene).
     */
    private static void applyAreaSideEffects(ServerPlayer player, int areaId) {
        // Story flag
        String flag = CCStoryFlags.areaFlag(areaId);
        if (!flag.isEmpty()) {
            CCStoryFlags.addFlag(player, flag);
        }

        // Area completion mail
        com.stardew.craft.mail.MailService.addMail(player, "cc_area_complete_" + areaId);

        // Check total completion
        CommunityCenterSavedData data = CommunityCenterSavedData.get();
        if (data.areAllAreasComplete(player.getUUID())) {
            CCStoryFlags.addFlag(player, CCStoryFlags.CC_IS_COMPLETE);
        }

        // Area-specific: cutscene + greenhouse
        if (player.level() instanceof ServerLevel serverLevel) {
            com.stardew.craft.communitycenter.cutscene.AreaRestoreCutscene.start(serverLevel, areaId, player.getUUID());

            if (areaId == 0) {
                GreenhouseManager greenhouse = GreenhouseManager.get(serverLevel);
                greenhouse.repairForPlayer(serverLevel, player.getUUID());
            }
        }
    }
}
