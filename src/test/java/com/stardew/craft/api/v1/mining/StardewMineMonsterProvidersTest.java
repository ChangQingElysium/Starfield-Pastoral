package com.stardew.craft.api.v1.mining;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class StardewMineMonsterProvidersTest {
    @Test
    void failingProviderDoesNotBlockLowerPriorityFallback() {
        StardewMineMonsterProviders.register(id("throwing"), 100, context -> {
            throw new IllegalStateException("test failure");
        });
        StardewMineMonsterProviders.register(id("fallback"), 90, context -> EntityType.ZOMBIE);

        assertSame(EntityType.ZOMBIE, StardewMineMonsterProviders.select(
                new StardewMineMonsterContext(42, false, 12.0, RandomSource.create(1L))));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft_test", path);
    }
}
