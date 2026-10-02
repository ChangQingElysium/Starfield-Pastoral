package com.example.stardewaddon;

import com.stardew.craft.api.v1.agriculture.StardewGiantCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;

/** Independent addon example; the addon owns/registers the actual giant block and its model. */
final class ExampleGiantCrops {
    private ExampleGiantCrops() {}
    static void register() {
        var id = new ResourceLocation("example_stardew_addon:giant_moonberry");
        StardewGiantCrops.register(new StardewGiantCrops.Definition(id,
                new ResourceLocation("example_stardew_addon:moonberry"), 3, 3, 2, .01, true,
                (context, crops) -> {
                    // Resolve deferred blocks at runtime, never during mod construction.
                    if (!BuiltInRegistries.BLOCK.containsKey(id)) return java.util.List.of();
                    var block = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
                    var cells = new ArrayList<StardewGiantCrops.Cell>();
                    for (int y = 0; y < 2; y++) for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++)
                        cells.add(new StardewGiantCrops.Cell(new BlockPos(x, y, z), block));
                    return cells;
                }));
    }
}
