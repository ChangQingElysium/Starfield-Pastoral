package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.crop.SweetGemBerryCropBlock;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_sweet_gem")
@PrefixGameTestTemplate(false)
public final class SweetGemBerryCropGameTests {
    private SweetGemBerryCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_sweet_gem", template = "ring_utilities")
    public static void sweetGemRetainsAllSourcePhasesAndMigratesExistingPlants(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var crop = (SweetGemBerryCropBlock) ModBlocks.SWEET_GEM_BERRY_CROP.get();
        var player = FakePlayerFactory.getMinecraft(level);
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        var oldHand = player.getMainHandItem();
        BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos pos = soil.above();
        try {
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{2, 4, 6, 6, 6}), "Missing source growth phase");
            level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
            var seed = ModItems.RARE_SEED.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
            clock.setCurrentSeason(0);
            helper.assertTrue(!seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 2, "Spring planting accepted or consumed seed");
            clock.setCurrentSeason(2);
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!seed.useOn(context).consumesAction() && level.getBlockState(pos.above()).is(Blocks.STONE), "Planting destroyed upper obstruction");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 1, "Fall planting failed");
            helper.assertTrue(level.getBlockState(pos).is(crop) && level.getBlockState(pos.above()).is(crop), "Missing two-block carriers");

            var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            // This overworld fixture calls migration directly; it does not simulate valley day ticks.
            growth.sourcePhases = true;
            growth.sourcePhaseVersion = 0;
            growth.phase = 2;
            growth.dayInPhase = 8;
            growth.regrowing = false;
            var growing = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1);
            level.setBlock(pos, growing, 2);
            crop.syncVisualStage(level, pos, growth);
            helper.assertTrue(growth.phase == 3 && growth.dayInPhase == 2 && growth.sourcePhaseVersion == 1,
                    "Legacy 14 elapsed days were not preserved across the split phase");
            crop.syncVisualStage(level, pos, growth);
            helper.assertTrue(growth.phase == 3 && growth.dayInPhase == 2, "Migration was not idempotent");

            var sync = SweetGemBerryCropBlock.class.getDeclaredMethod("syncUpper", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
            sync.setAccessible(true);
            for (int phase = 0; phase <= 5; phase++) {
                int age = phase == 5 ? 3 : phase == 0 ? 0 : 1;
                var state = crop.defaultBlockState().setValue(StardewCropBlock.AGE, age)
                        .setValue(StardewCropBlock.GROWTH_STAGE, phase + 1)
                        .setValue(SweetGemBerryCropBlock.MATURE, phase == 5);
                level.setBlock(pos, state, 2);
                sync.invoke(crop, level, pos);
                var upper = level.getBlockState(pos.above());
                helper.assertTrue(upper.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1
                        && upper.getValue(SweetGemBerryCropBlock.MATURE) == (phase == 5), "Upper stage or mature flag did not sync");
                helper.assertTrue(state.getCollisionShape(level, pos).isEmpty() && upper.getCollisionShape(level, pos.above()).isEmpty(),
                        "Ordinary sweet gem crop blocks movement");
                helper.assertTrue(!state.getShape(level, pos).isEmpty(), "Missing lower selection box");
                if (phase >= 4) helper.assertTrue(!upper.getShape(level, pos.above()).isEmpty(), "Tall plant lacks upper selection");
            }

            for (boolean junimo : new boolean[]{false, true}) {
                var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3)
                        .setValue(StardewCropBlock.GROWTH_STAGE, 6).setValue(SweetGemBerryCropBlock.MATURE, true);
                level.setBlock(pos, mature, 2);
                level.setBlock(pos.above(), mature.setValue(SweetGemBerryCropBlock.HALF, DoubleBlockHalf.UPPER), 2);
                growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                growth.phase = 5;
                growth.regrowing = false;
                if (junimo) {
                    var drops = new ArrayList<ItemStack>();
                    var fruit = crop.tryHarvestByJunimo(level, pos, mature, 10, drops::add);
                    helper.assertTrue(fruit.is(ModItems.SWEET_GEM_BERRY.get()) && fruit.getCount() == 1, "Wrong fruit or harvest count");
                } else helper.assertTrue(crop.tryHarvestByHand(level, pos, mature, null), "Hand harvest failed");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Harvest left a carrier or regrowing plant");
            }
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        helper.succeed();
    }
}
