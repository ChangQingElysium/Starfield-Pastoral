package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperedQuenchExecutionStateTest {
    @Test
    void delayedBlastFiresAtTheInclusiveBoundaryThenCompletesNextTick() {
        TemperedQuenchExecutionState state =
                new TemperedQuenchExecutionState(Level.OVERWORLD);
        UUID targetId = UUID.randomUUID();
        WeaponDamageSnapshot snapshot = snapshot();

        assertTrue(state.arm(
                targetId,
                Level.OVERWORLD,
                100L,
                TemperedQuenchSkillHandler.BLAST_DELAY_TICKS,
                snapshot
        ));
        assertEquals(
                TemperedQuenchExecutionState.TickAction.CONTINUE,
                state.prepareTick(119L, Level.OVERWORLD).action()
        );

        TemperedQuenchExecutionState.TickPlan firing =
                state.prepareTick(120L, Level.OVERWORLD);
        assertEquals(
                TemperedQuenchExecutionState.TickAction.FIRE_AND_CONTINUE,
                firing.action()
        );
        assertNotNull(firing.blast());
        assertEquals(targetId, firing.blast().targetId());
        assertSame(snapshot, firing.blast().weaponSnapshot());
        assertEquals(
                TemperedQuenchExecutionState.TickAction.COMPLETE,
                state.prepareTick(121L, Level.OVERWORLD).action()
        );
    }

    @Test
    void missingHitCancellationAndDimensionChangeCannotCreateABlast() {
        TemperedQuenchExecutionState missingHit =
                new TemperedQuenchExecutionState(Level.OVERWORLD);
        assertEquals(
                TemperedQuenchExecutionState.TickAction.COMPLETE,
                missingHit.prepareTick(101L, Level.OVERWORLD).action()
        );

        TemperedQuenchExecutionState changedDimension =
                new TemperedQuenchExecutionState(Level.OVERWORLD);
        assertFalse(changedDimension.arm(
                UUID.randomUUID(),
                Level.NETHER,
                100L,
                20,
                snapshot()
        ));
        assertEquals(
                TemperedQuenchExecutionState.TickAction.CANCEL,
                changedDimension.prepareTick(101L, Level.NETHER).action()
        );

        TemperedQuenchExecutionState cancelled =
                new TemperedQuenchExecutionState(Level.OVERWORLD);
        assertTrue(cancelled.arm(
                UUID.randomUUID(),
                Level.OVERWORLD,
                100L,
                20,
                snapshot()
        ));
        cancelled.cancel();
        assertEquals(
                TemperedQuenchExecutionState.TickAction.CANCEL,
                cancelled.prepareTick(120L, Level.OVERWORLD).action()
        );

        TemperedQuenchExecutionState missingSnapshot =
                new TemperedQuenchExecutionState(Level.OVERWORLD);
        assertFalse(missingSnapshot.arm(
                UUID.randomUUID(),
                Level.OVERWORLD,
                100L,
                20,
                null
        ));
    }

    @Test
    void aRepeatedQualifyingHitReplacesThePendingTargetLikeTheOldMap() {
        TemperedQuenchExecutionState state =
                new TemperedQuenchExecutionState(Level.OVERWORLD);
        UUID firstTarget = UUID.randomUUID();
        UUID replacementTarget = UUID.randomUUID();
        assertTrue(state.arm(
                firstTarget,
                Level.OVERWORLD,
                100L,
                20,
                snapshot()
        ));
        assertTrue(state.arm(
                replacementTarget,
                Level.OVERWORLD,
                101L,
                20,
                snapshot()
        ));

        assertEquals(
                TemperedQuenchExecutionState.TickAction.CONTINUE,
                state.prepareTick(120L, Level.OVERWORLD).action()
        );
        TemperedQuenchExecutionState.TickPlan firing =
                state.prepareTick(121L, Level.OVERWORLD);
        assertEquals(
                TemperedQuenchExecutionState.TickAction.FIRE_AND_CONTINUE,
                firing.action()
        );
        assertEquals(replacementTarget, firing.blast().targetId());
    }

    @Test
    void blastContextKeepsTheAuthoredMinorFortyFivePercentDamage() {
        SkillContext blast = TemperedQuenchExecutionState
                .createBlastContext();

        assertEquals("tempered_quench_blast", blast.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, blast.getTier());
        assertEquals(0.45F, blast.getDamageMultiplier());
        assertFalse(blast.isIgnoreDefense());
        assertFalse(blast.isGuaranteedCrit());
        assertFalse(TemperedQuenchExecutionState.shouldTrigger(119L, 120L));
        assertTrue(TemperedQuenchExecutionState.shouldTrigger(120L, 120L));
    }

    @Test
    void initialHitArmsOnlyTheExactRuntimeExecution() throws IOException {
        String handler = source(
                "combat/skill/handler/TemperedQuenchSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/TemperedQuenchExecutionState.java"
        );
        String combat = source("combat/WeaponCombatEvents.java");
        String applied = source(
                "combat/BuiltinSkillAppliedHitRules.java"
        );
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );

        int initialize = handler.indexOf(
                "instance.initializeExecutionState("
        );
        int initialDamage = handler.indexOf(
                "WeaponSkillDamage.apply(",
                initialize
        );
        assertTrue(initialize >= 0 && initialDamage > initialize);
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.TEMPERED_QUENCH"
        ));
        assertTrue(handler.contains("TemperedQuenchExecutionState.class"));
        assertFalse(handler.contains("TemperedQuenchTracker"));
        assertTrue(applied.contains(
                "TemperedQuenchSkillHandler.armBlast("
        ));
        assertTrue(applied.contains(
                "hit.weaponSnapshot().orElseThrow()"
        ));
        String compactCombat = combat.replaceAll("\\s+", " ");
        int smite = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyHolySmite(hit)"
        );
        int arm = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.armTemperedQuench(hit)"
        );
        int lifesteal = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)"
        );
        assertTrue(compactCombat.contains(
                "WeaponAppliedHitCoordinator.apply( "
                        + "ResolvedWeaponHit.from(event, player, meta, nowTick)"
                        + " )"
        ));
        assertTrue(smite >= 0 && arm > smite);
        assertTrue(lifesteal > arm);
        assertFalse(combat.contains("TemperedQuenchTracker"));
        assertFalse(applied.contains("TemperedQuenchTracker"));
        assertTrue(state.contains("implements SkillInstance.ExecutionState"));
        assertTrue(state.contains(
                "WeaponDamageSnapshot weaponSnapshot"
        ));
        assertFalse(state.contains("static final Map"));
    }

    @Test
    void blastPreservesPresentationBeforeSnapshotBoundDamage()
            throws IOException {
        String state = source(
                "combat/skill/handler/TemperedQuenchExecutionState.java"
        );
        String explode = method(state, "private static void explode(");

        int anvil = explode.indexOf("SoundEvents.ANVIL_LAND");
        int firecharge = explode.indexOf("SoundEvents.FIRECHARGE_USE");
        int strong = explode.indexOf("SoundEvents.PLAYER_ATTACK_STRONG");
        int flame = explode.indexOf("ParticleTypes.FLAME");
        int lava = explode.indexOf("ParticleTypes.LAVA");
        int smoke = explode.indexOf("ParticleTypes.SMOKE");
        int damage = explode.indexOf("WeaponSkillDamage.apply(");

        assertTrue(anvil >= 0);
        assertTrue(firecharge > anvil);
        assertTrue(strong > firecharge);
        assertTrue(flame > strong);
        assertTrue(lava > flame);
        assertTrue(smoke > lava);
        assertTrue(damage > smoke);
        assertTrue(explode.contains("weaponSnapshot,"));
        assertTrue(explode.contains(".BYPASS_FOR_AUTHORED_SEQUENCE"));
        assertFalse(explode.contains("target.invulnerableTime = 0"));
        assertFalse(explode.contains("target.hurtTime = 0"));
        assertFalse(explode.contains("target.addEffect("));
    }

    private static WeaponDamageSnapshot snapshot() {
        return WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft",
                        "tempered_broadsword"
                ),
                ItemStack.EMPTY
        );
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static String source(String relativePath) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativePath);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
