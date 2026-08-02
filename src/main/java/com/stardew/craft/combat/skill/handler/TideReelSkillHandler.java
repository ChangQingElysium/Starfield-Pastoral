package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.equipment.EquipmentMobEffectHandler;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.network.WaterRingEffectPayload;
import com.stardew.craft.combat.skill.BrokenTridentCatchTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.runtime.RuntimeWeaponSkillHandler;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTargeting;
import com.stardew.craft.combat.skill.runtime.SkillValidation;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import com.stardew.craft.combat.skill.runtime.WeaponSkillMovementArbiter;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative extraction of Broken Trident's original Tide Reel.
 */
public final class TideReelSkillHandler implements RuntimeWeaponSkillHandler {
    public static final double TARGET_RANGE = 4.0;
    public static final float ENERGY_COST = 10.0F;
    public static final float FISH_CATCH_DAMAGE_BONUS = 0.60F;
    public static final int FISH_INVENTORY_COOLDOWN_REDUCTION_TICKS = 40;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int FISH_CATCH_SLOW_TICKS = 100;
    public static final int FISH_CATCH_SLOW_AMPLIFIER = 0;
    public static final double NORMAL_PULL_STRENGTH = 0.40;
    public static final double FISH_CATCH_PULL_STRENGTH = 0.55;
    public static final double NORMAL_PULL_LIFT = 0.08;
    public static final double FISH_CATCH_PULL_LIFT = 0.12;
    public static final double MINIMUM_PULL_DISTANCE_SQUARED = 0.01;
    public static final float WATER_RING_RADIUS = 4.6F;
    public static final int WATER_RING_DURATION_TICKS = 24;
    public static final int ANIMATION_TICKS = 12;

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

        // Failure must be side-effect free: the legacy branch paid energy before
        // this target check and therefore charged the player for an empty cast.
        LivingEntity target = findTarget(context);
        if (target == null) {
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        }
        return canPayEnergy(context)
                ? SkillValidation.accept()
                : SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
    }

    @Override
    public void begin(SkillExecutionContext context, SkillInstance instance) {
        // Resolve the target again before payment so a disappearing target cannot
        // reintroduce the legacy empty-cast energy charge.
        LivingEntity target = findTarget(context);
        if (target == null) {
            throw new IllegalStateException(
                    "Validated Tide Reel target is no longer available"
            );
        }
        instance.setTargetEntityIds(List.of(target.getId()));

        if (!WeaponSkillRuntime.consumeEnergyDuringBegin(
                context,
                instance,
                ENERGY_COST
        )) {
            throw new IllegalStateException(
                    "Validated Tide Reel energy payment is no longer available"
            );
        }

        String weaponId = context.weaponId().getPath();
        String skillId = context.skillData().getId();
        Optional<BrokenTridentCatchTracker.CatchSnapshot> consumedCatch =
                BrokenTridentCatchTracker.consumeForBegin(
                context.player(),
                context.nowTick()
        );
        boolean fishCatchActive = consumedCatch.isPresent();
        consumedCatch.ifPresent(snapshot ->
                instance.registerBeginFailureCleanup(() ->
                        BrokenTridentCatchTracker.restore(
                                context.player(),
                                snapshot,
                                context.nowTick()
                        )
                )
        );

        boolean hasFish = BrokenTridentCatchTracker.hasFishInInventory(context.player());
        WeaponSkillRuntime.commitCooldown(
                context,
                instance,
                appliedCooldownTicks(
                        context.skillData().getCooldown() * 20,
                        hasFish
                )
        );
        instance.initializeExecutionState(
                new State(target.getUUID(), fishCatchActive)
        );
        instance.registerCommittedEffect(() -> {
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    createHitContext(context.skillData(), fishCatchActive),
                    context.weaponSnapshot(),
                    context.nowTick() + HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
            );
            sendLegacyImpactEffects(context.player().serverLevel(), target);
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
        });
    }

    static SkillContext createHitContext(
            WeaponSkillData skillData,
            boolean fishCatchActive
    ) {
        return SkillContext.builder()
                .skillId(skillData.getId())
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(
                        skillData.getDamagePercent() / 100.0F
                                + (fishCatchActive ? FISH_CATCH_DAMAGE_BONUS : 0.0F)
                )
                .build();
    }

    static int appliedCooldownTicks(int baseCooldownTicks, boolean hasFish) {
        return hasFish
                ? Math.max(1, baseCooldownTicks - FISH_INVENTORY_COOLDOWN_REDUCTION_TICKS)
                : baseCooldownTicks;
    }

    static boolean canPayEnergy(
            float currentEnergy,
            boolean creativeMode,
            boolean freeEnergyBlessing
    ) {
        return creativeMode || freeEnergyBlessing || currentEnergy >= ENERGY_COST;
    }

    static double pullStrength(boolean fishCatchActive) {
        return fishCatchActive ? FISH_CATCH_PULL_STRENGTH : NORMAL_PULL_STRENGTH;
    }

    static double pullLift(boolean fishCatchActive) {
        return fishCatchActive ? FISH_CATCH_PULL_LIFT : NORMAL_PULL_LIFT;
    }

    /** Applies the reel only after this cast's exact root hit dealt health damage. */
    public static boolean onAppliedHit(
            ServerPlayer player,
            LivingEntity target
    ) {
        if (player == null || target == null) {
            return false;
        }
        return WeaponSkillRuntime.activeExecutionState(
                player.getUUID(),
                BuiltinWeaponSkillHandlers.TIDE_REEL,
                State.class
        ).map(state -> state.onAppliedHit(player, target))
                .orElse(false);
    }

    private static boolean canPayEnergy(SkillExecutionContext context) {
        return canPayEnergy(
                PlayerStardewDataAPI.getEnergy(context.player()),
                context.player().getAbilities().instabuild,
                context.player().hasEffect(ModMobEffects.STATUE_OF_BLESSINGS_2)
        );
    }

    private static LivingEntity findTarget(SkillExecutionContext context) {
        return SkillTargeting.findNearestTargetInFront(
                context.player(),
                TARGET_RANGE
        );
    }

    private static void pullTarget(
            ServerPlayer player,
            LivingEntity target,
            boolean fishCatchActive
    ) {
        Vec3 toPlayer = player.position().subtract(target.position());
        Vec3 horizontalDirection = new Vec3(toPlayer.x, 0.0, toPlayer.z);
        if (horizontalDirection.lengthSqr() <= MINIMUM_PULL_DISTANCE_SQUARED) {
            return;
        }

        Vec3 pull = horizontalDirection.normalize().scale(pullStrength(fishCatchActive));
        WeaponSkillMovementArbiter.revokeCurrentIfPlayer(target);
        target.setDeltaMovement(target.getDeltaMovement().add(
                pull.x,
                pullLift(fishCatchActive),
                pull.z
        ));
        target.hurtMarked = true;
    }

    private static final class State implements SkillInstance.ExecutionState {
        private final UUID targetId;
        private final boolean fishCatchActive;
        private boolean consumed;

        private State(UUID targetId, boolean fishCatchActive) {
            this.targetId = targetId;
            this.fishCatchActive = fishCatchActive;
        }

        private synchronized boolean onAppliedHit(
                ServerPlayer player,
                LivingEntity target
        ) {
            if (consumed || !targetId.equals(target.getUUID())) {
                return false;
            }
            consumed = true;

            int protectionDuration = fishCatchActive
                    ? FISH_CATCH_SLOW_TICKS
                    : 1;
            EquipmentNegativeStatusProtection.Decision protection =
                    EquipmentNegativeStatusProtection.decide(
                            target,
                            protectionDuration
                    );
            if (protection.resisted()) {
                return false;
            }
            if (fishCatchActive) {
                EquipmentMobEffectHandler.addPreAdjustedEffect(
                        target,
                        new MobEffectInstance(
                                MobEffects.MOVEMENT_SLOWDOWN,
                                protection.durationTicks(),
                                FISH_CATCH_SLOW_AMPLIFIER,
                                false,
                                true,
                                true
                        )
                );
            }
            pullTarget(player, target, fishCatchActive);
            return true;
        }
    }

    private static void sendLegacyImpactEffects(
            ServerLevel level,
            LivingEntity target
    ) {
        PacketDistributor.sendToPlayersInDimension(
                level,
                new WaterRingEffectPayload(
                        (float) target.getX(),
                        (float) target.getY(),
                        (float) target.getZ(),
                        WATER_RING_RADIUS,
                        WATER_RING_DURATION_TICKS
                )
        );
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.TRIDENT_HIT,
                SoundSource.PLAYERS,
                0.95F,
                1.05F
        );
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.PLAYERS,
                0.85F,
                1.15F
        );
        level.sendParticles(
                ParticleTypes.SPLASH,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6,
                target.getZ(),
                28,
                0.9,
                0.3,
                0.9,
                0.05
        );
        level.sendParticles(
                ParticleTypes.BUBBLE,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ(),
                20,
                0.75,
                0.25,
                0.75,
                0.03
        );
        level.sendParticles(
                ParticleTypes.CLOUD,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.55,
                target.getZ(),
                12,
                0.7,
                0.15,
                0.7,
                0.02
        );
        level.sendParticles(
                ParticleTypes.ENCHANT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.65,
                target.getZ(),
                14,
                0.6,
                0.3,
                0.6,
                0.06
        );
        level.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6,
                target.getZ(),
                10,
                0.45,
                0.25,
                0.45,
                0.07
        );
    }
}
