package com.stardew.craft.command;

import com.stardew.craft.cutscene.runtime.EventActorEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorCommandTest {
    @Test
    void spawnPositionUsesHorizontalCameraDirection() {
        Vec3 source = new Vec3(10.0D, 64.0D, 20.0D);

        assertVec(new Vec3(10.0D, 64.0D, 22.5D), ActorCommand.defaultSpawnPosition(source, 0.0F));
        assertVec(new Vec3(7.5D, 64.0D, 20.0D), ActorCommand.defaultSpawnPosition(source, 90.0F));
    }

    @Test
    void actorCanFaceThePhotographer() {
        assertEquals(180.0F, Math.abs(ActorCommand.yawToward(
            new Vec3(0.0D, 64.0D, 2.5D), new Vec3(0.0D, 64.0D, 0.0D))), 0.001F);
        assertEquals(90.0F, ActorCommand.yawToward(
            new Vec3(2.5D, 64.0D, 0.0D), new Vec3(0.0D, 64.0D, 0.0D)), 0.001F);
    }

    @Test
    void npcIdsAreNormalizedForResourceLookup() {
        assertEquals("abigail", EventActorEntity.normalizeNpcId(" Abigail "));
        assertEquals("abigail", EventActorEntity.normalizeNpcId("stardewcraft:abigail"));
        assertEquals("", EventActorEntity.normalizeNpcId("othermod:abigail"));
    }

    @Test
    void walkingSpeedIsExpressedAsBlocksPerSecond() {
        assertEquals(0.08D, ActorCommand.blocksPerTick(1.6F), 0.0001D);
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 0.0001D);
        assertEquals(expected.y, actual.y, 0.0001D);
        assertEquals(expected.z, actual.z, 0.0001D);
    }
}
