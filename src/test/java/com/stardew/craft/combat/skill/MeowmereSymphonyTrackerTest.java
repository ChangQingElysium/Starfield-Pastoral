package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeowmereSymphonyTrackerTest {
    @Test
    void castContinuesWhileAtLeastOneProjectileRemains() {
        assertEquals(
                SkillTickResult.CONTINUE,
                MeowmereSymphonyTracker.status(
                        true,
                        true,
                        false,
                        300L,
                        301L
                )
        );
    }

    @Test
    void castCompletesWhenEveryProjectileIsGoneOrAtTheTimeout() {
        assertEquals(
                SkillTickResult.COMPLETE,
                MeowmereSymphonyTracker.status(
                        true,
                        true,
                        true,
                        250L,
                        301L
                )
        );
        assertEquals(
                SkillTickResult.COMPLETE,
                MeowmereSymphonyTracker.status(
                        true,
                        true,
                        false,
                        301L,
                        301L
                )
        );
    }

    @Test
    void casterOrProjectileCrossingDimensionsCancelsTheWholeVolley() {
        assertEquals(
                SkillTickResult.CANCEL,
                MeowmereSymphonyTracker.status(
                        false,
                        true,
                        false,
                        250L,
                        301L
                )
        );
        assertEquals(
                SkillTickResult.CANCEL,
                MeowmereSymphonyTracker.status(
                        true,
                        false,
                        false,
                        250L,
                        301L
                )
        );
        assertTrue(MeowmereSymphonyTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(MeowmereSymphonyTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void removingAnOfflineCasterWithoutStateIsIdempotent() {
        assertDoesNotThrow(() ->
                MeowmereSymphonyTracker.removeCaster(UUID.randomUUID())
        );
    }
}
