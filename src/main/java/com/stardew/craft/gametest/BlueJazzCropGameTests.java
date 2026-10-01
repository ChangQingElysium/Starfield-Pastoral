package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.BlueJazzCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_blue_jazz")
@PrefixGameTestTemplate(false)
public final class BlueJazzCropGameTests {
    private BlueJazzCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_blue_jazz", template = "ring_utilities")
    public static void fullScheduleColorsCarriersAndHarvest(GameTestHelper helper) {
        var level = helper.getLevel();
        var crop = (BlueJazzCropBlock) ModBlocks.BLUE_JAZZ_CROP.get();
        var clock = StardewTimeManager.get();
        var player = FakePlayerFactory.getMinecraft(level);
        int oldSeason = clock.getCurrentSeason();
        var oldHand = player.getMainHandItem();
        BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5)), pos = soil.above();
        try {
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{1, 2, 2, 2})
                    && crop.getRegrowDaysForDisplay() == 0, "Blue Jazz schedule changed");
            clock.setCurrentSeason(0);
            level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
            var seed = ModItems.BLUE_JAZZ_SEEDS.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!seed.useOn(context).consumesAction()
                    && player.getMainHandItem().getCount() == 2
                    && level.getBlockState(pos.above()).is(Blocks.STONE), "Planting replaced upper obstruction");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(seed.useOn(context).consumesAction()
                    && player.getMainHandItem().getCount() == 1, "Planting failed");
            var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            for (int phase = 0; phase <= 4; phase++) {
                growth.phase = phase;
                growth.dayInPhase = 0;
                growth.sourcePhases = true;
                int age = phase == 4 ? 3 : Math.min(phase, 2);
                level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, age), 2);
                crop.syncVisualStage(level, pos, growth);
                for (var part : new BlockPos[]{pos, pos.above()}) {
                    var current = level.getBlockState(part);
                    helper.assertTrue(current.is(crop)
                            && current.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1,
                            "Visual stage carriers differ at phase " + phase);
                    helper.assertTrue(current.getCollisionShape(level, part).isEmpty(), "Blue Jazz blocks movement");
                }
                var upperShape = level.getBlockState(pos.above()).getShape(level, pos.above());
                helper.assertTrue(upperShape.isEmpty() == (phase < 4), "Upper selection does not match model height");
            }
            var color = (IntegerProperty) crop.getStateDefinition().getProperty("color");
            for (int i = 0; i < 6; i++) {
                var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3)
                        .setValue(StardewCropBlock.GROWTH_STAGE, 5).setValue(color, i);
                level.setBlock(pos, mature, 2);
                level.setBlock(pos.above(), mature.setValue(BlueJazzCropBlock.HALF, DoubleBlockHalf.UPPER), 2);
                var harvest = crop.tryHarvestByJunimo(level, pos, mature, 0, stack -> {});
                helper.assertTrue(harvest.is(ModItems.BLUE_JAZZ.get()) && harvest.getCount() == 1, "Wrong harvest color=" + i + " stack=" + harvest + " state=" + level.getBlockState(pos));
                var data = harvest.get(DataComponents.CUSTOM_DATA);
                helper.assertTrue(data != null && data.copyTag().getInt("FlowerColor") == i, "Harvest color changed");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(),
                        "Harvest left a carrier");
            }
            var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3)
                    .setValue(StardewCropBlock.GROWTH_STAGE, 5);
            level.setBlock(pos, mature, 2);
            helper.assertTrue(crop.tryHarvestByHand(level, pos.above(), level.getBlockState(pos.above()), null),
                    "Upper hand harvest failed");
            helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(),
                    "Upper hand harvest left a carrier");

            // Loading a former single-block crop must not replace an unrelated block above it.
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1), 2);
            growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            growth.phase = 1;
            crop.syncVisualStage(level, pos, growth);
            crop.growCropOneDay(level, pos, level.getBlockState(pos), true, growth);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE), "Migration replaced obstruction");
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        helper.succeed();
    }
}
