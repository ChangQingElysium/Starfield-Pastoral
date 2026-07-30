package com.stardew.craft.network.payload;

import com.stardew.craft.api.v1.interaction.StardewInteractionHint;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapInteractionHintPayloadTest {
    @Test
    void blockAndEntityRequestsRoundTrip() {
        assertRequestRoundTrip(
                MapInteractionHintRequestPayload.block(
                        new BlockPos(12, 44, -16)));
        assertRequestRoundTrip(
                new MapInteractionHintRequestPayload(
                        new BlockPos(-85, 38, 46), 73));
    }

    @Test
    void pendingAndDoneResponsesRoundTrip() {
        ResourceLocation identity =
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "manor_house/fridge");
        assertResponseRoundTrip(
                MapInteractionHintPayload.visibleBlock(
                        new BlockPos(1, 43, -26),
                        new StardewInteractionHint(
                                StardewInteractionHintType.LOOK,
                                false,
                                identity)));
        assertResponseRoundTrip(
                MapInteractionHintPayload.visibleEntity(
                        41,
                        new StardewInteractionHint(
                                StardewInteractionHintType.TALK,
                                true,
                                identity)));
        assertResponseRoundTrip(
                MapInteractionHintPayload.hiddenBlock(BlockPos.ZERO));
        assertResponseRoundTrip(
                MapInteractionHintPayload.hiddenEntity(92));
    }

    @Test
    void everySemanticTypeHasAStableNetworkRoundTrip() {
        for (StardewInteractionHintType type
                : StardewInteractionHintType.values()) {
            assertEquals(
                    type,
                    StardewInteractionHintType.byNetworkId(
                            type.networkId()));
        }
        assertEquals(
                StardewInteractionHintType.GRAB,
                StardewInteractionHintType.byNetworkId(-1));
        assertEquals(
                StardewInteractionHintType.GRAB,
                StardewInteractionHintType.byNetworkId(999));
    }

    private static void assertRequestRoundTrip(
            MapInteractionHintRequestPayload expected
    ) {
        FriendlyByteBuf buffer =
                new FriendlyByteBuf(Unpooled.buffer());
        try {
            MapInteractionHintRequestPayload.STREAM_CODEC.encode(
                    buffer, expected);
            assertEquals(
                    expected,
                    MapInteractionHintRequestPayload.STREAM_CODEC
                            .decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static void assertResponseRoundTrip(
            MapInteractionHintPayload expected
    ) {
        FriendlyByteBuf buffer =
                new FriendlyByteBuf(Unpooled.buffer());
        try {
            MapInteractionHintPayload.STREAM_CODEC.encode(
                    buffer, expected);
            assertEquals(
                    expected,
                    MapInteractionHintPayload.STREAM_CODEC
                            .decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
