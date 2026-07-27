package com.stardew.craft.animal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalEntityProjectionPolicyTest {
    @Test
    void pausedBuildingKeepsProjectionButMissingAuthorityDoesNot() {
        assertEquals(
                AnimalEntityProjectionPolicy.JoinDecision.ACCEPT_PAUSED,
                AnimalEntityProjectionPolicy.decideJoin(
                        true, true, false, true));
        assertEquals(
                AnimalEntityProjectionPolicy.JoinDecision.ACCEPT_ACTIVE,
                AnimalEntityProjectionPolicy.decideJoin(
                        true, true, true, true));
        assertEquals(
                AnimalEntityProjectionPolicy.JoinDecision
                        .DISCARD_NO_RECORD,
                AnimalEntityProjectionPolicy.decideJoin(
                        false, true, true, true));
        assertEquals(
                AnimalEntityProjectionPolicy.JoinDecision
                        .DISCARD_NO_BUILDING,
                AnimalEntityProjectionPolicy.decideJoin(
                        true, false, false, false));
        assertEquals(
                AnimalEntityProjectionPolicy.JoinDecision
                        .DISCARD_WRONG_DIMENSION,
                AnimalEntityProjectionPolicy.decideJoin(
                        true, true, true, false));
    }
}
