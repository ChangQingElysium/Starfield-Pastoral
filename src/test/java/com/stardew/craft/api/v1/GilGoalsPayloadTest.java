package com.stardew.craft.api.v1;

import com.stardew.craft.network.payload.OpenGilGoalsPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GilGoalsPayloadTest {
    @Test
    void serverOwnedGoalPresentationRoundTrips() {
        OpenGilGoalsPayload expected = new OpenGilGoalsPayload(List.of(
                new OpenGilGoalsPayload.GoalEntry(
                        "addon:slimes", "addon.goal.slimes", 3, 5, false, true)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            OpenGilGoalsPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, OpenGilGoalsPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
