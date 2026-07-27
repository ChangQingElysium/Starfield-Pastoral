package com.stardew.craft.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientDisplayFallbacksTest {
    private static final ResourceLocation REQUESTED =
            ResourceLocation.fromNamespaceAndPath(
                    "example", "textures/portraits/new_friend.png");
    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft", "textures/mugshots/lewis.png");

    @Test
    void missingOrBrokenClientResourceUsesKnownFallback() {
        assertEquals(FALLBACK, ClientDisplayFallbacks.availableResource(
                REQUESTED, FALLBACK, ignored -> false));
        assertEquals(FALLBACK, ClientDisplayFallbacks.availableResource(
                REQUESTED, FALLBACK, ignored -> {
                    throw new IllegalStateException("resource pack failed");
                }));
        assertEquals(FALLBACK, ClientDisplayFallbacks.availableResource(
                null, FALLBACK, ignored -> true));
    }

    @Test
    void availableClientResourceIsPreserved() {
        assertEquals(REQUESTED, ClientDisplayFallbacks.availableResource(
                REQUESTED, FALLBACK, REQUESTED::equals));
    }

    @Test
    void missingTranslationFallsBackToReadableStableId() {
        assertEquals("New Friend", ClientDisplayFallbacks.readableId(
                "example:new_friend"));
        assertEquals("Mystery Guest", ClientDisplayFallbacks.readableId(
                "mystery-guest"));
        assertEquals("?", ClientDisplayFallbacks.readableId(" "));
        assertEquals("new_friend", ClientDisplayFallbacks.stablePath(
                "example:new_friend", "fallback"));
        assertEquals("fallback", ClientDisplayFallbacks.stablePath(
                "Bad ID!", "fallback"));
    }
}
