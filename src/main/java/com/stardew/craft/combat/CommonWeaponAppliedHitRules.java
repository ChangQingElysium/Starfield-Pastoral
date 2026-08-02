package com.stardew.craft.combat;

import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.trinket.TrinketEffectHandler;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Common applied-hit rules that do not belong to one authored skill. */
final class CommonWeaponAppliedHitRules {
    private CommonWeaponAppliedHitRules() {
    }

    static void applyKnockback(ResolvedWeaponHit hit) {
        float strength = hit.frame().knockbackStrength();
        if (!hit.dealtPositiveDamage() || strength <= 0.0F) {
            return;
        }
        Player player = hit.attacker();
        LivingEntity target = hit.target();
        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();
        if (dx * dx + dz * dz > 0.0001D) {
            target.knockback(strength, dx, dz);
        }
    }

    static void applyVampiricEnchantment(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !hit.killedByAttacker()
                || !(hit.attacker() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack weapon = hit.weapon();
        if (StardewEnchantments.has(weapon, StardewEnchantments.VAMPIRIC)
                && serverPlayer.getRandom().nextFloat() < 0.09F) {
            CombatHealing.healFraction(serverPlayer, 0.10F, 1.0F);
        }
    }

    static void notifyTrinkets(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        TrinketEffectHandler.onDamageMonster(
                player,
                hit.target(),
                Math.max(1, Math.round(hit.appliedDamage())),
                hit.damageOutcome().isCrit()
        );
    }

    static void applyKillRewards(ResolvedWeaponHit hit) {
        if (!hit.dealtPositiveDamage()
                || !hit.killedByAttacker()
                || !(hit.attacker() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        LivingEntity target = hit.target();
        if (WeaponForgeCombatRules.hasDragonToothBonus(
                hit.weapon(),
                "slime_gatherer"
        ) && WeaponForgeCombatRules.isSlimeGathererTarget(target)) {
            WeaponForgeCombatRules.dropSlimeGathererReward(
                    target,
                    serverPlayer
            );
        }
        if (hit.inStardewDimension()
                && CombatTargetRules.isCombatMonster(target)) {
            PlayerStardewDataAPI.addExperience(
                    serverPlayer,
                    SkillType.COMBAT,
                    CombatExperienceRules.experienceForKill(target)
            );
        }
    }
}
