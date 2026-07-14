package com.stardew.craft.api.v1.profession;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewProfessionEffectHandlersTest {
    @Test
    void failingHandlerReturnsOriginalValue() {
        ResourceLocation handlerId = id("throwing");
        StardewProfessionEffectHandlers.register(handlerId, context -> {
            throw new IllegalStateException("test failure");
        });

        double result = StardewProfessionEffectHandlers.apply(
                handlerId, id("profession"), id("operation"), null, ItemStack.EMPTY, 2.5);

        assertEquals(2.5, result);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft_test", path);
    }
}
