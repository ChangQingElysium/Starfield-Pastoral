package com.stardew.craft.network;

import com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry;
import com.stardew.craft.api.v1.network.StardewNetworkCapability;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilityRequirement;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityNegotiationPayloadTest {
    @Test
    void helloAndAckRoundTripRequiredAndOptionalCapabilities() {
        List<StardewNetworkCapability> capabilities = List.of(
                capability(
                        "required",
                        2,
                        StardewNetworkCapabilityRequirement.REQUIRED_REMOTE),
                capability(
                        "optional",
                        5,
                        StardewNetworkCapabilityRequirement.OPTIONAL));

        assertEquals(
                new CapabilityHelloPayload(capabilities),
                roundTripHello(new CapabilityHelloPayload(capabilities)));
        assertEquals(
                new CapabilityAckPayload(capabilities),
                roundTripAck(new CapabilityAckPayload(capabilities)));
    }

    @Test
    void constructorsRejectSnapshotsAboveTheProtocolBound() {
        ArrayList<StardewNetworkCapability> capabilities =
                new ArrayList<>();
        for (int index = 0;
             index <= StardewNetworkCapabilityRegistry.MAX_CAPABILITIES;
             index++) {
            capabilities.add(capability(
                    "capability_" + index,
                    1,
                    StardewNetworkCapabilityRequirement.OPTIONAL));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new CapabilityHelloPayload(capabilities));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CapabilityAckPayload(capabilities));
    }

    private static CapabilityHelloPayload roundTripHello(
            CapabilityHelloPayload expected
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CapabilityHelloPayload.STREAM_CODEC.encode(buffer, expected);
            return CapabilityHelloPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static CapabilityAckPayload roundTripAck(
            CapabilityAckPayload expected
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CapabilityAckPayload.STREAM_CODEC.encode(buffer, expected);
            return CapabilityAckPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static StardewNetworkCapability capability(
            String path,
            int version,
            StardewNetworkCapabilityRequirement requirement
    ) {
        return new StardewNetworkCapability(
                ResourceLocation.fromNamespaceAndPath("test", path),
                version,
                requirement);
    }
}
