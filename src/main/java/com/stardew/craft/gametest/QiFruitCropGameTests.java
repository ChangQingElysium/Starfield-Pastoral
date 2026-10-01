package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.QiFruitCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_qi_fruit")
@PrefixGameTestTemplate(false)
public final class QiFruitCropGameTests {
    private QiFruitCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_qi_fruit", template = "ring_utilities")
    public static void qiBeansPlantInEverySeasonAndProduceOneNormalFruit(GameTestHelper helper) {
        var level = helper.getLevel();
        var crop = (QiFruitCropBlock) ModBlocks.QI_FRUIT_CROP.get();
        var clock = StardewTimeManager.get();
        var player = FakePlayerFactory.getMinecraft(level);
        int oldSeason = clock.getCurrentSeason();
        var oldHand = player.getMainHandItem();
        BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5)), pos = soil.above();
        try {
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{1, 1, 1, 1}), "Incorrect four-day Qi crop phases");
            for (int season = 0; season < 4; season++) {
                clock.setCurrentSeason(season);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
                var seed = ModItems.VANILLA_CATEGORY_ITEMS.get("qi_bean").get();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
                level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
                helper.assertTrue(!seed.useOn(context).consumesAction() && level.getBlockState(pos.above()).is(Blocks.STONE), "Qi bean destroyed upper obstruction");
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 1, "Qi bean did not plant or consume one seed");
                var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                growth.sourcePhases = true;
                growth.sourcePhaseVersion = 1;
                growth.regrowing = false;
                for (int phase = 0; phase <= 4; phase++) {
                    growth.phase = phase;
                    growth.dayInPhase = 0;
                    level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, phase == 4 ? 3 : phase == 0 ? 0 : 1), 2);
                    crop.syncVisualStage(level, pos, growth);
                    for (var part : new BlockPos[]{pos, pos.above()}) {
                        var current = level.getBlockState(part);
                        helper.assertTrue(current.is(crop) && current.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1, "Qi crop stage carriers differ");
                        helper.assertTrue(current.getCollisionShape(level, part).isEmpty(), "Qi fruit blocks movement");
                    }
                    helper.assertTrue(!level.getBlockState(pos).getShape(level, pos).isEmpty(), "Missing model selection box");
                    if (phase == 4) helper.assertTrue(!level.getBlockState(pos.above()).getShape(level, pos.above()).isEmpty(), "Missing tall vine selection box");
                }
                var mature = level.getBlockState(pos);
                if (season % 2 == 0) {
                    var fruit = crop.tryHarvestByJunimo(level, pos, mature, 100, stack -> {});
                    helper.assertTrue(fruit.is(ModItems.VANILLA_CATEGORY_ITEMS.get("qi_fruit").get())
                            && fruit.getCount() == 1 && QualityHelper.getQuality(fruit) == QualityHelper.NORMAL,
                            "Qi fruit item, amount or mandatory normal quality differs from source");
                } else helper.assertTrue(crop.tryHarvestByHand(level, pos, mature, null), "Qi fruit hand harvest failed");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Qi fruit regrew or left an upper carrier");
            }
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        // The fixture prepares phases explicitly; natural valley day ticks and Qi challenge rules are separate.
        helper.succeed();
    }
}
