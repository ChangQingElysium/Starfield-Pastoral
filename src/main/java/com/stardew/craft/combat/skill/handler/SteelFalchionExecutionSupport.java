package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared pure geometry and identifiers for the two Steel Falchion executions. */
final class SteelFalchionExecutionSupport {
    static final int LINE_DURATION_TICKS = 100;
    static final int SPEED_DURATION_TICKS = 100;
    static final int TRACE_SPEED_AMPLIFIER = 2;
    static final int LINE_SPEED_AMPLIFIER = 1;
    static final float LINE_LENGTH = 7.0F;
    static final float LINE_WIDTH = 0.55F;
    static final float TRIGGER_RADIUS = 0.70F;
    static final float BURST_RADIUS = 1.0F;
    static final float TRACE_POINT_STEP = 0.35F;
    static final float TRACE_MIN_DISTANCE = 0.18F;
    static final float TRACE_BURST_DAMAGE_MULTIPLIER = 1.0F;
    static final String TRACE_SKILL_ID = "steel_falchion_trace";

    private static int nextLineId = 1;

    private SteelFalchionExecutionSupport() {}

    static int nextLineId() {
        return nextLineId++;
    }

    static List<Vec3> createMinorLinePoints(
            Vec3 center,
            float yawDegrees
    ) {
        Vec3 direction = yawToDirection(yawDegrees);
        return List.of(
                center.add(direction.scale(-LINE_LENGTH * 0.5D)),
                center.add(direction.scale(LINE_LENGTH * 0.5D))
        );
    }

    static List<Vec3> sampleTracePoints(Vec3 start, Vec3 end) {
        double distance = end.subtract(start).horizontalDistance();
        int steps = Math.max(
                1,
                (int) Math.ceil(distance / TRACE_POINT_STEP)
        );
        Vec3 delta = end.subtract(start);
        List<Vec3> points = new ArrayList<>(steps);
        for (int index = 1; index <= steps; index++) {
            double progress = index / (double) steps;
            points.add(start.add(delta.scale(progress)));
        }
        return points;
    }

    static double distanceToPolylineSqr2D(
            Vec3 point,
            List<Vec3> points
    ) {
        double minimum = Double.MAX_VALUE;
        for (int index = 0; index < points.size() - 1; index++) {
            double distance = distanceToSegmentSqr2D(
                    point,
                    points.get(index),
                    points.get(index + 1)
            );
            minimum = Math.min(minimum, distance);
        }
        return minimum;
    }

    static AABB computeBounds(List<Vec3> points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static SkillContext createDamageContext(
            String skillId,
            SkillContext.SkillTier tier,
            float damageMultiplier
    ) {
        return SkillContext.builder()
                .skillId(skillId)
                .tier(tier)
                .damageMultiplier(damageMultiplier)
                .build();
    }

    static SkillContext createTraceBurstContext() {
        return createDamageContext(
                TRACE_SKILL_ID,
                SkillContext.SkillTier.MAJOR,
                TRACE_BURST_DAMAGE_MULTIPLIER
        );
    }

    static boolean isWithinTraceWindow(long nowTick, long endTick) {
        return !isExpired(nowTick, endTick);
    }

    static boolean isExpired(long nowTick, long endTick) {
        return nowTick >= endTick;
    }

    static boolean isSameDimension(
            ResourceKey<Level> expected,
            ResourceKey<Level> actual
    ) {
        return expected.equals(actual);
    }

    private static Vec3 yawToDirection(float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vec3(
                -Math.sin(radians),
                0.0D,
                Math.cos(radians)
        );
    }

    private static double distanceToSegmentSqr2D(
            Vec3 point,
            Vec3 start,
            Vec3 end
    ) {
        Vec3 fromStart = new Vec3(
                point.x - start.x,
                0.0D,
                point.z - start.z
        );
        Vec3 segment = new Vec3(
                end.x - start.x,
                0.0D,
                end.z - start.z
        );
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared < 1.0E-6D) {
            return fromStart.lengthSqr();
        }
        double progress = (fromStart.x * segment.x
                + fromStart.z * segment.z) / lengthSquared;
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        double closestX = start.x + segment.x * progress;
        double closestZ = start.z + segment.z * progress;
        double deltaX = point.x - closestX;
        double deltaZ = point.z - closestZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }
}
