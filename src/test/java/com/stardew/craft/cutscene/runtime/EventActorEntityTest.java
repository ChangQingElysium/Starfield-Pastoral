package com.stardew.craft.cutscene.runtime;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventActorEntityTest {
    @Test
    void scriptedWalkAdvancesWithoutOvershooting() {
        Vec3 current = Vec3.ZERO;
        Vec3 target = new Vec3(3.0D, 0.0D, 4.0D);

        Vec3 next = EventActorEntity.nextWalkPosition(current, target, 1.0D);

        assertEquals(0.6D, next.x, 0.0001D);
        assertEquals(0.8D, next.z, 0.0001D);
    }

    @Test
    void scriptedWalkSnapsExactlyToFinalPoint() {
        Vec3 target = new Vec3(1.0D, 2.0D, 3.0D);

        Vec3 next = EventActorEntity.nextWalkPosition(
            new Vec3(0.95D, 2.0D, 3.0D), target, 0.08D);

        assertEquals(target, next);
    }
}
