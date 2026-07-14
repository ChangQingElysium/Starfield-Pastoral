package com.stardew.craft.festival;

import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.network.payload.FestivalHudStatePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ActiveFestivalHandlers {
    private static final Map<String, ActiveFestivalHandler> HANDLERS = new LinkedHashMap<>();
    private static final String TAG_FESTIVAL_HUD_KNOWN = "stardewcraft_active_festival_hud_known";
    private static final String TAG_FESTIVAL_HUD_HIDDEN = "stardewcraft_active_festival_hud_hidden";

    static {
        register(new DelegateHandler(
            EggFestivalService.FESTIVAL_ID,
            "Egg Festival",
            EggFestivalService::tick,
            EggFestivalService::startDebugFestival,
            EggFestivalService::restoreDebugFestival,
            level -> EggFestivalService.debugMainEventStatus(level) + "\n" + EggFestivalNpcService.debugStatus(level),
            ActiveFestivalHandlers::noop,
            EggFestivalService::tickNpcActors,
            EggFestivalNpcService::requestDebugStart,
            EggFestivalNpcService::restore,
            EggFestivalNpcService::debugStatus,
            EggFestivalNpcService::controlsNpc,
            EggFestivalService::isParticipant,
            ActiveFestivalHandlers::noopPlayer,
            ActiveFestivalHandlers::noopPlayer,
            ActiveFestivalHandlers::noopDialogueSeen,
            EggFestivalService::tryOpenPierreFestivalShop,
            () -> EggFestivalService.isEggHuntActive() || EggFestivalService.isMainEventCutsceneActive(),
            EggFestivalService::tryStartMainEvent,
            EggFestivalService::debugMainEventStatus,
            EggFestivalService::isTimeFreezeActive,
            EggFestivalService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Egg Festival", "spring13")
        ));
        register(new DelegateHandler(
            FlowerDanceService.FESTIVAL_ID,
            "Flower Dance",
            FlowerDanceService::tick,
            FlowerDanceService::startDebugFestival,
            FlowerDanceService::restoreDebugFestival,
            FlowerDanceService::debugStatus,
            ActiveFestivalHandlers::noop,
            FlowerDanceService::tickNpcActors,
            FlowerDanceNpcService::requestDebugStart,
            FlowerDanceNpcService::restore,
            FlowerDanceNpcService::debugStatus,
            FlowerDanceNpcService::controlsNpc,
            FlowerDanceService::isParticipant,
            FlowerDanceService::onPlayerLogin,
            FlowerDanceService::onPlayerLogout,
            FlowerDanceService::markFestivalDialogueSeen,
            FlowerDanceService::tryOpenPierreFestivalShop,
            FlowerDanceService::isMainEventCutsceneActive,
            FlowerDanceService::tryStartMainEvent,
            FlowerDanceService::debugStatus,
            FlowerDanceService::isTimeFreezeActive,
            FlowerDanceService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Flower Dance", "spring24")
        ));
        register(new DelegateHandler(
            LuauFestivalService.FESTIVAL_ID,
            "Luau",
            LuauFestivalService::tick,
            LuauFestivalService::startDebugFestival,
            LuauFestivalService::restoreDebugFestival,
            LuauFestivalService::debugStatus,
            ActiveFestivalHandlers::noop,
            LuauFestivalService::tickNpcActors,
            LuauFestivalService::requestDebugNpcs,
            LuauFestivalService::restoreNpcs,
            LuauFestivalService::debugStatus,
            LuauFestivalService::controlsNpc,
            LuauFestivalService::isParticipant,
            LuauFestivalService::onPlayerLogin,
            LuauFestivalService::onPlayerLogout,
            LuauFestivalService::markFestivalDialogueSeen,
            LuauFestivalService::tryOpenPierreFestivalShop,
            LuauFestivalService::isMainEventActive,
            LuauFestivalService::tryStartMainEvent,
            LuauFestivalService::debugStatus,
            LuauFestivalService::isTimeFreezeActive,
            LuauFestivalService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Luau", "summer11")
        ));
        register(new DelegateHandler(
            MoonlightJelliesFestivalService.FESTIVAL_ID,
            "Dance of the Moonlight Jellies",
            MoonlightJelliesFestivalService::tick,
            MoonlightJelliesFestivalService::startDebugFestival,
            MoonlightJelliesFestivalService::restoreDebugFestival,
            MoonlightJelliesFestivalService::debugStatus,
            ActiveFestivalHandlers::noop,
            MoonlightJelliesFestivalService::tickNpcActors,
            MoonlightJelliesFestivalService::requestDebugNpcs,
            MoonlightJelliesFestivalService::restoreNpcs,
            MoonlightJelliesFestivalService::debugStatus,
            MoonlightJelliesFestivalService::controlsNpc,
            MoonlightJelliesFestivalService::isParticipant,
            MoonlightJelliesFestivalService::onPlayerLogin,
            MoonlightJelliesFestivalService::onPlayerLogout,
            MoonlightJelliesFestivalService::markFestivalDialogueSeen,
            MoonlightJelliesFestivalService::tryOpenPierreFestivalShop,
            MoonlightJelliesFestivalService::isMainEventActive,
            MoonlightJelliesFestivalService::tryStartMainEvent,
            MoonlightJelliesFestivalService::debugStatus,
            MoonlightJelliesFestivalService::isTimeFreezeActive,
            MoonlightJelliesFestivalService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Moonlight Jellies", "summer28")
        ));
        register(new DelegateHandler(
            FairFestivalService.FESTIVAL_ID,
            "Stardew Valley Fair",
            FairFestivalService::tick,
            FairFestivalService::startDebugFestival,
            FairFestivalService::restoreDebugFestival,
            FairFestivalService::debugStatus,
            FairFestivalService::onMapOverlayApplied,
            FairFestivalService::tickNpcActors,
            FairFestivalService::requestDebugNpcs,
            FairFestivalService::restoreNpcs,
            FairFestivalService::debugNpcStatus,
            FairFestivalService::controlsNpc,
            FairFestivalService::isParticipant,
            FairFestivalService::onPlayerLogin,
            FairFestivalService::onPlayerLogout,
            FairFestivalService::markFestivalDialogueSeen,
            FairFestivalService::tryOpenPierreFestivalShop,
            FairFestivalService::isMainEventActive,
            FairFestivalService::tryStartMainEvent,
            FairFestivalService::debugStatus,
            FairFestivalService::isTimeFreezeActive,
            FairFestivalService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Stardew Valley Fair", "fall16")
        ));
        register(new DelegateHandler(
            SpiritEveFestivalService.FESTIVAL_ID,
            "Spirit's Eve",
            SpiritEveFestivalService::tick,
            SpiritEveFestivalService::startDebugFestival,
            SpiritEveFestivalService::restoreDebugFestival,
            SpiritEveFestivalService::debugStatus,
            SpiritEveFestivalService::onMapOverlayApplied,
            SpiritEveFestivalService::tickNpcActors,
            SpiritEveFestivalService::requestDebugNpcs,
            SpiritEveFestivalService::restoreNpcs,
            SpiritEveFestivalService::debugNpcStatus,
            SpiritEveFestivalService::controlsNpc,
            SpiritEveFestivalService::isParticipant,
            SpiritEveFestivalService::onPlayerLogin,
            SpiritEveFestivalService::onPlayerLogout,
            SpiritEveFestivalService::markFestivalDialogueSeen,
            SpiritEveFestivalService::tryOpenPierreFestivalShop,
            SpiritEveFestivalService::isMainEventActive,
            null,
            SpiritEveFestivalService::debugStatus,
            SpiritEveFestivalService::isTimeFreezeActive,
            SpiritEveFestivalService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Spirit's Eve", "fall27")
        ));
        register(new DelegateHandler(
            FestivalOfIceService.FESTIVAL_ID,
            "Festival of Ice",
            FestivalOfIceService::tick,
            FestivalOfIceService::startDebugFestival,
            FestivalOfIceService::restoreDebugFestival,
            FestivalOfIceService::debugStatus,
            FestivalOfIceService::onMapOverlayApplied,
            FestivalOfIceService::tickNpcActors,
            FestivalOfIceService::requestDebugNpcs,
            FestivalOfIceService::restoreNpcs,
            FestivalOfIceService::debugNpcStatus,
            FestivalOfIceService::controlsNpc,
            FestivalOfIceService::isParticipant,
            FestivalOfIceService::onPlayerLogin,
            FestivalOfIceService::onPlayerLogout,
            FestivalOfIceService::markFestivalDialogueSeen,
            FestivalOfIceService::tryOpenPierreFestivalShop,
            FestivalOfIceService::isMainEventActive,
            FestivalOfIceService::tryStartMainEvent,
            FestivalOfIceService::debugStatus,
            FestivalOfIceService::isTimeFreezeActive,
            FestivalOfIceService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Festival of Ice", "winter8")
        ));
        register(new DelegateHandler(
            WinterStarFestivalService.FESTIVAL_ID,
            "Feast of the Winter Star",
            WinterStarFestivalService::tick,
            WinterStarFestivalService::startDebugFestival,
            WinterStarFestivalService::restoreDebugFestival,
            WinterStarFestivalService::debugStatus,
            WinterStarFestivalService::onMapOverlayApplied,
            WinterStarFestivalService::tickNpcActors,
            WinterStarFestivalService::requestDebugNpcs,
            WinterStarFestivalService::restoreNpcs,
            WinterStarFestivalService::debugStatus,
            WinterStarFestivalService::controlsNpc,
            WinterStarFestivalService::isParticipant,
            WinterStarFestivalService::onPlayerLogin,
            WinterStarFestivalService::onPlayerLogout,
            WinterStarFestivalService::markFestivalDialogueSeen,
            WinterStarFestivalService::tryOpenPierreFestivalShop,
            WinterStarFestivalService::isMainEventActive,
            null,
            WinterStarFestivalService::debugStatus,
            () -> false,
            WinterStarFestivalService::applyTimeFreeze,
            Component.translatable("stardewcraft.command.festival.debug_started", "Feast of the Winter Star", "winter25")
        ));
    }

    private ActiveFestivalHandlers() {
    }

    private static void register(ActiveFestivalHandler handler) {
        if (handler == null || handler.festivalId() == null || handler.festivalId().isBlank()) {
            return;
        }
        HANDLERS.put(key(handler.festivalId()), handler);
    }

    /** Registers an addon mechanic referenced by a festival definition's mechanic_id. */
    public static synchronized void register(ResourceLocation mechanicId, ActiveFestivalHandler handler) {
        if (mechanicId == null || handler == null) {
            throw new IllegalArgumentException("festival mechanic id and handler must not be null");
        }
        String key = key(mechanicId.toString());
        if (HANDLERS.putIfAbsent(key, handler) != null) {
            throw new IllegalStateException("Duplicate active festival mechanic: " + mechanicId);
        }
    }

    public static Optional<ActiveFestivalHandler> get(String festivalId) {
        return Optional.ofNullable(HANDLERS.get(key(festivalId)));
    }

    public static Optional<ActiveFestivalHandler> get(FestivalDefinition definition) {
        if (definition == null || definition.type() != FestivalType.ACTIVE) {
            return Optional.empty();
        }
        Optional<ActiveFestivalHandler> mechanic = get(definition.mechanicId());
        return mechanic.isPresent() ? mechanic : get(definition.id());
    }

    public static Collection<ActiveFestivalHandler> all() {
        return HANDLERS.values();
    }

    public static List<String> festivalIds() {
        return HANDLERS.values().stream().map(ActiveFestivalHandler::festivalId).toList();
    }

    public static List<String> mainEventFestivalIds() {
        return HANDLERS.values().stream()
            .filter(ActiveFestivalHandler::supportsMainEventDebug)
            .map(ActiveFestivalHandler::festivalId)
            .toList();
    }

    public static void tickAll(ServerLevel level) {
        for (ActiveFestivalHandler handler : HANDLERS.values()) {
            handler.tick(level);
        }
        syncFestivalHud(level);
    }

    public static void tickNpcActors(ServerLevel level) {
        for (ActiveFestivalHandler handler : HANDLERS.values()) {
            handler.tickNpcActors(level);
        }
    }

    public static boolean controlsNpc(String npcId) {
        return HANDLERS.values().stream().anyMatch(handler -> handler.controlsNpc(npcId));
    }

    public static boolean isParticipant(ServerPlayer player) {
        return getParticipating(player).isPresent();
    }

    public static Optional<ActiveFestivalHandler> getParticipating(ServerPlayer player) {
        if (player == null) {
            return Optional.empty();
        }
        return currentActiveHandler()
            .filter(handler -> handler.isParticipant(player));
    }

    public static void onPlayerLogin(ServerPlayer player) {
        for (ActiveFestivalHandler handler : HANDLERS.values()) {
            handler.onPlayerLogin(player);
        }
        syncFestivalHud(player, true);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        for (ActiveFestivalHandler handler : HANDLERS.values()) {
            handler.onPlayerLogout(player);
        }
        if (player != null) {
            CompoundTag data = player.getPersistentData();
            data.remove(TAG_FESTIVAL_HUD_KNOWN);
            data.remove(TAG_FESTIVAL_HUD_HIDDEN);
        }
    }

    public static boolean tryOpenPierreFestivalShop(ServerPlayer player) {
        return getParticipating(player)
            .map(handler -> handler.tryOpenPierreFestivalShop(player))
            .orElse(false);
    }

    public static void restoreNpcsForOverlay(ServerLevel level, String overlayId) {
        for (ActiveFestivalHandler handler : HANDLERS.values()) {
            FestivalDefinition definition = FestivalRegistry.get(handler.festivalId()).orElse(null);
            if (definition != null && definition.mapOverlayId().equalsIgnoreCase(overlayId == null ? "" : overlayId)) {
                handler.restoreDebugNpcs(level);
            }
        }
    }

    public static void onMapOverlayApplied(ServerLevel level, String overlayId) {
        for (ActiveFestivalHandler handler : HANDLERS.values()) {
            FestivalDefinition definition = FestivalRegistry.get(handler.festivalId()).orElse(null);
            if (definition != null && definition.mapOverlayId().equalsIgnoreCase(overlayId == null ? "" : overlayId)) {
                handler.onMapOverlayApplied(level);
            }
        }
    }

    public static String debugNpcStatus(ServerLevel level) {
        return HANDLERS.values().stream()
            .map(handler -> handler.debugNpcStatus(level))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("No active festival NPC controllers registered");
    }

    public static String debugMainEventStatus(ServerLevel level) {
        return HANDLERS.values().stream()
            .map(handler -> handler.debugMainEventStatus(level))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("No active festival handlers registered");
    }

    public static boolean isAnyTimeFreezeActive() {
        return currentActiveHandler()
            .map(ActiveFestivalHandler::isTimeFreezeActive)
            .orElse(false);
    }

    public static long applyTimeFreeze(ServerLevel level, StardewTimeManager timeManager) {
        return currentActiveHandler()
            .map(handler -> handler.applyTimeFreeze(level, timeManager))
            .orElseGet(() -> timeManager.getVirtualDayTime(level));
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<ActiveFestivalHandler> currentActiveHandler() {
        return FestivalService.getActiveFestivalToday()
            .flatMap(ActiveFestivalHandlers::get);
    }

    private static void syncFestivalHud(ServerLevel level) {
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            syncFestivalHud(player, false);
        }
    }

    private static void syncFestivalHud(ServerPlayer player, boolean force) {
        if (player == null) {
            return;
        }
        boolean hidden = getParticipating(player).isPresent();
        CompoundTag data = player.getPersistentData();
        boolean known = data.getBoolean(TAG_FESTIVAL_HUD_KNOWN);
        boolean previous = data.getBoolean(TAG_FESTIVAL_HUD_HIDDEN);
        if (!force && known && previous == hidden) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new FestivalHudStatePayload(hidden));
        data.putBoolean(TAG_FESTIVAL_HUD_KNOWN, true);
        data.putBoolean(TAG_FESTIVAL_HUD_HIDDEN, hidden);
    }

    private static void noop(ServerLevel level) {
    }

    private static void noopPlayer(ServerPlayer player) {
    }

    private static void noopDialogueSeen(ServerPlayer player, String npcId) {
    }

    private record DelegateHandler(
        String festivalId,
        String displayName,
        Consumer<ServerLevel> tickAction,
        Consumer<ServerLevel> startDebugAction,
        Consumer<ServerLevel> restoreDebugAction,
        Function<ServerLevel, String> debugStatusAction,
        Consumer<ServerLevel> overlayAppliedAction,
        Consumer<ServerLevel> tickNpcActorsAction,
        Consumer<ServerLevel> requestDebugNpcsAction,
        Consumer<ServerLevel> restoreDebugNpcsAction,
        Function<ServerLevel, String> debugNpcStatusAction,
        Predicate<String> controlsNpcAction,
        Predicate<ServerPlayer> participantAction,
        Consumer<ServerPlayer> playerLoginAction,
        Consumer<ServerPlayer> playerLogoutAction,
        BiConsumer<ServerPlayer, String> npcDialogueSeenAction,
        Predicate<ServerPlayer> pierreShopAction,
        BooleanSupplier npcInteractionLockAction,
        Predicate<ServerPlayer> startMainEventAction,
        Function<ServerLevel, String> debugMainEventStatusAction,
        BooleanSupplier timeFreezeActiveAction,
        TimeFreezeApplier timeFreezeAction,
        Component debugApplyMessage
    ) implements ActiveFestivalHandler {
        @Override
        public void tick(ServerLevel level) {
            tickAction.accept(level);
        }

        @Override
        public void startDebugFestival(ServerLevel level) {
            startDebugAction.accept(level);
        }

        @Override
        public void restoreDebugFestival(ServerLevel level) {
            restoreDebugAction.accept(level);
        }

        @Override
        public String debugStatus(ServerLevel level) {
            return debugStatusAction.apply(level);
        }

        @Override
        public void onMapOverlayApplied(ServerLevel level) {
            overlayAppliedAction.accept(level);
        }

        @Override
        public void tickNpcActors(ServerLevel level) {
            tickNpcActorsAction.accept(level);
        }

        @Override
        public void requestDebugNpcs(ServerLevel level) {
            requestDebugNpcsAction.accept(level);
        }

        @Override
        public void restoreDebugNpcs(ServerLevel level) {
            restoreDebugNpcsAction.accept(level);
        }

        @Override
        public String debugNpcStatus(ServerLevel level) {
            return debugNpcStatusAction.apply(level);
        }

        @Override
        public boolean controlsNpc(String npcId) {
            return controlsNpcAction.test(npcId);
        }

        @Override
        public boolean isParticipant(ServerPlayer player) {
            return participantAction.test(player);
        }

        @Override
        public void onPlayerLogin(ServerPlayer player) {
            playerLoginAction.accept(player);
        }

        @Override
        public void onPlayerLogout(ServerPlayer player) {
            playerLogoutAction.accept(player);
        }

        @Override
        public void onNpcDialogueSeen(ServerPlayer player, String npcId) {
            npcDialogueSeenAction.accept(player, npcId);
        }

        @Override
        public boolean tryOpenPierreFestivalShop(ServerPlayer player) {
            return pierreShopAction.test(player);
        }

        @Override
        public boolean blocksNpcInteractionDuringMainEvent() {
            return npcInteractionLockAction.getAsBoolean();
        }

        @Override
        public boolean supportsMainEventDebug() {
            return startMainEventAction != null;
        }

        @Override
        public boolean tryStartMainEvent(ServerPlayer player) {
            return startMainEventAction != null && startMainEventAction.test(player);
        }

        @Override
        public String debugMainEventStatus(ServerLevel level) {
            return debugMainEventStatusAction.apply(level);
        }

        @Override
        public boolean isTimeFreezeActive() {
            return timeFreezeActiveAction.getAsBoolean();
        }

        @Override
        public long applyTimeFreeze(ServerLevel level, StardewTimeManager timeManager) {
            return timeFreezeAction.apply(level, timeManager);
        }

        @Override
        public Component debugApplyMessage() {
            return debugApplyMessage;
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @FunctionalInterface
    private interface TimeFreezeApplier {
        long apply(ServerLevel level, StardewTimeManager timeManager);
    }
}
