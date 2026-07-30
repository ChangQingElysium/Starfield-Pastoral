package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.MeowmereShotTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative lifecycle for Meowmere's original Rainbow Bolt.
 */
public final class MeowmereShotSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final int PIERCE_COUNT = 0;
    public static final float PROJECTILE_SPEED = 1.1F;
    public static final float PROJECTILE_INACCURACY = 1.0F;
    public static final int PROJECTILE_RUNTIME_TICKS =
            MeowmereProjectileEntity.MAX_LIFETIME_TICKS + 1;
    public static final int ANIMATION_TICKS = 10;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillRuntime.hasActive(
                context.player().getUUID(),
                context.skillId()
        )) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
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
        return projectileDamage(context) > 0.0F
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        float damage = projectileDamage(context);
        if (damage <= 0.0F) {
            throw new IllegalStateException(
                    "Validated Meowmere projectile damage is unavailable"
            );
        }

        ServerLevel level = context.player().serverLevel();
        MeowmereProjectileEntity projectile =
                new MeowmereProjectileEntity(
                        level,
                        context.player(),
                        damage,
                        PIERCE_COUNT,
                        context.skillData().getId(),
                        SkillContext.SkillTier.MINOR,
                        context.skillData().getDamagePercent() / 100.0F,
                        context.weaponSnapshot()
                );
        projectile.shootFromRotation(
                context.player(),
                context.player().getXRot(),
                context.player().getYRot(),
                0.0F,
                PROJECTILE_SPEED,
                PROJECTILE_INACCURACY
        );
        if (!level.addFreshEntity(projectile)) {
            throw new IllegalStateException(
                    "Failed to add a Meowmere Rainbow Bolt projectile"
            );
        }

        try {
            MeowmereShotTracker.start(
                    instance.instanceId(),
                    context.player().getUUID(),
                    level.dimension(),
                    projectile,
                    context.nowTick() + PROJECTILE_RUNTIME_TICKS
            );

            String weaponId = context.weaponId().getPath();
            String skillId = context.skillData().getId();
            WeaponSkillCooldowns.setCooldown(
                    context.player(),
                    weaponId,
                    skillId,
                    context.nowTick(),
                    context.skillData().getCooldown() * 20
            );

            // Preserve the authored presentation-only notification.
            WeaponSkillAnimationDispatcher.sendSkillAnim(
                    context.player(),
                    weaponId,
                    skillId,
                    ANIMATION_TICKS
            );
        } catch (RuntimeException exception) {
            MeowmereShotTracker.stop(instance.instanceId());
            throw exception;
        }
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
        return MeowmereShotTracker.tick(
                instance.instanceId(),
                context.player().level().dimension(),
                context.nowTick()
        );
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        MeowmereShotTracker.stop(instance.instanceId());
    }

    static float projectileDamage(WeaponData weaponData) {
        return weaponData == null
                ? 0.0F
                : (float) weaponData.getAverageDamage();
    }

    private static float projectileDamage(SkillExecutionContext context) {
        if (!(context.weapon().getItem() instanceof IStardewWeapon weapon)) {
            return 0.0F;
        }
        return projectileDamage(weapon.getWeaponData());
    }
}
