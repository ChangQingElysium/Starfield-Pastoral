package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.StardewCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Exclusive server-side ownership for weapon-skill movement.
 *
 * <p>A later movement replaces the earlier owner for the same player. The
 * displaced owner is notified immediately so its client prediction can stop,
 * while exact leases prevent an old execution from releasing a newer one.</p>
 */
public final class WeaponSkillMovementArbiter {
    public interface Owner {
        void onMovementRevoked(ServerPlayer player);
    }

    public record Lease(UUID playerId, UUID movementId) {}

    private record Claim(Lease lease, Owner owner) {}

    private static final Map<UUID, Claim> ACTIVE = new HashMap<>();

    private WeaponSkillMovementArbiter() {}

    public static synchronized Lease claim(
            ServerPlayer player,
            Owner owner
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(owner, "owner");
        Lease lease = new Lease(player.getUUID(), UUID.randomUUID());
        Claim previous = ACTIVE.put(
                player.getUUID(),
                new Claim(lease, owner)
        );
        if (previous != null) {
            try {
                previous.owner().onMovementRevoked(player);
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stopping replaced weapon-skill movement failed for {}",
                        player.getGameProfile().getName(),
                        exception
                );
            }
        }
        return lease;
    }

    public static synchronized boolean owns(Lease lease) {
        if (lease == null) {
            return false;
        }
        Claim claim = ACTIVE.get(lease.playerId());
        return claim != null && claim.lease().equals(lease);
    }

    public static synchronized boolean release(Lease lease) {
        if (!owns(lease)) {
            return false;
        }
        ACTIVE.remove(lease.playerId());
        return true;
    }

    /**
     * Ends the player's current skill-driven movement before an authored
     * one-shot displacement takes control. The claim is removed before its
     * owner is notified so callback code cannot accidentally release a newer
     * movement.
     */
    public static synchronized boolean revokeCurrent(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Claim previous = ACTIVE.remove(player.getUUID());
        if (previous == null) {
            return false;
        }
        try {
            previous.owner().onMovementRevoked(player);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stopping displaced weapon-skill movement failed for {}",
                    player.getGameProfile().getName(),
                    exception
            );
        }
        return true;
    }

    /** Revokes skill movement only when a forced-movement target is a player. */
    public static boolean revokeCurrentIfPlayer(LivingEntity target) {
        return target instanceof ServerPlayer player
                && revokeCurrent(player);
    }

    public static synchronized void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
