package com.stardew.craft.platform.fabric.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ProtocolManifestTest {
    @Test
    void matchingRequiredVersionsAndMissingOptionalPayloadsAreAccepted() {
        var required = entry("required", "31", false);
        var optional = entry("optional", "4", true);
        assertNull(ProtocolManifest.incompatibility(List.of(required, optional), List.of(required)));
        assertNull(ProtocolManifest.incompatibility(List.of(required), List.of(required, optional)));
    }

    @Test
    void requiredPresenceAndVersionsAreCheckedInBothDirections() {
        var required = entry("required", "31", false);
        assertNotNull(ProtocolManifest.incompatibility(List.of(required), List.of()));
        assertNotNull(ProtocolManifest.incompatibility(List.of(), List.of(required)));
        assertNotNull(ProtocolManifest.incompatibility(List.of(required), List.of(entry("required", "30", false))));
        assertNotNull(ProtocolManifest.incompatibility(List.of(entry("optional", "1", true)),
                List.of(entry("optional", "2", true))));
    }

    @Test
    void identicalIdsInAnotherDirectionOrProtocolCannotSatisfyARequirement() {
        var required = entry("required", "31", false);
        var wrongDirection = new ProtocolManifest.Entry(required.id(), ConnectionProtocol.PLAY,
                PacketFlow.SERVERBOUND, "31", false);
        var wrongProtocol = new ProtocolManifest.Entry(required.id(), ConnectionProtocol.CONFIGURATION,
                PacketFlow.CLIENTBOUND, "31", false);
        assertNotNull(ProtocolManifest.incompatibility(List.of(required), List.of(wrongDirection)));
        assertNotNull(ProtocolManifest.incompatibility(List.of(required), List.of(wrongProtocol)));
    }

    @Test
    void offerAndAckKeepNonceDirectionPhaseVersionAndOptionalFlagsThroughRealCodecs() {
        var play = entry("play", "31", false);
        var configuration = new ProtocolManifest.Entry(ResourceLocation.parse("test:configuration"),
                ConnectionProtocol.CONFIGURATION, PacketFlow.SERVERBOUND, "2", true);
        List<ProtocolManifest.Entry> entries = List.of(play, configuration);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ProtocolManifest.Offer.CODEC.encode(buffer, new ProtocolManifest.Offer(456, entries));
            assertEquals(new ProtocolManifest.Offer(456, entries), ProtocolManifest.Offer.CODEC.decode(buffer));
            ProtocolManifest.Ack.CODEC.encode(buffer, new ProtocolManifest.Ack(456, entries));
            assertEquals(new ProtocolManifest.Ack(456, entries), ProtocolManifest.Ack.CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void duplicateAndOversizedManifestsAreRejected() {
        var entry = entry("a", "31", false);
        assertThrows(IllegalArgumentException.class, () -> new ProtocolManifest.Offer(1, List.of(entry, entry)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeLong(1);
            buffer.writeVarInt(1025);
            assertThrows(IllegalArgumentException.class, () -> ProtocolManifest.Offer.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void unknownManifestFlagsAreRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeLong(1);
            buffer.writeVarInt(1);
            buffer.writeUtf("test:a");
            buffer.writeByte(8);
            assertThrows(IllegalArgumentException.class, () -> ProtocolManifest.Ack.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static ProtocolManifest.Entry entry(String path, String version, boolean optional) {
        return new ProtocolManifest.Entry(ResourceLocation.fromNamespaceAndPath("test", path),
                ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND, version, optional);
    }
}
