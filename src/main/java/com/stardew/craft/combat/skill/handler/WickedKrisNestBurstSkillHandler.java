package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Wicked Kris's original Nest Burst.
 */
public final class WickedKrisNestBurstSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final float ENERGY_COST = 10.0F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int POISON_DURATION_TICKS = 200;
    public static final int POISON_STACKS = 5;
    public static final boolean SCHEDULE_DETONATION = true;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        boolean coolingDown = WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        );
        if (coolingDown) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }

        // Check the target before any resource mutation. The legacy branch paid
        // energy first and charged an otherwise rejected empty cast.
        LivingEntity target = findTarget(context);
        if (target == null) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = findTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Nest Burst target is no longer available"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        if (!context.player().getAbilities().instabuild
                && !PlayerStardewDataAPI.consumeEnergy(context.player(), ENERGY_COST)) {
            throw new IllegalStateException(
                    "Validated Nest Burst energy payment is no longer available"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(context.skillData()),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
        );

        // Authored behavior injects a fresh full poison state after the attack
        // attempt; it does not require or consume pre-existing poison here.
        WickedKrisPoisonTracker.applyPoison(
                target,
                context.player(),
                context.nowTick(),
                POISON_DURATION_TICKS,
                POISON_STACKS,
                SCHEDULE_DETONATION,
                context.weaponSnapshot()
        );

        // Preserve the old server notification order.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
    }

    static SkillContext createHitContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .build();
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode || freeEnergyBlessing || currentEnergy >= ENERGY_COST;
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_2)
        );
    }

    private static LivingEntity findTarget(SkillExecutionContext context) {
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
    }
}
