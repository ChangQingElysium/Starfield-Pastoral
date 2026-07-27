package com.stardew.craft.animal.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalBuildingValidationRulesTest {
    @Test
    void sharedRuleAcceptsFactsAtEveryConfiguredThreshold() {
        var requirements =
                new AnimalBuildingValidationRules.Requirements(
                        2, 1, 1, 1, 40,
                        true, true, 2);
        var facts =
                new AnimalBuildingValidationRules.ScanFacts(
                        2, 1, 1, 1, 40,
                        5, 4, 2, true, 2);

        var outcome = AnimalBuildingValidationRules.evaluate(
                "coop", requirements, facts);

        assertTrue(outcome.success());
        assertTrue(outcome.failures().isEmpty());
    }

    @Test
    void sharedRuleReportsShortfallsInStablePlayerFacingOrder() {
        var requirements =
                new AnimalBuildingValidationRules.Requirements(
                        2, 1, 1, 1, 40,
                        true, true, 2);
        var facts =
                new AnimalBuildingValidationRules.ScanFacts(
                        0, 0, 0, 0, 0,
                        0, 0, 0, false, 0);

        var outcome = AnimalBuildingValidationRules.evaluate(
                "barn", requirements, facts);

        assertFalse(outcome.success());
        assertEquals(
                List.of(
                        AnimalBuildingValidationRules.FailureType.NO_INTERIOR,
                        AnimalBuildingValidationRules.FailureType.FEED_TROUGHS,
                        AnimalBuildingValidationRules.FailureType.AUTO_FEED_TROUGHS,
                        AnimalBuildingValidationRules.FailureType.HAY_HOPPERS,
                        AnimalBuildingValidationRules.FailureType.INCUBATORS,
                        AnimalBuildingValidationRules.FailureType.INTERIOR_SIZE,
                        AnimalBuildingValidationRules.FailureType.NOT_ENCLOSED,
                        AnimalBuildingValidationRules.FailureType.DOORS
                ),
                outcome.failures().stream()
                        .map(AnimalBuildingValidationRules.Failure::type)
                        .toList());
    }

    @Test
    void optionalEnclosureAndDoorRulesDoNotLeakBetweenFamilies() {
        var requirements =
                new AnimalBuildingValidationRules.Requirements(
                        0, 0, 0, 0, 1,
                        false, false, 99);
        var facts =
                new AnimalBuildingValidationRules.ScanFacts(
                        0, 0, 0, 0, 1,
                        1, 1, 1, false, 0);

        var outcome = AnimalBuildingValidationRules.evaluate(
                "coop", requirements, facts);

        assertTrue(outcome.success());
    }

    @Test
    void onlyDoorWhoseOutsideAirReachesScanBoundaryCountsAsExterior() {
        BlockPos exteriorDoor = new BlockPos(0, 0, 0);
        BlockPos partitionDoor = new BlockPos(2, 0, 0);
        Set<Long> interior = packed(
                new BlockPos(1, 0, 0),
                new BlockPos(1, 1, 0)
        );
        Set<Long> air = packed(
                new BlockPos(-2, 0, 0),
                new BlockPos(-1, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(1, 1, 0),
                new BlockPos(3, 0, 0)
        );

        Set<Long> result =
                AnimalBuildingValidationRules.exteriorDoorCells(
                        interior,
                        packed(exteriorDoor, partitionDoor),
                        new AnimalBuildingValidationRules.Bounds(
                                -2, 4, -1, 1, -1, 1),
                        new AnimalBuildingValidationRules.DoorEnvironment() {
                            @Override
                            public boolean isAir(BlockPos pos) {
                                return air.contains(pos.asLong());
                            }

                            @Override
                            public List<Direction> passageDirections(
                                    BlockPos pos
                            ) {
                                return List.of(
                                        Direction.EAST,
                                        Direction.WEST
                                );
                            }
                        }
                );

        assertEquals(Set.of(exteriorDoor.asLong()), result);
    }

    private static Set<Long> packed(BlockPos... positions) {
        LinkedHashSet<Long> packed = new LinkedHashSet<>();
        for (BlockPos position : positions) {
            packed.add(position.asLong());
        }
        return packed;
    }
}
