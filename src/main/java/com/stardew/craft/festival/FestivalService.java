package com.stardew.craft.festival;

import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FestivalService {
    private static String debugActiveFestivalId;
    private static String debugPassiveFestivalId;
    private static String lastActiveFestivalStartMessageKey;
    private static final Set<String> lastPassiveFestivalStartMessageKeys = new HashSet<>();

    private FestivalService() {
    }

    public static boolean isFestivalDay() {
        return getActiveFestivalToday().isPresent();
    }

    public static boolean isFestivalDay(int day, int season) {
        return getActiveFestivalForDate(day, season).isPresent();
    }

    public static Optional<FestivalDefinition> getActiveFestivalToday() {
        Optional<FestivalDefinition> debugFestival = getDebugActiveFestival();
        if (debugFestival.isPresent()) {
            return debugFestival;
        }
        if (getDebugPassiveFestival().isPresent()) {
            return Optional.empty();
        }
        StardewTimeManager time = StardewTimeManager.get();
        return getActiveFestivalForDate(time.getCurrentDay(), time.getCurrentSeason());
    }

    public static void setDebugActiveFestival(String festivalId) {
        debugActiveFestivalId = festivalId == null || festivalId.isBlank() ? null : festivalId.trim();
        if (debugActiveFestivalId != null) {
            debugPassiveFestivalId = null;
        }
    }

    public static void setDebugPassiveFestival(String festivalId) {
        debugPassiveFestivalId = festivalId == null || festivalId.isBlank() ? null : festivalId.trim();
        if (debugPassiveFestivalId != null) {
            debugActiveFestivalId = null;
        }
    }

    public static void clearDebugActiveFestival(String festivalId) {
        if (festivalId == null || festivalId.isBlank()) {
            debugActiveFestivalId = null;
            return;
        }
        if (debugActiveFestivalId != null && debugActiveFestivalId.equalsIgnoreCase(festivalId.trim())) {
            debugActiveFestivalId = null;
        }
    }

    public static void clearDebugPassiveFestival(String festivalId) {
        if (festivalId == null || festivalId.isBlank()) {
            debugPassiveFestivalId = null;
            return;
        }
        if (debugPassiveFestivalId != null && debugPassiveFestivalId.equalsIgnoreCase(festivalId.trim())) {
            debugPassiveFestivalId = null;
        }
    }

    public static boolean isDebugActiveFestival(String festivalId) {
        return festivalId != null && debugActiveFestivalId != null && debugActiveFestivalId.equalsIgnoreCase(festivalId.trim());
    }

    public static boolean isDebugPassiveFestival(String festivalId) {
        return festivalId != null && debugPassiveFestivalId != null && debugPassiveFestivalId.equalsIgnoreCase(festivalId.trim());
    }

    private static Optional<FestivalDefinition> getDebugActiveFestival() {
        if (debugActiveFestivalId == null || debugActiveFestivalId.isBlank()) {
            return Optional.empty();
        }
        return FestivalRegistry.get(debugActiveFestivalId)
            .filter(definition -> definition.type() == FestivalType.ACTIVE);
    }

    private static Optional<FestivalDefinition> getDebugPassiveFestival() {
        if (debugPassiveFestivalId == null || debugPassiveFestivalId.isBlank()) {
            return Optional.empty();
        }
        return FestivalRegistry.get(debugPassiveFestivalId)
            .filter(definition -> definition.type() == FestivalType.PASSIVE);
    }

    public static Optional<FestivalDefinition> getActiveFestivalForDate(int day, int season) {
        return FestivalRegistry.activeFestivals().stream()
            .filter(definition -> definition.isDate(season, day))
            .findFirst();
    }

    public static List<FestivalDefinition> getActivePassiveFestivalsToday() {
        StardewTimeManager time = StardewTimeManager.get();
        int day = time.getCurrentDay();
        int season = time.getCurrentSeason();
        List<FestivalDefinition> passiveToday = FestivalRegistry.passiveFestivals().stream()
            .filter(definition -> definition.isDate(season, day))
            .filter(FestivalService::conditionsPass)
            .toList();
        Optional<FestivalDefinition> debugFestival = getDebugPassiveFestival();
        if (debugFestival.isEmpty() || passiveToday.stream().anyMatch(definition -> definition.id().equalsIgnoreCase(debugFestival.get().id()))) {
            return passiveToday;
        }
        return Stream.concat(passiveToday.stream(), debugFestival.stream()).toList();
    }

    public static void onNewDay(ServerLevel level) {
        if (level == null) {
            return;
        }
        StardewTimeManager time = StardewTimeManager.get();
        FestivalWorldData data = FestivalWorldData.get(level);
        cleanupStalePassiveFestivalSessions(level, data, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
        restoreStaleOverlays(level, data, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
        data.closeStaleSessions(time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
        lastActiveFestivalStartMessageKey = null;
        lastPassiveFestivalStartMessageKeys.clear();

        getActiveFestivalToday().ifPresent(definition -> {
            FestivalSessionState session = data.getOrCreateSession(definition, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
            if (session.phase() == FestivalSessionPhase.SCHEDULED && !requiresUnregisteredOverlay(definition)) {
                boolean overlayStarted = FestivalMapOverlayManager.beginApply(
                    level,
                    definition,
                    time.getCurrentYear(),
                    time.getCurrentSeason(),
                    time.getCurrentDay()
                );
                if (overlayStarted) {
                    session.setPhase(FestivalSessionPhase.PREPARING_MAP);
                    data.setDirty();
                }
            }
        });

        List<FestivalDefinition> passiveToday = getActivePassiveFestivalsToday();
        data.setActivePassiveFestivalIds(passiveToday.stream().map(FestivalDefinition::id).collect(Collectors.toList()));
        for (FestivalDefinition definition : passiveToday) {
            FestivalSessionState session = data.getOrCreateSession(definition, time.getCurrentYear(), time.getCurrentSeason(), time.getCurrentDay());
            PassiveFestivalHandlers.onNewDay(level, definition, session);
        }
    }

    public static void onTimeChanged(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel stardewLevel = server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return;
        }
        FestivalWorldData data = FestivalWorldData.get(stardewLevel);
        StardewTimeManager time = StardewTimeManager.get();
        broadcastActiveFestivalStartIfDue(server, stardewLevel, data, time);
        advancePassiveFestivalLifecycle(stardewLevel, data, time);
    }

    private static void broadcastActiveFestivalStartIfDue(MinecraftServer server, ServerLevel level, FestivalWorldData data, StardewTimeManager time) {
        Optional<FestivalDefinition> definitionOpt = getActiveFestivalToday();
        if (definitionOpt.isEmpty()) {
            return;
        }
        FestivalDefinition definition = definitionOpt.get();
        if (isDebugActiveFestival(definition.id())) {
            return;
        }
        String translationKey = definition.startMessageKey();
        if (translationKey.isBlank()) {
            return;
        }
        int now = currentTimeOfDay();
        if (now < definition.startTime() || now >= definition.endTime()) {
            return;
        }
        FestivalSessionState session = ensureActiveFestivalPrepared(level, data, definition, time).orElse(null);
        if (session == null || session.phase() != FestivalSessionPhase.OPEN) {
            return;
        }
        String messageKey = time.getCurrentYear() + ":" + time.getCurrentSeason() + ":" + time.getCurrentDay() + ":" + definition.id();
        if (messageKey.equals(lastActiveFestivalStartMessageKey)) {
            return;
        }
        lastActiveFestivalStartMessageKey = messageKey;
        Component message = Component.translatable(translationKey);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, false);
        }
    }

    private static void broadcastPassiveFestivalStartIfDue(MinecraftServer server, FestivalDefinition definition, FestivalSessionState session) {
        if (server == null || definition == null || session == null || definition.type() != FestivalType.PASSIVE) {
            return;
        }
        if (definition.onlyShowStartMessageOnFirstDay()
            && definition.dayOfFestival(session.season(), session.day()) != 1) {
            return;
        }
        String translationKey = definition.startMessageKey();
        if (translationKey.isBlank()) {
            return;
        }
        String messageKey = session.year() + ":" + session.season() + ":" + session.day() + ":" + definition.id();
        if (!lastPassiveFestivalStartMessageKeys.add(messageKey)) {
            return;
        }
        Component message = Component.translatable(translationKey);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, false);
        }
    }

    public static List<String> activeFestivalAnnouncementMailIdsForDate(int season, int day) {
        String mailId = calendarMailId(season, day);
        if (mailId.isBlank()) {
            return List.of();
        }
        return FestivalRegistry.activeFestivals().stream()
            .map(FestivalDefinition::announcementMailId)
            .filter(id -> !id.isBlank() && id.equals(mailId))
            .toList();
    }

    private static String calendarMailId(int season, int day) {
        String seasonName = switch (season) {
            case FestivalRegistry.SPRING -> "spring";
            case FestivalRegistry.SUMMER -> "summer";
            case FestivalRegistry.FALL -> "fall";
            case FestivalRegistry.WINTER -> "winter";
            default -> "";
        };
        return seasonName.isBlank() || day < 1 || day > 28 ? "" : seasonName + "_" + day;
    }

    public static boolean isActiveFestivalEntryOpen(String festivalId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel stardewLevel = server == null ? null : server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        return isActiveFestivalEntryOpen(stardewLevel, festivalId);
    }

    public static boolean isActiveFestivalEntryOpen(ServerLevel level, String festivalId) {
        if (isDebugActiveFestival(festivalId)) {
            return true;
        }
        if (level == null) {
            return false;
        }
        Optional<FestivalDefinition> definitionOpt = getActiveFestivalToday()
            .filter(definition -> definition.id().equalsIgnoreCase(festivalId))
            .filter(definition -> {
                int now = currentTimeOfDay();
                return now >= definition.startTime() && now < definition.endTime();
            });
        if (definitionOpt.isEmpty()) {
            return false;
        }
        FestivalDefinition definition = definitionOpt.get();
        StardewTimeManager time = StardewTimeManager.get();
        FestivalSessionState session = ensureActiveFestivalPrepared(level, FestivalWorldData.get(level), definition, time).orElse(null);
        return session != null && session.phase() == FestivalSessionPhase.OPEN;
    }

    public static boolean isActiveFestivalEntryClosedForToday(ServerLevel level, String festivalId) {
        if (isDebugActiveFestival(festivalId) || level == null || festivalId == null || festivalId.isBlank()) {
            return false;
        }
        Optional<FestivalDefinition> definitionOpt = getActiveFestivalToday()
            .filter(definition -> definition.id().equalsIgnoreCase(festivalId));
        if (definitionOpt.isEmpty()) {
            return false;
        }
        FestivalDefinition definition = definitionOpt.get();
        if (currentTimeOfDay() >= definition.endTime()) {
            return true;
        }
        StardewTimeManager time = StardewTimeManager.get();
        return FestivalWorldData.get(level).getSession(definition.id())
            .filter(session -> session.year() == time.getCurrentYear()
                && session.season() == time.getCurrentSeason()
                && session.day() == time.getCurrentDay())
            .map(session -> isTerminalSessionPhase(session.phase()))
            .orElse(false);
    }

    public static Optional<FestivalSessionState> openActiveFestival(ServerPlayer player, String festivalId) {
        if (player == null || !isActiveFestivalEntryOpen(player.serverLevel(), festivalId)) {
            return Optional.empty();
        }
        Optional<FestivalDefinition> definitionOpt = getActiveFestivalToday()
            .filter(definition -> definition.id().equalsIgnoreCase(festivalId));
        if (definitionOpt.isEmpty()) {
            return Optional.empty();
        }

        FestivalDefinition definition = definitionOpt.get();
        if (requiresUnregisteredOverlay(definition)) {
            return Optional.empty();
        }
        StardewTimeManager time = StardewTimeManager.get();
        ServerLevel level = player.serverLevel();
        FestivalWorldData data = FestivalWorldData.get(level);
        FestivalSessionState session = data.getOrCreateSession(
            definition,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        if (session.phase() == FestivalSessionPhase.PREPARING_MAP || isTerminalSessionPhase(session.phase())) {
            return Optional.empty();
        }
        if (definition.mapOverlayId().isBlank()) {
            session.setPhase(FestivalSessionPhase.OPEN);
        }
        if (session.phase() == FestivalSessionPhase.SCHEDULED) {
            boolean overlayStarted = FestivalMapOverlayManager.beginApply(
                level,
                definition,
                time.getCurrentYear(),
                time.getCurrentSeason(),
                time.getCurrentDay()
            );
            if (!overlayStarted && !definition.mapOverlayId().isBlank()) {
                return Optional.empty();
            }
            session.setPhase(overlayStarted ? FestivalSessionPhase.PREPARING_MAP : FestivalSessionPhase.OPEN);
        }
        session.addParticipant(player.getUUID());
        data.setDirty();
        return Optional.of(session);
    }

    private static Optional<FestivalSessionState> ensureActiveFestivalPrepared(ServerLevel level, FestivalWorldData data,
                                                                              FestivalDefinition definition, StardewTimeManager time) {
        if (level == null || data == null || definition == null || time == null || definition.type() != FestivalType.ACTIVE) {
            return Optional.empty();
        }
        FestivalSessionState session = data.getOrCreateSession(
            definition,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        if (isTerminalSessionPhase(session.phase()) || requiresUnregisteredOverlay(definition)) {
            return Optional.of(session);
        }
        if (session.phase() == FestivalSessionPhase.SCHEDULED) {
            if (definition.mapOverlayId().isBlank()) {
                session.setPhase(FestivalSessionPhase.OPEN);
                data.setDirty();
            } else {
                boolean overlayStarted = FestivalMapOverlayManager.beginApply(
                    level,
                    definition,
                    time.getCurrentYear(),
                    time.getCurrentSeason(),
                    time.getCurrentDay()
                );
                if (overlayStarted) {
                    session.setPhase(FestivalSessionPhase.PREPARING_MAP);
                    data.setDirty();
                }
            }
        }
        if (session.phase() == FestivalSessionPhase.PREPARING_MAP
            && (definition.mapOverlayId().isBlank() || FestivalMapOverlayManager.isApplied(level, definition.mapOverlayId()))) {
            session.setPhase(FestivalSessionPhase.OPEN);
            data.setDirty();
        }
        return Optional.of(session);
    }

    public static boolean endFestival(ServerLevel level, String festivalId) {
        if (level == null || festivalId == null || festivalId.isBlank()) {
            return false;
        }
        FestivalDefinition definition = FestivalRegistry.get(festivalId).orElse(null);
        if (definition == null) {
            return false;
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        StardewTimeManager time = StardewTimeManager.get();
        FestivalSessionState session = data.getOrCreateSession(
            definition,
            time.getCurrentYear(),
            time.getCurrentSeason(),
            time.getCurrentDay()
        );
        if (definition.type() == FestivalType.PASSIVE) {
            PassiveFestivalHandlers.onCleanup(level, definition, session);
        }
        if (!definition.mapOverlayId().isBlank() && FestivalMapOverlayManager.beginRestore(level, definition.mapOverlayId())) {
            session.setPhase(FestivalSessionPhase.RESTORING_MAP);
        } else {
            session.setPhase(FestivalSessionPhase.CLOSED);
        }
        clearDebugActiveFestival(festivalId);
        clearDebugPassiveFestival(festivalId);
        data.setDirty();
        return true;
    }

    public static void advancePreparingSessions(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        for (FestivalDefinition definition : FestivalRegistry.all()) {
            data.getSession(definition.id()).ifPresent(session -> {
                if (session.phase() != FestivalSessionPhase.PREPARING_MAP) {
                    return;
                }
                if (definition.mapOverlayId().isBlank() || FestivalMapOverlayManager.isApplied(level, definition.mapOverlayId())) {
                    session.setPhase(FestivalSessionPhase.OPEN);
                    if (definition.type() == FestivalType.PASSIVE) {
                        PassiveFestivalHandlers.onOpen(level, definition, session);
                        broadcastPassiveFestivalStartIfDue(level.getServer(), definition, session);
                    } else if (definition.type() == FestivalType.ACTIVE) {
                        broadcastActiveFestivalStartIfDue(level.getServer(), level, data, StardewTimeManager.get());
                    }
                    data.setDirty();
                }
            });
            data.getSession(definition.id()).ifPresent(session -> {
                if (session.phase() != FestivalSessionPhase.RESTORING_MAP) {
                    return;
                }
                if (definition.mapOverlayId().isBlank() || FestivalMapOverlayManager.isRestored(level, definition.mapOverlayId())) {
                    session.setPhase(FestivalSessionPhase.CLOSED);
                    data.setDirty();
                }
            });
        }
    }

    public static void tickPassiveFestivals(ServerLevel level) {
        if (level == null) {
            return;
        }
        FestivalWorldData data = FestivalWorldData.get(level);
        advancePassiveFestivalLifecycle(level, data, StardewTimeManager.get());
        for (FestivalDefinition definition : getActivePassiveFestivalsToday()) {
            if (!isPassiveFestivalTimeOpen(definition)) {
                continue;
            }
            data.getSession(definition.id()).ifPresent(session -> {
                if (!isCurrentSession(session) || (session.phase() != FestivalSessionPhase.OPEN && session.phase() != FestivalSessionPhase.MAIN_EVENT)) {
                    return;
                }
                PassiveFestivalHandlers.tick(level, definition, session);
            });
        }
    }

    private static void advancePassiveFestivalLifecycle(ServerLevel level, FestivalWorldData data, StardewTimeManager time) {
        for (FestivalDefinition definition : getActivePassiveFestivalsToday()) {
            FestivalSessionState session = data.getOrCreateSession(
                definition,
                time.getCurrentYear(),
                time.getCurrentSeason(),
                time.getCurrentDay()
            );
            if (!isCurrentSession(session)) {
                continue;
            }
            if (isPassiveFestivalTimeOpen(definition)) {
                openPassiveFestivalIfDue(level, data, definition, session, time);
            } else {
                closePassiveFestivalIfExpired(level, data, definition, session);
            }
        }
    }

    private static void openPassiveFestivalIfDue(ServerLevel level, FestivalWorldData data, FestivalDefinition definition,
                                                FestivalSessionState session, StardewTimeManager time) {
        if (session.phase() == FestivalSessionPhase.SCHEDULED) {
            if (requiresUnregisteredOverlay(definition)) {
                return;
            }
            boolean overlayStarted = FestivalMapOverlayManager.beginApply(
                level,
                definition,
                time.getCurrentYear(),
                time.getCurrentSeason(),
                time.getCurrentDay()
            );
            if (!overlayStarted && !definition.mapOverlayId().isBlank()) {
                return;
            }
            session.setPhase(overlayStarted ? FestivalSessionPhase.PREPARING_MAP : FestivalSessionPhase.OPEN);
            data.setDirty();
            if (session.phase() == FestivalSessionPhase.OPEN) {
                PassiveFestivalHandlers.onOpen(level, definition, session);
                broadcastPassiveFestivalStartIfDue(level.getServer(), definition, session);
            }
        } else if (session.phase() == FestivalSessionPhase.OPEN || session.phase() == FestivalSessionPhase.MAIN_EVENT) {
            broadcastPassiveFestivalStartIfDue(level.getServer(), definition, session);
        }
    }

    private static void closePassiveFestivalIfExpired(ServerLevel level, FestivalWorldData data, FestivalDefinition definition,
                                                     FestivalSessionState session) {
        if (isDebugPassiveFestival(definition.id())) {
            return;
        }
        FestivalSessionPhase phase = session.phase();
        if (phase != FestivalSessionPhase.PREPARING_MAP && phase != FestivalSessionPhase.OPEN && phase != FestivalSessionPhase.MAIN_EVENT) {
            return;
        }
        PassiveFestivalHandlers.onCleanup(level, definition, session);
        if (!definition.mapOverlayId().isBlank() && FestivalMapOverlayManager.beginRestore(level, definition.mapOverlayId())) {
            session.setPhase(FestivalSessionPhase.RESTORING_MAP);
        } else {
            session.setPhase(FestivalSessionPhase.CLOSED);
        }
        data.setDirty();
    }

    public static boolean isPassiveFestivalDay(String festivalId) {
        return getPassiveFestivalToday(festivalId).isPresent();
    }

    public static Optional<FestivalDefinition> getPassiveFestivalToday(String festivalId) {
        if (isDebugPassiveFestival(festivalId)) {
            return getDebugPassiveFestival();
        }
        StardewTimeManager time = StardewTimeManager.get();
        return FestivalRegistry.get(festivalId)
            .filter(definition -> definition.type() == FestivalType.PASSIVE)
            .filter(definition -> definition.isDate(time.getCurrentSeason(), time.getCurrentDay()))
            .filter(FestivalService::conditionsPass);
    }

    public static boolean isPassiveFestivalOpen(String festivalId) {
        if (isDebugPassiveFestival(festivalId)) {
            return true;
        }
        Optional<FestivalDefinition> definitionOpt = getPassiveFestivalToday(festivalId)
            .filter(FestivalService::isPassiveFestivalTimeOpen);
        if (definitionOpt.isEmpty()) {
            return false;
        }
        FestivalDefinition definition = definitionOpt.get();
        if (requiresUnregisteredOverlay(definition)) {
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel stardewLevel = server == null ? null : server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return true;
        }
        Optional<FestivalSessionState> sessionOpt = FestivalWorldData.get(stardewLevel).getSession(definition.id())
            .filter(FestivalService::isCurrentSession);
        if (sessionOpt.isEmpty()) {
            return definition.mapOverlayId().isBlank();
        }
        FestivalSessionPhase phase = sessionOpt.get().phase();
        return phase == FestivalSessionPhase.OPEN || phase == FestivalSessionPhase.MAIN_EVENT;
    }

    public static int getDayOfPassiveFestival(String festivalId) {
        if (isDebugPassiveFestival(festivalId)) {
            StardewTimeManager time = StardewTimeManager.get();
            return getDebugPassiveFestival()
                .map(definition -> {
                    int dayOfFestival = definition.dayOfFestival(time.getCurrentSeason(), time.getCurrentDay());
                    return dayOfFestival > 0 ? dayOfFestival : 1;
                })
                .orElse(-1);
        }
        StardewTimeManager time = StardewTimeManager.get();
        return getPassiveFestivalToday(festivalId)
            .map(definition -> definition.dayOfFestival(time.getCurrentSeason(), time.getCurrentDay()))
            .orElse(-1);
    }

    public static int currentTimeOfDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return time.getHour() * 100 + time.getMinute();
    }

    private static boolean conditionsPass(FestivalDefinition definition) {
        if (!definition.availableWhen().isEmpty()) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            ServerLevel level = server == null ? null : server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if (level == null) {
                return false;
            }
            StardewConditionContext context = new StardewConditionContext(level, null);
            boolean pass = definition.availableWhen().stream().allMatch(condition ->
                    StardewConditions.test(condition, context).result().orElse(false));
            if (!pass) {
                return false;
            }
        }
        String condition = definition.sourceCondition();
        if (condition == null || condition.isBlank()) {
            return true;
        }
        String normalized = condition.trim().toUpperCase(Locale.ROOT);
        if ("LOCATION_ACCESSIBLE DESERT".equals(normalized)) {
            return isDesertAccessible();
        }
        return false;
    }

    private static boolean isPassiveFestivalTimeOpen(FestivalDefinition definition) {
        if (definition == null || definition.type() != FestivalType.PASSIVE) {
            return false;
        }
        if (isDebugPassiveFestival(definition.id())) {
            return true;
        }
        int now = currentTimeOfDay();
        return now >= definition.startTime() && now < definition.endTime();
    }

    private static boolean requiresUnregisteredOverlay(FestivalDefinition definition) {
        return definition != null && !definition.mapOverlayId().isBlank() && !FestivalMapOverlayRegistry.isRegistered(definition.mapOverlayId());
    }

    private static boolean isTerminalSessionPhase(FestivalSessionPhase phase) {
        return phase == FestivalSessionPhase.ENDING
            || phase == FestivalSessionPhase.RESTORING_MAP
            || phase == FestivalSessionPhase.CLOSED;
    }

    private static boolean isCurrentSession(FestivalSessionState session) {
        if (session == null) {
            return false;
        }
        StardewTimeManager time = StardewTimeManager.get();
        return session.year() == time.getCurrentYear()
            && session.season() == time.getCurrentSeason()
            && session.day() == time.getCurrentDay();
    }

    private static void cleanupStalePassiveFestivalSessions(ServerLevel level, FestivalWorldData data, int year, int season, int day) {
        for (FestivalSessionState session : data.sessions()) {
            if (session.phase() == FestivalSessionPhase.CLOSED) {
                continue;
            }
            if (session.year() == year && session.season() == season && session.day() == day) {
                continue;
            }
            FestivalRegistry.get(session.festivalId())
                .filter(definition -> definition.type() == FestivalType.PASSIVE)
                .ifPresent(definition -> PassiveFestivalHandlers.onCleanup(level, definition, session));
        }
    }

    private static void restoreStaleOverlays(ServerLevel level, FestivalWorldData data, int year, int season, int day) {
        for (FestivalMapOverlayState state : data.overlayStates()) {
            if (state.phase() != FestivalMapOverlayPhase.APPLIED) {
                continue;
            }
            if (state.year() == year && state.season() == season && state.day() == day) {
                continue;
            }
            FestivalMapOverlayManager.beginRestore(level, state.overlayId());
        }
    }

    private static boolean isDesertAccessible() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        try {
            for (PlayerStardewData data : PlayerDataManager.get().getAllPlayerData().values()) {
                if (canAccessDesert(data)) {
                    return true;
                }
            }
        } catch (IllegalStateException ignored) {
            return false;
        }
        return false;
    }

    private static boolean canAccessDesert(PlayerStardewData data) {
        return data != null
            && (data.hasMailFlag(CCStoryFlags.CC_VAULT)
                || data.hasMailFlag(CCStoryFlags.JOJA_VAULT));
    }
}
