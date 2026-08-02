package com.stardew.craft.combat.skill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
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
    /** Identifies one exact detached rift without exposing mutable damage state. */
    public record Handle(UUID playerId, UUID pathId) {}

    private static final class State {
        private final UUID pathId;
        private final Vec3 start;
        private final float yaw;
        private final float length;
        private final int durationTicks;
        private final String skillId;
        private final ResourceKey<Level> dimension;
        private final WeaponDamageSnapshot weaponSnapshot;
        private int age = 0;
        private final Set<UUID> hit = new HashSet<>();

        private State(
                UUID pathId,
                Vec3 start,
                float yaw,
                float length,
                int durationTicks,
                String skillId,
                ResourceKey<Level> dimension,
                WeaponDamageSnapshot weaponSnapshot
        ) {
            this.pathId = pathId;
            this.start = start;
            this.yaw = yaw;
            this.length = length;
            this.durationTicks = durationTicks;
            this.skillId = skillId;
            this.dimension = dimension;
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

    /** Starts a rift and returns ownership of that exact detached path. */
    public static Handle startExact(
            ServerPlayer player,
            Vec3 start,
            float yaw,
            float length,
            int durationTicks,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        return startInternal(
                player,
                start,
                yaw,
                length,
                durationTicks,
                skillId,
                weaponSnapshot
        );
    }

    private static Handle startInternal(
            ServerPlayer player,
            Vec3 start,
            float yaw,
            float length,
            int durationTicks,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || durationTicks <= 0 || length <= 0.0f) {
            return null;
        }
        UUID pathId = UUID.randomUUID();
        ACTIVE.put(
                player.getUUID(),
                new State(
                        pathId,
                        start,
                        yaw,
                        length,
                        durationTicks,
                        skillId,
                        player.level().dimension(),
                        weaponSnapshot
                )
        );
        return new Handle(player.getUUID(), pathId);
    }

    public static void tick(ServerPlayer player, long nowTick) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (!isSameDimension(
                state.dimension,
                player.level().dimension()
        )) {
            ACTIVE.remove(player.getUUID(), state);
            return;
        }
        ServerLevel level = player.serverLevel();
        state.age++;

        applyStepDamage(player, level, state);

        if (state.age >= state.durationTicks) {
            try {
                applyFinalBurst(player, level, state);
            } finally {
                ACTIVE.remove(player.getUUID(), state);
            }
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
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
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
            if (state.weaponSnapshot == null) {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
                );
            } else {
                WeaponSkillDamage.apply(
                        player,
                        target,
                        context,
                        state.weaponSnapshot,
                        level.getGameTime() + 5,
                        WeaponSkillDamage.AttackGatePolicy
                                .SKILL_DAMAGE,
                        WeaponSkillDamage.HitCooldownPolicy
                                .BYPASS_FOR_AUTHORED_SEQUENCE
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

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    /** Cancels only the represented rift, never a newer replacement path. */
    public static boolean cancel(ServerPlayer player, Handle handle) {
        if (player == null || handle == null
                || !player.getUUID().equals(handle.playerId())) {
            return false;
        }
        State state = ACTIVE.get(handle.playerId());
        if (state == null || !state.pathId.equals(handle.pathId())) {
            return false;
        }
        return ACTIVE.remove(handle.playerId(), state);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
