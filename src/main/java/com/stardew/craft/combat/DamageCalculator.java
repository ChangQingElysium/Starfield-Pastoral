package com.stardew.craft.combat;

import com.stardew.craft.combat.equipment.EquipmentStats;
import com.stardew.craft.combat.skill.DragontoothShivBreathTracker;
import com.stardew.craft.combat.skill.ElfBladeMarkTracker;
import com.stardew.craft.combat.skill.OssifiedExecutionTracker;
import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.WindSpireTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Collects game state into an immutable {@link DamageRequest}.
 *
 * <p>All arithmetic is owned by {@link DamagePipeline}; this class is the
 * compatibility boundary between Minecraft entities and the pure pipeline.</p>
 */
public final class DamageCalculator {
    public static final float DEFAULT_CRIT_MULTIPLIER = 3.0f;
    public static final float DEFAULT_CRIT_CHANCE = 0.02f;

    private DamageCalculator() {}

    public static DamageRequest createPlayerDamageRequest(
            Player attacker,
            LivingEntity target,
            ItemStack weapon,
            SkillContext skillContext,
            EquipmentStats equipmentStats
    ) {
        SkillContext resolvedSkill = skillContext != null
                ? skillContext
                : SkillContext.normalAttack();
        WeaponStats weaponStats = WeaponStats.fromItemStack(weapon);
        MonsterStats targetStats = MonsterStats.fromEntity(target);
        PlayerStardewData playerData = attacker instanceof ServerPlayer serverPlayer
                ? PlayerDataManager.getPlayerData(serverPlayer)
                : null;
        String weaponId = weapon.getItem() instanceof IStardewWeapon stardewWeapon
                ? stardewWeapon.getWeaponId()
                : "unknown_weapon";
        long nowTick = attacker.level() != null ? attacker.level().getGameTime() : 0L;
        float ringAttackMultiplier = equipmentStats != null
                ? 1.0f + equipmentStats.getAttackMultiplier()
                : 1.0f;
        float minimumDamage = applyAttackMultiplierToWeaponRange(
                weaponStats.getMinDamage(),
                ringAttackMultiplier
        );
        float maximumDamage = applyAttackMultiplierToWeaponRange(
                weaponStats.getMaxDamage(),
                ringAttackMultiplier
        );

        DamageRequest.Builder request = DamageRequest.builder(weaponId)
                .sourceKind(DamageRequest.SourceKind.PLAYER_WEAPON)
                .skillId(resolvedSkill.getSkillId())
                .baseDamage(minimumDamage, maximumDamage)
                .variance(1.0f, 1.0f)
                .defense(targetStats.getResilience(), resolvedSkill.isIgnoreDefense())
                .minimumFinalDamage(1.0f)
                .accuracy(targetStats.getMissChance(), weaponStats.getPrecision())
                .inStardewDimension(DimensionDamageMapper.isInStardewDimension(target));

        if (attacker instanceof ServerPlayer serverPlayer) {
            if ("galaxy_sword".equals(weaponId)) {
                addIfNonZero(request, "startrail_stacks", StartrailTracker.getStacks(serverPlayer) * 2.0f);
            } else if ("infinity_blade".equals(weaponId)) {
                addIfNonZero(request, "singularity_stacks", SingularityTracker.getStacks(serverPlayer) * 2.0f);
            }
        }
        if (equipmentStats != null) {
            addPreDefenseIfNonZero(request, "equipment_attack", equipmentStats.getAttack() * 3.0f);
        }
        if (playerData != null) {
            addPreDefenseIfNonZero(
                    request,
                    "temporary_attack",
                    Math.max(0, playerData.getTempAttackBonus()) * 3.0f
            );
        }

        float criticalChance = calculateCriticalChance(
                weaponStats,
                weaponId,
                attacker,
                target,
                playerData,
                equipmentStats,
                resolvedSkill,
                nowTick
        );
        float criticalMultiplier = calculateCriticalMultiplier(
                weaponStats,
                attacker,
                target,
                equipmentStats,
                nowTick
        );
        if (attacker instanceof ServerPlayer serverPlayer
                && "dragontooth_shiv".equals(weaponId)
                && DragontoothShivBreathTracker.isActive(serverPlayer, nowTick)) {
            criticalMultiplier = DEFAULT_CRIT_MULTIPLIER;
        }
        request.critical(
                criticalChance,
                criticalMultiplier,
                resolvedSkill.isGuaranteedCrit()
        );

        if (playerData != null && playerData.hasProfession(ProfessionType.FIGHTER)) {
            request.addPreDefenseAdjustment(DamageAdjustment.multiplyCeil("profession_fighter", 1.10f));
        }
        if (playerData != null && playerData.hasProfession(ProfessionType.BRUTE)) {
            request.addPreDefenseAdjustment(DamageAdjustment.multiplyCeil("profession_brute", 1.15f));
        }
        if (playerData != null && playerData.hasProfession(ProfessionType.DESPERADO)) {
            request.addPreDefenseAdjustment(DamageAdjustment.multiplyFloor("profession_desperado", 2.0f));
        }
        request.addPreDefenseAdjustment(DamageAdjustment.multiply(
                "skill:" + resolvedSkill.getSkillId(),
                resolvedSkill.getDamageMultiplier()
        ));

        MobEffectInstance vulnerable = target.getEffect(ModMobEffects.VULNERABLE);
        if (vulnerable != null) {
            float multiplier = 1.0f + 0.10f * (vulnerable.getAmplifier() + 1);
            request.addPreDefenseAdjustment(DamageAdjustment.multiply("target_vulnerable", multiplier));
        }
        return request.build();
    }

    private static float calculateCriticalChance(
            WeaponStats weaponStats,
            String weaponId,
            Player attacker,
            LivingEntity target,
            PlayerStardewData playerData,
            EquipmentStats equipmentStats,
            SkillContext skillContext,
            long nowTick
    ) {
        float criticalChance = weaponStats.getCritChance() + weaponStats.getBonusCritChance();
        if (weaponStats.getWeaponType() == WeaponType.DAGGER) {
            criticalChance = (criticalChance + 0.005f) * 1.12f;
        }
        if (equipmentStats != null) {
            criticalChance *= 1.0f + equipmentStats.getCritChance();
        }
        if (attacker.hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_5)) {
            criticalChance += 0.1f;
        }
        if (playerData != null && playerData.hasProfession(ProfessionType.SCOUT)) {
            criticalChance *= 1.5f;
        }
        float luckLevel = playerData != null ? playerData.getLuckLevel() : 0.0f;
        if (equipmentStats != null) {
            luckLevel += equipmentStats.getLuck();
        }
        criticalChance = applyLuckToCriticalChance(criticalChance, luckLevel);
        criticalChance += Math.max(0.0f, skillContext.getCritChanceBonus());
        if (attacker instanceof ServerPlayer serverPlayer) {
            criticalChance += WindSpireTracker.getCritChanceBonus(
                    serverPlayer,
                    skillContext,
                    nowTick
            );
        }
        criticalChance += OssifiedMarkTracker.getCritChanceBonus(target, attacker, nowTick);
        criticalChance += ElfBladeMarkTracker.getCritChanceBonus(target, attacker, nowTick);

        MobEffect weakPointEffect = ModMobEffects.WEAK_POINT.get();
        MobEffectInstance weakPoint = target.getEffect(Holder.direct(weakPointEffect));
        if (weakPoint != null) {
            criticalChance += 0.05f * (weakPoint.getAmplifier() + 1);
        }
        if (attacker instanceof ServerPlayer serverPlayer
                && weaponStats != null
                && "infinity_blade".equals(weaponId)
                && SingularityTracker.isEvolved(serverPlayer)) {
            criticalChance += 0.20f;
        }
        return criticalChance;
    }

    private static float calculateCriticalMultiplier(
            WeaponStats weaponStats,
            Player attacker,
            LivingEntity target,
            EquipmentStats equipmentStats,
            long nowTick
    ) {
        float multiplier = DEFAULT_CRIT_MULTIPLIER + weaponStats.getBonusCritPower() / 100.0f;
        if (equipmentStats != null) {
            multiplier *= 1.0f + equipmentStats.getCritPower();
        }
        multiplier += OssifiedExecutionTracker.getCritDamageBonus(attacker, target, nowTick);
        return multiplier;
    }

    private static void addIfNonZero(DamageRequest.Builder request, String id, float value) {
        if (value != 0.0f) {
            request.addBaseAdjustment(DamageAdjustment.add(id, value));
        }
    }

    private static void addPreDefenseIfNonZero(DamageRequest.Builder request, String id, float value) {
        if (value != 0.0f) {
            request.addPreDefenseAdjustment(DamageAdjustment.add(id, value));
        }
    }

    static float applyAttackMultiplierToWeaponRange(float damage, float multiplier) {
        if (multiplier == 1.0f) {
            return damage;
        }
        return (float) (int) (damage * Math.max(0.0f, multiplier));
    }

    static float applyLuckToCriticalChance(float criticalChance, float luckLevel) {
        return criticalChance + luckLevel * (criticalChance / 40.0f);
    }
}
