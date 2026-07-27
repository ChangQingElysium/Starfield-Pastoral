package com.stardew.craft.animal.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalPendingBirthTest {
    @Test
    void pendingBirthRoundTripPreservesNamingTransactionIdentity() {
        AnimalPendingBirth original = new AnimalPendingBirth(
                9L,
                UUID.randomUUID().toString(),
                "barn_1",
                42L,
                "cow",
                73
        );

        AnimalPendingBirth loaded =
                AnimalPendingBirth.load(original.save());

        assertEquals(original, loaded);
    }
}
