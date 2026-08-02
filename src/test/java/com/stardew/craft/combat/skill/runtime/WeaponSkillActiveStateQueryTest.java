package com.stardew.craft.combat.skill.runtime;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillActiveStateQueryTest {
    private static final UUID CASTER = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final ResourceLocation WEAPON_ID = id("lava_katana");
    private static final ResourceLocation SKILL_ID = id(
            "lava_katana_reverb"
    );

    @Test
    void exactActiveCasterSkillAndTypeExposeTheirState() {
        TestState expected = new TestState("active");
        SkillInstance instance = activeInstance(
                CASTER,
                SKILL_ID,
                expected
        );

        assertEquals(
                expected,
                WeaponSkillRuntime.findActiveExecutionState(
                        List.of(instance),
                        CASTER,
                        SKILL_ID,
                        TestState.class
                ).orElseThrow()
        );
    }

    @Test
    void wrongCasterSkillOrStateTypeAreInvisible() {
        SkillInstance instance = activeInstance(
                CASTER,
                SKILL_ID,
                new TestState("active")
        );

        assertTrue(WeaponSkillRuntime.findActiveExecutionState(
                List.of(instance),
                UUID.randomUUID(),
                SKILL_ID,
                TestState.class
        ).isEmpty());
        assertTrue(WeaponSkillRuntime.findActiveExecutionState(
                List.of(instance),
                CASTER,
                id("other_skill"),
                TestState.class
        ).isEmpty());
        assertTrue(WeaponSkillRuntime.findActiveExecutionState(
                List.of(instance),
                CASTER,
                SKILL_ID,
                OtherState.class
        ).isEmpty());
    }

    @Test
    void uninitializedRecoveringAndEndedInstancesAreInvisible() {
        SkillInstance uninitialized = newInstance(CASTER, SKILL_ID);
        uninitialized.activate();

        SkillInstance recovering = activeInstance(
                CASTER,
                SKILL_ID,
                new TestState("recovering")
        );
        recovering.beginRecovery();

        SkillInstance ended = activeInstance(
                CASTER,
                SKILL_ID,
                new TestState("ended")
        );
        ended.finish(SkillInstance.EndReason.COMPLETED);

        for (SkillInstance instance : List.of(
                uninitialized,
                recovering,
                ended
        )) {
            assertTrue(WeaponSkillRuntime.findActiveExecutionState(
                    List.of(instance),
                    CASTER,
                    SKILL_ID,
                    TestState.class
            ).isEmpty());
        }
    }

    private static SkillInstance activeInstance(
            UUID casterId,
            ResourceLocation skillId,
            SkillInstance.ExecutionState state
    ) {
        SkillInstance instance = newInstance(casterId, skillId);
        instance.activate();
        instance.initializeExecutionState(state);
        return instance;
    }

    private static SkillInstance newInstance(
            UUID casterId,
            ResourceLocation skillId
    ) {
        return new SkillInstance(
                UUID.randomUUID(),
                casterId,
                1,
                WEAPON_ID,
                skillId,
                100L,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 1.0D),
                1L
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft",
                path
        );
    }

    private record TestState(String value)
            implements SkillInstance.ExecutionState {
    }

    private record OtherState()
            implements SkillInstance.ExecutionState {
    }
}
