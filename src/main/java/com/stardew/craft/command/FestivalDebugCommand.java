package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.ActiveFestivalHandler;
import com.stardew.craft.festival.ActiveFestivalHandlers;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalMapOverlayDefinition;
import com.stardew.craft.festival.FestivalMapOverlayManager;
import com.stardew.craft.festival.FestivalMapOverlayRegistry;
import com.stardew.craft.festival.FestivalMapOverlayState;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.festival.FestivalSessionPhase;
import com.stardew.craft.festival.FestivalType;
import com.stardew.craft.festival.FestivalWorldData;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.festival.nightmarket.NightMarketNpcVisitService;
import com.stardew.craft.festival.squid.SquidFestService;
import com.stardew.craft.festival.trout.TroutDerbyService;
import com.stardew.craft.npc.runtime.NpcScheduleRuntimeService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("null")
public final class FestivalDebugCommand {
    private FestivalDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("festival")
                .then(Commands.literal("apply")
                    .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(festivalIdSuggestions(), builder))
                        .executes(FestivalDebugCommand::applyFestival)))
                .then(Commands.literal("restore")
                    .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(festivalIdSuggestions(), builder))
                        .executes(FestivalDebugCommand::restoreFestival)))
                .then(Commands.literal("overlay")
                    .then(Commands.literal("apply")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(idSuggestions(), builder))
                            .executes(FestivalDebugCommand::applyOverlay)))
                    .then(Commands.literal("restore")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(idSuggestions(), builder))
                            .executes(FestivalDebugCommand::restoreOverlay)))
                    .then(Commands.literal("status")
                        .executes(FestivalDebugCommand::statusAll)
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(idSuggestions(), builder))
                            .executes(FestivalDebugCommand::statusOne))))
                .then(Commands.literal("desert")
                    .then(Commands.literal("egg")
                        .then(Commands.literal("give")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(FestivalDebugCommand::giveCalicoEggs)))
                        .then(Commands.literal("clear")
                            .executes(FestivalDebugCommand::clearCalicoEggs))
                        .then(Commands.literal("count")
                            .executes(FestivalDebugCommand::countCalicoEggs)))
                    .then(Commands.literal("shop")
                        .executes(FestivalDebugCommand::openDesertEggShop)))
                .then(Commands.literal("npc")
                    .then(Commands.literal("apply")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ActiveFestivalHandlers.festivalIds(), builder))
                            .executes(FestivalDebugCommand::applyNpcs)))
                    .then(Commands.literal("restore")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ActiveFestivalHandlers.festivalIds(), builder))
                            .executes(FestivalDebugCommand::restoreNpcs)))
                    .then(Commands.literal("status")
                        .executes(FestivalDebugCommand::npcStatus)))
                .then(Commands.literal("main")
                    .then(Commands.literal("start")
                        .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ActiveFestivalHandlers.mainEventFestivalIds(), builder))
                            .executes(FestivalDebugCommand::startMainEvent)))
                    .then(Commands.literal("status")
                        .executes(FestivalDebugCommand::mainEventStatus)))));
    }

    private static int applyFestival(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }

        FestivalDefinition festival = FestivalRegistry.get(id).orElse(null);
        if (festival == null) {
            context.getSource().sendFailure(Component.literal("未注册节日: " + id));
            return 0;
        }

        if (festival.type() == FestivalType.PASSIVE) {
            return applyPassiveFestival(context, level, festival);
        }

        ActiveFestivalHandler handler = ActiveFestivalHandlers.get(festival).orElse(null);
        if (handler == null) {
            context.getSource().sendFailure(Component.literal("当前主动节日未注册调试 handler: " + festival.id()));
            return 0;
        }

        StardewTimeManager time = StardewTimeManager.get();
        boolean overlayStarted = FestivalMapOverlayManager.beginApply(
            level,
            festival,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        if (!overlayStarted) {
            context.getSource().sendFailure(Component.literal("地图 overlay 启动失败: " + festival.mapOverlayId()));
            return 0;
        }

        handler.startDebugFestival(level);
        context.getSource().sendSuccess(() -> Component.literal(handler.debugApplyMessage()), true);
        return 1;
    }

    private static int restoreFestival(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }

        FestivalDefinition festival = FestivalRegistry.get(id).orElse(null);
        if (festival == null) {
            context.getSource().sendFailure(Component.literal("未注册节日: " + id));
            return 0;
        }

        if (festival.type() == FestivalType.PASSIVE) {
            FestivalService.clearDebugPassiveFestival(festival.id());
            boolean overlayRestoreStarted = festival.mapOverlayId().isBlank()
                ? false
                : FestivalMapOverlayManager.beginRestore(level, festival.mapOverlayId());
            FestivalWorldData data = FestivalWorldData.get(level);
            data.getSession(festival.id()).ifPresent(session -> session.setPhase(FestivalSessionPhase.CLOSED));
            data.setActivePassiveFestivalIds(FestivalService.getActivePassiveFestivalsToday().stream().map(FestivalDefinition::id).toList());
            refreshFestivalSchedules(level);
            context.getSource().sendSuccess(() -> Component.literal(overlayRestoreStarted
                ? "已恢复 passive festival 总调试: " + festival.id() + "，地图 overlay 正在恢复，NPC 日程已刷新"
                : "已恢复 passive festival 总调试: " + festival.id() + "，地图 overlay 当前无需恢复，NPC 日程已刷新"), true);
            return 1;
        }

        ActiveFestivalHandler handler = ActiveFestivalHandlers.get(festival).orElse(null);
        if (handler == null) {
            context.getSource().sendFailure(Component.literal("当前主动节日未注册调试 handler: " + festival.id()));
            return 0;
        }

        handler.restoreDebugFestival(level);
        boolean overlayRestoreStarted = FestivalMapOverlayManager.beginRestore(level, festival.mapOverlayId());
        context.getSource().sendSuccess(() -> Component.literal(overlayRestoreStarted
            ? "已恢复主动节日总调试: " + festival.id() + "，玩家状态已恢复，地图 overlay 正在恢复"
            : "已恢复主动节日总调试: " + festival.id() + "，玩家状态已恢复，地图 overlay 当前无需恢复或尚未应用"), true);
        return 1;
    }

    private static int applyPassiveFestival(CommandContext<CommandSourceStack> context, ServerLevel level, FestivalDefinition festival) {
        StardewTimeManager time = StardewTimeManager.get();
        FestivalService.setDebugPassiveFestival(festival.id());

        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalSessionPhase phase = FestivalSessionPhase.OPEN;
        if (!festival.mapOverlayId().isBlank()) {
            boolean overlayStarted = FestivalMapOverlayManager.beginApply(
                level,
                festival,
                time.getCurrentYear(),
                time.getCurrentSeason(),
                time.getCurrentDay()
            );
            if (!overlayStarted) {
                context.getSource().sendFailure(Component.literal("地图 overlay 启动失败: " + festival.mapOverlayId()));
                return 0;
            }
            phase = FestivalMapOverlayManager.isApplied(level, festival.mapOverlayId())
                ? FestivalSessionPhase.OPEN
                : FestivalSessionPhase.PREPARING_MAP;
        }
        data.getOrCreateSession(festival, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay()).setPhase(phase);
        data.setActivePassiveFestivalIds(FestivalService.getActivePassiveFestivalsToday().stream().map(FestivalDefinition::id).toList());
        int forcedNpcs = forcePassiveFestivalNpcs(level, festival);
        context.getSource().sendSuccess(() -> Component.literal(
            "已启动 passive festival 总调试: " + festival.id() + "，overlay " + (festival.mapOverlayId().isBlank() ? "无" : "应用中") + "，NPC 日程已刷新，已同步 NPC " + forcedNpcs), true);
        return 1;
    }

    private static int forcePassiveFestivalNpcs(ServerLevel level, FestivalDefinition festival) {
        if (DesertFestivalService.FESTIVAL_ID.equalsIgnoreCase(festival.id())) {
            return DesertFestivalService.forceRefreshNpcSchedules(level);
        }
        if (TroutDerbyService.FESTIVAL_ID.equalsIgnoreCase(festival.id())) {
            return TroutDerbyService.forceRefreshNpcSchedules(level);
        }
        if (SquidFestService.FESTIVAL_ID.equalsIgnoreCase(festival.id())) {
            return SquidFestService.forceRefreshNpcSchedules(level);
        }
        if ("NightMarket".equalsIgnoreCase(festival.id())) {
            return NightMarketNpcVisitService.forceRefreshNpcSchedules(level);
        }
        refreshFestivalSchedules(level);
        return 0;
    }

    private static int applyOverlay(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Optional<FestivalDefinition> festivalOpt = resolveFestival(id);
        if (festivalOpt.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未找到带地图 overlay 的节日或 overlay: " + id));
            return 0;
        }

        FestivalDefinition festival = festivalOpt.get();
        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }

        StardewTimeManager time = StardewTimeManager.get();
        boolean started = FestivalMapOverlayManager.beginApply(
            level,
            festival,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        if (!started) {
            context.getSource().sendFailure(Component.literal("地图 overlay 启动失败: " + festival.mapOverlayId()));
            return 0;
        }

        ActiveFestivalHandlers.get(festival).ifPresent(handler -> handler.requestDebugNpcs(level));

        context.getSource().sendSuccess(() -> Component.literal("已开始应用节日地图 overlay: " + festival.mapOverlayId()), true);
        return 1;
    }

    private static int restoreOverlay(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Optional<String> overlayId = resolveOverlayId(id);
        if (overlayId.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未找到 overlay: " + id));
            return 0;
        }

        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }

        if (!FestivalMapOverlayManager.beginRestore(level, overlayId.get())) {
            context.getSource().sendFailure(Component.literal("地图 overlay 无法恢复，可能尚未应用: " + overlayId.get()));
            return 0;
        }

        ActiveFestivalHandlers.restoreNpcsForOverlay(level, overlayId.get());

        context.getSource().sendSuccess(() -> Component.literal("已开始恢复节日地图 overlay: " + overlayId.get()), true);
        return 1;
    }

    private static int applyNpcs(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ActiveFestivalHandler handler = ActiveFestivalHandlers.get(id).orElse(null);
        if (handler == null) {
            context.getSource().sendFailure(Component.literal("当前主动节日未注册 NPC 调试 handler: " + id));
            return 0;
        }
        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }
        handler.requestDebugNpcs(level);
        context.getSource().sendSuccess(() -> Component.literal("已请求 " + handler.displayName() + " NPC 进入节日点位"), true);
        return 1;
    }

    private static int giveCalicoEggs(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("该命令需要由玩家执行"));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        DesertFestivalService.giveEggs(player, count);
        context.getSource().sendSuccess(() -> Component.literal("已给予 Calico Egg: " + count), true);
        return 1;
    }

    private static int clearCalicoEggs(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("该命令需要由玩家执行"));
            return 0;
        }
        int removed = DesertFestivalService.clearEggs(player);
        context.getSource().sendSuccess(() -> Component.literal("已清理 Calico Egg: " + removed), true);
        return 1;
    }

    private static int countCalicoEggs(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("该命令需要由玩家执行"));
            return 0;
        }
        int count = DesertFestivalService.countEggs(player);
        context.getSource().sendSuccess(() -> Component.literal("Calico Egg: " + count), false);
        return 1;
    }

    private static int openDesertEggShop(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("该命令需要由玩家执行"));
            return 0;
        }
        if (!DesertFestivalService.openEggShop(player)) {
            context.getSource().sendFailure(Component.literal("DesertFestival_EggShop 未注册"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已打开 DesertFestival EggShop"), false);
        return 1;
    }

    private static int restoreNpcs(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ActiveFestivalHandler handler = ActiveFestivalHandlers.get(id).orElse(null);
        if (handler == null) {
            context.getSource().sendFailure(Component.literal("当前主动节日未注册 NPC 调试 handler: " + id));
            return 0;
        }
        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }
        handler.restoreDebugNpcs(level);
        context.getSource().sendSuccess(() -> Component.literal("已恢复 " + handler.displayName() + " NPC 到当前日程"), true);
        return 1;
    }

    private static int npcStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel level = stardewLevel(context.getSource());
        context.getSource().sendSuccess(() -> Component.literal(ActiveFestivalHandlers.debugNpcStatus(level)), false);
        return 1;
    }

    private static int startMainEvent(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        ActiveFestivalHandler handler = ActiveFestivalHandlers.get(id).orElse(null);
        if (handler == null || !handler.supportsMainEventDebug()) {
            context.getSource().sendFailure(Component.literal("当前主动节日未注册主事件调试 handler: " + id));
            return 0;
        }
        if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("该命令需要由玩家执行"));
            return 0;
        }
        if (!handler.tryStartMainEvent(player)) {
            context.getSource().sendFailure(Component.literal("需要先进入 " + handler.displayName() + " 会场"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已请求 " + handler.displayName() + " 主事件"), true);
        return 1;
    }

    private static int mainEventStatus(CommandContext<CommandSourceStack> context) {
        ServerLevel level = stardewLevel(context.getSource());
        context.getSource().sendSuccess(() -> Component.literal(ActiveFestivalHandlers.debugMainEventStatus(level)), false);
        return 1;
    }

    private static int statusAll(CommandContext<CommandSourceStack> context) {
        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }

        FestivalWorldData data = FestivalWorldData.get(level);
        StringBuilder message = new StringBuilder("节日地图 overlay 状态:");
        for (FestivalMapOverlayDefinition definition : FestivalMapOverlayRegistry.all()) {
            FestivalMapOverlayState state = data.getOverlayState(definition.overlayId()).orElse(null);
            message.append("\n- ").append(definition.overlayId()).append(": ")
                .append(state == null ? "NONE" : state.phase().name());
        }
        context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int statusOne(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Optional<String> overlayId = resolveOverlayId(id);
        if (overlayId.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未找到 overlay: " + id));
            return 0;
        }

        ServerLevel level = stardewLevel(context.getSource());
        if (level == null) {
            context.getSource().sendFailure(Component.literal("Stardew Valley 维度尚未加载"));
            return 0;
        }

        FestivalMapOverlayState state = FestivalWorldData.get(level).getOverlayState(overlayId.get()).orElse(null);
        String status = state == null ? "NONE" : state.phase().name() + " cursor=" + state.cursor();
        context.getSource().sendSuccess(() -> Component.literal(overlayId.get() + ": " + status), false);
        return 1;
    }

    private static ServerLevel stardewLevel(CommandSourceStack source) {
        return source.getServer().getLevel(ModDimensions.STARDEW_VALLEY);
    }

    private static Optional<FestivalDefinition> resolveFestival(String id) {
        Optional<FestivalDefinition> byFestivalId = FestivalRegistry.get(id)
            .filter(definition -> !definition.mapOverlayId().isBlank());
        return byFestivalId.isPresent() ? byFestivalId : FestivalRegistry.getByOverlayId(id);
    }

    private static Optional<String> resolveOverlayId(String id) {
        Optional<String> direct = FestivalMapOverlayRegistry.get(id).map(FestivalMapOverlayDefinition::overlayId);
        return direct.isPresent() ? direct : resolveFestival(id).map(FestivalDefinition::mapOverlayId);
    }

    private static Iterable<String> idSuggestions() {
        return Stream.concat(
                FestivalMapOverlayRegistry.all().stream().map(FestivalMapOverlayDefinition::overlayId),
                FestivalRegistry.all().stream()
                    .filter(definition -> !definition.mapOverlayId().isBlank())
                    .map(FestivalDefinition::id)
            )
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private static Iterable<String> festivalIdSuggestions() {
        return FestivalRegistry.all().stream()
            .map(FestivalDefinition::id)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private static void refreshFestivalSchedules(ServerLevel level) {
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);
    }
}
