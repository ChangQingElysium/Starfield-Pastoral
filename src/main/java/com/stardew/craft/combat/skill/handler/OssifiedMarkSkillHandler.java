package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Ossified Blade's original Bone Mark.
 */
public final class OssifiedMarkSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 6.0;
    public static final int MARK_DURATION_TICKS = 60;

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
        return SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        ) == null
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                TARGET_RANGE
        );
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Bone Mark target is no longer available"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> OssifiedMarkTracker.apply(
                target, context.player(), context.nowTick(),
                MARK_DURATION_TICKS
        ));
    }
}
