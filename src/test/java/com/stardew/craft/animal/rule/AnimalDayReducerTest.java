package com.stardew.craft.animal.rule;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalDayReducerTest {
    private static final ResourceLocation FIRST =
            ResourceLocation.fromNamespaceAndPath("test", "first");
    private static final ResourceLocation SECOND =
            ResourceLocation.fromNamespaceAndPath("test", "second");
    private static final ResourceLocation DELUXE =
            ResourceLocation.fromNamespaceAndPath("test", "deluxe");

    @Test
    void openDoorReturnOnlyHalvesHappinessAfterSixPmAndReturnsBeforeCooldown() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.State original = state(
                chicken.daysToMature(), 500, 200, 255, 4, false, false);

        AnimalDayReducer.BeginResult atSix = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        original,
                        AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_OPEN,
                        1800
                )
        );
        assertTrue(atSix.returnedHomeEarly());
        assertEquals(200, atSix.state().happiness());
        assertEquals(4, atSix.state().daysSinceLastProduce());
        assertFalse(atSix.state().wasAutoPetToday());

        AnimalDayReducer.BeginResult afterSix = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        original,
                        AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_OPEN,
                        1810
                )
        );
        assertEquals(100, afterSix.state().happiness());
        assertEquals(4, afterSix.state().daysSinceLastProduce());
    }

    @Test
    void openDoorReturnContinuesThroughAnimalHouseDailyPass() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.State original = state(
                chicken.daysToMature(), 500, 200, 255, 4, true, false);

        AnimalDayReducer.BeginResult farmPass = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        original,
                        AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_OPEN,
                        1810
                )
        );
        AnimalDayReducer.BeginResult housePass =
                AnimalDayReducer.continueAfterOpenDoorReturn(
                        chicken,
                        farmPass,
                        1810
                );

        assertTrue(farmPass.returnedHomeEarly());
        assertFalse(housePass.returnedHomeEarly());
        assertEquals(100, housePass.state().happiness());
        assertEquals(5, housePass.state().daysSinceLastProduce());

        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        chicken,
                        housePass,
                        true,
                        false,
                        0.0D,
                        List.of(),
                        List.of()
                ),
                new ScriptedRandom(List.of(0.5D, 0.5D, 0.99D), List.of())
        );
        assertEquals(1, finish.state().daysOwned());
        assertFalse(finish.state().wasPetToday());
        assertTrue(finish.state().wasFedToday());
    }

    @Test
    void closedDoorOutsideAppliesLeftOutMoodAndContinuesAllOtherRules() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        state(chicken.daysToMature(), 500, 200, 0, 0, false, false),
                        AnimalDayReducer.HomeSituation.OUTSIDE_DOOR_CLOSED,
                        600
                )
        );
        assertFalse(begin.returnedHomeEarly());
        assertTrue(begin.wasLeftOutLastNight());
        assertEquals(100, begin.state().happiness());
        assertEquals(6, begin.state().moodMessage());

        ScriptedRandom random = new ScriptedRandom(
                List.of(0.5D, 0.5D),
                List.of()
        );
        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        chicken,
                        begin,
                        false,
                        false,
                        0.0D,
                        List.of(FIRST),
                        List.of()
                ),
                random
        );

        assertEquals(1, finish.state().daysOwned());
        assertEquals(472, finish.state().friendship());
        assertEquals(0, finish.state().happiness());
        assertEquals(6, finish.state().moodMessage());
        assertFalse(finish.state().wasPetToday());
        assertFalse(finish.state().wasAutoPetToday());
        assertNull(finish.production());
        random.assertConsumed();
    }

    @Test
    void priorAutoPetFlagPreventsPenaltyThenObjectPassEstablishesNextDayFlag() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        state(chicken.daysToMature(), 100, 100, 0, 0, false, true),
                        AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,
                        600
                )
        );
        assertEquals(100, begin.state().friendship());
        assertEquals(100, begin.state().happiness());
        assertTrue(begin.state().wasAutoPetToday());

        ScriptedRandom random = new ScriptedRandom(
                List.of(0.5D, 0.5D),
                List.of()
        );
        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        chicken,
                        begin,
                        false,
                        false,
                        0.0D,
                        List.of(FIRST),
                        List.of()
                ),
                random
        );

        assertEquals(80, finish.state().friendship());
        assertEquals(0, finish.state().happiness());
        assertFalse(finish.state().wasAutoPetToday());

        AnimalDayReducer.State afterObjects = AnimalDayReducer.applyAutoPetter(
                chicken,
                finish.state(),
                true,
                true
        );
        assertEquals(88, afterObjects.friendship());
        assertEquals(37, afterObjects.happiness());
        assertTrue(afterObjects.wasAutoPetToday());
        random.assertConsumed();
    }

    @Test
    void autoPetterNeverTouchesAnimalsOutsideTheAnimalHouse() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.State original =
                state(chicken.daysToMature(), 100, 100, 255, 0, false, false);

        AnimalDayReducer.State afterObjects = AnimalDayReducer.applyAutoPetter(
                chicken,
                original,
                false,
                true
        );

        assertEquals(original, afterObjects);
    }

    @Test
    void dayStartedAutoFeedsOnlyAnimalsWhoseSourceDataConsumesNoGrass() {
        AnimalDayReducer.State empty =
                state(3, 100, 100, 0, 0, false, false)
                        .withWasFedToday(false);

        AnimalDayReducer.State noFoodRequired =
                AnimalDayReducer.applyDayStarted(empty, 0);
        assertEquals(255, noFoodRequired.fullness());
        assertTrue(noFoodRequired.wasFedToday());

        AnimalDayReducer.State ordinary =
                AnimalDayReducer.applyDayStarted(empty, 2);
        assertEquals(empty, ordinary);
    }

    @Test
    void consumedHayIsAppliedBeforeGrowthAndProduction() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        state(chicken.daysToMature(), 500, 100, 0, 0, true, false),
                        AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,
                        600
                )
        );
        ScriptedRandom random = new ScriptedRandom(
                List.of(0.1D, 0.1D, 0.99D),
                List.of(0)
        );

        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        chicken,
                        begin,
                        true,
                        false,
                        0.0D,
                        List.of(FIRST),
                        List.of()
                ),
                random
        );

        assertEquals(chicken.daysToMature() + 1, finish.state().ageDays());
        assertEquals(114, finish.state().happiness());
        assertTrue(finish.state().wasFedToday());
        assertNotNull(finish.production());
        random.assertConsumed();
    }

    @Test
    void produceOnMatureStoresProduceAndCooldownBeforeRegularProductionRoll() {
        FarmAnimalDefinition sheep = FarmAnimalDefinitions.require("sheep");
        assertTrue(sheep.produceOnMature());
        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        sheep,
                        state(sheep.daysToMature() - 1, 500, 0, 255, 0, true, false),
                        AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,
                        600
                )
        );
        ScriptedRandom random = new ScriptedRandom(
                List.of(0.2D, 0.9D),
                List.of(0)
        );

        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        sheep,
                        begin,
                        false,
                        false,
                        0.0D,
                        List.of(FIRST),
                        List.of()
                ),
                random
        );

        assertTrue(finish.maturedToday());
        assertEquals(sheep.daysToMature(), finish.state().ageDays());
        assertEquals(99, finish.state().daysSinceLastProduce());
        assertEquals(FIRST.toString(), finish.state().currentProduceId());
        assertNull(finish.production());
        random.assertConsumed();
    }

    @Test
    void eligibleProduceIsChosenUniformlyInsteadOfAlwaysTakingFirstEntry() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        state(chicken.daysToMature(), 500, 0, 255, 0, true, false),
                        AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,
                        600
                )
        );
        ScriptedRandom random = new ScriptedRandom(
                List.of(0.1D, 0.1D, 0.99D),
                List.of(1)
        );

        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        chicken,
                        begin,
                        false,
                        false,
                        0.0D,
                        List.of(FIRST, SECOND),
                        List.of()
                ),
                random
        );

        assertNotNull(finish.production());
        assertEquals(SECOND, finish.production().itemId());
        assertEquals(AnimalDayReducer.Delivery.DROP_OVERNIGHT,
                finish.production().delivery());
        random.assertConsumed();
    }

    @Test
    void deluxeSelectionCooldownAndQualityFollowSourceRandomOrder() {
        FarmAnimalDefinition chicken = FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.BeginResult begin = AnimalDayReducer.begin(
                new AnimalDayReducer.BeginInput(
                        chicken,
                        state(chicken.daysToMature(), 1000, 255, 255, 0, true, false),
                        AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,
                        600
                )
        );
        ScriptedRandom random = new ScriptedRandom(
                List.of(0.1D, 0.1D, 0.1D, 0.1D, 0.2D),
                List.of(0, 0)
        );

        AnimalDayReducer.FinishResult finish = AnimalDayReducer.finish(
                finishInput(
                        chicken,
                        begin,
                        false,
                        true,
                        0.0D,
                        List.of(FIRST),
                        List.of(DELUXE)
                ),
                random
        );

        assertNotNull(finish.production());
        assertEquals(DELUXE, finish.production().itemId());
        assertEquals(QualityHelper.IRIDIUM, finish.production().quality());
        assertEquals(0, finish.state().daysSinceLastProduce());
        assertEquals(1, finish.state().moodMessage());
        assertEquals(0, finish.state().fullness());
        assertTrue(finish.state().wasFedToday());
        random.assertConsumed();
    }

    @Test
    void produceFriendshipThresholdIsCheckedAfterMissedPetPenalty() {
        FarmAnimalDefinition chicken =
                FarmAnimalDefinitions.require("white_chicken");
        AnimalDayReducer.BeginResult begin =
                AnimalDayReducer.begin(
                        new AnimalDayReducer.BeginInput(
                                chicken,
                                state(
                                        chicken.daysToMature(),
                                        200,
                                        255,
                                        255,
                                        1,
                                        false,
                                        false),
                                AnimalDayReducer.HomeSituation
                                        .INSIDE_DOOR_OPEN,
                                600));
        ScriptedRandom random = new ScriptedRandom(
                List.of(
                        0.1D, 0.1D, 0.99D,
                        0.99D, 0.99D),
                List.of());

        AnimalDayReducer.FinishResult finish =
                AnimalDayReducer.finish(
                        new AnimalDayReducer.FinishInput(
                                chicken,
                                begin.state(),
                                false,
                                true,
                                false,
                                false,
                                false,
                                false,
                                false,
                                0.0D,
                                null,
                                List.of(
                                        new AnimalDayReducer
                                                .ProduceCandidate(
                                                FIRST, 200)),
                                List.of()),
                        random);

        assertEquals(191, finish.state().friendship());
        assertNull(finish.production());
        random.assertConsumed();
    }

    @Test
    void optionalTraceCapturesReducerOrderWithoutChangingResults() {
        FarmAnimalDefinition animal =
                FarmAnimalDefinitions.require("sheep");
        AnimalDayReducer.BeginInput beginInput =
                new AnimalDayReducer.BeginInput(
                        animal,
                        state(
                                animal.daysToMature(),
                                500,
                                220,
                                255,
                                0,
                                true,
                                false),
                        AnimalDayReducer.HomeSituation.INSIDE_DOOR_OPEN,
                        600);

        AnimalDayReducer.BeginResult ordinaryBegin =
                AnimalDayReducer.begin(beginInput);
        AnimalDayReducer.TracedBeginResult tracedBegin =
                AnimalDayReducer.traceBegin(beginInput);

        assertEquals(ordinaryBegin, tracedBegin.result());
        assertEquals(
                List.of(
                        AnimalDayReducer.TraceStage.BEGIN_INPUT,
                        AnimalDayReducer.TraceStage.BEGIN_HOME_STATE,
                        AnimalDayReducer.TraceStage.BEGIN_PRODUCE_COOLDOWN),
                tracedBegin.trace().entries().stream()
                        .map(AnimalDayReducer.TraceEntry::stage)
                        .toList());

        AnimalDayReducer.FinishInput finishInput =
                finishInput(
                        animal,
                        ordinaryBegin,
                        false,
                        false,
                        0.0D,
                        List.of(FIRST),
                        List.of());
        AnimalDayReducer.FinishResult ordinaryFinish =
                AnimalDayReducer.finish(
                        finishInput,
                        new ScriptedRandom(List.of(), List.of()));
        AnimalDayReducer.TracedFinishResult tracedFinish =
                AnimalDayReducer.traceFinish(
                        finishInput,
                        new ScriptedRandom(List.of(), List.of()));

        assertEquals(ordinaryFinish, tracedFinish.result());
        assertEquals(
                List.of(
                        AnimalDayReducer.TraceStage.FINISH_INPUT,
                        AnimalDayReducer.TraceStage.FINISH_MISSED_PET,
                        AnimalDayReducer.TraceStage.FINISH_DAILY_FLAGS,
                        AnimalDayReducer.TraceStage.FINISH_FEEDING,
                        AnimalDayReducer.TraceStage.FINISH_GROWTH,
                        AnimalDayReducer.TraceStage.FINISH_HUNGER,
                        AnimalDayReducer.TraceStage.FINISH_PRODUCTION,
                        AnimalDayReducer.TraceStage.FINISH_DAY_END),
                tracedFinish.trace().entries().stream()
                        .map(AnimalDayReducer.TraceEntry::stage)
                        .toList());
    }

    private static AnimalDayReducer.FinishInput finishInput(
            FarmAnimalDefinition definition,
            AnimalDayReducer.BeginResult begin,
            boolean hayConsumed,
            boolean qualityProfession,
            double luck,
            List<ResourceLocation> normal,
            List<ResourceLocation> deluxe
    ) {
        return new AnimalDayReducer.FinishInput(
                definition,
                begin.state(),
                begin.wasLeftOutLastNight(),
                true,
                hayConsumed,
                false,
                false,
                false,
                qualityProfession,
                luck,
                null,
                normal.stream()
                        .map(item -> new AnimalDayReducer
                                .ProduceCandidate(item, 0))
                        .toList(),
                deluxe.stream()
                        .map(item -> new AnimalDayReducer
                                .ProduceCandidate(item, 0))
                        .toList()
        );
    }

    private static AnimalDayReducer.State state(
            int age,
            int friendship,
            int happiness,
            int fullness,
            int cooldown,
            boolean pet,
            boolean autoPet
    ) {
        return new AnimalDayReducer.State(
                age,
                0,
                friendship,
                happiness,
                fullness,
                cooldown,
                pet,
                autoPet,
                fullness >= 200,
                0,
                "",
                QualityHelper.NORMAL
        );
    }

    private static final class ScriptedRandom implements AnimalDayReducer.RandomPort {
        private final Queue<Double> doubles;
        private final Queue<Integer> integers;

        private ScriptedRandom(List<Double> doubles, List<Integer> integers) {
            this.doubles = new ArrayDeque<>(doubles);
            this.integers = new ArrayDeque<>(integers);
        }

        @Override
        public double nextDouble() {
            if (doubles.isEmpty()) {
                throw new AssertionError("Unexpected nextDouble call");
            }
            return doubles.remove();
        }

        @Override
        public int nextInt(int bound) {
            if (integers.isEmpty()) {
                throw new AssertionError("Unexpected nextInt call");
            }
            int value = integers.remove();
            if (value < 0 || value >= bound) {
                throw new AssertionError("Scripted value " + value + " outside bound " + bound);
            }
            return value;
        }

        private void assertConsumed() {
            assertTrue(doubles.isEmpty(), "Unused scripted doubles: " + doubles);
            assertTrue(integers.isEmpty(), "Unused scripted integers: " + integers);
        }
    }
}
