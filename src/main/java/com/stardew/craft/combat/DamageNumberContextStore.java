package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.SkillContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Binds display and post-hit metadata to one synchronous hurt invocation.
 *
 * <p>A player can cause another hurt call while the first call is still in its
 * Pre event. Keeping a LIFO stack preserves the outer frame until its matching
 * Post event and prevents nested or unrelated player-owned damage from borrowing
 * another hit's skill, critical, or release-weapon metadata.</p>
 */
public final class DamageNumberContextStore {
    private static final Map<UUID, Deque<BoundMeta>> ACTIVE = new HashMap<>();

    private DamageNumberContextStore() {}

    public static synchronized void bind(
            Player player,
            LivingEntity target,
            DamageSource source,
            String skillId,
            boolean crit,
            WeaponDamageSnapshot weaponSnapshot,
            WeaponCombatIdentity.Resolved weaponIdentity,
            SkillContext skillContext,
            DamageOutcome damageOutcome,
            boolean primaryTarget,
            boolean sweepTarget,
            boolean inStardewDimension,
            boolean targetAliveBeforeApplication,
            float knockbackStrength,
            long expireTick
    ) {
        UUID playerId = player.getUUID();
        ACTIVE.computeIfAbsent(playerId, ignored -> new ArrayDeque<>())
                .push(new BoundMeta(
                        target.getUUID(),
                        source,
                        expireTick,
                        new Meta(
                                skillId,
                                crit,
                                weaponSnapshot,
                                weaponIdentity,
                                skillContext,
                                damageOutcome,
                                primaryTarget,
                                sweepTarget,
                                inStardewDimension,
                                targetAliveBeforeApplication,
                                knockbackStrength
                        )
                ));
    }

    /**
     * Consumes only the top frame belonging to this exact damage sequence.
     * A mismatched Post event is unrelated damage and must leave the active
     * Stardew frame untouched.
     */
    public static synchronized Meta consume(
            Player player,
            LivingEntity target,
            DamageSource source,
            long nowTick
    ) {
        UUID playerId = player.getUUID();
        Deque<BoundMeta> stack = ACTIVE.get(playerId);
        if (stack == null) {
            return null;
        }
        while (!stack.isEmpty() && stack.peek().expireTick() < nowTick) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            ACTIVE.remove(playerId);
            return null;
        }

        BoundMeta bound = stack.peek();
        if (!bound.matches(target, source)) {
            return null;
        }

        stack.pop();
        if (stack.isEmpty()) {
            ACTIVE.remove(playerId);
        }
        return bound.meta();
    }

    public static synchronized void clear(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        ACTIVE.remove(playerId);
    }

    public record Meta(
            String skillId,
            boolean crit,
            WeaponDamageSnapshot boundWeapon,
            WeaponCombatIdentity.Resolved weaponIdentity,
            SkillContext skillContext,
            DamageOutcome damageOutcome,
            boolean primaryTarget,
            boolean sweepTarget,
            boolean inStardewDimension,
            boolean targetAliveBeforeApplication,
            float knockbackStrength
    ) {
        public Optional<WeaponDamageSnapshot> weaponSnapshot() {
            return Optional.ofNullable(boundWeapon);
        }
    }

    private record BoundMeta(
            UUID targetId,
            DamageSource source,
            long expireTick,
            Meta meta
    ) {
        private boolean matches(LivingEntity target, DamageSource candidate) {
            return targetId.equals(target.getUUID()) && source == candidate;
        }
    }

}
