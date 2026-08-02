package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.network.TemplarMarkPayload;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** One Templar Judgement window with ordered cross-dimension target refs. */
final class TemplarJudgementExecutionState
        implements SkillInstance.ExecutionState {
    private record TargetRef(
            ResourceKey<Level> dimension,
            UUID entityId
    ) {
        private TargetRef {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(entityId, "entityId");
        }
    }

    private final ResourceKey<Level> casterDimension;
    private final long endTick;
    private final List<TargetRef> targets;
    private final WeaponDamageSnapshot weaponSnapshot;
    private boolean settled;

    TemplarJudgementExecutionState(
            ResourceKey<Level> casterDimension,
            long nowTick,
            int durationTicks,
            List<LivingEntity> targets,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Templar Judgement duration must be positive"
            );
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Templar Judgement requires marked targets"
            );
        }
        this.casterDimension = Objects.requireNonNull(
                casterDimension,
                "casterDimension"
        );
        this.endTick = nowTick + durationTicks;
        List<TargetRef> targetRefs = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            targetRefs.add(new TargetRef(
                    target.level().dimension(),
                    target.getUUID()
            ));
        }
        this.targets = List.copyOf(targetRefs);
        this.weaponSnapshot = Objects.requireNonNull(
                weaponSnapshot,
                "weaponSnapshot"
        );
    }

    @SuppressWarnings("null")
    void startPresentation(
            ServerPlayer player,
            List<LivingEntity> markedTargets,
            int durationTicks
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (LivingEntity target : markedTargets) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    target,
                    new TemplarMarkPayload(target.getId(), durationTicks)
            );
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.6D,
                    target.getZ(),
                    6,
                    0.4D,
                    0.2D,
                    0.4D,
                    0.01D
            );
        }
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.6F,
                1.7F
        );
    }

    boolean isActive(
            ServerPlayer player,
            long nowTick
    ) {
        return !settled
                && casterDimension.equals(player.level().dimension())
                && isWithinActiveWindow(nowTick, endTick);
    }

    List<LivingEntity> getMarkedTargets(ServerPlayer player) {
        if (settled
                || !casterDimension.equals(player.level().dimension())
                || player.getServer() == null) {
            return List.of();
        }
        return resolveTargets(player.getServer());
    }

    SkillTickResult advance(SkillExecutionContext context) {
        if (settled) {
            return SkillTickResult.COMPLETE;
        }
        if (!casterDimension.equals(
                context.player().level().dimension()
        )) {
            settled = true;
            return SkillTickResult.CANCEL;
        }
        if (isWithinActiveWindow(context.nowTick(), endTick)) {
            return SkillTickResult.CONTINUE;
        }

        settle(context);
        settled = true;
        return SkillTickResult.COMPLETE;
    }

    void cancel() {
        settled = true;
    }

    private void settle(SkillExecutionContext context) {
        MinecraftServer server = context.player().getServer();
        if (server == null) {
            return;
        }
        for (LivingEntity target : resolveTargets(server)) {
            SkillContext hitContext = createSettlementContext(
                    context.skillData().getId()
            );
            WeaponSkillDamage.apply(
                    context.player(),
                    target,
                    hitContext,
                    weaponSnapshot,
                    context.nowTick()
                            + TemplarJudgementSkillHandler
                                    .HIT_CONTEXT_LIFETIME_TICKS,
                    WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,
                    WeaponSkillDamage.HitCooldownPolicy
                            .BYPASS_FOR_AUTHORED_SEQUENCE
            );
        }
    }

    private List<LivingEntity> resolveTargets(MinecraftServer server) {
        List<LivingEntity> resolved = new ArrayList<>();
        for (TargetRef targetRef : targets) {
            ServerLevel targetLevel = server.getLevel(
                    targetRef.dimension()
            );
            if (targetLevel == null) {
                continue;
            }
            Entity entity = targetLevel.getEntity(targetRef.entityId());
            if (entity instanceof LivingEntity living && living.isAlive()) {
                resolved.add(living);
            }
        }
        return List.copyOf(resolved);
    }

    static boolean isWithinActiveWindow(
            long nowTick,
            long endTick
    ) {
        return nowTick < endTick;
    }

    static SkillContext createSettlementContext(String skillId) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(
                        TemplarJudgementSkillHandler
                                .SETTLEMENT_DAMAGE_MULTIPLIER
                )
                .build();
    }
}
