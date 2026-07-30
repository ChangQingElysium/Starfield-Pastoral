package com.stardew.craft.combat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamagePipelineTest {
    @Test
    void evaluatesEveryAdjustmentInDeclaredOrder() {
        DamageRequest request = DamageRequest.builder("test_weapon")
                .skillId("test_skill")
                .baseDamage(10.0f, 10.0f)
                .addBaseAdjustment(DamageAdjustment.add("equipment", 2.0f))
                .critical(1.0f, 2.0f, true)
                .addPreDefenseAdjustment(DamageAdjustment.multiply("skill", 1.5f))
                .variance(1.0f, 1.0f)
                .defense(5.0f, false)
                .minimumFinalDamage(1.0f)
                .addPostDefenseAdjustment(DamageAdjustment.multiply("slayer", 2.0f))
                .addPostDefenseAdjustment(DamageAdjustment.add("flat_finish", 1.0f))
                .accuracy(0.25f, 20.0f)
                .inStardewDimension(true)
                .build();

        DamageOutcome outcome = DamagePipeline.evaluate(
                request,
                sequence(0.5f, 0.5f, 0.5f, 0.99f)
        );

        assertEquals(12.0f, outcome.getBaseDamage());
        assertTrue(outcome.isCrit());
        assertEquals(5.0f, outcome.getDefenseReduction());
        assertEquals(63.0f, outcome.getFinalDamage());
        assertEquals(11, outcome.getStages().size());
        assertEquals(
                List.of(
                        DamageOutcome.Phase.BASE_ROLL,
                        DamageOutcome.Phase.BASE_FLAT,
                        DamageOutcome.Phase.CRITICAL,
                        DamageOutcome.Phase.PRE_DEFENSE,
                        DamageOutcome.Phase.VARIANCE,
                        DamageOutcome.Phase.DEFENSE,
                        DamageOutcome.Phase.MINIMUM,
                        DamageOutcome.Phase.POST_DEFENSE,
                        DamageOutcome.Phase.POST_DEFENSE,
                        DamageOutcome.Phase.FINALIZE,
                        DamageOutcome.Phase.DODGE
                ),
                outcome.getStages().stream().map(DamageOutcome.Stage::phase).toList()
        );
        assertTrue(outcome.toExplainLines().stream().anyMatch(line -> line.contains("slayer")));
    }

    @Test
    void dodgeIsFinalAndCannotBeOverwrittenBySweepReplacement() {
        DamageRequest request = DamageRequest.builder("test_sword")
                .baseDamage(20.0f, 20.0f)
                .critical(0.0f, 3.0f, false)
                .variance(1.0f, 1.0f)
                .defense(0.0f, false)
                .addPostDefenseAdjustment(
                        DamageAdjustment.replaceWithBaseMultiplier("sword_sweep", 0.5f)
                )
                .accuracy(1.0f, 0.0f)
                .build();

        DamageOutcome outcome = DamagePipeline.evaluate(
                request,
                sequence(0.5f, 0.9f, 0.5f, 0.0f)
        );

        assertTrue(outcome.isDodged());
        assertEquals(0.0f, outcome.getFinalDamage());
        DamageOutcome.Stage dodgeStage = outcome.getStages().get(outcome.getStages().size() - 1);
        assertEquals(DamageOutcome.Phase.DODGE, dodgeStage.phase());
        assertEquals(10.0f, dodgeStage.before());
        assertEquals(0.0f, dodgeStage.after());
    }

    @Test
    void requestCopiesAdjustmentListsAndRejectsInvalidRanges() {
        DamageRequest request = DamageRequest.builder("test")
                .baseDamage(1.0f, 2.0f)
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> request.baseAdjustments().add(DamageAdjustment.add("invalid", 1.0f))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DamageRequest.builder("test").baseDamage(2.0f, 1.0f).build()
        );
    }

    @Test
    void incomingEnvironmentDamageUsesTheSameOrderedPipelineWithAnExplicitBoundary() {
        DamageRequest request = DamageRequest.builder("incoming:fall")
                .sourceKind(DamageRequest.SourceKind.ENVIRONMENT)
                .skillId("incoming")
                .baseDamage(4.0f, 4.0f)
                .critical(0.0f, 1.0f, false)
                .addPreDefenseAdjustment(DamageAdjustment.multiply("shelter", 0.75f))
                .addPreDefenseAdjustment(DamageAdjustment.multiply("book_bomb_resistance", 0.80f))
                .variance(1.0f, 1.0f)
                .defense(1.0f, false)
                .defenseRule(DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE)
                .minimumFinalDamage(1.0f)
                .addPostDefenseAdjustment(DamageAdjustment.multiply("declared_post_defense", 0.5f))
                .accuracy(0.0f, 0.0f)
                .inStardewDimension(true)
                .build();

        DamageOutcome outcome = DamagePipeline.evaluate(
                request,
                sequence(0.5f, 0.9f, 0.5f, 0.5f, 0.5f)
        );

        assertEquals(DamageRequest.SourceKind.ENVIRONMENT, outcome.getSourceKind());
        assertEquals(0.7f, outcome.getFinalDamage(), 0.0001f);
        assertEquals(
                List.of("shelter", "book_bomb_resistance"),
                outcome.getStages().stream()
                        .filter(stage -> stage.phase() == DamageOutcome.Phase.PRE_DEFENSE)
                        .map(DamageOutcome.Stage::id)
                        .toList()
        );
    }

    @Test
    void weaponPrecisionUsesStardewsZeroToTenScale() {
        DamageRequest request = DamageRequest.builder("precise_weapon")
                .baseDamage(10.0f, 10.0f)
                .critical(0.0f, 3.0f, false)
                .variance(1.0f, 1.0f)
                .defense(0.0f, false)
                .accuracy(0.50f, 5.0f)
                .build();

        DamageOutcome hit = DamagePipeline.evaluate(
                request,
                sequence(0.0f, 0.9f, 0.0f, 0.30f)
        );
        DamageOutcome dodge = DamagePipeline.evaluate(
                request,
                sequence(0.0f, 0.9f, 0.0f, 0.20f)
        );

        assertFalse(hit.isDodged());
        assertTrue(dodge.isDodged());
    }

    @Test
    void playerWeaponRollIsIntegerInclusiveAndDimensionDoesNotChangeDamage() {
        DamageRequest stardew = DamageRequest.builder("test_weapon")
                .sourceKind(DamageRequest.SourceKind.PLAYER_WEAPON)
                .baseDamage(2.0f, 5.0f)
                .critical(0.0f, 3.0f, false)
                .variance(1.0f, 1.0f)
                .defense(0.0f, false)
                .accuracy(0.0f, 0.0f)
                .inStardewDimension(true)
                .build();
        DamageRequest overworld = stardew.toBuilder()
                .inStardewDimension(false)
                .build();

        DamageOutcome stardewOutcome = DamagePipeline.evaluate(
                stardew,
                sequence(0.99f, 0.99f, 0.5f, 0.99f)
        );
        DamageOutcome overworldOutcome = DamagePipeline.evaluate(
                overworld,
                sequence(0.99f, 0.99f, 0.5f, 0.99f)
        );

        assertEquals(5.0f, stardewOutcome.getBaseDamage());
        assertEquals(stardewOutcome.getFinalDamage(), overworldOutcome.getFinalDamage());
    }

    @Test
    void stardewProfessionRoundingIsExplicitAndOrderedAfterAttackBonus() {
        DamageRequest request = DamageRequest.builder("profession_test")
                .sourceKind(DamageRequest.SourceKind.PLAYER_WEAPON)
                .baseDamage(5.0f, 5.0f)
                .critical(1.0f, 3.0f, true)
                .addPreDefenseAdjustment(DamageAdjustment.add("temporary_attack", 3.0f))
                .addPreDefenseAdjustment(DamageAdjustment.multiplyCeil("profession_fighter", 1.10f))
                .addPreDefenseAdjustment(DamageAdjustment.multiplyFloor("profession_desperado", 2.0f))
                .variance(1.0f, 1.0f)
                .defense(0.0f, false)
                .accuracy(0.0f, 0.0f)
                .build();

        DamageOutcome outcome = DamagePipeline.evaluate(
                request,
                sequence(0.0f, 0.0f, 0.99f)
        );

        assertEquals(40.0f, outcome.getFinalDamage());
        assertTrue(outcome.toExplainLines().stream()
                .anyMatch(line -> line.contains("profession_fighter") && line.contains("ceiling")));
    }

    @Test
    void stardewEnchantmentMultiplierIsResolvedBeforeMonsterResilience() {
        DamageRequest request = DamageRequest.builder("crusader_test")
                .sourceKind(DamageRequest.SourceKind.PLAYER_WEAPON)
                .baseDamage(10.0f, 10.0f)
                .critical(0.0f, 3.0f, false)
                .addPreDefenseAdjustment(
                        DamageAdjustment.multiplyFloor("enchantment_crusader", 1.5f)
                )
                .variance(1.0f, 1.0f)
                .defense(4.0f, false)
                .accuracy(0.0f, 0.0f)
                .build();

        DamageOutcome outcome = DamagePipeline.evaluate(
                request,
                sequence(0.0f, 0.99f, 0.0f, 0.99f)
        );

        assertEquals(11.0f, outcome.getFinalDamage());
    }

    private static DamageRandomSource sequence(float... values) {
        return new DamageRandomSource() {
            private int index;

            @Override
            public float nextFloat() {
                if (index >= values.length) {
                    throw new AssertionError("Damage pipeline consumed more random values than expected");
                }
                return values[index++];
            }
        };
    }
}
