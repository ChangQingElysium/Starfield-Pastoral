package com.stardew.craft.api.v1.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewAcquisitionSourcesTest {
    @Test
    void providersComposeDeduplicateAndIsolateFailures() {
        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(Items.BRICK);
        ResourceLocation firstSource = id("first");
        ResourceLocation secondSource = id("second");
        StardewAcquisitionSources.register(
                id("throwing"),
                300,
                context -> {
                    context.target().setCount(0);
                    throw new IllegalStateException(
                            "expected provider failure");
                });
        StardewAcquisitionSources.register(
                id("high"),
                200,
                context -> List.of(source(
                        itemId, firstSource, "high")));
        StardewAcquisitionSources.register(
                id("low"),
                100,
                context -> {
                    assertEquals(1, context.target().getCount());
                    return List.of(
                            source(itemId, firstSource, "duplicate"),
                            source(itemId, secondSource, "second"),
                            source(
                                    BuiltInRegistries.ITEM.getKey(
                                            Items.DIAMOND),
                                    id("wrong_item"),
                                    "wrong"));
                });

        List<StardewAcquisitionSource> sources =
                StardewAcquisitionSources.find(
                                new ItemStack(Items.BRICK))
                        .stream()
                        .filter(source -> source.sourceId()
                                .getNamespace().equals(
                                        "acquisition_test"))
                        .toList();

        assertEquals(
                List.of(firstSource, secondSource),
                sources.stream()
                        .map(StardewAcquisitionSource::sourceId)
                        .toList());
        assertEquals(
                Component.literal("high"),
                sources.getFirst().display());
        assertThrows(UnsupportedOperationException.class, () ->
                StardewAcquisitionSources.find(
                        new ItemStack(Items.BRICK)).add(
                        source(itemId, id("third"), "third")));
    }

    private static StardewAcquisitionSource source(
            ResourceLocation itemId,
            ResourceLocation sourceId,
            String display
    ) {
        return new StardewAcquisitionSource(
                itemId,
                StardewAcquisitionSource.Kind.OTHER,
                sourceId,
                1,
                Component.literal(display),
                false);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "acquisition_test", path);
    }
}
