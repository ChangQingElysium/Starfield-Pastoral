package com.stardew.craft.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.FiberCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.interior.InteriorRegionRegistry;
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

@GameTestHolder("stardewcraft_fiber")
@PrefixGameTestTemplate(false)
public final class FiberCropGameTests {
    private FiberCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_fiber", template = "ring_utilities")
    public static void fiberTracksEverySeasonAndLocationWithoutBlockingPlayers(GameTestHelper helper) {
        var level = helper.getLevel();
        var crop = (FiberCropBlock) ModBlocks.FIBER_CROP.get();
        var clock = StardewTimeManager.get();
        var player = FakePlayerFactory.getMinecraft(level);
        int oldSeason = clock.getCurrentSeason();
        var oldHand = player.getMainHandItem();
        String oldLocations = InteriorRegionRegistry.getCachedJson();
        BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5)), pos = soil.above();
        try {
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{1, 2, 2, 2}), "Incorrect fiber phase days");
            for (int season = 0; season < 4; season++) {
                clock.setCurrentSeason(season);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
                var seed = ModItems.FIBER_SEEDS.get();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
                helper.assertTrue(seed.useOn(context).consumesAction() && player.getMainHandItem().getCount() == 1, "Fiber cannot plant in every season");
                var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                growth.sourcePhases = true;
                growth.regrowing = false;
                for (int phase = 0; phase <= 4; phase++) {
                    growth.phase = phase;
                    growth.dayInPhase = 0;
                    var state = crop.defaultBlockState().setValue(StardewCropBlock.AGE, phase == 4 ? 3 : phase == 0 ? 0 : 1);
                    level.setBlock(pos, state, 2);
                    crop.syncVisualStage(level, pos, growth);
                    for (var part : new BlockPos[]{pos, pos.above()}) {
                        var current = level.getBlockState(part);
                        helper.assertTrue(current.getValue(FiberCropBlock.SEASON) == (season == 2 ? 1 : season == 3 ? 2 : 0)
                                && current.getValue(StardewCropBlock.GROWTH_STAGE) == phase + 1, "Season or phase differs between halves");
                        helper.assertTrue(current.getCollisionShape(level, part).isEmpty(), "Fiber blocks player movement");
                    }
                    helper.assertTrue(!level.getBlockState(pos).getShape(level, pos).isEmpty(), "Missing model selection bounds");
                    if (phase == 4) helper.assertTrue(!level.getBlockState(pos.above()).getShape(level, pos.above()).isEmpty(), "Missing tall upper selection bounds");
                }
                var mature = level.getBlockState(pos);
                helper.assertTrue(!crop.tryHarvestByHand(level, pos, mature, null), "Scythe crop harvested by bare hand");
                var outputs = new java.util.ArrayList<ItemStack>();
                var fruit = crop.tryHarvestByJunimo(level, pos, mature, 10, stack -> outputs.add(stack.copy()));
                helper.assertTrue(fruit.is(ModItems.FIBER.get()) && fruit.getCount() == 1
                        && QualityHelper.getQuality(fruit) == QualityHelper.NORMAL, "Fiber primary item or normal quality changed");
                helper.assertTrue(outputs.stream().allMatch(stack -> stack.is(ModItems.FIBER.get())
                                && QualityHelper.getQuality(stack) == QualityHelper.NORMAL)
                        && outputs.stream().mapToInt(ItemStack::getCount).sum() >= 4,
                        "Fiber item, minimum stack or normal quality changed");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Fiber harvest left a regrowing carrier");
            }

            clock.setCurrentSeason(3);
            // Test local summer contexts and an explicit map-season override independently of global winter.
            for (int kind = 0; kind < 3; kind++) {
                var root = JsonParser.parseString(oldLocations).getAsJsonObject();
                var location = new JsonObject();
                location.addProperty("dimension", level.dimension().location().toString());
                location.addProperty("ledger_id", kind == 1 ? "Desert" : "FiberSeasonTest");
                location.addProperty("priority", 100000);
                var min = new JsonArray(); var max = new JsonArray();
                for (int v : new int[]{pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2}) min.add(v);
                for (int v : new int[]{pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2}) max.add(v);
                location.add("min", min); location.add("max", max);
                if (kind == 0) {
                    var tags = new JsonArray(); tags.add("stardewcraft:ginger_island"); location.add("tags", tags);
                } else if (kind == 2) {
                    var properties = new JsonObject(); properties.addProperty("stardewcraft:season_override", "Fall"); location.add("properties", properties);
                }
                root.add("stardewcraft:test_fiber_season", location);
                InteriorRegionRegistry.applyFromJson(root.toString());
                level.setBlock(pos, crop.defaultBlockState(), 3);
                var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                crop.syncVisualStage(level, pos, growth);
                helper.assertTrue(level.getBlockState(pos).getValue(FiberCropBlock.SEASON) == (kind == 2 ? 1 : 0), "Location season did not override global winter");
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        } finally {
            InteriorRegionRegistry.applyFromJson(oldLocations);
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        // This overworld fixture checks explicit stage synchronization, not valley daily growth ticks.
        helper.succeed();
    }
}
