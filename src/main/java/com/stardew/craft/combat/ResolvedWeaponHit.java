package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Immutable identity and calculation frame for one exact weapon hurt call.
 *
 * <p>The frame is created only after the matching {@code Pre} metadata has
 * been consumed by the exact {@link DamageSource} identity. Weapon identity,
 * authored skill context, calculation outcome, sweep classification, and
 * release weapon are therefore never reconstructed from mutable player state
 * in {@code Post}.</p>
 */
public record ResolvedWeaponHit(
        Player attacker,
        LivingEntity target,
        DamageSource source,
        long gameTick,
        DamageNumberContextStore.Meta frame,
        float appliedDamage
) {
    public ResolvedWeaponHit {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(
                frame.boundWeapon(),
                "frame.boundWeapon"
        );
    }

    public static ResolvedWeaponHit from(
            LivingDamageEvent.Post event,
            Player attacker,
            DamageNumberContextStore.Meta frame,
            long gameTick
    ) {
        return new ResolvedWeaponHit(
                attacker,
                event.getEntity(),
                event.getSource(),
                gameTick,
                frame,
                event.getNewDamage()
        );
    }

    public String skillId() {
        return frame.skillId();
    }

    public boolean displayCritical() {
        return frame.crit();
    }

    public Optional<WeaponDamageSnapshot> weaponSnapshot() {
        return frame.weaponSnapshot();
    }

    public ItemStack weapon() {
        return frame.boundWeapon().weapon();
    }

    public WeaponCombatIdentity.Resolved weaponIdentity() {
        return frame.weaponIdentity();
    }

    public SkillContext authoredSkillContext() {
        return frame.skillContext();
    }

    public DamageOutcome damageOutcome() {
        return frame.damageOutcome();
    }

    public boolean primaryTarget() {
        return frame.primaryTarget();
    }

    public boolean sweepTarget() {
        return frame.sweepTarget();
    }

    public boolean inStardewDimension() {
        return frame.inStardewDimension();
    }

    public boolean dealtPositiveDamage() {
        return isPositiveHealthDamage(
                frame.targetAliveBeforeApplication(),
                appliedDamage
        );
    }

    public boolean killedByAttacker() {
        return isKillTransition(
                frame.targetAliveBeforeApplication(),
                MineMonsterSpawnHandler.isCollapsedMummy(target),
                target.isAlive(),
                target.getHealth()
        );
    }

    static boolean isKillTransition(
            boolean aliveBeforeApplication,
            boolean collapsedMummy,
            boolean aliveAfterApplication,
            float healthAfterApplication
    ) {
        return aliveBeforeApplication
                && !collapsedMummy
                && (!aliveAfterApplication || healthAfterApplication <= 0.0F);
    }

    static boolean isPositiveHealthDamage(
            boolean aliveBeforeApplication,
            float appliedDamage
    ) {
        return aliveBeforeApplication && appliedDamage > 0.0F;
    }
}
