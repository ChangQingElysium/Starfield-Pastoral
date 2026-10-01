package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.CarrotCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_carrot")
@PrefixGameTestTemplate(false)
public final class CarrotCropGameTests {
    private CarrotCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_carrot", template = "ring_utilities")
    public static void stagesTallInteractionAndWholePlantHarvest(GameTestHelper helper) {
        var level = helper.getLevel();
        var crop = (CarrotCropBlock) ModBlocks.CARROT_CROP.get();
        var clock = StardewTimeManager.get();
        var player = FakePlayerFactory.getMinecraft(level);
        int oldSeason = clock.getCurrentSeason();
        var oldHand = player.getMainHandItem();
        BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5)), pos = soil.above();
        try {
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{1, 1, 1})
                    && crop.getRegrowDaysForDisplay() == 0, "Carrot growth schedule changed");
            level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
            var seed = ModItems.CARROT_SEEDS.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
            clock.setCurrentSeason(1);
            helper.assertTrue(!seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 2, "Summer planting consumed seed");
            clock.setCurrentSeason(0);
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!seed.useOn(context).consumesAction() && level.getBlockState(pos.above()).is(Blocks.STONE), "Planting replaced upper obstruction");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 1, "Spring planting failed");
            var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            growth.sourcePhases = false;
            growth.phase = 3;
            growth.dayInPhase = 0;
            level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1), 2);
            crop.syncVisualStage(level, pos, growth);
            helper.assertTrue(growth.phase == 2 && growth.dayInPhase == 0, "Legacy elapsed days changed");
            crop.syncVisualStage(level, pos, growth);
            helper.assertTrue(growth.phase == 2 && growth.dayInPhase == 0, "Migration applied twice");
            for (int phase = 0; phase <= 3; phase++) {
                growth.phase = phase;
                growth.dayInPhase = 0;
                level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, phase), 2);
                crop.syncVisualStage(level, pos, growth);
                for (var part : new BlockPos[]{pos, pos.above()}) {
                    var current = level.getBlockState(part);
                    helper.assertTrue(current.is(crop) && current.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1, "Stage carriers differ");
                    helper.assertTrue(current.getCollisionShape(level, part).isEmpty(), "Carrot blocks movement");
                }
                helper.assertTrue(!level.getBlockState(pos).getShape(level, pos).isEmpty(), "Missing lower selection");
                CropSelectionAssertions.matchesStage(helper, pos);
            }
            for (boolean junimo : new boolean[]{false, true}) {
                var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3).setValue(StardewCropBlock.GROWTH_STAGE, 4);
                level.setBlock(pos, mature, 2);
                level.setBlock(pos.above(), mature.setValue(CarrotCropBlock.HALF, DoubleBlockHalf.UPPER), 2);
                if (junimo) {
                    var fruit = crop.tryHarvestByJunimo(level, pos, mature, 0, stack -> {});
                    helper.assertTrue(fruit.is(ModItems.CARROT.get()) && fruit.getCount() == 1, "Wrong carrot yield");
                } else helper.assertTrue(crop.tryHarvestByHand(level, pos.above(), level.getBlockState(pos.above()), null), "Upper hand interaction failed");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Harvest left a carrier or regrew carrot");
            }
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1), 2);
            growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            growth.phase = 1;
            crop.syncVisualStage(level, pos, growth);
            crop.growCropOneDay(level, pos, level.getBlockState(pos), true, growth);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE), "Legacy crop replaced obstruction");
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        helper.succeed();
    }
}
