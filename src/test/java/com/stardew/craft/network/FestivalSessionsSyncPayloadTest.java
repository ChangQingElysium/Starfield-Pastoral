package com.stardew.craft.network;

import com.stardew.craft.api.v1.festival.StardewFestivalClientSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import com.stardew.craft.network.payload.FestivalSessionsSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FestivalSessionsSyncPayloadTest {
    @Test
    void boundedPrivacySafeSnapshotRoundTrips() {
        StardewFestivalClientSessionSnapshot session =
                new StardewFestivalClientSessionSnapshot(
                        ResourceLocation.fromNamespaceAndPath(
                                "orchard_addon", "apple_day"),
                        "orchard_addon:apple_day",
                        3,
                        2,
                        5,
                        StardewFestivalSessionSnapshot.Phase.OPEN,
                        StardewFestivalSessionSnapshot.MapPhase.APPLIED,
                        7,
                        true);
        FestivalSessionsSyncPayload expected =
                new FestivalSessionsSyncPayload(
                        UUID.fromString(
                                "536fa7e2-0715-482e-a513-58d3be7cfebc"),
                        42L,
                        List.of(session));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FestivalSessionsSyncPayload.STREAM_CODEC.encode(
                    buffer, expected);
            assertEquals(
                    expected,
                    FestivalSessionsSyncPayload.STREAM_CODEC.decode(
                            buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsSnapshotsAboveTheProtocolBound() {
        StardewFestivalClientSessionSnapshot session =
                new StardewFestivalClientSessionSnapshot(
                        ResourceLocation.fromNamespaceAndPath(
                                "orchard_addon", "apple_day"),
                        "apple_day",
                        1,
                        0,
                        1,
                        StardewFestivalSessionSnapshot.Phase.CLOSED,
                        StardewFestivalSessionSnapshot.MapPhase.NONE,
                        0,
                        false);
        ArrayList<StardewFestivalClientSessionSnapshot> sessions =
                new ArrayList<>();
        for (int index = 0;
             index <= FestivalSessionsSyncPayload.MAX_SESSIONS;
             index++) {
            sessions.add(session);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new FestivalSessionsSyncPayload(
                        UUID.randomUUID(), 1L, sessions));
    }
}
