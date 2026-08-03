package com.stardew.craft.cutscene.server;

import com.mojang.logging.LogUtils;
import com.stardew.craft.cutscene.data.CutsceneTriggerLocations;
import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.data.EventRegistry;
import com.stardew.craft.cutscene.network.TriggerEventPayload;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 服务端侧记录当前正在观看 cutscene 的玩家。
 * 用于在事件期间禁止方块破坏/放置/实体交互等行为。
 * 同时把真实玩家临时切到旁观模式，避免剧情播放时被攻击、淹死、烧死或摔死。
 *
 * 注意：客户端完成事件后会通过 {@link com.stardew.craft.cutscene.network.MarkEventSeenPayload}
 * 回调服务端，届时会清除对应玩家的活动状态。
 */
public final class ServerCutsceneTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 20L;

    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private static final class State {
        private final GameType originalGameMode;
        private final ResourceKey<Level> originalDimension;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final String eventId;
        private final long sessionId;
        private final long startedAtServerTick;
        private final CutsceneActionManifest actionManifest;
        private final CutsceneActionManifest.AuthorizationState actionState;
        private boolean restoreOriginalPosition = true;

        private State(ServerPlayer player, GameType originalGameMode, EventData event, long sessionId) {
            this.originalGameMode = originalGameMode;
            this.originalDimension = player.level().dimension();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.eventId = event.id();
            this.sessionId = sessionId;
            // Cutscenes are allowed to pause the Stardew simulation. Their watchdog must use the
            // real server clock or an unresponsive client could keep the shared world paused forever.
            this.startedAtServerTick = player.server.getTickCount();
            this.actionManifest = CutsceneActionManifest.from(event);
            this.actionState = actionManifest.newState();
        }
    }

    private ServerCutsceneTracker() {}

    /** 启动一个事件：记录玩家为活动状态并向其发送触发包。 */
    public static boolean startEvent(ServerPlayer player, String eventId) {
        EventData event = EventRegistry.getById(eventId);
        if (event == null) {
            LOGGER.warn("Cannot start unknown cutscene '{}' for {}", eventId, player.getName().getString());
            return false;
        }
        return beginAuthorized(player, event);
    }

    /** Non-mutating preflight used before a shared festival changes its global phase. */
    public static boolean canStartEvent(ServerPlayer player, String eventId) {
        return player != null
                && eventId != null
                && EventRegistry.getById(eventId) != null
                && !ACTIVE.containsKey(player.getUUID());
    }

    /** Validate a client-detected enter-area trigger and start it only when server state agrees. */
    public static boolean requestEnterAreaEvent(ServerPlayer player, String eventId) {
        if (ACTIVE.containsKey(player.getUUID())) {
            return false;
        }
        EventData event = EventRegistry.getById(eventId);
        if (event == null || event.trigger() == null || !"enter_area".equals(event.trigger().type())) {
            LOGGER.warn("Rejected invalid enter-area cutscene request '{}' from {}",
                    eventId, player.getName().getString());
            return false;
        }
        if (EventSeenData.get(player.serverLevel()).hasSeen(player.getUUID(), event.id())
                || !CutsceneTriggerLocations.contains(player, event.trigger())
                || !ServerPreconditionEvaluator.evaluate(player, player.serverLevel(), event.preconditions())) {
            return false;
        }
        return beginAuthorized(player, event);
    }

    private static boolean beginAuthorized(ServerPlayer player, EventData event) {
        if (ACTIVE.containsKey(player.getUUID())) {
            LOGGER.warn("Cannot start cutscene '{}' for {} while another session is active",
                    event.id(), player.getName().getString());
            return false;
        }
        GameType original = player.gameMode.getGameModeForPlayer();
        long sessionId = nextSessionId();
        State state = new State(player, original, event, sessionId);
        if (ACTIVE.putIfAbsent(player.getUUID(), state) != null) {
            return false;
        }
        protectPlayer(player);
        if (original != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        PacketDistributor.sendToPlayer(player, new TriggerEventPayload(event.id(), sessionId));
        return true;
    }

    public static boolean authorizeAction(
            ServerPlayer player,
            long sessionId,
            String eventId,
            int commandToken,
            String action,
            String value
    ) {
        State state = validState(player, sessionId, eventId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.actionManifest.authorize(state.actionState, commandToken, action, value);
        }
    }

    public static boolean authorizeCompletion(ServerPlayer player, long sessionId, String eventId) {
        return validState(player, sessionId, eventId) != null;
    }

    /** Release a matching session without marking its event as seen or applying completion hooks. */
    public static boolean abortSession(ServerPlayer player, long sessionId, String eventId) {
        if (validState(player, sessionId, eventId) == null) {
            return false;
        }
        LOGGER.warn("Client aborted cutscene '{}' for {} before playback started",
                eventId, player.getName().getString());
        clear(player);
        return true;
    }

    private static State validState(ServerPlayer player, long sessionId, String eventId) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null || state.sessionId != sessionId || !state.eventId.equals(eventId)) {
            return null;
        }
        if (player.server.getTickCount() - state.startedAtServerTick > SESSION_TIMEOUT_TICKS) {
            failActiveEvent(player, "authorization timeout");
            return null;
        }
        return state;
    }

    public static void markServerMovedPlayer(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        if (state != null) {
            state.restoreOriginalPosition = false;
        }
    }

    /** 清除玩家的活动状态。 */
    public static void clear(ServerPlayer player) {
        State state = ACTIVE.remove(player.getUUID());
        if (state == null) {
            return;
        }

        protectPlayer(player);
        if (state.restoreOriginalPosition) {
            net.minecraft.server.level.ServerLevel originalLevel = player.server.getLevel(state.originalDimension);
            if (originalLevel != null) {
                ModTeleport.to(player, originalLevel, state.x, state.y, state.z, state.yaw, state.pitch);
            }
        }
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setGameMode(state.originalGameMode != null ? state.originalGameMode : GameType.SURVIVAL);
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, 40);
    }

    /** 清除玩家的活动状态；用于无法安全访问 ServerPlayer 实例的兜底路径。 */
    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    /** 剧情期间持续清掉环境危险的积累状态。 */
    public static void tickProtection(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (player.server.getTickCount() - state.startedAtServerTick > SESSION_TIMEOUT_TICKS) {
            failActiveEvent(player, "playback timeout");
            return;
        }
        protectPlayer(player);
    }

    /**
     * Releases a cutscene that cannot complete and advances any shared festival
     * waiting for that participant. Clearing only the tracker would leave the
     * festival's global interaction lock active forever.
     */
    public static boolean failActiveEvent(ServerPlayer player, String reason) {
        if (player == null) {
            return false;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return false;
        }
        String eventId = state.eventId;
        LOGGER.warn("Aborting cutscene session '{}' for {}: {}",
                eventId, player.getName().getString(), reason == null ? "unavailable" : reason);
        clear(player);
        com.stardew.craft.festival.ActiveFestivalHandlers
                .onCutsceneUnavailable(player, eventId);
        return true;
    }

    /** 判断玩家当前是否处于 cutscene 中。 */
    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    private static long nextSessionId() {
        long value;
        do {
            value = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        } while (value == 0L);
        return value;
    }

    private static void protectPlayer(ServerPlayer player) {
        player.setAirSupply(player.getMaxAirSupply());
        player.clearFire();
        player.fallDistance = 0.0f;
    }
}
