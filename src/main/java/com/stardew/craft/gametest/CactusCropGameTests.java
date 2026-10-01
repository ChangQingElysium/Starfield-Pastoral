package com.stardew.craft.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.CactusFruitCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.crop.TomatoCropBlock;
import com.stardew.craft.interior.InteriorRegionRegistry;
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

@GameTestHolder("stardewcraft_cactus")
@PrefixGameTestTemplate(false)
public final class CactusCropGameTests {
    private CactusCropGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_cactus", template = "ring_utilities")
    public static void cactusHonorsLocationsInEverySeasonAndRegrowsItsOwnFruit(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        ItemStack oldHand = player.getMainHandItem();
        String oldLocations = InteriorRegionRegistry.getCachedJson();
        try {
            BlockPos soil = helper.absolutePos(new BlockPos(5, 1, 5));
            BlockPos pos = soil.above();
            var crop = (CactusFruitCropBlock) ModBlocks.CACTUS_FRUIT_CROP.get();
            var seed = ModItems.VANILLA_CATEGORY_ITEMS.get("cactus_seeds").get();
            helper.assertTrue(java.util.Arrays.equals(crop.getPhaseDaysForDisplay(), new int[]{2, 2, 2, 3, 3}), "Incorrect twelve-day cactus phases");
            for (int place = 0; place < 3; place++) {
                var root = JsonParser.parseString(oldLocations).getAsJsonObject();
                var location = new JsonObject();
                location.addProperty("dimension", level.dimension().location().toString());
                location.addProperty("ledger_id", "CactusTest");
                location.addProperty("priority", 100000);
                location.addProperty("indoor", place == 1);
                var min = new JsonArray();
                var max = new JsonArray();
                for (int v : new int[]{pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2}) min.add(v);
                for (int v : new int[]{pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2}) max.add(v);
                location.add("min", min);
                location.add("max", max);
                if (place == 2) {
                    var tags = new JsonArray();
                    tags.add("stardewcraft:ginger_island");
                    location.add("tags", tags);
                }
                root.add("stardewcraft:test_cactus_location", location);
                InteriorRegionRegistry.applyFromJson(root.toString());
                for (int season = 0; season < 4; season++) {
                    clock.setCurrentSeason(season);
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    // Include outdoor pots: a pot alone must not bypass the location restriction.
                    var support = place == 1 || (place == 0 && season % 2 == 0)
                            ? ModBlocks.GARDEN_POT.get() : ModBlocks.FARMLAND.get();
                    level.setBlock(soil, support.defaultBlockState(), 3);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
                    var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
                    if (place == 0) {
                        helper.assertTrue(!seed.useOn(context).consumesAction(), "Cactus planted in ordinary outdoors");
                        helper.assertTrue(player.getMainHandItem().getCount() == 2 && level.getBlockState(pos).isAir(), "Denied planting consumed seeds or placed a crop");
                        continue;
                    }
                    level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
                    helper.assertTrue(!seed.useOn(context).consumesAction() && level.getBlockState(pos.above()).is(Blocks.STONE), "Cactus replaced an upper obstruction");
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                    helper.assertTrue(seed.useOn(context).consumesAction(), "Cactus failed in indoor/island location or a permitted season");
                    helper.assertTrue(player.getMainHandItem().getCount() == 1, "Planting did not consume one seed");
                    helper.assertTrue(level.getBlockState(pos).is(crop) && level.getBlockState(pos.above()).is(crop), "Cactus carriers missing");
                    // Valley-only daily ticks are not exercised by this overworld fixture.
                    var growth = CropGrowthManager.get(level).getOrCreateState(level, pos);
                    growth.phase = 5;
                    growth.regrowing = false;
                    growth.dayInPhase = 0;
                    var mature = crop.defaultBlockState().setValue(StardewCropBlock.AGE, 3).setValue(StardewCropBlock.GROWTH_STAGE, 6);
                    level.setBlock(pos, mature, 2);
                    level.setBlock(pos.above(), mature.setValue(TomatoCropBlock.HALF, DoubleBlockHalf.UPPER), 2);
                    helper.assertTrue(mature.getCollisionShape(level, pos).isEmpty() && !mature.getShape(level, pos).isEmpty(), "Cactus movement or selection differs from ordinary crops");
                    if (season % 2 == 0) {
                        var drops = new ArrayList<ItemStack>();
                        var fruit = crop.tryHarvestByJunimo(level, pos, mature, 10, drops::add);
                        helper.assertTrue(fruit.is(ModItems.VANILLA_CATEGORY_ITEMS.get("cactus_fruit").get()) && fruit.getCount() == 1, "Cactus harvested the wrong fruit or count");
                    } else helper.assertTrue(crop.tryHarvestByHand(level, pos, mature, null), "Cactus hand harvest failed");
                    helper.assertTrue(growth.regrowing && growth.dayInPhase == 3, "Cactus did not begin three-day regrowth");
                    for (BlockPos part : new BlockPos[]{pos, pos.above()}) {
                        var state = level.getBlockState(part);
                        helper.assertTrue(state.is(crop) && state.getValue(StardewCropBlock.AGE) == 2
                                && state.getValue(StardewCropBlock.GROWTH_STAGE) == 8, "Cactus regrowth halves differ");
                    }
                }
            }
        } finally {
            InteriorRegionRegistry.applyFromJson(oldLocations);
            clock.setCurrentSeason(oldSeason);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        helper.succeed();
    }
}
