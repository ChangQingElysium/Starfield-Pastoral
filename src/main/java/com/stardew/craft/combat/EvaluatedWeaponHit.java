package com.stardew.craft.combat;

import com.stardew.craft.combat.equipment.EquipmentStats;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.handler.SteelSpineFurySkillHandler;
import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Exact result of the Stardew damage pipeline before vanilla applies health loss. */
public record EvaluatedWeaponHit(
        Player attacker,
        LivingEntity target,
        DamageSource source,
        ItemStack weapon,
        WeaponCombatIdentity.Resolved weaponIdentity,
        WeaponDamageSnapshot weaponSnapshot,
        SkillContext skillContext,
        WeaponHitPreparation.Reservation preparationReservation,
        SteelSpineFurySkillHandler.AttackBoost steelSpineBoost,
        DamageOutcome outcome,
        EquipmentStats equipmentStats,
        boolean primaryTarget,
        boolean sweepTarget,
        boolean inStardewDimension,
        boolean bloodMoonActive,
        long gameTick
) {
    public EvaluatedWeaponHit {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(weaponIdentity, "weaponIdentity");
        Objects.requireNonNull(weaponSnapshot, "weaponSnapshot");
        Objects.requireNonNull(skillContext, "skillContext");
        Objects.requireNonNull(
                preparationReservation,
                "preparationReservation"
        );
        Objects.requireNonNull(outcome, "outcome");
    }

    public float finalDamage() {
        return outcome.getFinalDamage();
    }

    public boolean successful() {
        return !outcome.isDodged() && finalDamage() > 0.0F;
    }
}
