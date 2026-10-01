package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.RiceCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.crop.TaroRootCropBlock;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_taro")
@PrefixGameTestTemplate(false)
public final class TaroCropGameTests {
    private TaroCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_taro", template = "ring_utilities")
    public static void plantedTaroUsesPaddyRulesAndHarvestsItsOwnRoot(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        ItemStack oldHand = player.getMainHandItem();
        try {
            clock.setCurrentSeason(1);
            for (boolean paddy : new boolean[]{false, true}) {
                BlockPos soil = helper.absolutePos(new BlockPos(paddy ? 12 : 4, 1, paddy ? 12 : 4));
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                    for (int y = 0; y <= 3; y++) level.setBlock(soil.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 0), 3);
                if (paddy) level.setBlock(soil.east(3), Blocks.WATER.defaultBlockState(), 3);
                var seed = ModItems.VANILLA_CATEGORY_ITEMS.get("taro_tuber").get();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
                helper.assertTrue(seed.useOn(context).consumesAction(), "Taro tuber did not plant");
                BlockPos pos = soil.above();
                var crop = (TaroRootCropBlock) ModBlocks.TARO_ROOT_CROP.get();
                helper.assertTrue(level.getBlockState(pos).is(crop) && level.getBlockState(pos.above()).is(crop), "Taro carriers missing");
                helper.assertTrue(player.getMainHandItem().getCount() == 1, "Planting did not consume exactly one tuber");
                helper.assertTrue(!level.getBlockState(pos).getValue(RiceCropBlock.WATERLOGGED), "Taro incorrectly contains water");
                if (paddy) helper.assertTrue(level.getBlockState(soil).getValue(FarmBlock.MOISTURE) == 7, "Paddy did not water soil");
                var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                // GameTestServer only loads the overworld; daily crop ticks are valley-only.
                // Exercise the exact phase calculation without bypassing that production gate.
                int[] phases = crop.getPhaseDaysForDisplay();
                helper.assertTrue(java.util.Arrays.equals(phases, new int[]{1, 2, 3, 4}), "Taro inherited rice phases");
                var bonusMethod = RiceCropBlock.class.getDeclaredMethod("getAdditionalSpeedBoost",
                        net.minecraft.server.level.ServerLevel.class, BlockPos.class, CropGrowthManager.CropGrowthState.class);
                bonusMethod.setAccessible(true);
                float bonus = (float) bonusMethod.invoke(crop, level, pos, growth);
                helper.assertTrue(bonus == (paddy ? .25f : 0f), "Incorrect paddy speed bonus");
                var phaseCalculation = StardewCropBlock.class.getDeclaredMethod("applySpeedGroToPhaseDays", int[].class, float.class);
                phaseCalculation.setAccessible(true);
                int days = java.util.Arrays.stream((int[]) phaseCalculation.invoke(crop, phases, bonus)).sum();
                helper.assertTrue(paddy ? days < 10 : days == 10, "Incorrect calculated taro maturation time");
                growth.phase = 4;
                level.setBlock(pos, level.getBlockState(pos).setValue(StardewCropBlock.AGE, 3)
                        .setValue(StardewCropBlock.GROWTH_STAGE, 5), 2);
                level.setBlock(pos.above(), level.getBlockState(pos).setValue(RiceCropBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                var state = level.getBlockState(pos);
                helper.assertTrue(state.getCollisionShape(level, pos).isEmpty(), "Taro blocks player movement");
                helper.assertTrue(!state.getShape(level, pos).isEmpty(), "Taro cannot be selected");
                var harvested = new ArrayList<ItemStack>();
                if (paddy) {
                    var root = crop.tryHarvestByJunimo(level, pos, state, 10, harvested::add);
                    helper.assertTrue(root.is(ModItems.VANILLA_CATEGORY_ITEMS.get("taro_root").get()) && root.getCount() == 1,
                            "Taro harvested rice or inherited rice's extra yield");
                } else helper.assertTrue(crop.tryHarvestByHand(level, pos, state, null), "Taro incorrectly requires a scythe");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Taro regrew or left an upper carrier");
            }
        } finally {
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        helper.succeed();
    }
}
