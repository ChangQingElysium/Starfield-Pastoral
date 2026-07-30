package com.stardew.craft.combat.skill;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    private WeaponSkillDamage() {}

    /**
     * Preferred overload for runtime and delayed hits that own an immutable
     * release-time weapon snapshot.
     */
    public static boolean apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick
    ) {
        return apply(
                attacker,
                target,
                skillContext,
                weaponSnapshot,
                expireTick,
                AttackGatePolicy.SKILL_DAMAGE
        );
    }

    /**
     * Explicit-snapshot overload for delayed hits that must recheck the
     * cancellable player attack hook at impact.
     */
    public static boolean apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick,
            AttackGatePolicy attackGatePolicy
    ) {
        Objects.requireNonNull(weaponSnapshot, "weaponSnapshot");
        if (!(weaponSnapshot.weapon().getItem() instanceof IStardewWeapon)) {
            throw new IllegalArgumentException(
                    "weaponSnapshot must contain a Stardew weapon"
            );
        }
        return applyInternal(
                attacker,
                target,
                skillContext,
                weaponSnapshot,
                expireTick,
                attackGatePolicy
        );
    }

    /**
     * Compatibility overload for callers that still rely on the active
     * runtime context or the attacker's current Stardew weapon.
     */
    public static boolean apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            long expireTick
    ) {
        return apply(
                attacker,
                target,
                skillContext,
                expireTick,
                AttackGatePolicy.SKILL_DAMAGE
        );
    }

    /**
     * Compatibility overload with an explicit impact permission policy.
     */
    public static boolean apply(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            long expireTick,
            AttackGatePolicy attackGatePolicy
    ) {
        return applyInternal(
                attacker,
                target,
                skillContext,
                null,
                expireTick,
                attackGatePolicy
        );
    }

    private static boolean applyInternal(
            Player attacker,
            LivingEntity target,
            SkillContext skillContext,
            WeaponDamageSnapshot weaponSnapshot,
            long expireTick,
            AttackGatePolicy attackGatePolicy
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(skillContext, "skillContext");
        Objects.requireNonNull(attackGatePolicy, "attackGatePolicy");
        if (!(attacker instanceof ServerPlayer serverPlayer)
                || target.level() != serverPlayer.level()) {
            return false;
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
            return false;
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
                return false;
            }
            return target.hurt(
                    serverPlayer.damageSources().playerAttack(serverPlayer),
                    pipelineInputDamage(resolvedSnapshot, skillContext)
            );
        } finally {
            clearUnconsumedContext(serverPlayer, nowTick);
        }
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
        ItemStack currentWeapon = player.getMainHandItem();
        if (!(currentWeapon.getItem() instanceof IStardewWeapon weaponItem)) {
            return null;
        }
        return WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID,
                        weaponItem.getWeaponId()
                ),
                currentWeapon
        );
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
