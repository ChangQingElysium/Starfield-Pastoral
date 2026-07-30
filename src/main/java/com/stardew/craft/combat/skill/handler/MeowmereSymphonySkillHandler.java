package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.MeowmereSymphonyTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative lifecycle for Meowmere's original five-shot Symphony.
 */
public final class MeowmereSymphonySkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final int PROJECTILE_COUNT = 5;
    public static final int PIERCE_COUNT = 1;
    public static final float YAW_STEP_DEGREES = 8.0F;
    public static final float PROJECTILE_SPEED = 1.0F;
    public static final float PROJECTILE_INACCURACY = 1.0F;
    public static final int PROJECTILE_RUNTIME_TICKS =
            MeowmereProjectileEntity.MAX_LIFETIME_TICKS + 1;
    public static final int ANIMATION_TICKS = 15;

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
        if (!canPayEnergy(context)
                || projectileDamage(context) <= 0.0F) {
            return SkillValidation.reject(
                    SkillValidation.RejectionReason.INVALID_STATE
            );
        }
        return SkillValidation.accept();
    }

    @Override
    public void begin(
            SkillExecutionContext context,
            SkillInstance instance
    ) {
        if (!canPayEnergy(context)) {
            throw new IllegalStateException(
                    "Validated Meowmere Symphony energy is unavailable"
            );
        }
        float damage = projectileDamage(context);
        if (damage <= 0.0F) {
            throw new IllegalStateException(
                    "Validated Meowmere Symphony damage is unavailable"
            );
        }

        List<MeowmereProjectileEntity> projectiles =
                createProjectiles(context, damage);
        ServerLevel level = context.player().serverLevel();
        List<MeowmereProjectileEntity> spawned =
                new ArrayList<>(PROJECTILE_COUNT);
        try {
            for (MeowmereProjectileEntity projectile : projectiles) {
                if (!level.addFreshEntity(projectile)) {
                    throw new IllegalStateException(
                            "Failed to add every Meowmere Symphony projectile"
                    );
                }
                spawned.add(projectile);
            }
        } catch (RuntimeException exception) {
            spawned.forEach(MeowmereProjectileEntity::discard);
            throw exception;
        }

        try {
            if (!context.player().getAbilities().instabuild
                    && !PlayerStardewDataAPI.consumeEnergy(
                            context.player(),
                            ENERGY_COST
                    )) {
                throw new IllegalStateException(
                        "Validated Meowmere Symphony energy payment failed"
                );
            }
        } catch (RuntimeException exception) {
            spawned.forEach(MeowmereProjectileEntity::discard);
            throw exception;
        }

        MeowmereSymphonyTracker.start(
                instance.instanceId(),
                context.player().getUUID(),
                level.dimension(),
                spawned,
                context.nowTick() + PROJECTILE_RUNTIME_TICKS
        );

        try {
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
            MeowmereSymphonyTracker.stop(instance.instanceId());
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
        return MeowmereSymphonyTracker.tick(
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
        MeowmereSymphonyTracker.stop(instance.instanceId());
    }

    static float projectileDamage(
            WeaponData weaponData,
            int damagePercent
    ) {
        return weaponData == null
                ? 0.0F
                : (float) (
                        weaponData.getAverageDamage()
                                * damagePercent
                                / 100.0
                );
    }

    static float yawOffsetDegrees(int projectileIndex) {
        return (projectileIndex - PROJECTILE_COUNT / 2)
                * YAW_STEP_DEGREES;
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode
                || freeEnergyBlessing
                || currentEnergy >= ENERGY_COST;
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(
                        ModMobEffects.STATUE_OF_BLESSINGS_2
                )
        );
    }

    private static float projectileDamage(SkillExecutionContext context) {
        if (!(context.weapon().getItem() instanceof IStardewWeapon weapon)) {
            return 0.0F;
        }
        return projectileDamage(
                weapon.getWeaponData(),
                context.skillData().getDamagePercent()
        );
    }

    private static List<MeowmereProjectileEntity> createProjectiles(
            SkillExecutionContext context,
            float damage
    ) {
        List<MeowmereProjectileEntity> projectiles =
                new ArrayList<>(PROJECTILE_COUNT);
        float damageMultiplier =
                context.skillData().getDamagePercent() / 100.0F;
        float basePitch = context.player().getXRot();
        float baseYaw = context.player().getYRot();
        for (int index = 0; index < PROJECTILE_COUNT; index++) {
            MeowmereProjectileEntity projectile =
                    new MeowmereProjectileEntity(
                            context.player().serverLevel(),
                            context.player(),
                            damage,
                            PIERCE_COUNT,
                            context.skillData().getId(),
                            SkillContext.SkillTier.MAJOR,
                            damageMultiplier,
                            context.weaponSnapshot()
                    );
            projectile.shootFromRotation(
                    context.player(),
                    basePitch,
                    baseYaw + yawOffsetDegrees(index),
                    0.0F,
                    PROJECTILE_SPEED,
                    PROJECTILE_INACCURACY
            );
            projectiles.add(projectile);
        }
        return projectiles;
    }
}
