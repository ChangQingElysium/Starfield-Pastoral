package com.stardew.craft.api.v1.agriculture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewCropRuntimeTypesTest {
    @Test
    void descriptorsAndWorldGeometryAreDefensive() {
        ResourceLocation blockId =
                ResourceLocation.withDefaultNamespace("gold_block");
        StardewCropType type = new StardewCropType(
                id("moonberry"),
                "block.crop_test.moonberry",
                5,
                List.of(blockId, blockId),
                null
        );
        assertEquals(List.of(blockId), type.blockIds());
        assertThrows(UnsupportedOperationException.class,
                () -> type.blockIds().add(
                        ResourceLocation.withDefaultNamespace("dirt")));

        BlockPos.MutableBlockPos root =
                new BlockPos.MutableBlockPos(1, 2, 3);
        BlockPos.MutableBlockPos soil =
                new BlockPos.MutableBlockPos(1, 1, 3);
        StardewCropState state = new StardewCropState(
                type.id(),
                root,
                StardewCropState.Part.ROOT,
                2,
                false,
                List.of(soil, soil)
        );
        root.set(8, 8, 8);
        soil.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), state.root());
        assertEquals(List.of(new BlockPos(1, 1, 3)),
                state.soilPositions());
    }

    @Test
    void invalidDescriptorsAndContextsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StardewCropType(
                        id("no_blocks"),
                        "block.crop_test.no_blocks",
                        1,
                        List.of(),
                        null));
        assertThrows(IllegalArgumentException.class,
                () -> new StardewCropType(
                        id("no_stages"),
                        "block.crop_test.no_stages",
                        0,
                        List.of(ResourceLocation.withDefaultNamespace(
                                "dirt")),
                        null));
        assertThrows(IllegalArgumentException.class,
                () -> new StardewCropDailyContext(
                        true, 4, false, 1, false));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "crop_test", path);
    }
}
