package com.stardew.craft.combat.equipment;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EquipmentFireProtectionTest {
    @Test
    void failedRollIsNotRetriedDuringSameIgnition() {
        AtomicInteger rolls = new AtomicInteger();
        EquipmentFireProtection.FireDecision first =
                EquipmentFireProtection.advance(
                        null,
                        100L,
                        80,
                        () -> failedRoll(rolls, false)
                );
        EquipmentFireProtection.FireDecision next =
                EquipmentFireProtection.advance(
                        first.state(),
                        101L,
                        79,
                        () -> failedRoll(rolls, false)
                );
        EquipmentFireProtection.FireDecision brieflyExtinguished =
                EquipmentFireProtection.advance(
                        next.state(),
                        102L,
                        0,
                        () -> failedRoll(rolls, false)
                );
        EquipmentFireProtection.FireDecision reignitedWithinGrace =
                EquipmentFireProtection.advance(
                        brieflyExtinguished.state(),
                        102L + EquipmentFireProtection.RELEASE_GRACE_TICKS - 1L,
                        80,
                        () -> failedRoll(rolls, false)
                );

        assertEquals(1, rolls.get());
        assertEquals(
                EquipmentFireProtection.ProtectionMode.UNPROTECTED,
                reignitedWithinGrace.state().mode()
        );
        assertEquals(-1, reignitedWithinGrace.maximumFireTicks());
    }

    @Test
    void newIgnitionAfterReleaseGraceRollsAgain() {
        AtomicInteger rolls = new AtomicInteger();
        EquipmentFireProtection.FireDecision first =
                EquipmentFireProtection.advance(
                        null,
                        100L,
                        80,
                        () -> failedRoll(rolls, false)
                );
        EquipmentFireProtection.FireDecision released =
                EquipmentFireProtection.advance(
                        first.state(),
                        180L,
                        0,
                        () -> failedRoll(rolls, false)
                );
        EquipmentFireProtection.FireDecision second =
                EquipmentFireProtection.advance(
                        released.state(),
                        180L + EquipmentFireProtection.RELEASE_GRACE_TICKS,
                        80,
                        () -> {
                            rolls.incrementAndGet();
                            return new EquipmentFireProtection.ProtectionRoll(
                                    true,
                                    false
                            );
                        }
                );

        assertEquals(2, rolls.get());
        assertNotNull(second.state());
        assertEquals(
                EquipmentFireProtection.ProtectionMode.RESISTED,
                second.state().mode()
        );
        assertEquals(0, second.maximumFireTicks());
    }

    @Test
    void sturdyCapsOneIgnitionAtHalfDuration() {
        AtomicInteger rolls = new AtomicInteger();
        EquipmentFireProtection.FireDecision first =
                EquipmentFireProtection.advance(
                        null,
                        50L,
                        100,
                        () -> failedRoll(rolls, true)
                );
        EquipmentFireProtection.FireDecision continued =
                EquipmentFireProtection.advance(
                        first.state(),
                        60L,
                        100,
                        () -> failedRoll(rolls, true)
                );
        EquipmentFireProtection.FireDecision exhausted =
                EquipmentFireProtection.advance(
                        continued.state(),
                        100L,
                        100,
                        () -> failedRoll(rolls, true)
                );

        assertEquals(1, rolls.get());
        assertEquals(50, first.maximumFireTicks());
        assertEquals(40, continued.maximumFireTicks());
        assertEquals(0, exhausted.maximumFireTicks());
    }

    private static EquipmentFireProtection.ProtectionRoll failedRoll(
            AtomicInteger rolls,
            boolean sturdy
    ) {
        rolls.incrementAndGet();
        return new EquipmentFireProtection.ProtectionRoll(false, sturdy);
    }
}
