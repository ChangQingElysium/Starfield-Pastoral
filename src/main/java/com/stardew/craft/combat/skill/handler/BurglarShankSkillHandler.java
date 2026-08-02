package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Burglar's Shank's original Cutpurse slash.
 *
 * <p>The post-damage event remains responsible for the authored kill reward or
 * Weak Point outcome because it owns the authoritative damage result.</p>
 */
public final class BurglarShankSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
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
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        ) == null
                ? SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE)
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        LivingEntity target = SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Burglar's Shank target is no longer available"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> WeaponSkillDamage.apply(
                context.player(), target,
                createHitContext(context.skillData()),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
        ));

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
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skillData.getDamagePercent() / 100.0F)
                .build();
    }

}
