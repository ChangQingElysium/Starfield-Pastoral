package com.stardew.craft.network.payload;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerVitalsSyncPayloadTest {
    @Test
    void roundTripsCombatVitals() {
        PlayerVitalsSyncPayload expected = new PlayerVitalsSyncPayload(
                73,
                140,
                182.5F,
                304,
                true
        );
        ByteBuf buffer = Unpooled.buffer();
        try {
            PlayerVitalsSyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, PlayerVitalsSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
