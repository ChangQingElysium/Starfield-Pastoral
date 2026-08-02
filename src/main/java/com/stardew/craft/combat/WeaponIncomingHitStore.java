package com.stardew.craft.combat;

import java.util.IdentityHashMap;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

/**
 * Binds one authoritative weapon roll to the exact synchronous hurt call.
 *
 * <p>The roll is created during {@code LivingIncomingDamageEvent}, before
 * Minecraft applies shields, armor, enchantments, resistance, or absorption.
 * The matching Pre event can therefore preserve the exact native protection
 * result without reconstructing a weapon or rolling damage a second time.</p>
 */
final class WeaponIncomingHitStore {
    private static final IdentityHashMap<DamageContainer, BoundHit> ACTIVE =
            new IdentityHashMap<>();

    private WeaponIncomingHitStore() {
    }

    static synchronized void bind(
            DamageContainer container,
            EvaluatedWeaponHit hit,
            long nowTick,
            long expireTick
    ) {
        discardExpired(nowTick);
        ACTIVE.put(
                container,
                new BoundHit(hit.attacker().getUUID(), expireTick, hit)
        );
    }

    static synchronized EvaluatedWeaponHit consume(
            DamageContainer container,
            long nowTick
    ) {
        BoundHit bound = ACTIVE.remove(container);
        if (bound == null) {
            return null;
        }
        if (bound.expireTick() < nowTick) {
            bound.hit().preparationReservation().release();
            return null;
        }
        return bound.hit();
    }

    static synchronized void discard(DamageContainer container) {
        BoundHit bound = ACTIVE.remove(container);
        if (bound != null) {
            bound.hit().preparationReservation().release();
        }
    }

    static synchronized void clear(Player attacker) {
        if (attacker != null) {
            ACTIVE.entrySet().removeIf(entry -> {
                BoundHit bound = entry.getValue();
                if (!bound.attackerId().equals(attacker.getUUID())) {
                    return false;
                }
                bound.hit().preparationReservation().release();
                return true;
            });
        }
    }

    static synchronized void discardExpired(long nowTick) {
        ACTIVE.entrySet().removeIf(entry -> {
            BoundHit bound = entry.getValue();
            if (bound.expireTick() >= nowTick) {
                return false;
            }
            bound.hit().preparationReservation().release();
            return true;
        });
    }

    private record BoundHit(
            UUID attackerId,
            long expireTick,
            EvaluatedWeaponHit hit
    ) {
    }
}
