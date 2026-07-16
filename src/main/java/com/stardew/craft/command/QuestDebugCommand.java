package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.stardew.craft.cutscene.network.SyncEventSeenPayload;
import com.stardew.craft.cutscene.server.EventSeenData;
import com.stardew.craft.network.payload.SyncNpcFriendshipStatusPayload;
import com.stardew.craft.museum.MuseumDonationData;
import com.stardew.craft.museum.MuseumQuestService;
import com.stardew.craft.network.MuseumDonationSyncPacket;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.StardewQuest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

public final class QuestDebugCommand {

    private static final String MARNIE_REQUEST_EVENT = "marnie_cave_carrot_request_wake_up";
    private static final String JODI_INVITE_EVENT = "jodi_fish_casserole_invite_wake_up";
    private static final String MARNIE_COMPLETE_EVENT = "marnie_cave_carrot_delivery";
    private static final String JODI_COMPLETE_EVENT = "94";
    private static final String MUSEUM_INTRO_EVENT = "0";

    private QuestDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .then(Commands.literal("quest")
                    .requires(source -> source.hasPermission(2))
                    .then(CommandTargets.executesWithTarget(
                        Commands.literal("status"),
                        QuestDebugCommand::status
                    ))
                    .then(Commands.literal("validate").executes(QuestDebugCommand::validate))
                    .then(Commands.literal("grant")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::grant
                        ))
                    )
                    .then(Commands.literal("inspect")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::inspect
                        ))
                    )
                    .then(Commands.literal("reset")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::reset
                        ))
                    )
                    .then(Commands.literal("accept")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::accept
                        ))
                    )
                    .then(Commands.literal("complete")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::complete
                        ))
                    )
                    .then(Commands.literal("remove")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::remove
                        ))
                    )
                    .then(Commands.literal("forget")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestDebugCommand::suggestQuestIds),
                            QuestDebugCommand::forget
                        ))
                    )
                    .then(Commands.literal("forget_event")
                        .then(CommandTargets.executesWithTarget(
                            Commands.argument("eventId", StringArgumentType.word()),
                            QuestDebugCommand::forgetEvent
                        ))
                    )
                    .then(Commands.literal("debug")
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("prepare21"),
                            QuestDebugCommand::prepare21
                        ))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("prepare22"),
                            QuestDebugCommand::prepare22
                        ))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("prepare23"),
                            QuestDebugCommand::prepare23
                        ))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("prepare24"),
                            QuestDebugCommand::prepare24
                        ))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("prepare25"),
                            QuestDebugCommand::prepare25
                        ))
                        .then(CommandTargets.executesWithTarget(
                            Commands.literal("prepare26"),
                            QuestDebugCommand::prepare26
                        ))
                    )
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestQuestIds(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            QuestDataLoader.getAllQuestIds().stream()
                .sorted(Comparator.comparingInt(QuestDebugCommand::sortQuestId))
                .toList(),
            builder);
    }

    private static int sortQuestId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        var message = Component.translatable("stardewcraft.command.quest.status_header",
                com.stardew.craft.player.PlayerDisplayName.get(target));
        if (mgr.getQuestLog().isEmpty()) {
            message.append(Component.translatable("stardewcraft.command.quest.active_empty"));
        } else {
            message.append(Component.translatable("stardewcraft.command.quest.active_header"));
            for (StardewQuest quest : mgr.getQuestLog()) {
                message.append(Component.translatable(
                    "stardewcraft.command.quest.active_entry",
                    quest.getId(), quest.getTitleComponent(), quest.isCompleted(), quest.isDestroy()));
            }
        }
        String completed;
        if (mgr.getCompletedQuestIds().isEmpty()) {
            completed = "<empty>";
        } else {
            completed = mgr.getCompletedQuestIds().stream()
                .sorted(Comparator.comparingInt(QuestDebugCommand::sortQuestId))
                .toList().toString();
        }
        message.append(Component.translatable("stardewcraft.command.quest.completed", completed));
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        var snapshot = QuestDataLoader.snapshot();
        long errors = snapshot.diagnostics().stream()
            .filter(diagnostic -> diagnostic.severity()
                == com.stardew.craft.api.v1.content.DefinitionDiagnostic.Severity.ERROR)
            .count();
        context.getSource().sendSuccess(() -> Component.literal(
            "Quest definitions: v" + snapshot.version()
                + ", count=" + snapshot.definitions().size()
                + ", hash=" + snapshot.contentHash().substring(0, Math.min(12, snapshot.contentHash().length()))
                + ", errors=" + errors), false);
        for (var diagnostic : snapshot.diagnostics()) {
            context.getSource().sendSuccess(() -> Component.literal(
                diagnostic.severity() + " "
                    + (diagnostic.source() == null ? "<reload>" : diagnostic.source())
                    + ": " + diagnostic.message()), false);
        }
        return errors == 0 ? 1 : 0;
    }

    private static int grant(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager manager = QuestManager.of(target);
        if (manager == null) return fail(context, "stardewcraft.command.quest.no_data");
        if (!manager.debugGrantQuest(questId, target)) {
            return fail(context, "stardewcraft.command.quest.unknown", questId);
        }
        context.getSource().sendSuccess(() -> Component.literal(
            "Granted quest " + questId + " to "
                    + com.stardew.craft.player.PlayerDisplayName.get(target)), false);
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager manager = QuestManager.of(target);
        if (manager == null) return fail(context, "stardewcraft.command.quest.no_data");
        StardewQuest quest = manager.getQuest(questId);
        if (quest == null) return fail(context, "stardewcraft.command.quest.not_active", questId);
        String objectiveType = quest instanceof com.stardew.craft.quest.DataDrivenQuest dataDriven
            ? dataDriven.getObjectiveType().toString() : "legacy:" + quest.getQuestType();
        context.getSource().sendSuccess(() -> Component.literal(
            "Quest " + quest.getId()
                + " definition=" + quest.getDefinitionId()
                + " objective=" + objectiveType
                + " progress=" + quest.getCurrentObjectiveCount() + "/" + quest.getTotalObjectiveCount()
                + " accepted=" + quest.isAccepted()
                + " completed=" + quest.isCompleted()
                + " destroy=" + quest.isDestroy()), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager manager = QuestManager.of(target);
        if (manager == null) return fail(context, "stardewcraft.command.quest.no_data");
        manager.removeQuest(questId, target);
        manager.debugForgetCompletedQuest(questId, target);
        context.getSource().sendSuccess(() -> Component.literal(
            "Reset quest " + questId + " for "
                    + com.stardew.craft.player.PlayerDisplayName.get(target)), false);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");
        if (QuestDataLoader.createQuest(questId) == null) {
            return fail(context, "stardewcraft.command.quest.unknown", questId);
        }
        mgr.debugGrantQuest(questId, target);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.accepted", questId,
                com.stardew.craft.player.PlayerDisplayName.get(target)), false);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");
        StardewQuest quest = mgr.getQuest(questId);
        if (quest == null) return fail(context, "stardewcraft.command.quest.not_active", questId);
        quest.questComplete(target);
        mgr.cleanupDestroyed(target);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.completed_for", questId,
                com.stardew.craft.player.PlayerDisplayName.get(target)), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");
        mgr.removeQuest(questId, target);
        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.removed", questId,
                com.stardew.craft.player.PlayerDisplayName.get(target)), false);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String questId = StringArgumentType.getString(context, "questId");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");
        boolean removed = mgr.debugForgetCompletedQuest(questId, target);
        context.getSource().sendSuccess(() -> Component.translatable(
            removed ? "stardewcraft.command.quest.forgotten" : "stardewcraft.command.quest.no_completed_record",
            questId), false);
        return removed ? 1 : 0;
    }

    private static int forgetEvent(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        String eventId = StringArgumentType.getString(context, "eventId");
        boolean removed = forgetEvent(target, eventId);
        context.getSource().sendSuccess(() -> Component.translatable(
            removed ? "stardewcraft.command.quest.event_forgotten" : "stardewcraft.command.quest.no_event_record",
            eventId), false);
        return removed ? 1 : 0;
    }

    private static int prepare21(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        mgr.removeQuest("21", target);
        mgr.debugForgetCompletedQuest("21", target);
        forgetEvent(target, MARNIE_REQUEST_EVENT);
        forgetEvent(target, MARNIE_COMPLETE_EVENT);
        ensureFriendship(target, "marnie", 750);
        giveItem(target, "stardewcraft:cave_carrot", 1);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.prepare_21", MARNIE_REQUEST_EVENT), false);
        return 1;
    }

    private static int prepare22(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        mgr.removeQuest("22", target);
        mgr.debugForgetCompletedQuest("22", target);
        forgetEvent(target, JODI_INVITE_EVENT);
        forgetEvent(target, JODI_COMPLETE_EVENT);
        ensureFriendship(target, "jodi", 1000);
        giveItem(target, "stardewcraft:largemouth_bass", 1);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.prepare_22", JODI_INVITE_EVENT), false);
        return 1;
    }

    private static int prepare23(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        resetMuseumQuestState(target, mgr);
        giveItem(target, "stardewcraft:chipped_amphora", 1);
        MuseumQuestService.onItemReceived(target, "stardewcraft:chipped_amphora");

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.prepare_23"), false);
        return 1;
    }

    private static int prepare24(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        resetMuseumQuestState(target, mgr);
        EventSeenData seenData = EventSeenData.get(target.serverLevel());
        seenData.markSeen(target.getUUID(), MUSEUM_INTRO_EVENT);
        syncSeenEvents(target, seenData);
        com.stardew.craft.player.PlayerDataManager.getPlayerData(target)
            .addMailFlag(MuseumQuestService.FIRST_ARTIFACT_FLAG);
        mgr.acceptQuest("24", target);
        giveItem(target, "stardewcraft:chipped_amphora", 1);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.prepare_24"), false);
        return 1;
    }

    private static int prepare25(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        mgr.removeQuest("25", target);
        mgr.debugForgetCompletedQuest("25", target);
        mgr.acceptQuest("25", target);
        giveItem(target, "stardewcraft:parsnip", 1);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.prepare_25"), false);
        return 1;
    }

    private static int prepare26(CommandContext<CommandSourceStack> context) {
        ServerPlayer target = CommandTargets.resolve(context);
        if (target == null) return fail(context, "stardewcraft.command.target_required");
        QuestManager mgr = QuestManager.of(target);
        if (mgr == null) return fail(context, "stardewcraft.command.quest.no_data");

        mgr.removeQuest("26", target);
        mgr.debugForgetCompletedQuest("26", target);
        mgr.acceptQuest("26", target);
        var playerData = com.stardew.craft.player.PlayerDataManager.getPlayerData(target);
        playerData.removeMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.SEEN_JUNIMO_NOTE);
        playerData.addMailFlag(com.stardew.craft.communitycenter.state.CCStoryFlags.CC_DOOR_UNLOCKED);
        com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(target, playerData);

        context.getSource().sendSuccess(() -> Component.translatable(
            "stardewcraft.command.quest.prepare_26"), false);
        return 1;
    }

    private static void resetMuseumQuestState(ServerPlayer target, QuestManager mgr) {
        mgr.removeQuest("23", target);
        mgr.removeQuest("24", target);
        mgr.debugForgetCompletedQuest("23", target);
        mgr.debugForgetCompletedQuest("24", target);
        forgetEvent(target, MUSEUM_INTRO_EVENT);
        com.stardew.craft.player.PlayerDataManager.getPlayerData(target)
            .removeMailFlag(MuseumQuestService.FIRST_ARTIFACT_FLAG);

        MuseumDonationData museum = MuseumDonationData.get(target.serverLevel());
        museum.debugResetPlayer(target.getUUID());
        PacketDistributor.sendToPlayer(target,
            new MuseumDonationSyncPacket(java.util.List.copyOf(museum.getDonatedItems(target.getUUID()))));
        com.stardew.craft.block.utility.MuseumExhibitStandBlock.syncStands(target.serverLevel(), museum, target);
    }

    private static boolean forgetEvent(ServerPlayer target, String eventId) {
        EventSeenData data = EventSeenData.get(target.serverLevel());
        boolean removed = data.clearSeen(target.getUUID(), eventId);
        syncSeenEvents(target, data);
        return removed;
    }

    private static void syncSeenEvents(ServerPlayer target, EventSeenData data) {
        PacketDistributor.sendToPlayer(target,
            new SyncEventSeenPayload(new ArrayList<>(data.getSeenEvents(target.getUUID()))));
    }

    private static void ensureFriendship(ServerPlayer target, String npcId, int minPoints) {
        var manager = NpcFriendshipDataManager.get(target.serverLevel());
        var state = manager.getOrCreate(target.getUUID(), npcId);
        int current = Math.max(0, state.points());
        if (current < minPoints) {
            state.addPoints(minPoints - current, NpcInteractionService.getMaxFriendshipPointsFor(npcId));
            manager.setDirty();
        }
        syncFriendship(target, npcId, state);
    }

    private static void syncFriendship(ServerPlayer target, String npcId, NpcFriendshipDataManager.FriendshipState state) {
        int points = Math.max(0, state.points());
        int hearts = Math.max(0, Math.min(14, points / 250));
        PacketDistributor.sendToPlayer(target, new SyncNpcFriendshipStatusPayload(
            npcId,
            points,
            hearts,
            Math.max(0, Math.min(2, state.giftsThisWeek())),
            false,
            false
        ));
    }

    private static void giveItem(ServerPlayer target, String itemId, int count) {
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == Items.AIR) return;
            ItemStack stack = new ItemStack(item, count);
            if (!target.getInventory().add(stack)) {
                target.drop(stack, false);
            }
        } catch (Exception ignored) {
        }
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }
}
