package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.HotPepperCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.crop.TomatoCropBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_hot_pepper")
@PrefixGameTestTemplate(false)
public final class HotPepperCropGameTests {
    private HotPepperCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_hot_pepper", template = "ring_utilities")
    public static void hot_pepperPlantsWithoutReplacingBlocksAndRegrowsAfterHarvest(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        ItemStack oldHand = player.getMainHandItem();
        try {
            clock.setCurrentSeason(1);
            BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5));
            BlockPos pos = soil.above();
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 0), 3);
            level.setBlock(soil.east(), Blocks.WATER.defaultBlockState(), 3);
            var seed = ModItems.HOT_PEPPER_SEEDS.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!seed.useOn(context).consumesAction(), "HotPepper planted through the upper obstruction");
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE)
                    && player.getMainHandItem().getCount() == 2, "Obstruction or seeds were consumed");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            clock.setCurrentSeason(0);
            helper.assertTrue(!seed.useOn(context).consumesAction(), "HotPepper planted in spring");
            clock.setCurrentSeason(1);
            helper.assertTrue(seed.useOn(context).consumesAction(), "HotPepper failed to plant in summer");
            var crop = (HotPepperCropBlock) ModBlocks.HOT_PEPPER_CROP.get();
            helper.assertTrue(level.getBlockState(pos).is(crop) && level.getBlockState(pos.above()).is(crop), "HotPepper carriers missing");
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "Planting did not consume one seed");
            helper.assertTrue(level.getBlockState(soil).getValue(FarmBlock.MOISTURE) == 0, "HotPepper inherited paddy auto-watering");
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{1, 1, 1, 1, 1}), "Incorrect hot_pepper growth phases");
            var phases = CropGrowthManager.get(level).getOrCreateState(level, pos);
            phases.sourcePhases = false;
            phases.phase = 3;
            phases.dayInPhase = 0;
            crop.syncVisualStage(level, pos, phases);
            helper.assertTrue(phases.phase == 3 && phases.dayInPhase == 0, "Legacy elapsed days changed");
            crop.syncVisualStage(level, pos, phases);
            helper.assertTrue(phases.phase == 3 && phases.dayInPhase == 0, "Legacy migration repeated");
            for (int phase = 0; phase <= 5; phase++) {
                phases.phase = phase;
                phases.dayInPhase = 0;
                phases.regrowing = false;
                level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, phase == 5 ? 3 : Math.min(phase, 2)), 2);
                crop.syncVisualStage(level, pos, phases);
                for (BlockPos part : new BlockPos[]{pos, pos.above()}) {
                    var state = level.getBlockState(part);
                    helper.assertTrue(state.is(crop) && state.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1, "Phase carriers differ");
                    helper.assertTrue(state.getCollisionShape(level, part).isEmpty(), "Pepper blocks movement");
                    if (part.equals(pos)) CropSelectionAssertions.matchesStage(helper, pos);
                }
            }
            // This server loads only the overworld, so natural valley-only daily ticks are not covered.
            for (boolean junimo : new boolean[]{false, true}) {
                var ready = CropGrowthManager.get(level).getOrCreateState(level, pos);
                ready.phase = 5;
                ready.regrowing = false;
                ready.dayInPhase = 0;
                var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3)
                        .setValue(StardewCropBlock.GROWTH_STAGE, 6);
                level.setBlock(pos, mature, 2);
                level.setBlock(pos.above(), mature.setValue(TomatoCropBlock.HALF, DoubleBlockHalf.UPPER), 2);
                for (BlockPos part : new BlockPos[]{pos, pos.above()}) {
                    var state = level.getBlockState(part);
                    helper.assertTrue(state.getCollisionShape(level, part).isEmpty(), "HotPepper blocks movement");
                    helper.assertTrue(!state.getShape(level, part).isEmpty(), "HotPepper has no interaction shape");
                }
                if (junimo) {
                    var drops = new ArrayList<ItemStack>();
                    var fruit = crop.tryHarvestByJunimo(level, pos, mature, 10, drops::add);
                    helper.assertTrue(fruit.is(ModItems.HOT_PEPPER.get())
                            && fruit.getCount() == 1, "HotPepper harvested another fruit or wrong quantity");
                } else helper.assertTrue(crop.tryHarvestByHand(level, pos.above(), level.getBlockState(pos.above()), null), "HotPepper could not be hand harvested");
                var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                helper.assertTrue(growth.regrowing && growth.dayInPhase == 3, "HotPepper did not start three-day regrowth");
                for (BlockPos part : new BlockPos[]{pos, pos.above()}) {
                    var state = level.getBlockState(part);
                    helper.assertTrue(state.is(crop) && state.getValue(StardewCropBlock.AGE) == 2
                            && state.getValue(StardewCropBlock.GROWTH_STAGE) == 8, "Regrowth model did not synchronize both halves");
                }
            }
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1), 2);
            var oldGrowth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            oldGrowth.phase = 1;
            crop.syncVisualStage(level, pos, oldGrowth);
            crop.growCropOneDay(level, pos, level.getBlockState(pos), true, oldGrowth);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE), "Legacy pepper replaced obstruction");
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        helper.succeed();
    }
}
