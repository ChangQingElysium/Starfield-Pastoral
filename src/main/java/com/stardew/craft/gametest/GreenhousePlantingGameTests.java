package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.item.MixedSeedsItem;
import com.stardew.craft.item.WildSeedsItem;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.mining.StructureLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder("stardewcraft_greenhouse_planting")
@PrefixGameTestTemplate(false)
public final class GreenhousePlantingGameTests {
    private GreenhousePlantingGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_greenhouse_planting", template = "construction_site", timeoutTicks = 400)
    public static void everySeedPlantsAcrossTheActualBedWithoutBreakingAdjacentCrops(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(6, 0, 6));
        h.assertTrue(StructureLoader.loadAndPlaceWithResult(level, GreenhouseManager.INTERIOR_STRUCTURE_PATH, origin),
                "Shipped greenhouse did not load");
        var soils = new ArrayList<BlockPos>();
        for (int z = 0; z < 20; z++) for (int x = 0; x < 19; x++) {
            var soil = origin.offset(x, 0, z);
            if (level.getBlockState(soil).is(ModBlocks.DIRT.get())) {
                h.assertTrue(level.getBlockState(soil.above()).isAir() && level.getBlockState(soil.above(2)).isAir(),
                        "Authored bed has an invisible or solid obstruction at " + soil.subtract(origin));
                soils.add(soil);
                level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        h.assertTrue(soils.size() == 120, "Expected the original 120 planting cells");
        var enabled = new AtomicBoolean(true);
        SeasonLocationRules.registerIgnoreSeasonsRule((world, pos) -> enabled.get() && world == level
                && pos.getX() >= origin.getX() && pos.getX() < origin.getX() + 19
                && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + 20);
        var player = FakePlayerFactory.getMinecraft(level);
        var oldHand = player.getMainHandItem();
        String oldLocations = indoorFixture(level, origin);
        int species = 0;
        var clock = com.stardew.craft.time.StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        try {
            for (int season = 0; season < 4; season++) {
                clock.setCurrentSeason(season);
                for (var seed : BuiltInRegistries.ITEM) {
                    if (!cropSeed(seed)
                            && !(seed instanceof MixedSeedsItem || seed instanceof WildSeedsItem
                                || seed instanceof com.stardew.craft.item.MixedFlowerSeedsItem)) continue;
                    for (var soil : soils) {
                        var stack = new ItemStack(seed, 2);
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                        var planted = player.getMainHandItem().useOn(context(player, soil));
                        var root = level.getBlockState(soil.above());
                        h.assertTrue(planted.consumesAction() && stack.getCount() == 1
                                        && root.getBlock() instanceof StardewCropBlock && root.canSurvive(level, soil.above()),
                                "Planting failed or left an invalid root: " + BuiltInRegistries.ITEM.getKey(seed) + " at " + soil.subtract(origin));
                    }
                    // Full neighbor updates and breaking one column must not remove the next plant.
                    var first = com.stardew.craft.port.PortJava.getFirst(soils).above();
                    var next = first.east();
                    for (var soil : soils) {
                        var at = soil.above();
                        level.updateNeighborsAt(at, level.getBlockState(at).getBlock());
                        h.assertTrue(level.getBlockState(at).getBlock() instanceof StardewCropBlock,
                                "Neighbor planting removed " + seed + " at " + soil.subtract(origin));
                    }
                    var neighbor = level.getBlockState(next);
                    level.destroyBlock(first, false);
                    h.assertTrue(level.getBlockState(next).equals(neighbor), "Breaking one plant removed its horizontal neighbor: " + seed);
                    for (var soil : soils) {
                        level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        CropGrowthManager.get(level).removeCrop(level, soil.above());
                    }
                    species++;
                }
            }
            h.assertTrue(species >= 160, "Seed matrix coverage unexpectedly small: " + species);
            com.stardew.craft.StardewCraft.LOGGER.info("[GREENHOUSE-TEST] Verified {} seed/season combinations across 120 bed cells", species);
        } finally {
            clock.setCurrentSeason(oldSeason);
            com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(oldLocations);
            enabled.set(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_greenhouse_planting", template = "construction_site")
    public static void upperObstructionsNeverConsumeSeedsOrLeaveInvalidPlants(GameTestHelper h) {
        var level = h.getLevel();
        var soil = h.absolutePos(new BlockPos(8, 1, 8));
        var enabled = new AtomicBoolean(true);
        SeasonLocationRules.registerIgnoreSeasonsRule((world, pos) -> enabled.get() && world == level && pos.equals(soil.above()));
        var player = FakePlayerFactory.getMinecraft(level);
        var oldHand = player.getMainHandItem();
        String oldLocations = indoorFixture(level, soil.below());
        int tall = 0;
        try {
            level.setBlock(soil, ModBlocks.FARMLAND.get().defaultBlockState(), Block.UPDATE_ALL);
            for (var seed : BuiltInRegistries.ITEM) {
                if (!cropSeed(seed)) continue;
                level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
                h.assertTrue(player.getMainHandItem().useOn(context(player, soil)).consumesAction(), "Open fixture rejected " + seed);
                var root = level.getBlockState(soil.above());
                boolean twoCells = root.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
                level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                for (var obstacle : new Block[]{Blocks.STONE, Blocks.LIGHT}) {
                    var obstruction = obstacle.defaultBlockState();
                    level.setBlock(soil.above(2), obstruction, Block.UPDATE_ALL);
                    var stack = new ItemStack(seed, 2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var result = player.getMainHandItem().useOn(context(player, soil));
                    h.assertTrue(level.getBlockState(soil.above(2)).equals(obstruction), "Seed overwrote upper obstacle: " + seed);
                    if (twoCells) {
                        h.assertTrue(!result.consumesAction() && stack.getCount() == 2 && level.getBlockState(soil.above()).isAir(),
                                "Blocked tall crop consumed a seed or left a doomed root: " + seed);
                    } else {
                        h.assertTrue(result.consumesAction() && stack.getCount() == 1
                                        && level.getBlockState(soil.above()).canSurvive(level, soil.above()),
                                "One-cell crop incorrectly required two empty cells: " + seed);
                        level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                if (twoCells) tall++;
            }
            h.assertTrue(tall >= 10, "Tall-crop coverage unexpectedly small: " + tall);
        } finally {
            com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(oldLocations);
            enabled.set(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            CropGrowthManager.get(level).removeCrop(level, soil.above());
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_greenhouse_planting", template = "construction_site")
    public static void legacyLightRepairPreservesCropsContainersAndPlayerEdits(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(6, 0, 6));
        var light = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15);
        int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        var cache = com.stardew.craft.greenhouse.GreenhouseInteriorCache.get();
        var columns = new ArrayList<BlockPos>();
        for (int z = 0; z < 20; z++) for (int x = 0; x < 19; x++) {
            if (!cache.isPlantingBed(x, z)) continue;
            var pos = origin.offset(x, 2, z);
            columns.add(pos);
            level.setBlock(pos.below(2), ModBlocks.FARMLAND.get().defaultBlockState(), quiet);
            level.setBlock(pos, light, quiet);
            level.setBlock(pos.above(), light, quiet);
        }
        var editedLight = columns.get(0);
        var dimLight = light.setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 7);
        level.setBlock(editedLight, dimLight, quiet);
        var chestPos = columns.get(1);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), quiet);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 17));
        var ceilingEdit = columns.get(2).above();
        level.setBlock(ceilingEdit, Blocks.STONE.defaultBlockState(), quiet);
        var outside = origin.offset(0, 2, 0);
        level.setBlock(outside, light, quiet);
        var cropPos = columns.get(3).below();
        var crop = ModBlocks.TOMATO_CROP.get().defaultBlockState().setValue(StardewCropBlock.AGE, 2);
        level.setBlock(cropPos, crop, quiet);
        var growth = CropGrowthManager.get(level).getState(level, cropPos);
        h.assertTrue(growth != null, "Legacy crop was not registered");
        growth.phase = 2;
        growth.dayInPhase = 1;
        try {
            h.assertTrue(com.stardew.craft.greenhouse.GreenhouseBuildings.migrateInteriorLighting(level, origin) == 118,
                    "Did not repair exactly the untouched historical bed lights");
            h.assertTrue(level.getBlockState(editedLight).equals(dimLight) && level.getBlockEntity(chestPos) == chest
                            && chest.getItem(0).getCount() == 17 && level.getBlockState(ceilingEdit).is(Blocks.STONE)
                            && level.getBlockState(outside).equals(light), "Light repair changed player edits or a container");
            h.assertTrue(level.getBlockState(cropPos).equals(crop) && crop.canSurvive(level, cropPos)
                            && level.getBlockState(cropPos.above()).equals(crop.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER))
                            && CropGrowthManager.get(level).getState(level, cropPos) == growth && growth.phase == 2 && growth.dayInPhase == 1,
                    "Light repair failed to retain and restore the old two-cell crop");
            h.assertTrue(com.stardew.craft.greenhouse.GreenhouseBuildings.migrateInteriorLighting(level, origin) == 0,
                    "Legacy repair was not idempotent");
        } finally { CropGrowthManager.get(level).removeCrop(level, cropPos); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_greenhouse_planting", template = "construction_site")
    public static void authoredTemplateRetainsDecorationAndOwnSoil(GameTestHelper h) throws Exception {
        GreenhouseSoilGameTests.authoredInteriorContainsOwnPlantableDirtWithoutChangingDecoration(h);
    }

    @GameTest(templateNamespace = "stardewcraft_greenhouse_planting", template = "construction_site")
    public static void lightingUpgradeRetainsExistingSoilMigration(GameTestHelper h) {
        GreenhouseSoilGameTests.migratedLegacySoilWorksWithActualHoeAndSeedsWithoutAllowingForeignSoil(h);
    }

    private static boolean cropSeed(net.minecraft.world.item.Item item) {
        if (!item.getClass().getPackageName().startsWith("com.stardew.craft.item.crop")
                || !(item instanceof com.stardew.craft.item.IStardewItem typed)) return false;
        return typed.getItemTypeKey().equals("stardewcraft.type.seed")
                || typed.getItemTypeKey().equals("stardewcraft.type.crop_seed");
    }

    private static String indoorFixture(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        String old = com.stardew.craft.interior.InteriorRegionRegistry.getCachedJson();
        var root = com.google.gson.JsonParser.parseString(old).getAsJsonObject();
        var location = new com.google.gson.JsonObject();
        location.addProperty("dimension", level.dimension().location().toString());
        location.addProperty("ledger_id", "GreenhousePlantingTest");
        location.addProperty("priority", 1000000);
        location.addProperty("indoor", true);
        var min = new com.google.gson.JsonArray();
        var max = new com.google.gson.JsonArray();
        for (int v : new int[]{origin.getX(), origin.getY(), origin.getZ()}) min.add(v);
        for (int v : new int[]{origin.getX() + 19, origin.getY() + 11, origin.getZ() + 20}) max.add(v);
        location.add("min", min);
        location.add("max", max);
        root.add("stardewcraft:test_greenhouse_planting", location);
        com.stardew.craft.interior.InteriorRegionRegistry.applyFromJson(root.toString());
        return old;
    }

    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos soil) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
    }
}
