package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.crop.SummerSquashCropBlock;
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

@GameTestHolder("stardewcraft_summer_squash")
@PrefixGameTestTemplate(false)
public final class SummerSquashCropGameTests {
    private SummerSquashCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_summer_squash", template = "ring_utilities")
    public static void sourceStagesRegrowthAndTallSelection(GameTestHelper helper) {
        var level = helper.getLevel();
        var crop = (SummerSquashCropBlock) ModBlocks.SUMMER_SQUASH_CROP.get();
        var clock = StardewTimeManager.get();
        var player = FakePlayerFactory.getMinecraft(level);
        int oldSeason = clock.getCurrentSeason();
        var oldHand = player.getMainHandItem();
        BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5)), pos = soil.above();
        try {
            var noWorldReads = (net.minecraft.world.level.BlockGetter) java.lang.reflect.Proxy.newProxyInstance(
                    SummerSquashCropGameTests.class.getClassLoader(),
                    new Class<?>[]{net.minecraft.world.level.BlockGetter.class},
                    (proxy, method, args) -> { throw new AssertionError("Crop lighting queried world: " + method.getName()); });
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (!(block instanceof StardewCropBlock)) continue;
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    helper.assertTrue(state.propagatesSkylightDown(noWorldReads, pos)
                            && state.getLightBlock(noWorldReads, pos) == 0, "Crop selection bounds affected lighting");
                }
            }
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{1, 1, 1, 1, 2}), "Source growth schedule differs");
            helper.assertTrue(crop.getRegrowDaysForDisplay() == 3, "Source three-day regrowth differs");
            level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
            var seed = ModItems.SUMMER_SQUASH_SEEDS.get();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
            clock.setCurrentSeason(0);
            helper.assertTrue(!seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 2, "Spring planting consumed seed");
            clock.setCurrentSeason(1);
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 2
                    && level.getBlockState(pos.above()).is(Blocks.STONE), "Planting replaced obstruction");
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 1, "Summer planting failed");
            var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            growth.sourcePhases = true;
            growth.sourcePhaseVersion = 0;
            growth.phase = 2;
            growth.dayInPhase = 1;
            growth.regrowing = false;
            level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1), 2);
            crop.syncVisualStage(level, pos, growth);
            helper.assertTrue(growth.phase == 3 && growth.dayInPhase == 0 && growth.sourcePhaseVersion == 1, "Legacy elapsed days lost");
            crop.syncVisualStage(level, pos, growth);
            helper.assertTrue(growth.phase == 3 && growth.dayInPhase == 0, "Migration applied twice");
            for (int phase = 0; phase <= 5; phase++) {
                growth.phase = phase;
                growth.dayInPhase = 0;
                level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, phase == 5 ? 3 : phase == 0 ? 0 : 1), 2);
                crop.syncVisualStage(level, pos, growth);
                for (var part : new BlockPos[]{pos, pos.above()}) {
                    var current = level.getBlockState(part);
                    helper.assertTrue(current.is(crop) && current.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1, "Stage carriers differ");
                    helper.assertTrue(current.getCollisionShape(level, part).isEmpty(), "Ordinary crop blocks movement");
                }
                helper.assertTrue(!level.getBlockState(pos).getShape(level, pos).isEmpty(), "Missing lower model selection");
                CropSelectionAssertions.matchesStage(helper, pos);
            }
            for (boolean junimo : new boolean[]{false, true}) {
                growth.phase = 5;
                growth.dayInPhase = 0;
                growth.regrowing = false;
                var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3).setValue(StardewCropBlock.GROWTH_STAGE, 6);
                level.setBlock(pos, mature, 2);
                level.setBlock(pos.above(), mature.setValue(SummerSquashCropBlock.HALF, DoubleBlockHalf.UPPER), 2);
                if (junimo) {
                    var fruit = crop.tryHarvestByJunimo(level, pos, mature, 0, stack -> {});
                    helper.assertTrue(fruit.is(ModItems.SUMMER_SQUASH.get()) && fruit.getCount() == 1, "Wrong regrowing fruit yield");
                } else helper.assertTrue(crop.tryHarvestByHand(level, pos, mature, null), "Hand harvest failed");
                helper.assertTrue(growth.regrowing && growth.dayInPhase == 3, "Harvest did not start three-day regrowth");
                for (var part : new BlockPos[]{pos, pos.above()}) {
                    var current = level.getBlockState(part);
                    helper.assertTrue(current.is(crop) && current.getValue(StardewCropBlock.GROWTH_STAGE) == 8, "Harvest did not restore flowering regrow model");
                }
                helper.assertTrue(!crop.tryHarvestByHand(level, pos, level.getBlockState(pos), null), "Regrowing plant was harvested twice");
            }
            // Simulate a legacy single-block crop under an occupied upper position.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, crop.defaultBlockState().setValue(StardewCropBlock.AGE, 1), 2);
            growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
            growth.sourcePhases = false;
            growth.phase = 2;
            growth.dayInPhase = 1;
            growth.regrowing = false;
            crop.syncVisualStage(level, pos, growth);
            crop.growCropOneDay(level, pos, level.getBlockState(pos), true, growth);
            helper.assertTrue(level.getBlockState(pos.above()).is(Blocks.STONE), "Legacy migration replaced upper obstruction");
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        // Stage preparation is explicit; this fixture does not claim natural valley day-tick coverage.
        helper.succeed();
    }
}
