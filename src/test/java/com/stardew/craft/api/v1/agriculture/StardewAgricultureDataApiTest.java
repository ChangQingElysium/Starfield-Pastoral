package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StardewAgricultureDataApiTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void cropProvidersUsePriorityAndContinueAfterRuntimeException() {
        StardewCropData selected = cropData(17);
        StardewCropData lower = cropData(3);
        AtomicInteger lowerCalls = new AtomicInteger();

        StardewAgricultureDataApi.registerCropProvider(uniqueId("low"), -100, (level, pos, state) -> {
            if (!state.is(Blocks.WHEAT)) return null;
            lowerCalls.incrementAndGet();
            return lower;
        });
        StardewAgricultureDataApi.registerCropProvider(uniqueId("selected"), 100, (level, pos, state) ->
                state.is(Blocks.WHEAT) ? selected : null);
        StardewAgricultureDataApi.registerCropProvider(uniqueId("throwing"), 200, (level, pos, state) -> {
            if (state.is(Blocks.WHEAT)) throw new IllegalStateException("test provider failure");
            return null;
        });

        StardewCropData resolved = StardewAgricultureDataApi.crop(
                null, net.minecraft.core.BlockPos.ZERO, Blocks.WHEAT.defaultBlockState());

        assertSame(selected, resolved);
        assertEquals(0, lowerCalls.get());
    }

    @Test
    void providerRegistrationDuringResolutionUsesNextImmutableSnapshot() {
        StardewCropData initial = cropData(5);
        StardewCropData added = cropData(29);
        AtomicBoolean registered = new AtomicBoolean();

        StardewAgricultureDataApi.registerCropProvider(uniqueId("snapshot_base"), -100,
                (level, pos, state) -> state.is(Blocks.CARROTS) ? initial : null);
        StardewAgricultureDataApi.registerCropProvider(uniqueId("snapshot_mutator"), 100,
                (level, pos, state) -> {
                    if (!state.is(Blocks.CARROTS)) return null;
                    if (registered.compareAndSet(false, true)) {
                        StardewAgricultureDataApi.registerCropProvider(uniqueId("snapshot_added"), 200,
                                (innerLevel, innerPos, innerState) -> innerState.is(Blocks.CARROTS) ? added : null);
                    }
                    return null;
                });

        assertSame(initial, StardewAgricultureDataApi.crop(
                null, net.minecraft.core.BlockPos.ZERO, Blocks.CARROTS.defaultBlockState()));
        assertSame(added, StardewAgricultureDataApi.crop(
                null, net.minecraft.core.BlockPos.ZERO, Blocks.CARROTS.defaultBlockState()));
    }

    private static StardewCropData cropData(int experience) {
        return new StardewCropData(
                List.of("spring"), List.of(1), -1, experience,
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "grab"),
                ResourceLocation.withDefaultNamespace("wheat"),
                ResourceLocation.withDefaultNamespace("wheat_seeds"));
    }

    private static ResourceLocation uniqueId(String path) {
        return ResourceLocation.fromNamespaceAndPath("agriculture_test", path + "_" + IDS.incrementAndGet());
    }
}
