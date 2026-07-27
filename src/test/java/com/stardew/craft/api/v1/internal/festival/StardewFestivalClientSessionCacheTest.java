package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.api.v1.festival.StardewFestivalClientSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewFestivalClientSessionCacheTest {
    @BeforeEach
    void clear() {
        StardewFestivalClientSessionCache.clear();
    }

    @Test
    void rejectsDuplicateOrOlderRevisionOnlyWithinTheSameServerEpoch() {
        UUID firstServer = UUID.randomUUID();
        UUID restartedServer = UUID.randomUUID();
        StardewFestivalClientSessionSnapshot oldSession =
                session("old_server");
        StardewFestivalClientSessionSnapshot duplicateSession =
                session("duplicate_revision");
        StardewFestivalClientSessionSnapshot staleSession =
                session("stale_packet");
        StardewFestivalClientSessionSnapshot restartedSession =
                session("restarted_server");

        StardewFestivalClientSessionCache.replace(
                firstServer, 42L, List.of(oldSession));
        StardewFestivalClientSessionCache.replace(
                firstServer, 42L, List.of(duplicateSession));
        StardewFestivalClientSessionCache.replace(
                firstServer, 41L, List.of(staleSession));
        assertEquals(
                List.of(oldSession),
                StardewFestivalClientSessionCache.all());

        StardewFestivalClientSessionCache.replace(
                restartedServer, 0L, List.of(restartedSession));
        assertEquals(0L, StardewFestivalClientSessionCache.revision());
        assertEquals(
                List.of(restartedSession),
                StardewFestivalClientSessionCache.all());
    }

    @Test
    void clearPreventsSessionLeakageAcrossDisconnect() {
        StardewFestivalClientSessionCache.replace(
                UUID.randomUUID(), 7L, List.of(session("old_server")));

        StardewFestivalClientSessionCache.clear();

        assertEquals(0L, StardewFestivalClientSessionCache.revision());
        assertEquals(List.of(), StardewFestivalClientSessionCache.all());
    }

    private static StardewFestivalClientSessionSnapshot session(
            String path
    ) {
        return new StardewFestivalClientSessionSnapshot(
                ResourceLocation.fromNamespaceAndPath("test", path),
                "test:" + path,
                1,
                0,
                1,
                StardewFestivalSessionSnapshot.Phase.OPEN,
                StardewFestivalSessionSnapshot.MapPhase.NONE,
                0,
                false);
    }
}
