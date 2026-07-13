package com.stardew.craft.festival;

import com.stardew.craft.festival.desert.DesertFestivalHandler;
import com.stardew.craft.festival.nightmarket.NightMarketHandler;
import com.stardew.craft.festival.squid.SquidFestHandler;
import com.stardew.craft.festival.trout.TroutDerbyHandler;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PassiveFestivalHandlers {
    private static final Map<String, PassiveFestivalHandler> HANDLERS = new HashMap<>();

    static {
        register(new DesertFestivalHandler());
        register(new TroutDerbyHandler());
        register(new SquidFestHandler());
        register(new NightMarketHandler());
    }

    private PassiveFestivalHandlers() {
    }

    private static void register(PassiveFestivalHandler handler) {
        if (handler == null || handler.festivalId() == null || handler.festivalId().isBlank()) {
            return;
        }
        HANDLERS.put(key(handler.festivalId()), handler);
    }

    public static Optional<PassiveFestivalHandler> get(String festivalId) {
        return Optional.ofNullable(HANDLERS.get(key(festivalId)));
    }

    public static void onNewDay(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onNewDay(level, definition, session));
    }

    public static void onOpen(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onOpen(level, definition, session));
    }

    public static void onMapOverlayApplyStarted(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onMapOverlayApplyStarted(level, definition, session));
    }

    public static void onMapOverlayApplied(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onMapOverlayApplied(level, definition, session));
    }

    public static void onMapOverlayRestoreStarted(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onMapOverlayRestoreStarted(level, definition, session));
    }

    public static void onMapOverlayRestored(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onMapOverlayRestored(level, definition, session));
    }

    public static void tick(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.tick(level, definition, session));
    }

    public static void onCleanup(ServerLevel level, FestivalDefinition definition, FestivalSessionState session) {
        get(definition.id()).ifPresent(handler -> handler.onCleanup(level, definition, session));
    }

    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
