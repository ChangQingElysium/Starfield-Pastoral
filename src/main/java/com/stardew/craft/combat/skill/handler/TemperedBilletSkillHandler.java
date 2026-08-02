package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.TemperedFireRingTracker;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative extraction of Tempered Broadsword's original Forged Billets.
 */
public final class TemperedBilletSkillHandler implements RuntimeWeaponSkillHandler {
    public static final float ENERGY_COST = 10.0F;
    public static final double INITIAL_TARGET_RANGE = 10.0;
    public static final int PROJECTILE_COUNT = 3;
    public static final float PROJECTILE_SPEED = 1.6F;
    public static final float PROJECTILE_INACCURACY = 0.2F;
    public static final float YAW_SPREAD_DEGREES = 16.0F;
    public static final float PITCH_SPREAD_DEGREES = 10.0F;
    public static final int PROJECTILE_STATE_TICKS = 65;
    public static final int ANIMATION_TICKS = 12;
    public static final float FIRE_RING_RADIUS = 2.5F;
    public static final int FIRE_RING_DURATION_TICKS = 10;

    @Override
    public SkillValidation validate(SkillExecutionContext context) {
        if (WeaponSkillCooldowns.isOnCooldown(
                context.player(),
                context.weaponId().getPath(),
                context.skillData().getId(),
                context.nowTick()
        )) {
            return SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN);
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(
                        SkillValidation.RejectionReason.INVALID_STATE
                );
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        if (!(context.weapon().getItem() instanceof IStardewWeapon weapon)
                || weapon.getWeaponData() == null) {
            throw new IllegalStateException(
                    "Forged Billets requires a resolved Stardew weapon"
            );
        }

        List<LivingEntity> targets = findTargetsInRadius(
                context,
                INITIAL_TARGET_RANGE
        );
        if (!targets.isEmpty()) {
            Collections.shuffle(
                    targets,
                    new Random(context.player().level().random.nextLong())
            );
        }
        instance.setTargetEntityIds(
                targets.stream()
                        .limit(PROJECTILE_COUNT)
                        .map(LivingEntity::getId)
                        .toList()
        );

        float damageSnapshot =
                (float) weapon.getWeaponData().getAverageDamage();
        WeaponDamageSnapshot releaseWeaponSnapshot =
                context.weaponSnapshot();
        List<TemperedBilletProjectileEntity> projectiles =
                createProjectiles(
                        context,
                        targets,
                        damageSnapshot,
                        releaseWeaponSnapshot
                );
        ServerLevel level = context.player().serverLevel();
        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Forged Billets energy payment is no longer available"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                context.skillData().getCooldown() * 20
        );
        TemperedFireRingTracker.beginBilletCastDuringBegin(
                instance,
                context.player(),
                context.nowTick(),
                PROJECTILE_STATE_TICKS,
                releaseWeaponSnapshot
        );
        instance.registerCommittedEffect(() -> {
            for (TemperedBilletProjectileEntity projectile : projectiles) {
                if (!level.addFreshEntity(projectile)) {
                    throw new IllegalStateException(
                            "Failed to add a Forged Billet projectile"
                    );
                }
            }
        });

        // The authored cast has no attack lock; only notify presentation.
        WeaponSkillAnimationDispatcher.sendSkillAnim(
                context.player(),
                weaponId,
                skillId,
                ANIMATION_TICKS
        );
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

    static int assignedTargetIndex(int projectileIndex, int targetCount) {
        if (targetCount <= 0) {
            return -1;
        }
        return projectileIndex < targetCount ? projectileIndex : 0;
    }

    /** Starts the billet fire ring after exact positive projectile damage. */
    public static void startFireRing(
            ServerPlayer player,
            LivingEntity target,
            long nowTick,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || target == null) {
            return;
        }
        if (weaponSnapshot == null) {
            TemperedFireRingTracker.start(
                    player,
                    target.position(),
                    nowTick,
                    FIRE_RING_RADIUS,
                    FIRE_RING_DURATION_TICKS
            );
            return;
        }
        TemperedFireRingTracker.start(
                player,
                target.position(),
                nowTick,
                FIRE_RING_RADIUS,
                FIRE_RING_DURATION_TICKS,
                weaponSnapshot
        );
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

    private static List<TemperedBilletProjectileEntity> createProjectiles(
            SkillExecutionContext context,
            List<LivingEntity> targets,
            float damageSnapshot,
            WeaponDamageSnapshot releaseWeaponSnapshot
    ) {
        List<TemperedBilletProjectileEntity> projectiles =
                new ArrayList<>(PROJECTILE_COUNT);
        for (int index = 0; index < PROJECTILE_COUNT; index++) {
            int targetIndex = assignedTargetIndex(index, targets.size());
            LivingEntity target =
                    targetIndex < 0 ? null : targets.get(targetIndex);
            TemperedBilletProjectileEntity projectile =
                    new TemperedBilletProjectileEntity(
                            context.player().serverLevel(),
                            context.player(),
                            damageSnapshot,
                            context.skillData().getId(),
                            target,
                            releaseWeaponSnapshot
                    );
            float yaw = context.player().getYRot()
                    + (context.player().level().random.nextFloat() - 0.5F)
                    * YAW_SPREAD_DEGREES;
            float pitch = context.player().getXRot()
                    + (context.player().level().random.nextFloat() - 0.5F)
                    * PITCH_SPREAD_DEGREES;
            projectile.shootFromRotation(
                    context.player(),
                    pitch,
                    yaw,
                    0.0F,
                    PROJECTILE_SPEED,
                    PROJECTILE_INACCURACY
            );
            projectiles.add(projectile);
        }
        return projectiles;
    }

    private static List<LivingEntity> findTargetsInRadius(
            SkillExecutionContext context,
            double range
    ) {
        Vec3 origin = context.player().position();
        AABB bounds = context.player().getBoundingBox().inflate(
                range,
                range * 0.75,
                range
        );
        return context.player().level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable()
                        && entity != context.player()
                        && entity.distanceToSqr(
                                origin.x,
                                origin.y,
                                origin.z
                        ) <= range * range
        );
    }
}
