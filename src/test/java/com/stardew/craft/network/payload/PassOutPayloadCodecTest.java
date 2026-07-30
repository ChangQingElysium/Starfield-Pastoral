package com.stardew.craft.network.payload;

import com.stardew.craft.player.PassOutService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassOutPayloadCodecTest {
    @Test
    void collapseAndAckRoundTripTransactionIdentity() {
        var collapse = new PassOutPayload(
                123456L, PassOutService.PassOutType.COMBAT_MINE, 0, List.of());
        var collapseBuffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        PassOutPayload.STREAM_CODEC.encode(collapseBuffer, collapse);
        assertEquals(collapse, PassOutPayload.STREAM_CODEC.decode(collapseBuffer));
        collapseBuffer.release();

        var ack = new PassOutAckPayload(123456L);
        var ackBuffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        PassOutAckPayload.STREAM_CODEC.encode(ackBuffer, ack);
        assertEquals(ack, PassOutAckPayload.STREAM_CODEC.decode(ackBuffer));
        ackBuffer.release();
    }

    @Test
    void durableOutcomeAndReceiptAckRoundTrip() {
        var outcome = new CombatRescueOutcomePayload(
                987654L, PassOutService.PassOutType.COMBAT_OVERWORLD, 1000, List.of());
        var outcomeBuffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        CombatRescueOutcomePayload.STREAM_CODEC.encode(outcomeBuffer, outcome);
        assertEquals(outcome, CombatRescueOutcomePayload.STREAM_CODEC.decode(outcomeBuffer));
        outcomeBuffer.release();

        var ack = new CombatRescueOutcomeAckPayload(987654L);
        var ackBuffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        CombatRescueOutcomeAckPayload.STREAM_CODEC.encode(ackBuffer, ack);
        assertEquals(ack, CombatRescueOutcomeAckPayload.STREAM_CODEC.decode(ackBuffer));
        ackBuffer.release();
    }
}
