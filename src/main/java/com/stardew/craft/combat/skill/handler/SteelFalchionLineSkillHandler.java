package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SteelFalchionLineTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative lifecycle for Steel Falchion's original fixed line.
 */
public final class SteelFalchionLineSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 7.0D;
    public static final float DOT_DAMAGE_MULTIPLIER = 0.30F;
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
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        ) || SteelFalchionLineTracker.hasMinorLine(
                context.player().getUUID()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
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
                    "Validated Swift Etch target is no longer available"
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
        Vec3 center = new Vec3(
                target.getX(),
                target.getY() + 0.02D,
                target.getZ()
        );
        SteelFalchionLineTracker.startMinorLine(
                context.player(),
                context.nowTick(),
                center,
                context.player().getYRot(),
                DOT_DAMAGE_MULTIPLIER,
                context.weaponSnapshot()
        );
        WeaponSkillAnimationLock.setLock(
                context.player(),
                context.nowTick(),
                ANIMATION_TICKS
        );
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
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
        if (!SteelFalchionLineTracker.isMinorLineBoundToCurrentDimension(
                context.player()
        )) {
            SteelFalchionLineTracker.tick(
                    context.player(),
                    context.nowTick()
            );
            return context.nowTick()
                    >= instance.startGameTick()
                            + SteelFalchionLineTracker.LINE_DURATION_TICKS
                    ? SkillTickResult.COMPLETE
                    : SkillTickResult.CANCEL;
        }
        SteelFalchionLineTracker.tick(
                context.player(),
                context.nowTick()
        );
        if (SteelFalchionLineTracker.hasMinorLine(
                context.player().getUUID()
        )) {
            return SkillTickResult.CONTINUE;
        }
        return context.nowTick()
                >= instance.startGameTick()
                        + SteelFalchionLineTracker.LINE_DURATION_TICKS
                ? SkillTickResult.COMPLETE
                : SkillTickResult.CANCEL;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        if (reason == SkillInstance.EndReason.COMPLETED) {
            return;
        }
        if (reason == SkillInstance.EndReason.CASTER_UNAVAILABLE) {
            SteelFalchionLineTracker.removePlayer(
                    context.player().getUUID()
            );
            return;
        }
        SteelFalchionLineTracker.cancelMinorLines(context.player());
    }

    private static LivingEntity findTarget(SkillExecutionContext context) {
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
    }
}
