package com.stardew.craft.combat.skill;

import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Owns the five short-lived ice-spine entities created by Yeti Tooth's major skill.
 */
public final class YetiToothSpineTracker {
    public static final int SPINE_COUNT = 5;
    public static final float ARC_DEGREES = 120.0F;
    public static final float ANGLE_STEP_DEGREES = 30.0F;
    public static final double SPAWN_RADIUS = 2.5;

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private static final class State {
        private final ResourceKey<Level> dimension;
        private final Set<UUID> spineIds;

        private State(
                ResourceKey<Level> dimension,
                Set<UUID> spineIds
        ) {
            this.dimension = dimension;
            this.spineIds = spineIds;
        }
    }

    private YetiToothSpineTracker() {}

    @SuppressWarnings("null")
    public static void start(
            ServerPlayer player,
            float damageMultiplier,
            String skillId
    ) {
        start(
                player,
                damageMultiplier,
                skillId,
                null
        );
    }

    @SuppressWarnings("null")
    public static void start(
            ServerPlayer player,
            float damageMultiplier,
            String skillId,
            WeaponDamageSnapshot weaponSnapshot
    ) {
        if (player == null || skillId == null) {
            return;
        }
        stop(player);

        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        Vec3 look = horizontalLook(player.getLookAngle(), player.getYRot());
        float baseYaw = (float) Math.toDegrees(
                Math.atan2(-look.x, look.z)
        );
        Set<UUID> spineIds = new LinkedHashSet<>();

        for (int index = 0; index < SPINE_COUNT; index++) {
            float angle = angleForIndex(baseYaw, index);
            Vec3 initialDirection = directionForAngle(angle);
            Vec3 start = center.add(initialDirection.scale(SPAWN_RADIUS));
            BlockPos surfacePos = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(start.x, player.getY(), start.z)
            );
            Vec3 groundStart = new Vec3(
                    start.x,
                    surfacePos.getY() + 0.05,
                    start.z
            );
            Vec3 direction = new Vec3(
                    groundStart.x - center.x,
                    0.0,
                    groundStart.z - center.z
            ).normalize();
            IceSpineEffectEntity spine = new IceSpineEffectEntity(
                    level,
                    player,
                    groundStart,
                    direction,
                    damageMultiplier,
                    skillId,
                    weaponSnapshot
            );
            if (level.addFreshEntity(spine)) {
                spineIds.add(spine.getUUID());
            }
        }

        if (!spineIds.isEmpty()) {
            ACTIVE.put(
                    player.getUUID(),
                    new State(level.dimension(), spineIds)
            );
        }
    }

    public static boolean isActive(ServerPlayer player) {
        State state = activeState(player);
        if (state == null) {
            return false;
        }

        ServerLevel level = player.server.getLevel(state.dimension);
        if (level == null) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        state.spineIds.removeIf(spineId -> {
            Entity spine = level.getEntity(spineId);
            return !(spine instanceof IceSpineEffectEntity)
                    || spine.isRemoved();
        });
        if (state.spineIds.isEmpty()) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void stop(ServerPlayer player) {
        if (player == null) {
            return;
        }
        State state = ACTIVE.remove(player.getUUID());
        if (state == null) {
            return;
        }
        ServerLevel level = player.server.getLevel(state.dimension);
        if (level != null) {
            discardSpines(level, state.spineIds);
        }
    }

    public static void removePlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    static float angleForIndex(float baseYaw, int index) {
        return baseYaw - ARC_DEGREES * 0.5F
                + index * ANGLE_STEP_DEGREES;
    }

    static Vec3 directionForAngle(float angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        return new Vec3(
                -Math.sin(radians),
                0.0,
                Math.cos(radians)
        ).normalize();
    }

    static Vec3 horizontalLook(Vec3 look, float yawDegrees) {
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            double yawRadians = Math.toRadians(yawDegrees);
            horizontal = new Vec3(
                    -Math.sin(yawRadians),
                    0.0,
                    Math.cos(yawRadians)
            );
        }
        return horizontal.normalize();
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static State activeState(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        State state = ACTIVE.get(player.getUUID());
        if (state != null && !isSameDimension(
                state.dimension,
                player.level().dimension()
        )) {
            stop(player);
            return null;
        }
        return state;
    }

    private static void discardSpines(
            ServerLevel level,
            Set<UUID> spineIds
    ) {
        for (UUID spineId : spineIds) {
            if (level.getEntity(spineId) instanceof IceSpineEffectEntity spine) {
                spine.discard();
            }
        }
    }
}
