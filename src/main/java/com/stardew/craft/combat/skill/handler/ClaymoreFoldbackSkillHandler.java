package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.ClaymoreFoldbackTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative extraction of Claymore's original two-hit Foldback.
 */
public final class ClaymoreFoldbackSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double INITIAL_TARGET_RANGE = 4.0D;
    public static final int RETURN_DELAY_TICKS = 12;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int INITIAL_ANIMATION_TICKS = 12;

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
        return ClaymoreFoldbackTracker.hasState(
                context.player().getUUID()
        )
                ? SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                )
                : SkillValidation.accept();
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        LivingEntity target = SkillTargeting.findTargetEntity(
                context.player(),
                INITIAL_TARGET_RANGE
        );
        if (target != null) {
            instance.setTargetEntityIds(List.of(target.getId()));
            attackInitialTarget(context, target);
        }

        ClaymoreFoldbackTracker.start(
                context.player(),
                context.nowTick(),
                RETURN_DELAY_TICKS,
                target,
                weaponId,
                skillId
        );

        // Preserve the original notification order after the first strike and
        // delayed return registration. This skill never imposed an attack lock.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                INITIAL_ANIMATION_TICKS
        );
    }

    @Override
    public boolean completesImmediately() {
        return false;
    }

    @Override
    public SkillTickResult tick(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        if (!ClaymoreFoldbackTracker.isBoundToCurrentDimension(
                context.player()
        )) {
            return SkillTickResult.CANCEL;
        }
        ClaymoreFoldbackTracker.tick(
                context.player(),
                context.nowTick()
        );
        return ClaymoreFoldbackTracker.hasState(
                context.player().getUUID()
        )
                ? SkillTickResult.CONTINUE
                : SkillTickResult.COMPLETE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        ClaymoreFoldbackTracker.removePlayer(
                context.player().getUUID()
        );
    }

    static SkillContext createInitialContext(WeaponSkillData skillData) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .build();
    }

    private static void attackInitialTarget(
            SkillExecutionContext context,
            LivingEntity target
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createInitialContext(context.skillData()),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
        );
    }
}
