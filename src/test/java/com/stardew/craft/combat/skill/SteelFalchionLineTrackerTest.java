package com.stardew.craft.combat.skill;

import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelFalchionLineTrackerTest {
    @Test
    void traceWindowEndsAndBurstsOnItsHundredthTick() {
        assertTrue(SteelFalchionLineTracker.isWithinTraceWindow(
                199L,
                200L
        ));
        assertFalse(SteelFalchionLineTracker.isWithinTraceWindow(
                200L,
                200L
        ));
        assertFalse(SteelFalchionLineTracker.isExpired(199L, 200L));
        assertTrue(SteelFalchionLineTracker.isExpired(200L, 200L));
    }

    @Test
    void dotAndEndBurstKeepTheirDistinctDamageContexts() {
        SkillContext minorDot =
                SteelFalchionLineTracker.createDamageContext(
                        SteelFalchionLineTracker.LINE_DOT_SKILL_ID,
                        SkillContext.SkillTier.MINOR,
                        0.30F
                );
        SkillContext dot = SteelFalchionLineTracker.createDamageContext(
                SteelFalchionLineTracker.LINE_DOT_SKILL_ID,
                SkillContext.SkillTier.MAJOR,
                0.50F
        );
        SkillContext burst =
                SteelFalchionLineTracker.createTraceBurstContext();

        assertEquals(SkillContext.SkillTier.MINOR, minorDot.getTier());
        assertEquals(0.30F, minorDot.getDamageMultiplier());
        assertEquals("steel_falchion_line_dot", dot.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, dot.getTier());
        assertEquals(0.50F, dot.getDamageMultiplier());
        assertEquals("steel_falchion_trace", burst.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, burst.getTier());
        assertEquals(1.0F, burst.getDamageMultiplier());
        assertEquals(
                5,
                SteelFalchionLineTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(20, SteelFalchionLineTracker.DOT_INTERVAL_TICKS);
        assertEquals(100, SteelFalchionLineTracker.DOT_DURATION_TICKS);
        assertEquals(
                5,
                SteelFalchionLineTracker.DOT_DURATION_TICKS
                        / SteelFalchionLineTracker.DOT_INTERVAL_TICKS
        );
        assertEquals(100, SteelFalchionLineTracker.LINE_DURATION_TICKS);
        assertEquals(0.55F, SteelFalchionLineTracker.LINE_WIDTH);
        assertEquals(0.70F, SteelFalchionLineTracker.TRIGGER_RADIUS);
        assertEquals(1.0F, SteelFalchionLineTracker.BURST_RADIUS);
        assertEquals(0.35F, SteelFalchionLineTracker.TRACE_POINT_STEP);
        assertEquals(0.18F, SteelFalchionLineTracker.TRACE_MIN_DISTANCE);
    }

    @Test
    void fixedLineIsCenteredOnTargetAndAlignedToCasterYaw() {
        Vec3 center = new Vec3(4.0D, 70.02D, -2.0D);
        List<Vec3> points =
                SteelFalchionLineTracker.createMinorLinePoints(
                        center,
                        0.0F
                );

        assertEquals(2, points.size());
        assertEquals(4.0D, points.get(0).x);
        assertEquals(-5.5D, points.get(0).z);
        assertEquals(4.0D, points.get(1).x);
        assertEquals(1.5D, points.get(1).z);
        assertEquals(70.02D, points.get(0).y);
        assertEquals(70.02D, points.get(1).y);
    }

    @Test
    void traceSamplingKeepsAuthoredMaximumPointSpacing() {
        List<Vec3> points = SteelFalchionLineTracker.sampleTracePoints(
                Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D)
        );

        assertEquals(3, points.size());
        assertEquals(1.0D / 3.0D, points.get(0).x);
        assertEquals(2.0D / 3.0D, points.get(1).x);
        assertEquals(1.0D, points.get(2).x);
        assertTrue(
                points.get(0).horizontalDistance()
                        <= SteelFalchionLineTracker.TRACE_POINT_STEP
        );
    }

    @Test
    void lineDistanceUsesTheWholeFinitePolyline() {
        List<Vec3> points = List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 2.0D)
        );

        assertEquals(
                0.25D,
                SteelFalchionLineTracker.distanceToPolylineSqr2D(
                        new Vec3(1.0D, 30.0D, 0.5D),
                        points
                )
        );
    }

    @Test
    void allSharedLineStateIsBoundToItsCastDimension() {
        assertTrue(SteelFalchionLineTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(SteelFalchionLineTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}
