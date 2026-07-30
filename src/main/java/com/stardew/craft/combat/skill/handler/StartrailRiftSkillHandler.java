package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.CombatHealing;
import com.stardew.craft.combat.VfxColors;
import com.stardew.craft.combat.network.RiftPathPayload;
import com.stardew.craft.combat.network.ShockwaveRingPayload;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative extraction of Galaxy Sword's original Startrail Rift.
 */
public final class StartrailRiftSkillHandler
        implements RuntimeWeaponSkillHandler {
    public static final double DASH_DISTANCE = 4.5D;
    public static final double PATH_HIT_RADIUS = 0.9D;
    public static final int DASH_DURATION_TICKS = 5;
    public static final int BOOST_STACKS = 6;
    public static final float BOOST_CRITICAL_CHANCE = 0.20F;
    public static final int HIT_STARTRAIL_RESTORE = 2;
    public static final float HIT_ENERGY_RESTORE = 6.0F;
    public static final float HIT_HEALTH_RESTORE = 3.0F;
    public static final int SPEED_DURATION_TICKS = 140;
    public static final int SPEED_AMPLIFIER = 0;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int ANIMATION_TICKS = 8;
    public static final int RIFT_DURATION_TICKS = 14;
    public static final float SHOCKWAVE_RADIUS = 1.25F;
    public static final int SHOCKWAVE_DURATION_TICKS = 8;

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
        return DragonBreathThrustSkillHandler.resolveSafeDashEnd(
                context.player(),
                DASH_DISTANCE
        ) != null
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
        Vec3 start = context.player().position();
        Vec3 end = DragonBreathThrustSkillHandler.resolveSafeDashEnd(
                context.player(),
                DASH_DISTANCE
        );
        if (end == null) {
            throw new IllegalStateException(
                    "Validated Startrail Rift path is no longer safe"
            );
        }

        List<LivingEntity> targets =
                DragonBreathThrustSkillHandler.findTargetsAlongPath(
                        context.player(),
                        start,
                        end,
                        PATH_HIT_RADIUS
                );
        instance.setTargetEntityIds(
                targets.stream().map(LivingEntity::getId).toList()
        );

        int stacks = StartrailTracker.getStacks(context.player());
        boolean boosted = isBoostedForStacks(stacks);
        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        WeaponSkillCooldowns.setCooldown(
                context.player(),
                weaponId,
                skillId,
                context.nowTick(),
                context.skillData().getCooldown() * 20
        );

        for (LivingEntity target : targets) {
            attackTarget(context, target, boosted);
        }
        DashMovementTracker.start(
                context.player(),
                context.nowTick(),
                end,
                DASH_DURATION_TICKS
        );
        sendRiftPresentation(context, start, end, boosted);

        if (!targets.isEmpty()) {
            StartrailTracker.addStacks(
                    context.player(),
                    HIT_STARTRAIL_RESTORE
            );
            PlayerStardewDataAPI.restoreEnergy(
                    context.player(),
                    HIT_ENERGY_RESTORE
            );
            CombatHealing.heal(
                    context.player(),
                    HIT_HEALTH_RESTORE
            );
        }
        context.player().addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                SPEED_DURATION_TICKS,
                SPEED_AMPLIFIER,
                false,
                true,
                true
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

    static boolean isBoostedForStacks(int stacks) {
        return stacks >= BOOST_STACKS;
    }

    static float criticalChanceBonusForStacks(int stacks) {
        return isBoostedForStacks(stacks)
                ? BOOST_CRITICAL_CHANCE
                : 0.0F;
    }

    static SkillContext createHitContext(
            WeaponSkillData skillData,
            boolean boosted
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                )
                .critChanceBonus(
                        boosted ? BOOST_CRITICAL_CHANCE : 0.0F
                )
                .build();
    }

    static int riftSegmentCount(double pathLength) {
        return Mth.clamp((int) (pathLength / 0.7D), 6, 10);
    }

    static int presentationColor(boolean boosted) {
        if (!boosted) {
            return VfxColors.GALAXY_PURPLE;
        }
        int color = VfxColors.GALAXY_PURPLE;
        int red = Math.min(255, (int) (((color >> 16) & 0xFF) * 1.25F));
        int green = Math.min(255, (int) (((color >> 8) & 0xFF) * 1.25F));
        int blue = Math.min(255, (int) ((color & 0xFF) * 1.25F));
        return (red << 16) | (green << 8) | blue;
    }

    private static void attackTarget(
            SkillExecutionContext context,
            LivingEntity target,
            boolean boosted
    ) {
        WeaponSkillDamage.apply(
                context.player(),
                target,
                createHitContext(context.skillData(), boosted),
                context.weaponSnapshot(),
                context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    private static void sendRiftPresentation(
            SkillExecutionContext context,
            Vec3 start,
            Vec3 end,
            boolean boosted
    ) {
        Vec3 path = end.subtract(start);
        double pathLength = path.length();
        if (pathLength > 0.05D) {
            Vec3 direction = path.normalize();
            float yaw = (float) (
                    Math.atan2(-direction.x, direction.z)
                            * (180.0D / Math.PI)
            );
            int segments = riftSegmentCount(pathLength);
            float segmentLength = (float) Math.max(
                    0.6D,
                    pathLength / segments
            );
            int color = presentationColor(boosted);
            for (int segment = 0; segment < segments; segment++) {
                Vec3 position = start.add(
                        direction.scale(
                                (segment + 0.5D) * segmentLength
                        )
                );
                PacketDistributor.sendToPlayersInDimension(
                        context.player().serverLevel(),
                        new RiftPathPayload(
                                (float) position.x,
                                (float) position.y,
                                (float) position.z,
                                yaw,
                                segmentLength,
                                RIFT_DURATION_TICKS,
                                color
                        )
                );
            }
        }
        if (boosted) {
            PacketDistributor.sendToPlayersInDimension(
                    context.player().serverLevel(),
                    new ShockwaveRingPayload(
                            (float) end.x,
                            (float) end.y,
                            (float) end.z,
                            SHOCKWAVE_RADIUS,
                            SHOCKWAVE_DURATION_TICKS,
                            VfxColors.GALAXY_PURPLE
                    )
            );
        }
    }
}
