package com.stardew.craft.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientFestivalAvailabilityTest {
    @AfterEach
    void clearSnapshot() {
        ClientFestivalAvailability.clear();
    }

    @Test
    void allowsAllBeforeSyncThenUsesServerSnapshot() {
        assertTrue(ClientFestivalAvailability.allows("AppleDay"));

        ClientFestivalAvailability.replace(Set.of("FlowerDance"));

        assertTrue(ClientFestivalAvailability.allows("FlowerDance"));
        assertFalse(ClientFestivalAvailability.allows("AppleDay"));
    }

    @Test
    void replacementIsDefensiveAndClearRestoresUnsyncedFallback() {
        Set<String> source = new HashSet<>(Set.of("FlowerDance"));
        ClientFestivalAvailability.replace(source);
        source.clear();

        assertTrue(ClientFestivalAvailability.allows("FlowerDance"));
        ClientFestivalAvailability.replace(Set.of());
        assertFalse(ClientFestivalAvailability.allows("FlowerDance"));

        ClientFestivalAvailability.clear();
        assertTrue(ClientFestivalAvailability.allows("FlowerDance"));
    }

    @Test
    void nullReplacementIsASyncedEmptySnapshot() {
        ClientFestivalAvailability.replace(null);
        assertFalse(ClientFestivalAvailability.allows("FlowerDance"));
    }
}
