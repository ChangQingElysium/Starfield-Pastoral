package com.stardew.craft.combat.equipment;

import com.stardew.craft.combat.debuff.ImmunitySystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Treats a continuous ignition as one harmful-status application so equipment
 * immunity and Sturdy Ring can affect fire without granting permanent fire
 * resistance.
 */
public final class EquipmentFireProtection {
    private static final int RELEASE_GRACE_TICKS = 20;
    private static final Map<UUID, FireState> ACTIVE = new HashMap<>();

    private EquipmentFireProtection() {
    }

    public static void tick(ServerPlayer player) {
        long nowTick = player.level().getGameTime();
        int fireTicks = player.getRemainingFireTicks();
        FireState state = ACTIVE.get(player.getUUID());

        if (fireTicks <= 0) {
            if (state != null && nowTick > state.endTick() + RELEASE_GRACE_TICKS) {
                ACTIVE.remove(player.getUUID());
            }
            return;
        }

        if (state == null) {
            EquipmentStats equipment = EquipmentResolver.getMergedStats(player);
            boolean resisted = ImmunitySystem.tryResistEffect(equipment.getImmunity());
            if (!resisted && !equipment.hasSturdy()) {
                return;
            }
            int allowedTicks = resisted
                    ? 0
                    : ImmunitySystem.adjustDurationTicks(fireTicks, equipment.hasSturdy());
            state = new FireState(nowTick + allowedTicks);
            ACTIVE.put(player.getUUID(), state);
        }

        long remainingAllowed = state.endTick() - nowTick;
        if (remainingAllowed <= 0L) {
            player.clearFire();
        } else if (fireTicks > remainingAllowed) {
            player.setRemainingFireTicks((int) Math.min(Integer.MAX_VALUE, remainingAllowed));
        }
    }

    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    private record FireState(long endTick) {
    }
}
