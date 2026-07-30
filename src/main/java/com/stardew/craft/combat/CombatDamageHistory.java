package com.stardew.craft.combat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps the latest authoritative incoming or outgoing damage trace per player.
 */
public final class CombatDamageHistory {
    private static final Map<UUID, Entry> LATEST = new ConcurrentHashMap<>();

    private CombatDamageHistory() {}

    public static void record(Player player, long gameTick, DamageOutcome outcome) {
        LATEST.put(player.getUUID(), new Entry(gameTick, outcome));
    }

    public static Optional<Entry> latest(UUID playerId) {
        return Optional.ofNullable(LATEST.get(playerId));
    }

    public static void remove(UUID playerId) {
        LATEST.remove(playerId);
    }

    public record Entry(long gameTick, DamageOutcome outcome) {}
}
