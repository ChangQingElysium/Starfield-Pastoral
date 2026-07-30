package com.stardew.craft.combat.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 进化态裂隙轨迹：踏入伤害 + 结束爆裂。
 */
public final class RiftPathDamageTracker {
    private static final class State {
        private final Vec3 start;
        private final float yaw;
        private final float length;
        private final int durationTicks;
        private final String skillId;
        private final WeaponDamageSnapshot weaponSnapshot;
        private int age = 0;
        private final Set<UUID> hit = new HashSet<>();

        private State(
                Vec3 start,
                float yaw,
                float length,
                int durationTicks,
                String skillId,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.start = start;
            this.yaw = yaw;
            this.length = length;
            this.durationTicks = durationTicks;
            this.skillId = skillId;
            this.weaponSnapshot = weaponSnapshot;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private RiftPathDamageTracker() {}

    public static void start(ServerPlayer player, Vec3 start, float yaw, float length, int durationTicks, String skillId) {
        startInternal(
                player,
                start,
                yaw,
                length,
                durationTicks,
                skillId,
                null
        );
    }

    public static void start(
            ServerPlayer player,
            Vec3 start,
            float yaw,
            float length,
            int durationTicks,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        startInternal(
                player,
                start,
                yaw,
                length,
                durationTicks,
                skillId,
                Objects.requireNonNull(
                        weaponSnapshot,
                        "weaponSnapshot"
                )
        );
    }

    private static void startInternal(
            ServerPlayer player,
            Vec3 start,
            float yaw,
            float length,
            int durationTicks,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0 || length <= 0.0f) {
            return;
        }
        ACTIVE.put(
                player.getUUID(),
                new State(
                        start,
                        yaw,
                        length,
                        durationTicks,
                        skillId,
                        weaponSnapshot
                )
        );
    }

    public static void tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        state.age++;

        applyStepDamage(player, level, state);

        if (state.age >= state.durationTicks) {
            applyFinalBurst(player, level, state);
            ACTIVE.remove(player.getUUID());
        }
    }

    @SuppressWarnings("null")
    private static void applyStepDamage(ServerPlayer player, ServerLevel level, State state) {
        AABB box = buildAabb(state);
        List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        for (LivingEntity target : targets) {
            if (state.hit.contains(target.getUUID())) {
                continue;
            }
            if (!isInsidePath(state, target.position())) {
                continue;
            }
            state.hit.add(target.getUUID());
            SkillContext context = SkillContext.builder()
                .skillId(state.skillId)
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(0.60f)
                .build();
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            }
        }
    }

    @SuppressWarnings("null")
    private static void applyFinalBurst(ServerPlayer player, ServerLevel level, State state) {
        AABB box = buildAabb(state);
        List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        for (LivingEntity target : targets) {
            if (!isInsidePath(state, target.position())) {
                continue;
            }
            SkillContext context = SkillContext.builder()
                .skillId(state.skillId + "_burst")
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(1.00f)
                .build();
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .RESPECT_AT_IMPACT
                );
            }
        }
    }

    @SuppressWarnings("null")
    private static AABB buildAabb(State state) {
        Vec3 dir = yawToDir(state.yaw);
        Vec3 end = state.start.add(dir.scale(state.length));
        Vec3 min = new Vec3(Math.min(state.start.x, end.x), Math.min(state.start.y, end.y), Math.min(state.start.z, end.z));
        Vec3 max = new Vec3(Math.max(state.start.x, end.x), Math.max(state.start.y, end.y), Math.max(state.start.z, end.z));
        return new AABB(min, max).inflate(0.9, 1.2, 0.9);
    }

    @SuppressWarnings("null")
    private static boolean isInsidePath(State state, Vec3 pos) {
        Vec3 dir = yawToDir(state.yaw);
        Vec3 to = pos.subtract(state.start);
        double along = to.dot(dir);
        if (along < 0.0 || along > state.length) {
            return false;
        }
        Vec3 proj = state.start.add(dir.scale(along));
        double lateral = pos.subtract(proj).horizontalDistance();
        return lateral <= 0.6;
    }

    private static Vec3 yawToDir(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        double x = -Math.sin(rad);
        double z = Math.cos(rad);
        return new Vec3(x, 0.0, z).normalize();
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
