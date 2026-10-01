package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineGroundConnections;
import com.stardew.craft.block.mine.MineStepStoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineGroundGameTests {
    private MineGroundGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void mineGroundPriorityAndStoneConnections(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
        int[][] neighbors = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(center.offset(x,0,z), ModBlocks.MINE_EARTH_WALL.get().defaultBlockState(), 3);
            level.setBlock(center.offset(x,1,z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(center.north(), ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(), 3);
        level.setBlock(center.east(), ModBlocks.MINE_EARTH_LOOSE_SOIL.get().defaultBlockState(), 3);
        helper.assertTrue(MineGroundConnections.mask(level, center, level.getBlockState(center), 1) == 1, "Compacted soil must cover wall");
        helper.assertTrue(MineGroundConnections.mask(level, center, level.getBlockState(center), 2) == 2, "Loose soil must also cover wall");
        level.setBlock(center, ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(), 3);
        helper.assertTrue(MineGroundConnections.mask(level, center, level.getBlockState(center), 1) == 0, "Same-ranked soil received a border");
        helper.assertTrue(MineGroundConnections.mask(level, center, level.getBlockState(center), 2) == 2, "Loose soil must cover compacted soil");
        level.setBlock(center.above(), ModBlocks.MINE_STEP_STONE.get().defaultBlockState(), 3);
        helper.assertTrue(MineGroundConnections.mask(level, center, level.getBlockState(center), 2) == 0, "Soil must not cover stone paving");
        level.removeBlock(center.above(), false);
        level.setBlock(center.east().above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(MineGroundConnections.mask(level, center, level.getBlockState(center), 2) == 0, "Buried soil leaked through wall");
        level.removeBlock(center.east().above(), false);
        BlockPos layer = center.above();
        var stone = ModBlocks.MINE_STEP_STONE.get();
        level.setBlock(layer, stone.defaultBlockState(), 3);
        for (int mask = 0; mask < 256; mask++) {
            for (int i = 0; i < neighbors.length; i++) level.setBlock(layer.offset(neighbors[i][0],0,neighbors[i][1]),
                    (mask & (1 << i)) == 0 ? Blocks.AIR.defaultBlockState() : stone.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(layer).getValue(MineStepStoneBlock.CONNECTIONS) == MineStepStoneBlock.canonicalMask(mask), "Stale stone connection at " + mask);
        }
        helper.assertTrue(level.getBlockState(layer).getCollisionShape(level, layer).max(Direction.Axis.Y) == 0.125, "Stone layer changed height");
        helper.assertTrue(level.getBlockState(center).is(ModBlocks.MINE_EARTH_SOIL.get()), "Stone replaced the underlying soil");
        level.removeBlock(center, false);
        helper.assertTrue(level.getBlockState(layer).isAir(), "Stone remained unsupported");
        helper.succeed();
    }
}
