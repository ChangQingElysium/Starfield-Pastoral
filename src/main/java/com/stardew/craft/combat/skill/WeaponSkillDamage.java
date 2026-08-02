package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponCombatIdentity;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Server-side entry point for a skill hit that must use the normal Stardew
 * weapon damage pipeline.
 *
 * <p>This class does not calculate or apply final damage itself. It binds the
 * authored skill context, emits a regular player-attack damage event, and lets
 * {@code WeaponCombatEvents} resolve the release weapon, critical hit,
 * professions, equipment, defense, enchantments, and post-hit hooks.</p>
 */
public final class WeaponSkillDamage {
    static final float MINIMUM_PIPELINE_INPUT_DAMAGE = 0.001F;

    public enum AttackGatePolicy {
        /**
         * A skill-authored damage event that does not replay the vanilla
         * player attack permission hook.
         */
        SKILL_DAMAGE,
        /**
         * Rechecks the cancellable player attack hook at the actual impact
         * tick after binding skill context and before emitting damage.
         */
        RESPECT_AT_IMPACT
    }

    public enum HitCooldownPolicy {
        /** Keeps vanilla's shared post-hit cooldown behavior. */
        RESPECT_VANILLA,
        /**
         * Lets this authored sequence hit pass vanilla's shared cooldown
         * check without clearing the target's global combat state.
         */
        BYPASS_FOR_AUTHORED_SEQUENCE
    }

    private WeaponSkillDamage() {}

    /**
     * Preferred overload for runtime and delayed hits that own an immutable
     * release-time weapon snapshot.
     */
    public static void apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick
    ) {
        apply(
                attacker,
                target,
                skillContext,
                weaponSnapshot,
                expireTick,
                AttackGatePolicy.SKILL_DAMAGE,
                HitCooldownPolicy.RESPECT_VANILLA
        );
    }

    /**
     * Explicit-snapshot overload for delayed hits that must recheck the
     * cancellable player attack hook at impact.
     */
    public static void apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick,
            AttackGatePolicy attackGatePolicy
    ) {
        apply(
                attacker,
                target,
                skillContext,
                weaponSnapshot,
                expireTick,
                attackGatePolicy,
                HitCooldownPolicy.RESPECT_VANILLA
        );
    }

    /**
     * Fully explicit emission policy for an immutable release snapshot.
     */
    public static void apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick,
            AttackGatePolicy attackGatePolicy,
            HitCooldownPolicy hitCooldownPolicy
    ) {
        Objects.requireNonNull(weaponSnapshot, "weaponSnapshot");
        if (!WeaponCombatIdentity.isWeapon(weaponSnapshot.weapon())) {
            throw new IllegalArgumentException(
                    "weaponSnapshot must contain a Stardew weapon"
            );
        }
        applyInternal(
                attacker,
                target,
                skillContext,
                weaponSnapshot,
                expireTick,
                attackGatePolicy,
                hitCooldownPolicy
        );
    }

    /**
     * Compatibility overload for callers that still rely on the active
     * runtime context. Missing release identity cancels the hit.
     */
    public static void apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            long expireTick
    ) {
        apply(
                attacker,
                target,
                skillContext,
                expireTick,
                AttackGatePolicy.SKILL_DAMAGE,
                HitCooldownPolicy.RESPECT_VANILLA
        );
    }

    /**
     * Compatibility overload with an explicit impact permission policy.
     */
    public static void apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            long expireTick,
            AttackGatePolicy attackGatePolicy
    ) {
        apply(
                attacker,
                target,
                skillContext,
                expireTick,
                attackGatePolicy,
                HitCooldownPolicy.RESPECT_VANILLA
        );
    }

    /**
     * Fully explicit emission policy for a compatibility snapshot lookup.
     */
    public static void apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            long expireTick,
            AttackGatePolicy attackGatePolicy,
            HitCooldownPolicy hitCooldownPolicy
    ) {
        applyInternal(
                attacker,
                target,
                skillContext,
                null,
                expireTick,
                attackGatePolicy,
                hitCooldownPolicy
        );
    }

    private static void applyInternal(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick,
            AttackGatePolicy attackGatePolicy,
            HitCooldownPolicy hitCooldownPolicy
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(skillContext, "skillContext");
        Objects.requireNonNull(attackGatePolicy, "attackGatePolicy");
        Objects.requireNonNull(hitCooldownPolicy, "hitCooldownPolicy");
        if (!(attacker instanceof ServerPlayer serverPlayer)
                || target.level() != serverPlayer.level()) {
            return;
        }

        long nowTick = serverPlayer.level().getGameTime();
        if (expireTick < nowTick) {
            throw new IllegalArgumentException(
                    "expireTick cannot precede the current server tick"
            );
        }

        WeaponDamageSnapshot resolvedSnapshot = resolveSnapshot(
                serverPlayer,
                skillContext,
                weaponSnapshot
        );
        if (resolvedSnapshot == null) {
            return;
        }
        WeaponSkillContextStore.setPending(
                serverPlayer,
                skillContext,
                resolvedSnapshot,
                expireTick
        );

        try {
            if (attackGatePolicy == AttackGatePolicy.RESPECT_AT_IMPACT
                    && !CommonHooks.onPlayerAttackTarget(
                            serverPlayer,
                            target
                    )) {
                return;
            }
            DamageSource source = applyHitCooldownPolicy(
                    serverPlayer.damageSources().playerAttack(serverPlayer),
                    hitCooldownPolicy
            );
            target.hurt(
                    source,
                    pipelineInputDamage(resolvedSnapshot, skillContext)
            );
        } finally {
            clearUnconsumedContext(serverPlayer, nowTick);
        }
    }

    static DamageSource applyHitCooldownPolicy(
            DamageSource source,
            HitCooldownPolicy policy
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(policy, "policy");
        return policy == HitCooldownPolicy.BYPASS_FOR_AUTHORED_SEQUENCE
                ? HitCooldownDamageSource.bypassVanillaCooldown(source)
                : source;
    }

    private static WeaponDamageSnapshot resolveSnapshot(
            ServerPlayer player,
            SkillContext skillContext,
            WeaponDamageSnapshot provided
    ) {
        if (provided != null) {
            return provided;
        }
        WeaponDamageSnapshot runtimeSnapshot =
                WeaponSkillRuntime.releaseWeaponSnapshot(
                        player.getUUID(),
                        skillContext.getSkillId()
                ).orElse(null);
        if (runtimeSnapshot != null) {
            return runtimeSnapshot;
        }
        return null;
    }

    /**
     * Supplies other damage listeners with a meaningful pre-mitigation input.
     * StardewCraft's authoritative event handler still performs the exact
     * random roll, critical, defense, profession, and enchantment calculation.
     */
    private static float pipelineInputDamage(
            WeaponDamageSnapshot weaponSnapshot,
            SkillContext skillContext
    ) {
        WeaponStats stats = WeaponStats.fromItemStack(
                weaponSnapshot.weapon()
        );
        float candidate = stats.getAverageDamage()
                * Math.max(0.0F, skillContext.getDamageMultiplier());
        if (!Float.isFinite(candidate)) {
            throw new IllegalArgumentException(
                    "Skill damage input must be finite"
            );
        }
        return Math.max(MINIMUM_PIPELINE_INPUT_DAMAGE, candidate);
    }

    private static void clearUnconsumedContext(
            ServerPlayer player,
            long nowTick
    ) {
        if (WeaponSkillContextStore.hasPending(player, nowTick)) {
            WeaponSkillContextStore.consume(player, nowTick);
        }
    }
}
