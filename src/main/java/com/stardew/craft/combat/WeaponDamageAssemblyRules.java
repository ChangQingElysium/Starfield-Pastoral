package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.handler.DarkSwordBloodMoonSkillHandler;
import com.stardew.craft.combat.skill.handler.SteelSpineFurySkillHandler;
import com.stardew.craft.enchantment.StardewEnchantments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Ordered modifiers applied while assembling one authoritative damage request. */
final class WeaponDamageAssemblyRules {
    private WeaponDamageAssemblyRules() {
    }

    static Result apply(
            DamageRequest.Builder request,
            Player player,
            LivingEntity target,
            ItemStack weapon,
            SkillContext skillContext,
            SteelSpineFurySkillHandler.AttackBoost spineBoost,
            boolean sweepTarget,
            WeaponType weaponType,
            boolean inStardewDimension,
            long gameTick
    ) {
        if (!inStardewDimension) {
            // Minecraft already committed armor/toughness, Protection, and
            // Resistance before LivingDamageEvent.Pre.
            request.defense(0.0F, false);
        }

        if (spineBoost != null) {
            request.addPostDefenseAdjustment(DamageAdjustment.add(
                    "steel_spine_bonus",
                    spineBoost.bonusDamage()
            ));
        }

        boolean bloodMoonActive = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && DarkSwordBloodMoonSkillHandler.isActive(
                        serverPlayer,
                        gameTick
                );
        if (bloodMoonActive) {
            request.addPostDefenseAdjustment(DamageAdjustment.multiply(
                    "dark_sword_blood_moon",
                    DarkSwordBloodMoonSkillHandler.getDamageBonusMultiplier(
                            (net.minecraft.server.level.ServerPlayer) player,
                            gameTick
                    )
            ));
        }

        if (StardewEnchantments.has(weapon, StardewEnchantments.BUG_KILLER)
                && StardewEnchantments.isBugKillerTarget(target)) {
            request.addPreDefenseAdjustment(
                    DamageAdjustment.multiplyFloor(
                            "enchantment_bug_killer",
                            2.0F
                    )
            );
        }
        if (StardewEnchantments.has(weapon, StardewEnchantments.CRUSADER)
                && StardewEnchantments.isCrusaderTarget(target)) {
            request.addPreDefenseAdjustment(
                    DamageAdjustment.multiplyFloor(
                            "enchantment_crusader",
                            1.5F
                    )
            );
        }
        if (WeaponForgeCombatRules.hasDragonToothBonus(weapon, "slime_slayer")
                && WeaponForgeCombatRules.isSlimeSlayerTarget(target)) {
            request.addPreDefenseAdjustment(DamageAdjustment.multiply(
                            "dragontooth_slime_slayer",
                            1.33F
                    ))
                    .addPreDefenseAdjustment(DamageAdjustment.addFloor(
                            "dragontooth_slime_slayer_flat",
                            1.0F
                    ));
        }

        return new Result(bloodMoonActive);
    }

    record Result(boolean bloodMoonActive) {
    }
}
