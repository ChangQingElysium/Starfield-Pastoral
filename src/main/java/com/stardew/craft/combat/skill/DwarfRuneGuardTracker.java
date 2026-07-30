package com.stardew.craft.combat.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Tracks the authored Rune Guard shelter window independently of its MobEffect.
 */
public final class DwarfRuneGuardTracker {
    public enum Status {
        ACTIVE,
        EXPIRED,
        INVALIDATED
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private DwarfRuneGuardTracker() {}

    public static void start(
            ServerPlayer player,
            long nowTick,
            int durationTicks
    ) {
        ACTIVE.put(
                player.getUUID(),
                new State(
                        player.level().dimension(),
                        nowTick + durationTicks
                )
        );
    }

    public static Status tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return Status.INVALIDATED;
        }
        Status status = statusForSnapshot(
                isSameDimension(
                        state.dimension,
                        player.level().dimension()
                ),
                nowTick,
                state.endTick
        );
        if (status != Status.ACTIVE) {
            ACTIVE.remove(player.getUUID());
        }
        return status;
    }

    public static boolean hasState(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    public static void stop(ServerPlayer player) {
        if (player != null) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    static Status statusForSnapshot(
            boolean sameDimension,
            long nowTick,
            long endTick
    ) {
        if (!sameDimension) {
            return Status.INVALIDATED;
        }
        return nowTick >= endTick ? Status.EXPIRED : Status.ACTIVE;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static final class State {
        private final ResourceKey<Level> dimension;
        private final long endTick;

        private State(
                ResourceKey<Level> dimension,
                long endTick
        ) {
            this.dimension = dimension;
            this.endTick = endTick;
        }
    }
}
