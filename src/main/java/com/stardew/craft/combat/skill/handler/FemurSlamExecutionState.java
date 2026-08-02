package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.TremorBlockPayload;
import com.stardew.craft.combat.equipment.EquipmentMobEffectHandler;
import com.stardew.craft.combat.equipment.EquipmentNegativeStatusProtection;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** One charged Femur Slam and its deferred cooldown transaction. */
final class FemurSlamExecutionState
        implements SkillInstance.ExecutionState {
    private final long fireTick;
    private final DeferredSkillCooldown cooldown;
    private final Set<UUID> eligibleHitTargets = new LinkedHashSet<>();
    private final Map<UUID, LivingEntity> appliedTargets =
            new LinkedHashMap<>();
    private boolean collectingAppliedHits;
    private boolean settled;
    private boolean advancing;

    FemurSlamExecutionState(
            long nowTick,
            int chargeTicks,
            DeferredSkillCooldown cooldown
    ) {
        this.fireTick = nowTick + Math.max(1, chargeTicks);
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (advancing) {
            return SkillTickResult.CONTINUE;
        }

        advancing = true;
        try {
            return switch (tickDecision(
                    fireTick,
                    context.nowTick(),
                    context.player().isUsingItem()
            )) {
                case WAIT -> SkillTickResult.CONTINUE;
                case CANCEL -> {
                    cancel(context.player());
                    yield SkillTickResult.CANCEL;
                }
                case FIRE -> fire(context);
            };
        } finally {
            advancing = false;
        }
    }

    void cancel(ServerPlayer player) {
        if (settled) {
            player.stopUsingItem();
            return;
        }
        settled = true;
        try {
            WeaponSkillRuntime.abandonDeferredCooldown(cooldown);
        } finally {
            player.stopUsingItem();
        }
    }

    static TickDecision tickDecision(
            long fireTick,
            long nowTick,
            boolean usingItem
    ) {
        if (nowTick >= fireTick) {
            return TickDecision.FIRE;
        }
        return usingItem ? TickDecision.WAIT : TickDecision.CANCEL;
    }

    static SkillContext createHitContext(
            String skillId,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(damageMultiplier)
                .defaultKnockback(false)
                .build();
    }

    private SkillTickResult fire(SkillExecutionContext context) {
        settled = true;
        try {
            try {
                handleSlam(context);
            } finally {
                WeaponSkillRuntime.commitDeferredCooldown(
                        context.player(),
                        cooldown,
                        context.nowTick()
                );
            }
        } finally {
            context.player().stopUsingItem();
        }
        return SkillTickResult.COMPLETE;
    }

    private void handleSlam(SkillExecutionContext context) {
        ServerPlayer player = context.player();
        player.swing(InteractionHand.MAIN_HAND, true);
        player.stopUsingItem();

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        List<LivingEntity> targets = findTargetsInArc(
                player,
                FemurSlamSkillHandler.RANGE,
                FemurSlamSkillHandler.MIN_DOT
        );
        if (!targets.isEmpty()) {
            beginAppliedHitCollection(targets);
            WeaponDamageSnapshot weaponSnapshot = context.weaponSnapshot();
            try {
                for (LivingEntity target : targets) {
                    SkillContext hitContext = createHitContext(
                            context.skillData().getId(),
                            context.skillData().getDamagePercent() / 100.0F
                    );
                    WeaponSkillDamage.apply(
                            player,
                            target,
                            hitContext,
                            weaponSnapshot,
                            context.nowTick()
                                    + FemurSlamSkillHandler
                                            .HIT_CONTEXT_LIFETIME_TICKS,
                            WeaponSkillDamage.AttackGatePolicy
                                    .RESPECT_AT_IMPACT,
                            WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                    );
                }
            } finally {
                try {
                    settleAppliedControls(player);
                } finally {
                    clearAppliedHitCollection();
                }
            }
        }
        spawnQuakeImpact(level, player);
        spawnTremorBurst(level, player, FemurSlamSkillHandler.RANGE);

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.IRON_GOLEM_ATTACK,
                SoundSource.PLAYERS,
                0.85F,
                0.9F
        );
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.55F,
                0.85F
        );
    }

    boolean recordAppliedHit(LivingEntity target) {
        if (!collectingAppliedHits
                || target == null
                || !eligibleHitTargets.contains(target.getUUID())) {
            return false;
        }
        return appliedTargets.putIfAbsent(
                target.getUUID(),
                target
        ) == null;
    }

    private void beginAppliedHitCollection(List<LivingEntity> targets) {
        eligibleHitTargets.clear();
        appliedTargets.clear();
        targets.forEach(target ->
                eligibleHitTargets.add(target.getUUID())
        );
        collectingAppliedHits = true;
    }

    private void settleAppliedControls(ServerPlayer player) {
        boolean singleAppliedTarget = appliedTargets.size() == 1;
        for (LivingEntity target : appliedTargets.values()) {
            applyControl(player, target, singleAppliedTarget);
        }
    }

    private void clearAppliedHitCollection() {
        collectingAppliedHits = false;
        eligibleHitTargets.clear();
        appliedTargets.clear();
    }

    private static void applyControl(
            ServerPlayer player,
            LivingEntity target,
            boolean singleAppliedTarget
    ) {
        EquipmentNegativeStatusProtection.Decision protection =
                EquipmentNegativeStatusProtection.decide(
                        target,
                        FemurSlamSkillHandler.SLOW_TICKS
                );
        if (!protection.resisted()) {
            EquipmentMobEffectHandler.addPreAdjustedEffect(
                    target,
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            protection.durationTicks(),
                            FemurSlamSkillHandler.SLOW_AMPLIFIER,
                            false,
                            true,
                            true
                    )
            );
            if (singleAppliedTarget) {
                int staggerTicks = protection.adjustRelatedDurationTicks(
                        FemurSlamSkillHandler.STAGGER_TICKS
                );
                if (staggerTicks > 0) {
                    EquipmentMobEffectHandler.addPreAdjustedEffect(
                            target,
                            new MobEffectInstance(
                                    MobEffects.DIG_SLOWDOWN,
                                    staggerTicks,
                                    FemurSlamSkillHandler.STAGGER_AMPLIFIER,
                                    false,
                                    true,
                                    true
                            )
                    );
                    target.setDeltaMovement(
                            0.0D,
                            target.getDeltaMovement().y,
                            0.0D
                    );
                    target.hasImpulse = true;
                }
            }
        }

        float knockback = singleAppliedTarget
                ? FemurSlamSkillHandler.KNOCKBACK_SINGLE
                : FemurSlamSkillHandler.KNOCKBACK_MULTI;
        applyKnockback(player, target, knockback);
        if (target.level() instanceof ServerLevel level) {
            spawnHitParticles(level, target);
        }
    }

    private static void applyKnockback(
            Player player,
            LivingEntity target,
            float strength
    ) {
        double deltaX = player.getX() - target.getX();
        double deltaZ = player.getZ() - target.getZ();
        if (deltaX * deltaX + deltaZ * deltaZ > 0.0001D) {
            target.knockback(strength, deltaX, deltaZ);
        }
    }

    private static void spawnHitParticles(
            ServerLevel level,
            LivingEntity target
    ) {
        ItemParticleOption bone = new ItemParticleOption(
                ParticleTypes.ITEM,
                new ItemStack(Items.BONE)
        );
        level.sendParticles(
                bone,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.6D,
                target.getZ(),
                6,
                0.2D,
                0.2D,
                0.2D,
                0.02D
        );
    }

    private static void spawnQuakeImpact(
            ServerLevel level,
            Player player
    ) {
        Vec3 center = player.position();
        double baseY = player.getY() + 0.05D;

        level.sendParticles(
                ParticleTypes.POOF,
                center.x,
                baseY + 0.1D,
                center.z,
                10,
                0.35D,
                0.05D,
                0.35D,
                0.02D
        );
        level.sendParticles(
                ParticleTypes.CRIT,
                center.x,
                baseY + 0.2D,
                center.z,
                8,
                0.45D,
                0.1D,
                0.45D,
                0.05D
        );
    }

    private static void spawnTremorBurst(
            ServerLevel level,
            Player player,
            double radius
    ) {
        RandomSource random = level.random;
        Vec3 center = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0D, look.z).normalize();

        int tremorCount = Math.min(
                FemurSlamSkillHandler.QUAKE_TREMOR_MAX,
                Math.max(18, (int) (radius * radius * 0.6D))
        );
        spawnTremorAt(level, center, random, 0.32F, 14);
        for (int index = 0; index < tremorCount; index++) {
            Vec3 offset = randomPointInArc(random, radius);
            if (offset.lengthSqr() < 0.0001D) {
                continue;
            }
            Vec3 flatOffset = new Vec3(
                    offset.x,
                    0.0D,
                    offset.z
            ).normalize();
            if (flatOffset.dot(flatLook) < FemurSlamSkillHandler.MIN_DOT) {
                continue;
            }
            spawnTremorAt(
                    level,
                    center.add(offset),
                    random,
                    0.22F,
                    2
            );
        }
    }

    private static void spawnTremorAt(
            ServerLevel level,
            Vec3 position,
            RandomSource random,
            float ySpeed,
            int count
    ) {
        BlockState ground = level.getBlockState(
                BlockPos.containing(
                        position.x,
                        position.y,
                        position.z
                ).below()
        );
        if (ground.isAir()) {
            return;
        }
        BlockParticleOption debris = new BlockParticleOption(
                Objects.requireNonNull(ParticleTypes.BLOCK),
                ground
        );
        level.sendParticles(
                debris,
                position.x,
                position.y + 0.05D,
                position.z,
                count,
                0.06D,
                0.02D,
                0.06D,
                0.02D
        );
        level.sendParticles(
                Objects.requireNonNull(ParticleTypes.CLOUD),
                position.x,
                position.y + 0.08D,
                position.z,
                1,
                0.05D,
                0.0D,
                0.05D,
                0.02D
        );
        PacketDistributor.sendToPlayersInDimension(
                level,
                new TremorBlockPayload(
                        (float) position.x,
                        (float) position.y + 0.05F,
                        (float) position.z,
                        Block.getId(ground),
                        ySpeed + random.nextFloat() * 0.12F
                )
        );
    }

    private static Vec3 randomPointInArc(
            RandomSource random,
            double radius
    ) {
        double distance = Math.sqrt(random.nextDouble()) * radius;
        double angle = (random.nextDouble() - 0.5D)
                * (Math.PI * 2.0D / 3.0D);
        return new Vec3(
                distance * Math.cos(angle),
                0.2D,
                distance * Math.sin(angle)
        );
    }

    private static List<LivingEntity> findTargetsInArc(
            Player player,
            double range,
            double minimumDot
    ) {
        Vec3 origin = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0D, look.z).normalize();
        AABB bounds = player.getBoundingBox().inflate(
                range,
                1.25D,
                range
        );
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity.isPickable() && entity != player
        );

        targets.removeIf(entity -> {
            Vec3 toTarget = entity.position().subtract(origin);
            double distanceSquared = toTarget.x * toTarget.x
                    + toTarget.z * toTarget.z;
            if (distanceSquared > range * range) {
                return true;
            }
            Vec3 flatToTarget = new Vec3(
                    toTarget.x,
                    0.0D,
                    toTarget.z
            );
            if (flatToTarget.lengthSqr() < 0.0001D) {
                return true;
            }
            return flatToTarget.normalize().dot(flatLook) < minimumDot;
        });

        targets.sort((first, second) -> Double.compare(
                first.distanceToSqr(player),
                second.distanceToSqr(player)
        ));
        return List.copyOf(targets);
    }

    enum TickDecision {
        WAIT,
        FIRE,
        CANCEL
    }
}
