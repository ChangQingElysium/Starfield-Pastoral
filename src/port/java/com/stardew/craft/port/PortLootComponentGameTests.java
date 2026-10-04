package com.stardew.craft.port;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real loot loading and component-to-NBT conversion, not just a JSON projection. */
@GameTestHolder("stardewcraft_ginger_aliases")
@PrefixGameTestTemplate(false)
public final class PortLootComponentGameTests {
    private PortLootComponentGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty", timeoutTicks = 100)
    public static void convertedLootRetainsCanonicalItemAndExactVariant(GameTestHelper h) {
        String[][] cases = {
                {"ginger_island_sand_starfish", "ginger_island_starfish_gold", "3"},
                {"ginger_palm_wall_ornament_right", "ginger_palm_wall_ornament_left", "1"}
        };
        for (String[] entry : cases) {
            var state = GingerIslandBlocks.get(entry[0]).defaultBlockState();
            var drops = Block.getDrops(state, h.getLevel(), h.absolutePos(new BlockPos(6, 2, 6)), null);
            h.assertTrue(drops.size() == 1 && drops.get(0).getCount() == 1
                            && drops.get(0).is(GingerIslandBlocks.get(entry[1]).asItem()),
                    "Converted loot did not produce one canonical item: " + entry[0]);
            var component = PortItemData.get(drops.get(0), DataComponents.BLOCK_STATE);
            h.assertTrue(component != null && component.properties().equals(java.util.Map.of("variant", entry[2])),
                    "Converted loot lost its exact appearance variant: " + entry[0]);
            var extension = state.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.EXTENSION);
            h.assertTrue(Block.getDrops(extension, h.getLevel(), h.absolutePos(new BlockPos(6, 3, 6)), null).isEmpty(),
                    "An extension produced a duplicate canonical drop: " + entry[0]);
        }
        h.succeed();
    }
}
