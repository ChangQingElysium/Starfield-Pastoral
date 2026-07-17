package com.stardew.craft.world;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable soft boundary for per-player locked areas.
 *
 * <p>The last safe position is restored when a player crosses a boundary. This behaves like a
 * collision rollback and avoids continuously fighting the player's movement input. A configured
 * exit is only used when the player appears inside the area without a nearby safe position.</p>
 */
public final class PlayerAreaEvictionService {
    private static final double MAX_SAFE_ROLLBACK_DISTANCE_SQR = 8.0D * 8.0D;
    private static final int MESSAGE_INTERVAL_TICKS = 20;
    private static final Map<UUID, Map<String, GateState>> STATES = new HashMap<>();

    private PlayerAreaEvictionService() {
    }

    public static void clearPlayer(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    public static boolean enforce(ServerPlayer player, String gateId, boolean insideLockedArea,
                                  Vec3 fallbackExit, @Nullable Component blockedMessage) {
        GateState state = STATES
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .computeIfAbsent(gateId, ignored -> new GateState());

        if (!isEvictionRequired(insideLockedArea, player.isCreative(), player.isSpectator())) {
            state.lastSafePosition = player.position();
            return false;
        }

        Vec3 current = player.position();
        Vec3 exit = state.lastSafePosition != null
                && state.lastSafePosition.distanceToSqr(current) <= MAX_SAFE_ROLLBACK_DISTANCE_SQR
                ? state.lastSafePosition
                : fallbackExit;

        Vec3 outward = exit.subtract(current);
        double horizontalLength = Math.sqrt(outward.x * outward.x + outward.z * outward.z);
        double impulseX = horizontalLength > 1.0E-4D ? outward.x / horizontalLength * 0.08D : 0.0D;
        double impulseZ = horizontalLength > 1.0E-4D ? outward.z / horizontalLength * 0.08D : 0.0D;

        player.teleportTo(player.serverLevel(), exit.x, exit.y, exit.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(impulseX, Math.max(0.0D, player.getDeltaMovement().y), impulseZ);
        player.fallDistance = 0.0F;
        player.hasImpulse = true;
        player.hurtMarked = true;
        state.lastSafePosition = exit;

        if (blockedMessage != null && player.tickCount - state.lastMessageTick >= MESSAGE_INTERVAL_TICKS) {
            player.displayClientMessage(blockedMessage, true);
            state.lastMessageTick = player.tickCount;
        }
        return true;
    }

    /** Creative and spectator are not exemptions for an active story gate. */
    static boolean isEvictionRequired(boolean insideLockedArea, boolean creative, boolean spectator) {
        return insideLockedArea;
    }

    private static final class GateState {
        @Nullable
        private Vec3 lastSafePosition;
        private int lastMessageTick = Integer.MIN_VALUE / 2;
    }
}
