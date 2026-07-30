package com.stardew.craft.network.overnight;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OvernightCollapsePayloadCodecTest {
    @Test
    void startRoundTripsSettlementIdentityAndCause() {
        var expected = new OvernightCollapseStartPayload(
                42, OvernightCollapseStartPayload.Cause.STAMINA);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        OvernightCollapseStartPayload.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, OvernightCollapseStartPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void returnToBedRoundTripsSettlementDay() {
        var expected = new OvernightCollapseReturnToBedPayload(42);
        var buffer = Unpooled.buffer();
        OvernightCollapseReturnToBedPayload.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, OvernightCollapseReturnToBedPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }

    @Test
    void cancelRoundTripsAsTerminalUnitPayload() {
        var expected = new OvernightCollapseCancelPayload();
        var buffer = Unpooled.buffer();
        OvernightCollapseCancelPayload.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, OvernightCollapseCancelPayload.STREAM_CODEC.decode(buffer));
        buffer.release();
    }
}
