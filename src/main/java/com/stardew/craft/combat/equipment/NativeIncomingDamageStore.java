package com.stardew.craft.combat.equipment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Exact Pre-to-Post bridge for native damage entering protection. */
final class NativeIncomingDamageStore {
    private static final Map<UUID, Deque<BoundDamage>> ACTIVE = new HashMap<>();

    private NativeIncomingDamageStore() {
    }

    static synchronized void bind(
            Player target,
            DamageSource source,
            float damageEnteringProtection,
            long expireTick
    ) {
        ACTIVE.computeIfAbsent(target.getUUID(), ignored -> new ArrayDeque<>())
                .push(new BoundDamage(
                        source,
                        damageEnteringProtection,
                        expireTick
                ));
    }

    static synchronized Float consume(
            Player target,
            DamageSource source,
            long nowTick
    ) {
        Deque<BoundDamage> stack = ACTIVE.get(target.getUUID());
        if (stack == null) return null;
        while (!stack.isEmpty() && stack.peek().expireTick() < nowTick) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            ACTIVE.remove(target.getUUID());
            return null;
        }
        BoundDamage bound = stack.peek();
        if (bound.source() != source) return null;
        stack.pop();
        if (stack.isEmpty()) {
            ACTIVE.remove(target.getUUID());
        }
        return bound.damageEnteringProtection();
    }

    static synchronized void clear(Player target) {
        if (target != null) {
            ACTIVE.remove(target.getUUID());
        }
    }

    private record BoundDamage(
            DamageSource source,
            float damageEnteringProtection,
            long expireTick
    ) {
    }
}
