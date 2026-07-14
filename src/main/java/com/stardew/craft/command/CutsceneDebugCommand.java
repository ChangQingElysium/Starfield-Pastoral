package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.cutscene.network.CutsceneAnchorPayload;
import com.stardew.craft.cutscene.network.SyncEventSeenPayload;
import com.stardew.craft.cutscene.server.WakeUpEventScheduler;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.commands.SharedSuggestionProvider;
import com.stardew.craft.cutscene.server.EventSeenData;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

/**
 * Debug commands for the cutscene/event system.
 * /stardew event play &lt;id&gt;  — trigger an event by id
 * /stardew event list       — list all loaded events
 * /stardew event reset      — clear eventsSeen for the player
 */
@SuppressWarnings("null")
public final class CutsceneDebugCommand {

    private CutsceneDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("event")
                    .then(Commands.literal("play")
                        .then(Commands.argument("eventId", StringArgumentType.word())
                            .suggests(CutsceneDebugCommand::suggestEventIds)
                            .executes(CutsceneDebugCommand::playEvent)
                        )
                    )
                    .then(Commands.literal("list")
                        .executes(CutsceneDebugCommand::listEvents)
                    )
                    .then(Commands.literal("reset")
                        .executes(CutsceneDebugCommand::resetSeen)
                    )
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestEventIds(CommandContext<CommandSourceStack> context,
                                                                  SuggestionsBuilder builder) {
        Collection<EventData> events = EventRegistry.all();
        if (!events.isEmpty()) {
            return SharedSuggestionProvider.suggest(
                events.stream()
                    .map(EventData::id)
                    .sorted(Comparator.naturalOrder())
                    .toList(),
                builder);
        }
        return SharedSuggestionProvider.suggest(
            EventRegistry.getRawJsonMap().keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList(),
            builder);
    }

    private static int playEvent(CommandContext<CommandSourceStack> context) {
        String eventId = StringArgumentType.getString(context, "eventId");
        EventData data = EventRegistry.getById(eventId);
        if (data == null) {
            context.getSource().sendFailure(Component.translatable("stardewcraft.command.event.not_found", eventId));
            return 0;
        }

        // Send the event data to the client for playback
        // For now, use a simple trigger payload approach:
        // The client will need to fetch the event from its own registry copy
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            if (data.trigger() != null && "wake_up".equals(data.trigger().type())) {
                BlockPos spawn = FarmInstanceRegistry.get().getFarmSpawnPoint(player.getUUID());
                if (spawn != null) {
                    PacketDistributor.sendToPlayer(player,
                            new CutsceneAnchorPayload(WakeUpEventScheduler.FARM_SPAWN_ANCHOR,
                                    spawn.getX() + 0.5,
                                    spawn.getY(),
                                    spawn.getZ() + 0.5));
                }
            }
            // Send a trigger packet (and mark server-side cutscene active)
            com.stardew.craft.cutscene.server.ServerCutsceneTracker.startEvent(player, eventId);
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.event.triggered", eventId), false);
            return 1;
        }

        context.getSource().sendFailure(Component.translatable("stardewcraft.command.player_required"));
        return 0;
    }

    private static int listEvents(CommandContext<CommandSourceStack> context) {
        var allEvents = EventRegistry.all();
        if (allEvents.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.event.none_loaded"), false);
            return 0;
        }

        net.minecraft.network.chat.MutableComponent message = Component.translatable(
                "stardewcraft.command.event.list_header", allEvents.size());
        for (EventData event : allEvents) {
            message.append(Component.literal("\n  - ")).append(Component.translatable(
                    "stardewcraft.command.event.list_entry", event.id(), event.trigger().type(),
                    event.rawCommands().size()));
        }
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int resetSeen(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            EventSeenData data = EventSeenData.get(player.serverLevel());
            data.clearSeen(player.getUUID());
            PacketDistributor.sendToPlayer(player, new SyncEventSeenPayload(new ArrayList<>()));
            context.getSource().sendSuccess(() -> Component.translatable("stardewcraft.command.event.reset"), false);
            return 1;
        }
        return 0;
    }
}
