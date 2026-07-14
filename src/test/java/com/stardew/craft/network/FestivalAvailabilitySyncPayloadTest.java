package com.stardew.craft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FestivalAvailabilitySyncPayloadTest {
    @Test
    void snapshotRoundTripsInServerOrder() {
        FestivalAvailabilitySyncPayload expected = new FestivalAvailabilitySyncPayload(
                List.of("stardewcraft:egg_festival", "example:addon_festival"));
        ByteBuf buffer = Unpooled.buffer();
        try {
            FestivalAvailabilitySyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, FestivalAvailabilitySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void snapshotOwnsAnImmutableCopyAndNullMeansEmpty() {
        List<String> source = new ArrayList<>(List.of("stardewcraft:egg_festival"));
        FestivalAvailabilitySyncPayload payload = new FestivalAvailabilitySyncPayload(source);
        source.clear();

        assertEquals(List.of("stardewcraft:egg_festival"), payload.festivalIds());
        assertThrows(UnsupportedOperationException.class, () -> payload.festivalIds().clear());
        assertTrue(new FestivalAvailabilitySyncPayload(null).festivalIds().isEmpty());
    }
}
