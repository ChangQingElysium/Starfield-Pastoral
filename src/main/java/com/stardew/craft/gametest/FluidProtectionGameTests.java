package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.StardewBlockFluidProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder("stardewcraft_fluid_protection")
@PrefixGameTestTemplate(false)
public final class FluidProtectionGameTests {
    private FluidProtectionGameTests() {
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void everyModBlockRejectsFlowReplacement(GameTestHelper helper) {
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!StardewCraft.MODID.equals(id.getNamespace())) continue;
            var state = block.defaultBlockState();
            if (!state.getFluidState().isEmpty() || block instanceof LiquidBlockContainer) continue;
            helper.assertTrue(StardewBlockFluidProtection.blocksFlowReplacement(state),
                    "Flow replacement remains enabled for " + id);
        }
        helper.assertTrue(!StardewBlockFluidProtection.blocksFlowReplacement(Blocks.GRASS.defaultBlockState()),
                "Protection leaked into vanilla blocks");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty", timeoutTicks = 60)
    public static void flowingWaterCannotWashAwayRepresentativeBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        List<Block> samples = List.of(
                ModBlocks.FARM_TWIG.get(),
                ModBlocks.LARGE_STUMP.get(),
                ModBlocks.HOLLOW_LOG.get(),
                ModBlocks.LARGE_BOULDER.get(),
                ModBlocks.OAK_TABLE.get(),
                ModBlocks.WOODEN_CHEST.get());
        for (int i = 0; i < samples.size(); i++) {
            BlockPos target = helper.absolutePos(new BlockPos(4 + i * 4, 2, 4));
            BlockPos source = target.west();
            level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (direction != Direction.EAST) {
                    level.setBlock(source.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.setBlock(target, samples.get(i).defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            level.scheduleTick(source, Fluids.WATER, 1);
        }

        helper.runAfterDelay(20, () -> {
            for (int i = 0; i < samples.size(); i++) {
                BlockPos target = helper.absolutePos(new BlockPos(4 + i * 4, 2, 4));
                helper.assertTrue(level.getBlockState(target).is(samples.get(i)),
                        BuiltInRegistries.BLOCK.getKey(samples.get(i)) + " was washed away");
            }
            helper.succeed();
        });
    }
}
