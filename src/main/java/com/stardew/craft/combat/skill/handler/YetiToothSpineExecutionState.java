package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillExecutionContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Exact entity ownership for one Yeti Tooth Spine execution. */
final class YetiToothSpineExecutionState
        implements SkillInstance.ExecutionState {
    private final ResourceKey<Level> dimension;
    private final Set<UUID> spineIds = new LinkedHashSet<>();

    YetiToothSpineExecutionState(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    void spawnSpines(SkillExecutionContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        Vec3 look = horizontalLook(player.getLookAngle(), player.getYRot());
        float baseYaw = (float) Math.toDegrees(
                Math.atan2(-look.x, look.z)
        );

        for (int index = 0;
                index < YetiToothSpineSkillHandler.SPINE_COUNT;
                index++) {
            float angle = angleForIndex(baseYaw, index);
            Vec3 initialDirection = directionForAngle(angle);
            Vec3 start = center.add(
                    initialDirection.scale(
                            YetiToothSpineSkillHandler.SPAWN_RADIUS
                    )
            );
            BlockPos surfacePosition = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(
                            start.x,
                            player.getY(),
                            start.z
                    )
            );
            Vec3 groundStart = new Vec3(
                    start.x,
                    surfacePosition.getY() + 0.05D,
                    start.z
            );
            Vec3 direction = new Vec3(
                    groundStart.x - center.x,
                    0.0D,
                    groundStart.z - center.z
            ).normalize();
            IceSpineEffectEntity spine = new IceSpineEffectEntity(
                    level,
                    player,
                    groundStart,
                    direction,
                    context.skillData().getDamagePercent() / 100.0F,
                    context.skillData().getId(),
                    context.weaponSnapshot()
            );
            if (!level.addFreshEntity(spine)) {
                continue;
            }

            UUID spineId = spine.getUUID();
            spineIds.add(spineId);
        }
    }

    boolean isActive(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(dimension);
        if (level == null) {
            spineIds.clear();
            return false;
        }
        spineIds.removeIf(spineId -> {
            Entity spine = level.getEntity(spineId);
            return !(spine instanceof IceSpineEffectEntity)
                    || spine.isRemoved();
        });
        return !spineIds.isEmpty();
    }

    void discardSpines(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            spineIds.clear();
            return;
        }
        RuntimeException failure = null;
        for (UUID spineId : Set.copyOf(spineIds)) {
            try {
                discardSpine(level, spineId);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else if (failure != exception) {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static float angleForIndex(float baseYaw, int index) {
        return baseYaw
                - YetiToothSpineSkillHandler.ARC_DEGREES * 0.5F
                + index * YetiToothSpineSkillHandler.ANGLE_STEP_DEGREES;
    }

    static Vec3 directionForAngle(float angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        return new Vec3(
                -Math.sin(radians),
                0.0D,
                Math.cos(radians)
        ).normalize();
    }

    static Vec3 horizontalLook(Vec3 look, float yawDegrees) {
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            double yawRadians = Math.toRadians(yawDegrees);
            horizontal = new Vec3(
                    -Math.sin(yawRadians),
                    0.0D,
                    Math.cos(yawRadians)
            );
        }
        return horizontal.normalize();
    }

    private void discardSpine(ServerLevel level, UUID spineId) {
        if (!spineIds.contains(spineId)) {
            return;
        }
        if (level.getEntity(spineId) instanceof IceSpineEffectEntity spine) {
            spine.discard();
        }
        spineIds.remove(spineId);
    }
}
