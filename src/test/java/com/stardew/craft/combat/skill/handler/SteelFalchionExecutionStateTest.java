package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelFalchionExecutionStateTest {
    @Test
    void traceWindowEndsAndBurstsOnItsHundredthTick() {
        assertTrue(SteelFalchionExecutionSupport.isWithinTraceWindow(
                199L,
                200L
        ));
        assertFalse(SteelFalchionExecutionSupport.isWithinTraceWindow(
                200L,
                200L
        ));
        assertFalse(SteelFalchionExecutionSupport.isExpired(199L, 200L));
        assertTrue(SteelFalchionExecutionSupport.isExpired(200L, 200L));
    }

    @Test
    void dotAndEndBurstKeepTheirDistinctDamageContexts() {
        SkillContext minorDot =
                SteelFalchionExecutionSupport.createDamageContext(
                        SteelFalchionDotTracker.LINE_DOT_SKILL_ID,
                        SkillContext.SkillTier.MINOR,
                        0.30F
                );
        SkillContext majorDot =
                SteelFalchionExecutionSupport.createDamageContext(
                        SteelFalchionDotTracker.LINE_DOT_SKILL_ID,
                        SkillContext.SkillTier.MAJOR,
                        0.50F
                );
        SkillContext burst = SteelFalchionExecutionSupport
                .createTraceBurstContext();

        assertEquals(SkillContext.SkillTier.MINOR, minorDot.getTier());
        assertEquals(0.30F, minorDot.getDamageMultiplier());
        assertEquals("steel_falchion_line_dot", majorDot.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, majorDot.getTier());
        assertEquals(0.50F, majorDot.getDamageMultiplier());
        assertEquals("steel_falchion_trace", burst.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, burst.getTier());
        assertEquals(1.0F, burst.getDamageMultiplier());
        assertEquals(5, SteelFalchionDotTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(20, SteelFalchionDotTracker.DOT_INTERVAL_TICKS);
        assertEquals(100, SteelFalchionDotTracker.DOT_DURATION_TICKS);
        assertEquals(
                5,
                SteelFalchionDotTracker.DOT_DURATION_TICKS
                        / SteelFalchionDotTracker.DOT_INTERVAL_TICKS
        );
        assertEquals(100, SteelFalchionExecutionSupport.LINE_DURATION_TICKS);
        assertEquals(0.55F, SteelFalchionExecutionSupport.LINE_WIDTH);
        assertEquals(0.70F, SteelFalchionExecutionSupport.TRIGGER_RADIUS);
        assertEquals(1.0F, SteelFalchionExecutionSupport.BURST_RADIUS);
        assertEquals(0.35F, SteelFalchionExecutionSupport.TRACE_POINT_STEP);
        assertEquals(0.18F, SteelFalchionExecutionSupport.TRACE_MIN_DISTANCE);
    }

    @Test
    void fixedLineAndTraceGeometryRemainAuthored() {
        Vec3 center = new Vec3(4.0D, 70.02D, -2.0D);
        List<Vec3> line = SteelFalchionExecutionSupport
                .createMinorLinePoints(center, 0.0F);

        assertEquals(2, line.size());
        assertEquals(4.0D, line.get(0).x);
        assertEquals(-5.5D, line.get(0).z);
        assertEquals(4.0D, line.get(1).x);
        assertEquals(1.5D, line.get(1).z);
        assertEquals(70.02D, line.get(0).y);
        assertEquals(70.02D, line.get(1).y);

        List<Vec3> trace = SteelFalchionExecutionSupport.sampleTracePoints(
                Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D)
        );
        assertEquals(3, trace.size());
        assertEquals(1.0D / 3.0D, trace.get(0).x);
        assertEquals(2.0D / 3.0D, trace.get(1).x);
        assertEquals(1.0D, trace.get(2).x);
        assertTrue(trace.get(0).horizontalDistance()
                <= SteelFalchionExecutionSupport.TRACE_POINT_STEP);
    }

    @Test
    void distanceAndDimensionHelpersRemainStable() {
        List<Vec3> points = List.of(
                new Vec3(0.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 2.0D)
        );
        assertEquals(
                0.25D,
                SteelFalchionExecutionSupport.distanceToPolylineSqr2D(
                        new Vec3(1.0D, 30.0D, 0.5D),
                        points
                )
        );
        assertTrue(SteelFalchionExecutionSupport.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(SteelFalchionExecutionSupport.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void executionsAndDetachedDotsHaveSeparateOwners() throws IOException {
        String line = source("SteelFalchionLineExecutionState.java");
        String trace = source("SteelFalchionTraceExecutionState.java");
        String dots = source("SteelFalchionDotTracker.java");
        String lineHandler = source("SteelFalchionLineSkillHandler.java");
        String traceHandler = source("SteelFalchionTraceSkillHandler.java");
        String stateRuntime = mainSource(
                "combat/skill/runtime/WeaponSkillStateRuntime.java"
        );

        assertTrue(line.contains("implements SkillInstance.ExecutionState"));
        assertTrue(trace.contains("implements SkillInstance.ExecutionState"));
        assertFalse(line.contains("static final Map"));
        assertFalse(trace.contains("static final Map"));
        assertTrue(dots.contains("private static final Map<UUID, PlayerDots>"));
        assertTrue(dots.contains("void tickDetachedEffects("));
        assertFalse(dots.contains("tickExecutionState"));
        assertTrue(lineHandler.contains(
                "SteelFalchionLineExecutionState.class"
        ));
        assertTrue(traceHandler.contains(
                "SteelFalchionTraceExecutionState.class"
        ));
        assertTrue(stateRuntime.contains(
                "SteelFalchionDotTracker.tickDetachedEffects("
        ));
        assertFalse(stateRuntime.contains("SteelFalchionLineTracker"));
    }

    @Test
    void traceUpdatesThenBurstsAndNeverTicksDots() throws IOException {
        String trace = source("SteelFalchionTraceExecutionState.java");
        int advance = trace.indexOf(
                "SkillTickResult advance(SkillExecutionContext context)"
        );
        int update = trace.indexOf("updateTrace(context.player())", advance);
        int burst = trace.indexOf("burst(context);", update);
        int expiry = trace.indexOf(
                "SteelFalchionExecutionSupport.isExpired(",
                burst
        );
        int trigger = trace.indexOf(
                "handleTriggers(context.player(), context.nowTick())",
                expiry
        );
        assertTrue(advance >= 0);
        assertTrue(update > advance);
        assertTrue(burst > update);
        assertTrue(expiry > burst);
        assertTrue(trigger > expiry);
        assertFalse(trace.contains("tickDetachedEffects("));
    }

    private static String source(String fileName) throws IOException {
        return mainSource("combat/skill/handler/" + fileName);
    }

    private static String mainSource(String relative) throws IOException {
        Path path = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relative);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + path);
    }
}
