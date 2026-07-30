package com.stardew.craft.combat.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class WeaponSkillAnimPayloadTest {
    @Test
    void codecRoundTripsPresentationSnapshot() {
        WeaponSkillAnimPayload expected = new WeaponSkillAnimPayload(
                42,
                "forest_sword",
                "forest_blessing",
                8,
                83,
                2400L,
                3,
                12.25,
                64.0,
                -7.5,
                135.0f,
                0x1234ABCDL
        );
        ByteBuf buffer = Unpooled.buffer();
        try {
            WeaponSkillAnimPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, WeaponSkillAnimPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void impactCodecRoundTripsConfirmedTargets() {
        WeaponSkillImpactPayload expected = new WeaponSkillImpactPayload(
                42,
                "crescent_slash",
                java.util.List.of(7, 11, 19),
                0xCAFEBABEL
        );
        ByteBuf buffer = Unpooled.buffer();
        try {
            WeaponSkillImpactPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, WeaponSkillImpactPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void forestStateCodecCarriesCasterLifecycle() {
        for (ForestBlessingPayload expected : java.util.List.of(
                new ForestBlessingPayload(42, true, 80, false),
                new ForestBlessingPayload(42, false, 0, true)
        )) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                ForestBlessingPayload.STREAM_CODEC.encode(buffer, expected);
                assertEquals(expected, ForestBlessingPayload.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }
}
