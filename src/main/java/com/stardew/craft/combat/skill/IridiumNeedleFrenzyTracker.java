package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.IridiumNeedleFrenzyPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IridiumNeedleFrenzyTracker {
    public static final float CRIT_CHANCE_BONUS = 0.30F;
    public static final int CRITICAL_HEAL_AMOUNT = 5;
    public static final float CRITICAL_ENERGY_RESTORE = 10.0F;
    public static final int CRITICAL_VULNERABLE_DURATION_TICKS = 40;
    public static final int CRITICAL_VULNERABLE_AMPLIFIER = 1;

    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    private IridiumNeedleFrenzyTracker() {}

    public static void start(ServerPlayer player, long nowTick, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }
        ACTIVE.put(player.getUUID(), nowTick + durationTicks);
        PacketDistributor.sendToPlayer(player, new IridiumNeedleFrenzyPayload(true, durationTicks));
    }

    public static boolean isActive(ServerPlayer player, long nowTick) {
        if (player == null) {
            return false;
        }
        Long endTick = ACTIVE.get(player.getUUID());
        if (endTick == null) {
            return false;
        }
        if (!isWithinActiveWindow(nowTick, endTick)) {
            clear(player);
            return false;
        }
        return true;
    }

    public static void tick(ServerPlayer player, long nowTick) {
        if (player == null) {
            return;
        }
        Long endTick = ACTIVE.get(player.getUUID());
        if (endTick == null) {
            return;
        }
        if (!isWithinActiveWindow(nowTick, endTick)) {
            clear(player);
        }
    }

    static boolean isWithinActiveWindow(long nowTick, long endTick) {
        return nowTick <= endTick;
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Long removed = ACTIVE.remove(player.getUUID());
        if (removed != null) {
            PacketDistributor.sendToPlayer(
                player,
                new IridiumNeedleFrenzyPayload(false, 0)
            );
        }
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
