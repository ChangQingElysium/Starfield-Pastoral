package com.stardew.craft.player;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapInteractionReadStateTest {
    @Test
    void readDefinitionIdsRoundTripThroughPlayerNbt() {
        ResourceLocation interactionId =
                ResourceLocation.fromNamespaceAndPath(
                        "example", "town/library_notice");
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data =
                new PlayerStardewData(playerId);

        assertFalse(data.hasReadMapInteraction(interactionId));
        data.markMapInteractionRead(interactionId);
        assertTrue(data.hasReadMapInteraction(interactionId));

        PlayerStardewData restored = PlayerStardewData.fromNBT(
                data.toNBT(), playerId);
        assertTrue(restored.hasReadMapInteraction(interactionId));
    }
}
