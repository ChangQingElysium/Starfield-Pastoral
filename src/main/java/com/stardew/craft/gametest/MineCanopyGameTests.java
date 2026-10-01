package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineCanopyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineCanopyGameTests {
    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 60)
    public static void canopyImportJoinsAndLosesOnlyUnsupportedCell(GameTestHelper helper) {
        var level = helper.getLevel(); var block = ModBlocks.MINE_CANOPY.get();
        var pos = helper.absolutePos(new BlockPos(8, 3, 8));
        // Import order may put foliage before its ceiling within the same tick.
        level.setBlock(pos, block.defaultBlockState(), 3);
        level.setBlock(pos.east(), block.defaultBlockState().setValue(MineCanopyBlock.VARIANT, 1)
                .setValue(MineCanopyBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos.east().above(), Blocks.STONE.defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            var left = level.getBlockState(pos); var right = level.getBlockState(pos.east());
            helper.assertTrue(left.is(block) && right.is(block), "Import removed supported foliage");
            helper.assertTrue(left.getValue(MineCanopyBlock.EAST) && right.getValue(MineCanopyBlock.WEST),
                    "Variants with different facing failed to join");
            helper.assertTrue(left.getCollisionShape(level, pos).isEmpty(), "Foliage obstructs walking");
            var rotated = left.rotate(Rotation.CLOCKWISE_90);
            helper.assertTrue(rotated.getValue(MineCanopyBlock.SOUTH) && !rotated.getValue(MineCanopyBlock.EAST),
                    "Rotation did not move the connection");
            helper.assertTrue(left.mirror(Mirror.FRONT_BACK).getValue(MineCanopyBlock.WEST), "Mirror lost connection");
            level.removeBlock(pos.above(), false);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(level.getBlockState(pos).isAir(), "Unsupported canopy remained floating");
            var right = level.getBlockState(pos.east());
            helper.assertTrue(right.is(block) && !right.getValue(MineCanopyBlock.WEST),
                    "Removing one support removed its neighbor or left a stale join");
            helper.succeed();
        });
    }
}
