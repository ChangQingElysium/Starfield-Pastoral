package com.stardew.craft.combat;

import com.stardew.craft.combat.equipment.EquipmentResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative recovery gate for ordinary Stardew weapon swings.
 *
 * <p>The gate rejects an early swing instead of letting Minecraft's partial
 * attack-strength value scale an otherwise complete Stardew damage roll.
 * Authored skill damage does not enter this class.</p>
 */
public final class StardewWeaponAttackRecovery {
    private static final Map<UUID, Double> READY_TICKS = new HashMap<>();

    private StardewWeaponAttackRecovery() {
    }

    public static boolean tryAcquire(
            ServerPlayer player,
            ItemStack weapon,
            long nowTick
    ) {
        WeaponStats stats = WeaponStats.fromItemStack(weapon);
        float equipmentSpeed = EquipmentResolver.getMergedStats(player)
                .getWeaponSpeedMultiplier();
        return tryAcquire(
                player.getUUID(),
                nowTick,
                recoveryIntervalTicks(stats, equipmentSpeed)
        );
    }

    static double recoveryIntervalTicks(
            WeaponStats stats,
            float equipmentSpeedMultiplier
    ) {
        return StardewWeaponSpeedRules.repeatMillisecondsFromRawSpeed(
                stats.getWeaponType(),
                stats.getRawSpeed(),
                equipmentSpeedMultiplier
                        + stats.getWeaponSpeedMultiplier()
        ) / 50.0D;
    }

    static int recoveryTicks(
            WeaponStats stats,
            float equipmentSpeedMultiplier
    ) {
        return StardewWeaponSpeedRules.recoveryTicksFromRawSpeed(
                stats.getWeaponType(),
                stats.getRawSpeed(),
                equipmentSpeedMultiplier
                        + stats.getWeaponSpeedMultiplier()
        );
    }

    static int recoveryTicks(
            WeaponType weaponType,
            int stardewSpeed,
            float equipmentSpeedMultiplier
    ) {
        return StardewWeaponSpeedRules.recoveryTicks(
                weaponType,
                stardewSpeed,
                equipmentSpeedMultiplier
        );
    }

    static synchronized boolean tryAcquire(
            UUID playerId,
            long nowTick,
            double recoveryTicks
    ) {
        double interval = Math.max(1.0D, recoveryTicks);
        Double readyTick = READY_TICKS.get(playerId);
        if (readyTick != null && nowTick + 1.0E-9D < readyTick) {
            return false;
        }
        double scheduleBase = readyTick != null
                && nowTick - readyTick < interval
                ? readyTick
                : nowTick;
        READY_TICKS.put(
                playerId,
                Math.min((double) Long.MAX_VALUE, scheduleBase + interval)
        );
        return true;
    }

    public static synchronized void clear(UUID playerId) {
        READY_TICKS.remove(playerId);
    }

}
