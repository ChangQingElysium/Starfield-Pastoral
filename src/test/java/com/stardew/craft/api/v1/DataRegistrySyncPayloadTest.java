package com.stardew.craft.api.v1;

import com.stardew.craft.network.DataRegistrySyncPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataRegistrySyncPayloadTest {
    @Test
    void allRegistryDocumentsRoundTripInProtocolOrder() {
        DataRegistrySyncPayload expected = new DataRegistrySyncPayload(
                "artisan", "cooking", "crafting", "preserves",
                "fishing", "npc-events", "unlock-sources", "festivals",
                "mastery-rewards", "locations", "professions");
        ByteBuf buffer = Unpooled.buffer();
        try {
            DataRegistrySyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, DataRegistrySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
