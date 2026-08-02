package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSwordBloodMoonExecutionStateTest {
    @Test
    void activeWindowBurnCadenceAndNaturalFinishKeepOldBoundaries() {
        DarkSwordBloodMoonExecutionState state = stateAt(100L);

        assertTrue(state.isActive(180L, true, Level.OVERWORLD));
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.CONTINUE,
                state.prepareTick(109L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.BURN_AND_CONTINUE,
                state.prepareTick(110L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.CONTINUE,
                state.prepareTick(119L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.BURN_AND_CONTINUE,
                state.prepareTick(120L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.BURN_AND_CONTINUE,
                state.prepareTick(180L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.FINISH,
                state.prepareTick(181L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.COMPLETE,
                state.prepareTick(182L, true, Level.OVERWORLD)
        );
    }

    @Test
    void missedTicksStillApplyOnlyOneBurnPerServerTick() {
        DarkSwordBloodMoonExecutionState state = stateAt(100L);

        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.BURN_AND_CONTINUE,
                state.prepareTick(140L, true, Level.OVERWORLD)
        );
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.BURN_AND_CONTINUE,
                state.prepareTick(141L, true, Level.OVERWORLD)
        );
    }

    @Test
    void lifestealIsRecordedOnlyInsideTheExactAliveDimensionWindow() {
        DarkSwordBloodMoonExecutionState state = stateAt(100L);

        assertTrue(state.recordLifeSteal(
                180L,
                true,
                Level.OVERWORLD,
                4.0F
        ));
        assertEquals(4.0F, state.totalHealed());
        assertFalse(state.recordLifeSteal(
                181L,
                true,
                Level.OVERWORLD,
                3.0F
        ));
        assertFalse(state.recordLifeSteal(
                160L,
                true,
                Level.NETHER,
                3.0F
        ));
        assertFalse(state.recordLifeSteal(
                160L,
                false,
                Level.OVERWORLD,
                3.0F
        ));
        assertEquals(4.0F, state.totalHealed());
    }

    @Test
    void deathDimensionChangeAndCancellationCannotReachTheFinisher() {
        DarkSwordBloodMoonExecutionState dead = stateAt(100L);
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.CANCEL,
                dead.prepareTick(160L, false, Level.OVERWORLD)
        );

        DarkSwordBloodMoonExecutionState changedDimension = stateAt(100L);
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.CANCEL,
                changedDimension.prepareTick(160L, true, Level.NETHER)
        );

        DarkSwordBloodMoonExecutionState cancelled = stateAt(100L);
        cancelled.cancel();
        assertEquals(
                DarkSwordBloodMoonExecutionState.TickAction.COMPLETE,
                cancelled.prepareTick(181L, true, Level.OVERWORLD)
        );
    }

    @Test
    void burnAndFinisherMathPreserveAuthoredHealthUnitsAndMajorContext() {
        assertEquals(1.0F, DarkSwordBloodMoonExecutionState.burnAmount(20.0F));
        assertEquals(1.0F, DarkSwordBloodMoonExecutionState.burnAmount(100.0F));
        assertEquals(2.5F, DarkSwordBloodMoonExecutionState.burnAmount(250.0F));
        assertEquals(8.0F, DarkSwordBloodMoonExecutionState.netBurn(12.0F, 4.0F));
        assertEquals(0.0F, DarkSwordBloodMoonExecutionState.netBurn(4.0F, 12.0F));
        assertEquals(
                0.2F,
                DarkSwordBloodMoonExecutionState.burstDamageMultiplier(
                        8.0F,
                        40.0F
                )
        );
        assertEquals(
                0.1F,
                DarkSwordBloodMoonExecutionState.burstDamageMultiplier(
                        1.0F,
                        40.0F
                )
        );
        assertEquals(
                0.0F,
                DarkSwordBloodMoonExecutionState.burstDamageMultiplier(
                        8.0F,
                        0.0F
                )
        );

        SkillContext burst = DarkSwordBloodMoonExecutionState
                .createBurstContext(0.2F);
        assertEquals("dark_sword_blood_moon_burst", burst.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, burst.getTier());
        assertEquals(0.2F, burst.getDamageMultiplier());
    }

    @Test
    void combatQueriesOnlyTheExactTypedRuntimeExecution()
            throws IOException {
        String handler = source(
                "combat/skill/handler/DarkSwordBloodMoonSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/DarkSwordBloodMoonExecutionState.java"
        );
        String assembly = source("combat/WeaponDamageAssemblyRules.java");
        String applied = source(
                "combat/BuiltinSkillAppliedHitRules.java"
        );
        String coordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );

        assertTrue(handler.contains("instance.initializeExecutionState("));
        assertTrue(handler.contains(
                "new DarkSwordBloodMoonExecutionState("
        ));
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_MOON"
        ));
        assertTrue(handler.contains(
                "DarkSwordBloodMoonExecutionState.class"
        ));
        assertTrue(assembly.contains(
                "DarkSwordBloodMoonSkillHandler.isActive("
        ));
        assertTrue(applied.contains(
                "DarkSwordBloodMoonSkillHandler.getLifestealRatio("
        ));
        assertTrue(applied.contains(
                "DarkSwordBloodMoonSkillHandler.recordLifeSteal("
        ));
        int quench = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.armTemperedQuench(hit)"
        );
        int lifesteal = coordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)"
        );
        int rewards = coordinator.indexOf(
                "CommonWeaponAppliedHitRules.notifyTrinkets(hit)"
        );
        assertTrue(quench >= 0 && lifesteal > quench);
        assertTrue(rewards > lifesteal);
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertFalse(state.contains("static final Map"));
    }

    @Test
    void finisherPreservesTargetEffectAndSnapshotDamageOrder()
            throws IOException {
        String state = source(
                "combat/skill/handler/DarkSwordBloodMoonExecutionState.java"
        );
        String finisher = method(state, "private void finishNaturally(");

        int netBurn = finisher.indexOf("float netBurn = netBurn(");
        int multiplier = finisher.indexOf(
                "float damageMultiplier = burstDamageMultiplier("
        );
        int targets = finisher.indexOf("getTargetsInRadius(");
        int effect = finisher.indexOf(
                "DarkSwordEffects.playBloodMoonBurst("
        );
        int damage = finisher.indexOf("WeaponSkillDamage.apply(");
        int snapshot = finisher.indexOf("weaponSnapshot", damage);
        int bypass = finisher.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                snapshot
        );

        assertTrue(netBurn >= 0);
        assertTrue(multiplier > netBurn);
        assertTrue(targets > multiplier);
        assertTrue(effect > targets);
        assertTrue(damage > effect);
        assertTrue(snapshot > damage);
        assertTrue(bypass > snapshot);
        assertFalse(finisher.contains("target.invulnerableTime = 0"));
        assertFalse(finisher.contains("target.hurtTime = 0"));
    }

    @Test
    void burnRecordsOnlyActualNonlethalHealthSpentBeforeItsEffect()
            throws IOException {
        String state = source(
                "combat/skill/handler/DarkSwordBloodMoonExecutionState.java"
        );
        String burn = method(state, "private void applyBurn(");

        int requested = burn.indexOf("float burn = burnAmount(");
        int spent = burn.indexOf("CombatHealing.spendNonlethal(");
        int accumulated = burn.indexOf("totalBurned += actualBurn");
        int effect = burn.indexOf("DarkSwordEffects.playBloodMoonBurn(");

        assertTrue(requested >= 0);
        assertTrue(spent > requested);
        assertTrue(accumulated > spent);
        assertTrue(effect > accumulated);
    }

    private static DarkSwordBloodMoonExecutionState stateAt(long nowTick) {
        return new DarkSwordBloodMoonExecutionState(
                nowTick,
                DarkSwordBloodMoonSkillHandler.ACTIVE_DURATION_TICKS,
                DarkSwordBloodMoonSkillHandler.BURN_INTERVAL_TICKS,
                Level.OVERWORLD,
                40.0F,
                WeaponDamageSnapshot.capture(
                        ResourceLocation.fromNamespaceAndPath(
                                "stardewcraft",
                                "dark_sword"
                        ),
                        ItemStack.EMPTY
                )
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
        Path relative = Path.of("src", "main", "java", "com", "stardew", "craft")
                .resolve(relativePath);
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate source " + relativePath);
    }
}
