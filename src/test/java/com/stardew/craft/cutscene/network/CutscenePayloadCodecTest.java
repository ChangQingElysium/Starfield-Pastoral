package com.stardew.craft.cutscene.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CutscenePayloadCodecTest {
    @Test
    void registrySyncRoundTripsVersionHashAndDefinitions() {
        var expected = new SyncEventRegistryPayload(
                42L, "0123456789abcdef", Map.of("example:event", "{\"id\":\"example:event\"}"));
        var buffer = Unpooled.buffer();

        SyncEventRegistryPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, SyncEventRegistryPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void authorizedActionRoundTripsEveryAuthorizationField() {
        var expected = new CutsceneServerActionPayload(
                "example:event", 987654321L, 17, "example:reward", "value");
        var buffer = Unpooled.buffer();

        CutsceneServerActionPayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, CutsceneServerActionPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void abortRoundTripsTheAuthorizedSessionIdentity() {
        var expected = new AbortCutscenePayload("example:event", 123456789L);
        var buffer = Unpooled.buffer();

        AbortCutscenePayload.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, AbortCutscenePayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void combatRescueReadinessPayloadsRoundTrip() {
        var prepare = new CombatRescuePreparePayload(
                456789L,
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "stardew_mining"),
                new BlockPos(1, 66, -10),
                "linus",
                "event.combat_rescue.mine.linus");
        var prepareBuffer = Unpooled.buffer();
        CombatRescuePreparePayload.STREAM_CODEC.encode(prepareBuffer, prepare);
        assertEquals(prepare, CombatRescuePreparePayload.STREAM_CODEC.decode(prepareBuffer));
        prepareBuffer.release();

        var ready = new CombatRescueReadyPayload(456789L);
        var readyBuffer = Unpooled.buffer();
        CombatRescueReadyPayload.STREAM_CODEC.encode(readyBuffer, ready);
        assertEquals(ready, CombatRescueReadyPayload.STREAM_CODEC.decode(readyBuffer));
        readyBuffer.release();
    }
}
