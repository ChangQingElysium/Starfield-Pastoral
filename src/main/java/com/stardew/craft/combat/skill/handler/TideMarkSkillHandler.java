package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.TideMarkTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Neptune's Glaive's original Tide Mark.
 */
public final class TideMarkSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 6.0;
    public static final int MARK_DURATION_TICKS =
            TideMarkTracker.MARK_DURATION_TICKS;
    public static final int ANIMATION_TICKS = 8;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.COOLDOWN
            );
        }
        return findTarget(context) == null
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = findTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Tide Mark target is no longer available"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );
        TideMarkTracker.apply(
                target,
                context.player(),
                context.nowTick(),
                MARK_DURATION_TICKS
        );

        // The original skill only sent its animation notification; it did not
        // impose a server-side animation lock.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
    }

    private static LivingEntity findTarget(SkillExecutionContext context) {
        return SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
    }
}
