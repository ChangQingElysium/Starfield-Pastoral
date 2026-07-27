package com.stardew.craft.api.v1.mining;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void profileSelectorReturnsCompleteRegisteredProfile() {
        String suffix = UUID.randomUUID().toString();
        ResourceLocation profileId = id("profile_" + suffix);
        StardewMineMonsterProfiles.register(
                profileId,
                EntityType.ZOMBIE,
                Set.of("stardewcraft_test:clockwork"),
                (mob, context) -> {
                });
        StardewMineMonsterProfiles.registerSelector(
                id("selector_" + suffix),
                100,
                context -> profileId);

        StardewMineMonsterProfile selected =
                StardewMineMonsterProfiles.select(
                        new StardewMineMonsterContext(
                                42,
                                false,
                                12.0,
                                RandomSource.create(1L)));

        assertEquals(profileId, selected.id());
        assertSame(EntityType.ZOMBIE, selected.entityType());
        assertEquals(
                Set.of("stardewcraft_test:clockwork"),
                selected.progressTags());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft_test", path);
    }
}
