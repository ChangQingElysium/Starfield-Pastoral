package com.stardew.craft.combat.skill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 双刃大剑“回刃折返”二段斩击追击
 */
public final class ClaymoreFoldbackTracker {

    public static final double RETURN_TARGET_RANGE = 4.5D;
    public static final float RETURN_DAMAGE_MULTIPLIER = 1.2F;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final int SLOW_DURATION_TICKS = 40;
    public static final int SLOW_AMPLIFIER = 0;
    public static final int RETURN_ANIMATION_TICKS = 12;

    private static final Map<UUID, State> PENDING = new HashMap<>();

    private record State(
            long fireTick,
            UUID targetId,
            String weaponId,
            String skillId,
            ResourceKey<Level> dimension
    ) {}

    enum TickDecision {
        WAIT,
        FIRE,
        CANCEL
    }

    private ClaymoreFoldbackTracker() {}

    public static void start(ServerPlayer player, long nowTick, int delayTicks,
                             LivingEntity target, String weaponId, String skillId) {
        if (player == null || weaponId == null || skillId == null) {
            return;
        }
        long fireTick = nowTick + Math.max(1, delayTicks);
        UUID targetId = target != null ? target.getUUID() : null;
        PENDING.put(
                player.getUUID(),
                new State(
                        fireTick,
                        targetId,
                        weaponId,
                        skillId,
                        player.level().dimension()
                )
        );
    }

    @SuppressWarnings("null")
    public static void tick(ServerPlayer player, long nowTick) {
        State state = PENDING.get(player.getUUID());
        if (state == null) {
            return;
        }
        TickDecision decision = tickDecision(
                state.fireTick,
                nowTick,
                player.isAlive() && !player.isRemoved(),
                isSameDimension(
                        state.dimension,
                        player.level().dimension()
                )
        );
        if (decision == TickDecision.WAIT) {
            return;
        }
        PENDING.remove(player.getUUID());
        if (decision == TickDecision.CANCEL
                || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        LivingEntity target = resolveTarget(player, serverLevel, state);
        if (target == null) {
            return;
        }

        // 允许二段在短时间内命中，清除默认无敌帧
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        WeaponSkillDamage.apply(
                player,
                target,
                createReturnContext(state.skillId),
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );

        if (target.hurtTime > 0) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN,
                    SLOW_DURATION_TICKS,
                    SLOW_AMPLIFIER,
                    false,
                    true,
                    true
            ));
        }

        WeaponSkillAnimationDispatcher.sendSkillAnim(
                player,
                state.weaponId,
                "claymore_foldback_return",
                RETURN_ANIMATION_TICKS
        );
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            PENDING.remove(player.getUUID());
        }
    }

    public static boolean hasState(UUID playerId) {
        return PENDING.containsKey(playerId);
    }

    public static boolean isBoundToCurrentDimension(ServerPlayer player) {
        State state = PENDING.get(player.getUUID());
        return state != null && isSameDimension(
                state.dimension,
                player.level().dimension()
        );
    }

    static TickDecision tickDecision(
            long fireTick,
            long nowTick,
            boolean casterAvailable,
            boolean sameDimension
    ) {
        if (!casterAvailable || !sameDimension) {
            return TickDecision.CANCEL;
        }
        return nowTick < fireTick
                ? TickDecision.WAIT
                : TickDecision.FIRE;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static SkillContext createReturnContext(String skillId) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(RETURN_DAMAGE_MULTIPLIER)
                .build();
    }

    private static LivingEntity resolveTarget(
            ServerPlayer player,
            ServerLevel serverLevel,
            State state
    ) {
        if (state.targetId != null) {
            Entity entity = serverLevel.getEntity(state.targetId);
            if (entity instanceof LivingEntity living
                    && canReuseStoredTarget(
                            living == player,
                            living.isAlive(),
                            living.isPickable()
                    )) {
                return living;
            }
        }
        return findTargetInFront(player, RETURN_TARGET_RANGE);
    }

    static boolean canReuseStoredTarget(
            boolean caster,
            boolean alive,
            boolean pickable
    ) {
        return !caster && alive && pickable;
    }

    private static LivingEntity findTargetInFront(ServerPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        @SuppressWarnings("null")
        Vec3 end = eyePos.add(lookVec.scale(range));
        @SuppressWarnings("null")
        AABB box = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

        @SuppressWarnings("null")
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player.level(),
            player,
            eyePos,
            end,
            box,
            entity -> entity instanceof LivingEntity && entity.isPickable() && entity != player
        );

        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        PENDING.remove(playerId);
    }
}
