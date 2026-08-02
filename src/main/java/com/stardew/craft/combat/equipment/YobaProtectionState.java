package com.stardew.craft.combat.equipment;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative five-second protection granted by the Ring of Yoba.
 */
public final class YobaProtectionState {
    private static final String END_TICK_TAG = "stardewcraft_yoba_protection_end_tick";

    private YobaProtectionState() {
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        long endTick = player.getPersistentData().getLong(END_TICK_TAG);
        if (endTick <= nowTick) {
            if (endTick != 0L) {
                player.getPersistentData().remove(END_TICK_TAG);
            }
            return false;
        }
        return true;
    }

    public static void start(ServerPlayer player, long nowTick) {
        player.getPersistentData().putLong(
                END_TICK_TAG,
                nowTick + CombatRingRules.YOBA_PROTECTION_DURATION_TICKS
        );
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            player.getPersistentData().remove(END_TICK_TAG);
        }
    }
}
