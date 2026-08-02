package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GalaxyJudgementExecutionStateTest {
    @Test
    void starfallsUseTheAuthoredThreeStageTenTickSchedule() {
        GalaxyJudgementExecutionState state = state(100L, 3, 3);

        assertEquals(110L, state.nextStrikeTick());
        assertEquals(3, state.remainingStrikes());
        assertFalse(state.isStrikeDue(109L));
        assertTrue(state.isStrikeDue(110L));

        assertEquals(
                SkillTickResult.CONTINUE,
                state.recordCompletedStrike(113L)
        );
        assertEquals(2, state.remainingStrikes());
        assertEquals(123L, state.nextStrikeTick());

        assertEquals(
                SkillTickResult.CONTINUE,
                state.recordCompletedStrike(123L)
        );
        assertEquals(1, state.remainingStrikes());
        assertEquals(133L, state.nextStrikeTick());

        assertEquals(
                SkillTickResult.COMPLETE,
                state.recordCompletedStrike(140L)
        );
        assertEquals(0, state.remainingStrikes());
    }

    @Test
    void everyTargetReceivesOnePlusClampedExtraHits() {
        assertEquals(1, state(0L, 3, -1).hitsPerTarget());
        assertEquals(1, state(0L, 3, 0).hitsPerTarget());
        assertEquals(4, state(0L, 3, 3).hitsPerTarget());
        assertEquals(4, state(0L, 3, 99).hitsPerTarget());
        assertThrows(
                IllegalArgumentException.class,
                () -> state(0L, 0, 0)
        );
    }

    @Test
    void delayedStrikeContextAndCancellationGateRemainStable() {
        SkillContext strike =
                GalaxyJudgementExecutionState.createStrikeContext(
                        "galaxy_judgement",
                        GalaxyJudgementSkillHandler
                                .STARFALL_DAMAGE_MULTIPLIER
                );

        assertEquals("galaxy_judgement", strike.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, strike.getTier());
        assertEquals(0.70F, strike.getDamageMultiplier());
        assertTrue(GalaxyJudgementExecutionState.isValidContext(
                true,
                true
        ));
        assertFalse(GalaxyJudgementExecutionState.isValidContext(
                false,
                true
        ));
        assertFalse(GalaxyJudgementExecutionState.isValidContext(
                true,
                false
        ));
    }

    @Test
    void runtimeInstanceOwnsStarfallStateAndReleaseSnapshot()
            throws IOException {
        Path root = handlerRoot();
        String handler = Files.readString(root.resolve(
                "GalaxyJudgementSkillHandler.java"
        ));
        String state = Files.readString(root.resolve(
                "GalaxyJudgementExecutionState.java"
        ));

        assertTrue(handler.contains(
                "instance.initializeExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                "GalaxyJudgementExecutionState.class"
        ));
        assertFalse(handler.contains("StarfallTracker"));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));

        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown("
        );
        int mainDamage = handler.indexOf(
                "WeaponSkillDamage.apply(",
                cooldown
        );
        int stateRegistration = handler.indexOf(
                "instance.initializeExecutionState(",
                cooldown
        );
        int committed = handler.indexOf(
                "instance.registerCommittedEffect(",
                stateRegistration
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                stateRegistration
        );
        int lock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                animation
        );
        assertTrue(cooldown >= 0);
        assertTrue(stateRegistration > cooldown);
        assertTrue(committed > stateRegistration);
        assertTrue(mainDamage > committed);
        assertTrue(animation > mainDamage);
        assertTrue(lock > animation);

        int currentCenter = state.indexOf(
                "Vec3 center = player.position();"
        );
        int damage = state.indexOf(
                "WeaponSkillDamage.apply(",
                currentCenter
        );
        int snapshot = state.indexOf("weaponSnapshot", damage);
        int skillDamage = state.indexOf("SKILL_DAMAGE", snapshot);
        int presentation = state.indexOf(
                "level.playSound(",
                skillDamage
        );
        assertTrue(currentCenter >= 0);
        assertTrue(damage > currentCenter);
        assertTrue(snapshot > damage);
        assertTrue(skillDamage > snapshot);
        assertTrue(presentation > skillDamage);
        assertFalse(state.contains("RESPECT_AT_IMPACT"));
    }

    private static GalaxyJudgementExecutionState state(
            long nowTick,
            int strikes,
            int extraHits
    ) {
        WeaponDamageSnapshot snapshot = WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID,
                        "galaxy_sword"
                ),
                ItemStack.EMPTY
        );
        return new GalaxyJudgementExecutionState(
                nowTick,
                strikes,
                extraHits,
                GalaxyJudgementSkillHandler.STARFALL_RADIUS,
                GalaxyJudgementSkillHandler
                        .STARFALL_DAMAGE_MULTIPLIER,
                "galaxy_judgement",
                Level.OVERWORLD,
                snapshot
        );
    }

    private static Path handlerRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate handler source root");
    }
}
