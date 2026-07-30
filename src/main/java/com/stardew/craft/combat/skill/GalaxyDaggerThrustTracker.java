package com.stardew.craft.combat.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GalaxyDaggerThrustTracker {

    public static final int STRIKE_COUNT = 3;
    public static final int STRIKE_INTERVAL_TICKS = 2;
    public static final int MARK_DURATION_TICKS = 60;
    public static final int STRIKE_ANIMATION_TICKS = 4;
    public static final int HIT_CONTEXT_LIFETIME_TICKS = 5;
    public static final double RETARGET_RANGE = 3.5D;

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private record State(
            long nextTick,
            int remainingStrikes,
            UUID targetId,
            String weaponId,
            String skillId,
            float baseDamageMultiplier,
            int intervalTicks,
            ResourceKey<Level> dimension
    ) {}

    enum TickDecision {
        WAIT,
        STRIKE,
        CANCEL
    }

    private GalaxyDaggerThrustTracker() {}

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId, float baseDamageMultiplier,
                             int strikes, int intervalTicks) {
        if (player == null || weaponId == null || skillId == null
                || strikes <= 0 || intervalTicks <= 0) {
            return;
        }
        UUID targetId = target != null ? target.getUUID() : null;
        ACTIVE.put(
                player.getUUID(),
                new State(
                        nowTick,
                        strikes,
                        targetId,
                        weaponId,
                        skillId,
                        baseDamageMultiplier,
                        intervalTicks,
                        player.level().dimension()
                )
        );
    }

    public static void start(ServerPlayer player, long nowTick, LivingEntity target,
                             String weaponId, String skillId, float baseDamageMultiplier) {
        start(
                player,
                nowTick,
                target,
                weaponId,
                skillId,
                baseDamageMultiplier,
                STRIKE_COUNT,
                STRIKE_INTERVAL_TICKS
        );
    }

    @SuppressWarnings("null")
    public static void tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }

        TickDecision decision = tickDecision(
                state.nextTick,
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
        if (decision == TickDecision.CANCEL) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        LivingEntity target = resolveTarget(serverLevel, player, state.targetId);
        if (target == null) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        if (state.remainingStrikes <= 0) {
            ACTIVE.remove(player.getUUID());
            return;
        }

        boolean hit = strike(player, target, nowTick, state.weaponId, state.skillId, state.baseDamageMultiplier);

        int remaining = state.remainingStrikes - 1;
        if (remaining <= 0) {
            if (hit && target.isAlive()) {
                GalaxyDaggerMarkTracker.apply(target, player, nowTick, MARK_DURATION_TICKS);
            }
            ACTIVE.remove(player.getUUID());
            return;
        }

        long nextTick = nextStrikeTick(nowTick, state.intervalTicks);
        ACTIVE.put(player.getUUID(), new State(nextTick, remaining, target.getUUID(),
            state.weaponId, state.skillId, state.baseDamageMultiplier,
            state.intervalTicks, state.dimension));
    }

    @SuppressWarnings("null")
    private static boolean strike(ServerPlayer player, LivingEntity target, long nowTick,
                                  String weaponId, String skillId, float baseDamageMultiplier) {
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        WeaponSkillAnimationDispatcher.sendSkillAnim(
                player,
                weaponId,
                skillId,
                STRIKE_ANIMATION_TICKS
        );
        WeaponSkillAnimationLock.setLock(
                player,
                nowTick,
                STRIKE_ANIMATION_TICKS
        );
        return WeaponSkillDamage.apply(
                player,
                target,
                createStrikeContext(skillId, baseDamageMultiplier),
                nowTick + HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    private static LivingEntity resolveTarget(ServerLevel level, ServerPlayer player, UUID targetId) {
        if (targetId != null) {
            Entity entity = level.getEntity(targetId);
            if (entity instanceof LivingEntity living
                    && canReuseStoredTarget(
                            living == player,
                            living.isAlive(),
                            living.isPickable()
                    )) {
                return living;
            }
        }
        return findTargetInFront(player, RETARGET_RANGE);
    }

    @SuppressWarnings("null")
    private static LivingEntity findTargetInFront(ServerPlayer player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 end = eyePos.add(lookVec.scale(range));
        AABB box = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

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

    public static boolean isActive(UUID playerId) {
        return playerId != null && ACTIVE.containsKey(playerId);
    }

    public static boolean isBoundToCurrentDimension(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        State state = ACTIVE.get(player.getUUID());
        return state != null && isSameDimension(
                state.dimension,
                player.level().dimension()
        );
    }

    static TickDecision tickDecision(
            long nextTick,
            long nowTick,
            boolean casterAvailable,
            boolean sameDimension
    ) {
        if (!casterAvailable || !sameDimension) {
            return TickDecision.CANCEL;
        }
        return nowTick < nextTick
                ? TickDecision.WAIT
                : TickDecision.STRIKE;
    }

    static long nextStrikeTick(long nowTick, int intervalTicks) {
        return nowTick + Math.max(1, intervalTicks);
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    static boolean canReuseStoredTarget(
            boolean caster,
            boolean alive,
            boolean pickable
    ) {
        return !caster && alive && pickable;
    }

    static SkillContext createStrikeContext(
            String skillId,
            float baseDamageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(baseDamageMultiplier)
                .guaranteedCrit(true)
                .build();
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
