package com.stardew.craft.combat.equipment;

import com.stardew.craft.combat.debuff.ImmunitySystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Treats a continuous ignition as one harmful-status application so equipment
 * immunity and Sturdy Ring can affect fire without granting permanent fire
 * resistance.
 */
public final class EquipmentFireProtection {
    static final int RELEASE_GRACE_TICKS = 20;
    private static final int KEEP_CURRENT_FIRE_TICKS = -1;
    private static final long NOT_RELEASED = Long.MIN_VALUE;
    private static final Map<UUID, FireState> ACTIVE = new HashMap<>();

    private EquipmentFireProtection() {
    }

    public static void tick(ServerPlayer player) {
        long nowTick = player.level().getGameTime();
        int fireTicks = player.getRemainingFireTicks();
        UUID playerId = player.getUUID();
        FireDecision decision = advance(
                ACTIVE.get(playerId),
                nowTick,
                fireTicks,
                () -> {
                    EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
                    return new ProtectionRoll(
                            ImmunitySystem.tryResistEffect(equipment.getImmunity()),
                            equipment.hasSturdy()
                    );
                }
        );

        if (decision.state() == null) {
            ACTIVE.remove(playerId);
        } else {
            ACTIVE.put(playerId, decision.state());
        }

        if (decision.maximumFireTicks() == 0) {
            player.clearFire();
        } else if (decision.maximumFireTicks() > 0
                && fireTicks > decision.maximumFireTicks()) {
            player.setRemainingFireTicks(decision.maximumFireTicks());
        }
    }

    static FireDecision advance(
            FireState current,
            long nowTick,
            int fireTicks,
            Supplier<ProtectionRoll> newIgnition
    ) {
        FireState state = current;
        if (state != null && state.releaseGraceElapsed(nowTick)) {
            state = null;
        }

        if (fireTicks <= 0) {
            if (state != null && state.releaseTick() == NOT_RELEASED) {
                state = state.withReleaseTick(nowTick);
            }
            return new FireDecision(state, KEEP_CURRENT_FIRE_TICKS);
        }

        if (state == null) {
            ProtectionRoll roll = newIgnition.get();
            ProtectionMode mode = roll.resisted()
                    ? ProtectionMode.RESISTED
                    : roll.sturdy()
                            ? ProtectionMode.STURDY
                            : ProtectionMode.UNPROTECTED;
            int allowedTicks = mode == ProtectionMode.STURDY
                    ? ImmunitySystem.adjustDurationTicks(fireTicks, true)
                    : 0;
            state = new FireState(mode, nowTick + allowedTicks, NOT_RELEASED);
        } else if (state.releaseTick() != NOT_RELEASED) {
            state = state.withReleaseTick(NOT_RELEASED);
        }

        return switch (state.mode()) {
            case RESISTED -> new FireDecision(state, 0);
            case UNPROTECTED ->
                    new FireDecision(state, KEEP_CURRENT_FIRE_TICKS);
            case STURDY -> {
                long remaining = state.endTick() - nowTick;
                int maximum = remaining <= 0L
                        ? 0
                        : (int) Math.min(Integer.MAX_VALUE, remaining);
                yield new FireDecision(state, maximum);
            }
        };
    }

    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    enum ProtectionMode {
        RESISTED,
        STURDY,
        UNPROTECTED
    }

    record ProtectionRoll(boolean resisted, boolean sturdy) {
    }

    record FireDecision(FireState state, int maximumFireTicks) {
    }

    record FireState(
            ProtectionMode mode,
            long endTick,
            long releaseTick
    ) {
        FireState withReleaseTick(long tick) {
            return new FireState(mode, endTick, tick);
        }

        boolean releaseGraceElapsed(long nowTick) {
            return releaseTick != NOT_RELEASED
                    && nowTick - releaseTick >= RELEASE_GRACE_TICKS;
        }
    }
}
