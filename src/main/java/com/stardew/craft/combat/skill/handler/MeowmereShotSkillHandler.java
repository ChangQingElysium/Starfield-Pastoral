package com.stardew.craft.combat.skill.handler;

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
        instance.initializeExecutionState(
                new State(
                        projectile,
                        context.nowTick() + PROJECTILE_RUNTIME_TICKS
                )
        );
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        instance.registerCommittedEffect(() -> {
            if (!level.addFreshEntity(projectile)) {
                throw new IllegalStateException(
                        "Failed to add a Meowmere Rainbow Bolt projectile"
                );
            }
        });

        // Preserve the authored presentation-only notification.
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
        State state = instance.requireExecutionState(State.class);
        if (!state.projectile.level().dimension().equals(
                context.player().level().dimension()
        )) {
            return SkillTickResult.CANCEL;
        }
        if (state.projectile.isRemoved()
                || hasTimedOut(context.nowTick(), state.endTick)) {
            return SkillTickResult.COMPLETE;
        }
        return SkillTickResult.CONTINUE;
    }

    @Override
    public void finish(
            SkillExecutionContext context,
            SkillInstance instance,
            SkillInstance.EndReason reason
    ) {
        instance.executionState(State.class)
                .ifPresent(state -> discard(state.projectile));
    }

    static boolean hasTimedOut(long nowTick, long endTick) {
        return nowTick >= endTick;
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

    private static void discard(MeowmereProjectileEntity projectile) {
        if (!projectile.isRemoved()) {
            projectile.discard();
        }
    }

    private static final class State implements SkillInstance.ExecutionState {
        private final MeowmereProjectileEntity projectile;
        private final long endTick;

        private State(
                MeowmereProjectileEntity projectile,
                long endTick
        ) {
            this.projectile = projectile;
            this.endTick = endTick;
        }
    }
}
