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

    static void applyNativeHitTiming(ResolvedWeaponHit hit) {
        if (!(hit.target() instanceof com.stardew.craft.monster.StardewMonsterEntity monster)) return;
        boolean dagger = WeaponStats.fromItemStack(hit.weapon()).getWeaponType() == WeaponType.DAGGER;
        if (hit.skillId() == null || hit.skillId().isBlank() || "normal".equals(hit.skillId())) monster.sourceWeaponRecovery(dagger);
        else if (dagger && monster.isAlive()
                && !com.stardew.craft.combat.skill.YetiFreezeTracker.isMovementLocked(monster, hit.gameTick())) {
            // Authored multi-hit skills keep their release cadence and receive the source 50 ms stagger.
            com.stardew.craft.combat.skill.YetiFreezeTracker.apply(monster, hit.gameTick(), 1);
        }
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
            // 原版 VampiricEnchantment：按被杀怪物最大生命 ±随机 取 10%，至少 1
            int monsterMax = Math.max(0, Math.round(hit.target().getMaxHealth()));
            int low = -monsterMax / 10;
            int high = monsterMax / 15 + 1;
            int rolled = monsterMax + low + serverPlayer.getRandom().nextInt(high - low);
            CombatHealing.heal(serverPlayer, Math.max(1, (int) (rolled * 0.1F)));
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
        if (target instanceof com.stardew.craft.monster.StardewMonsterEntity monster
                && !monster.claimSettlement(com.stardew.craft.monster.MonsterState.Settlement.WEAPON_REWARDS)) return;
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
            int experience = CombatExperienceRules.experienceForKill(target);
            // GameLocation.monsterKilled: kills on the Farm give max(1, experience / 3).
            if (com.stardew.craft.core.FarmAreaResolver.isInFarmArea(target.level(), target.blockPosition())) {
                experience = Math.max(1, experience / 3);
            }
            PlayerStardewDataAPI.addExperience(
                    serverPlayer,
                    SkillType.COMBAT,
                    experience
            );
        }
    }
}
