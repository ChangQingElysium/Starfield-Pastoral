package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineSpiralColumnBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_spiral_column")
@PrefixGameTestTemplate(false)
public final class MineSpiralColumnGameTests {
    @GameTest(templateNamespace = "stardewcraft_spiral_column", template = "ring_utilities", timeoutTicks = 40)
    public static void fourDirectionsPlaceSelectAndRemoveAsOne(GameTestHelper h) {
        var level = h.getLevel();
        var block = ModBlocks.MINE_SPIRAL_COLUMN.get();
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos root = h.absolutePos(new BlockPos(4 + index % 2 * 8, 3, 4 + index / 2 * 8));
            index++;
            level.setBlock(root.below(), Blocks.STONE.defaultBlockState(), 3);
            var base = block.defaultBlockState().setValue(MineSpiralColumnBlock.FACING, facing);
            level.setBlock(root, base, 3);
            block.setPlacedBy(level, root, base, null, new ItemStack(block));
            var whole = base.getShape(level, root).bounds();
            for (int tier = 0; tier < 4; tier++) {
                var pos = root.above(tier);
                var state = level.getBlockState(pos);
                h.assertTrue(state.is(block) && state.getValue(MineSpiralColumnBlock.TIER) == tier,
                        "Missing automatic part for " + facing + ": " + tier);
                h.assertTrue(state.getShape(level, pos).bounds().move(0, tier, 0).equals(whole),
                        "Selection outline changed between parts");
                h.assertTrue(state.getLightEmission() == 0 && state.getLightBlock(level, pos) == 0,
                        "Non-emitting column unexpectedly changes stored block light");
                var collision = state.getCollisionShape(level, pos).bounds();
                h.assertTrue(collision.minY == 0 && collision.maxY == 1, "Collision leaked outside its cell");
            }
            level.removeBlock(root.above(2), false);
            for (int tier = 0; tier < 4; tier++) h.assertTrue(level.getBlockState(root.above(tier)).isAir(),
                    "Removing an upper part left an orphan");
            // Import the upper pieces first, as structure placement may do.
            for (int tier = 3; tier >= 0; tier--) level.setBlock(root.above(tier), base.setValue(MineSpiralColumnBlock.TIER, tier), 3);
            h.runAtTickTime(3, () -> {
                for (int tier = 0; tier < 4; tier++) h.assertTrue(level.getBlockState(root.above(tier)).is(block),
                        "Delayed validation broke a completed structure import");
                level.removeBlock(root.below(), false);
            });
            h.runAtTickTime(6, () -> {
                for (int tier = 0; tier < 4; tier++) h.assertTrue(level.getBlockState(root.above(tier)).isAir(),
                        "Unsupported column survived");
            });
        }
        h.runAtTickTime(7, h::succeed);
    }
}
