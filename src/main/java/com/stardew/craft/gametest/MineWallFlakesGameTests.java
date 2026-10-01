package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineWallFlakesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.LinkedHashMap;

@GameTestHolder("stardewcraft_wall_flakes")
@PrefixGameTestTemplate(false)
public final class MineWallFlakesGameTests {
    @GameTest(templateNamespace = "stardewcraft_wall_flakes", template = "ring_utilities", timeoutTicks = 40)
    public static void fourWallConnectionsSurviveRemovalAndKeepVariants(GameTestHelper h) {
        var level = h.getLevel();
        var block = ModBlocks.MINE_DESERT_WALL_FLAKES.get();
        var centers = new LinkedHashMap<Direction, BlockPos>();
        int index = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos center = h.absolutePos(new BlockPos(4 + index % 2 * 8, 4, 4 + index / 2 * 8));
            centers.put(facing, center);
            index++;
            Direction right = facing.getClockWise();
            for (int side = -1; side <= 1; side++) for (int y = -1; y <= 1; y++) {
                BlockPos pos = center.relative(right, side).above(y);
                level.setBlock(pos.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
                level.removeBlock(pos, false);
            }
            // Structure-like insertion: initial flags are false, variants differ across the joins.
            int v = 0;
            for (BlockPos pos : new BlockPos[]{center, center.above(), center.below(), center.relative(right), center.relative(right.getOpposite())}) {
                level.setBlock(pos, block.defaultBlockState().setValue(MineWallFlakesBlock.FACING, facing)
                        .setValue(MineWallFlakesBlock.VARIANT, v++ % 3), 3);
            }
        }
        h.runAtTickTime(3, () -> {
            for (var entry : centers.entrySet()) {
                Direction facing = entry.getKey(), right = facing.getClockWise();
                BlockPos center = entry.getValue();
                var state = level.getBlockState(center);
                for (var connection : new net.minecraft.world.level.block.state.properties.BooleanProperty[]{
                        MineWallFlakesBlock.ABOVE, MineWallFlakesBlock.BELOW, MineWallFlakesBlock.LEFT, MineWallFlakesBlock.RIGHT})
                    h.assertTrue(state.getValue(connection), "Missing connection on " + facing + ": " + connection);
                h.assertTrue(state.getCollisionShape(level, center).isEmpty(), "Shallow decoration obstructs movement");
                h.assertTrue(PortItemData.getOrDefault(block.getCloneItemStack(level, center, state), DataComponents.BLOCK_STATE,
                        BlockItemStateProperties.EMPTY).get(MineWallFlakesBlock.VARIANT) == 0, "Pick lost variant");
                level.removeBlock(center.relative(right.getOpposite()), false);
                state = level.getBlockState(center);
                h.assertTrue(!state.getValue(MineWallFlakesBlock.LEFT) && state.getValue(MineWallFlakesBlock.RIGHT), "Removed neighbour left a stale join");
                h.assertTrue(state.getValue(MineWallFlakesBlock.VARIANT) == 0, "Connection update rerolled the stone pattern");
                var perpendicular = block.defaultBlockState().setValue(MineWallFlakesBlock.FACING, right);
                level.setBlock(center.relative(right.getOpposite()), perpendicular, 3);
                h.assertTrue(!level.getBlockState(center).getValue(MineWallFlakesBlock.LEFT), "Perpendicular faces joined");
                level.removeBlock(center.relative(facing.getOpposite()), false);
                h.assertTrue(level.getBlockState(center).isAir(), "Removed backing left floating flakes");
                h.assertTrue(!level.getBlockState(center.above()).getValue(MineWallFlakesBlock.BELOW), "Vertical end failed to update");
            }
            h.succeed();
        });
    }
}
