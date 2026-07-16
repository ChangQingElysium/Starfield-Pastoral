package com.stardew.craft.time;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.cutscene.server.ServerCutsceneTracker;
import com.stardew.craft.network.TimeSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative pause state for the shared Stardew Valley clock domain.
 *
 * <p>The farm and mine dimensions share one clock, so they pause and resume as a group. The
 * Minecraft server itself keeps ticking so players in other dimensions and menu/network traffic
 * remain responsive.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class StardewTimePauseService {

    private static final long CLIENT_STATE_TIMEOUT_TICKS = 300L;
    private static final Map<UUID, ClientState> CLIENT_STATES = new HashMap<>();

    private static MinecraftServer activeServer;
    /** Freezes entity/chunk/block-entity simulation while every player is in a real menu/sleep. */
    private static boolean simulationPaused;
    /** Freezes only the visible Stardew clock; cutscenes use this without freezing their actors. */
    private static boolean clockPaused;
    private static Long frozenVirtualDayTime;

    private StardewTimePauseService() {}

    public static void updateClientState(ServerPlayer player, boolean nonGameplay) {
        if (player == null) {
            return;
        }
        if (!isStardewTimeDimension(player.serverLevel())) {
            CLIENT_STATES.remove(player.getUUID());
            return;
        }
        CLIENT_STATES.put(player.getUUID(), new ClientState(nonGameplay, player.server.getTickCount()));
    }

    /** Used by the ServerLevel mixin to suppress gameplay simulation while retaining maintenance. */
    public static boolean shouldPauseLevel(ServerLevel level) {
        return level != null
            && level.getServer() == activeServer
            && simulationPaused
            && isStardewTimeDimension(level);
    }

    /** True only when the Stardew world simulation itself is collectively paused. */
    public static boolean isPaused(MinecraftServer server) {
        return server != null && server == activeServer && simulationPaused;
    }

    /** Includes collective menu/sleep pauses and cutscene-only clock freezes. */
    public static boolean isClockPaused(MinecraftServer server) {
        return server != null && server == activeServer && clockPaused;
    }

    public static boolean isStardewTimeDimension(ServerLevel level) {
        return level != null && (ModDimensions.STARDEW_VALLEY.equals(level.dimension())
            || ModMiningDimensions.STARDEW_MINING.equals(level.dimension()));
    }

    public static long getStardewSimulationGameTime(ServerLevel level) {
        StardewTimeManager timeManager = StardewTimeManager.get();
        long simulationGameTime = timeManager.getSimulationGameTime();
        return simulationGameTime >= 0L
            ? simulationGameTime
            : level.getServer().overworld().getGameTime();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (server != activeServer) {
            reset(server);
        }

        long now = server.getTickCount();
        CLIENT_STATES.entrySet().removeIf(entry -> now - entry.getValue().reportedAtTick() > CLIENT_STATE_TIMEOUT_TICKS);

        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
            .filter(player -> isStardewTimeDimension(player.serverLevel()))
            .toList();
        int simulationNonGameplayPlayers = 0;
        int clockNonGameplayPlayers = 0;
        for (ServerPlayer player : players) {
            boolean cutsceneActive = ServerCutsceneTracker.isActive(player.getUUID());
            boolean baseNonGameplay = isBaseNonGameplay(player, now);
            if (countsAsSimulationNonGameplay(cutsceneActive, baseNonGameplay)) {
                simulationNonGameplayPlayers++;
            }
            if (countsAsClockNonGameplay(cutsceneActive, baseNonGameplay)) {
                clockNonGameplayPlayers++;
            }
        }

        boolean nextSimulationPaused = shouldPauseForCounts(players.size(), simulationNonGameplayPlayers);
        boolean nextClockPaused = shouldPauseForCounts(players.size(), clockNonGameplayPlayers);
        StardewTimeManager timeManager = StardewTimeManager.get();
        timeManager.initializeSimulationGameTime(server.overworld().getGameTime());

        // Finish compensating the previous clock-paused tick before deciding whether to resume. Without
        // this, the overworld tick immediately before resume leaks into the Stardew clock.
        if (clockPaused && frozenVirtualDayTime != null
                && timeManager.getVirtualDayTime(server.overworld()) != frozenVirtualDayTime) {
            timeManager.setVirtualDayTime(server.overworld(), frozenVirtualDayTime);
        }

        if (nextClockPaused) {
            if (!clockPaused || frozenVirtualDayTime == null) {
                ServerLevel stardewLevel = server.getLevel(ModDimensions.STARDEW_VALLEY);
                frozenVirtualDayTime = stardewLevel == null
                    ? timeManager.getVirtualDayTime(server.overworld())
                    : com.stardew.craft.festival.ActiveFestivalHandlers.applyTimeFreeze(stardewLevel, timeManager);
            }
        } else {
            frozenVirtualDayTime = null;
        }

        boolean changed = simulationPaused != nextSimulationPaused || clockPaused != nextClockPaused;
        simulationPaused = nextSimulationPaused;
        clockPaused = nextClockPaused;
        if (!simulationPaused) {
            timeManager.advanceSimulationGameTime();
        }
        if (changed) {
            TimeSyncPacket packet = TimeSyncPacket.fromTimeManager(timeManager);
            for (ServerPlayer player : players) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CLIENT_STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset(null);
    }

    static boolean shouldPauseForCounts(int playerCount, int nonGameplayPlayerCount) {
        return playerCount == 0 || nonGameplayPlayerCount >= playerCount;
    }

    static boolean countsAsSimulationNonGameplay(boolean cutsceneActive, boolean baseNonGameplay) {
        return !cutsceneActive && baseNonGameplay;
    }

    static boolean countsAsClockNonGameplay(boolean cutsceneActive, boolean baseNonGameplay) {
        return cutsceneActive || baseNonGameplay;
    }

    private static boolean isBaseNonGameplay(ServerPlayer player, long now) {
        if (player.isSleeping()) {
            return true;
        }
        // The client screen classifier is the source of truth for menus. Do not infer pause from
        // containerMenu here: a realtime container screen must be able to opt out explicitly.
        ClientState state = CLIENT_STATES.get(player.getUUID());
        return state != null
            && state.nonGameplay()
            && now - state.reportedAtTick() <= CLIENT_STATE_TIMEOUT_TICKS;
    }

    private static void reset(MinecraftServer server) {
        activeServer = server;
        CLIENT_STATES.clear();
        simulationPaused = false;
        clockPaused = false;
        frozenVirtualDayTime = null;
    }

    private record ClientState(boolean nonGameplay, long reportedAtTick) {}
}
