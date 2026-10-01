package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.FarmTwigBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.event.WildWeedSeasonEvents;
import com.stardew.craft.farm.FarmDebrisDailyService;
import com.stardew.craft.farm.FarmDebrisPlacementRules;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.manager.PastureGrassGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_farm_debris")
@PrefixGameTestTemplate(false)
public final class FarmDebrisGameTests {
    private FarmDebrisGameTests() {
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void floorsAndOccupiedTilesBlockEveryFarmEcologyEntry(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        FarmInstance farm = new FarmInstance(
                UUID.randomUUID(), "debris-test", "debris-test", 0, origin, FarmType.STANDARD);
        BlockPos ground = origin.offset(4, 0, 4);
        BlockPos place = ground.above();

        level.setBlock(ground, ModBlocks.DIRT.get().defaultBlockState(), 3);
        for (int y = place.getY(); y <= farm.getFarmBoundsMax().getY(); y++) {
            level.removeBlock(new BlockPos(place.getX(), y, place.getZ()), false);
        }
        var bare = FarmDebrisPlacementRules.findBareSurface(
                level, farm, ground.getX(), ground.getZ());
        helper.assertTrue(bare != null && bare.place().equals(place),
                "Natural bare farm dirt was rejected");

        level.setBlock(place, ModBlocks.FLOORING_BLOCK.get().defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findBareSurface(
                        level, farm, ground.getX(), ground.getZ()) == null,
                "Flooring was treated as a bare debris surface");
        helper.assertTrue(!FarmDebrisPlacementRules.canPlaceYoungTree(level, farm, place),
                "Wild tree seed could replace flooring");
        helper.assertTrue(!FarmDebrisPlacementRules.canSpreadDebrisAt(level, farm, place),
                "Daily debris could replace flooring");

        level.removeBlock(place, false);
        level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findBareSurface(
                        level, farm, ground.getX(), ground.getZ()) == null,
                "A player path was treated as natural bare dirt");

        level.setBlock(ground, ModBlocks.DIRT.get().defaultBlockState(), 3);
        level.setBlock(place, Blocks.CHEST.defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findBareSurface(
                        level, farm, ground.getX(), ground.getZ()) == null,
                "An occupied farm tile accepted random debris");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void dailyDebrisUsesFarmTwigsInsteadOfTreeLogs(GameTestHelper helper)
            throws ReflectiveOperationException {
        var method = FarmDebrisDailyService.class.getDeclaredMethod(
                "randomDebrisState", RandomSource.class);
        method.setAccessible(true);
        RandomSource random = RandomSource.create(294295L);
        boolean foundTwig = false;
        for (int i = 0; i < 128; i++) {
            BlockState state = (BlockState) method.invoke(null, random);
            boolean twig = state.getBlock() instanceof FarmTwigBlock;
            boolean stone = state.is(ModBlocks.MINE_STONE_343.get())
                    || state.is(ModBlocks.MINE_STONE_450.get());
            helper.assertTrue(twig || stone,
                    "Daily farm debris still emitted a tree log or unrelated block");
            foundTwig |= twig;
        }
        helper.assertTrue(foundTwig, "Daily farm debris never selected either twig variant");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void automaticTreesUseEveryAuthoredDirtTileButNotLandscape(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8));
        FarmInstance farm = new FarmInstance(
                UUID.randomUUID(), "tree-ground-test", "tree-ground-test", 0,
                helper.absolutePos(new BlockPos(2, 1, 2)), FarmType.STANDARD);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(center.offset(x, 0, z),
                        ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
            }
        }
        level.setBlock(center, ModBlocks.DIRT.get().defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.isAutomaticTreeGround(
                        level.getBlockState(center)),
                "An isolated authored dirt tile rejected automatic trees");

        level.setBlock(center, ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
        helper.assertTrue(!FarmDebrisPlacementRules.isAutomaticTreeGround(
                        level.getBlockState(center)),
                "Unscoped landscape grass accepted an automatic tree");
        helper.assertTrue(FarmDebrisPlacementRules.isAutomaticTreeGround(
                        farm, level.getBlockState(center)),
                "Farm ecology rejected central grass tree growth");
        level.setBlock(center, ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.isAutomaticTreeGround(
                        farm, level.getBlockState(center)),
                "Farm ecology rejected central dark-grass tree growth");
        level.setBlock(center, ModBlocks.HARD_SOIL.get().defaultBlockState(), 3);
        helper.assertTrue(!FarmDebrisPlacementRules.isAutomaticTreeGround(
                        farm, level.getBlockState(center)),
                "Landscape hard soil accepted an initial tree");
        level.setBlock(center, ModBlocks.YELLOW_DIRT.get().defaultBlockState(), 3);
        helper.assertTrue(!FarmDebrisPlacementRules.isAutomaticTreeGround(
                        farm, level.getBlockState(center)),
                "Building yellow dirt accepted an initial tree");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void beachSandIsFarmableOnlyForBeachEcology(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        FarmInstance beach = new FarmInstance(
                UUID.randomUUID(), "beach-test", "beach-test", 0, origin, FarmType.BEACH);
        FarmInstance standard = new FarmInstance(
                UUID.randomUUID(), "standard-test", "standard-test", 0, origin, FarmType.STANDARD);
        BlockPos ground = origin.offset(5, 0, 5);
        BlockPos place = ground.above();
        level.setBlock(ground, ModBlocks.SAND.get().defaultBlockState(), 3);
        for (int y = place.getY(); y <= beach.getFarmBoundsMax().getY(); y++) {
            level.removeBlock(new BlockPos(place.getX(), y, place.getZ()), false);
        }

        BlockState sand = level.getBlockState(ground);
        helper.assertTrue(FarmDebrisPlacementRules.isBareFarmableGround(beach, sand),
                "Beach sand was excluded from beach debris and pasture ecology");
        helper.assertTrue(!FarmDebrisPlacementRules.isBareFarmableGround(standard, sand),
                "Decorative sand became farmable on non-beach layouts");
        helper.assertTrue(FarmDebrisPlacementRules.canPlaceYoungTree(level, beach, place)
                        && !FarmDebrisPlacementRules.canPlaceYoungTree(level, standard, place),
                "Beach sand tree rules leaked to another farm type");
        helper.assertTrue(FarmDebrisPlacementRules.canSpreadDebrisAt(level, beach, place)
                        && !FarmDebrisPlacementRules.canSpreadDebrisAt(level, standard, place),
                "Beach sand debris spreading leaked to another farm type");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void pastureParityKeepsLandscapeSeparateAndUsesOriginalRanges(
            GameTestHelper helper
    ) throws ReflectiveOperationException {
        helper.assertTrue(FarmDebrisPlacementRules.isBareFarmableGround(
                        ModBlocks.DIRT.get().defaultBlockState()),
                "Authored farm dirt lost its Diggable-equivalent role");
        helper.assertTrue(!FarmDebrisPlacementRules.isBareFarmableGround(
                        ModBlocks.GRASS_BLOCK.get().defaultBlockState())
                        && !FarmDebrisPlacementRules.isBareFarmableGround(
                        ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState()),
                "Landscape grass was still treated as farmable dirt");
        helper.assertTrue(!ModBlocks.PASTURE_GRASS.get().defaultBlockState().isRandomlyTicking()
                        && !ModBlocks.BLUE_PASTURE_GRASS.get().defaultBlockState().isRandomlyTicking(),
                "Pasture could still disappear or grow through Minecraft random ticks");
        helper.assertTrue(!ModBlocks.WILD_WEEDS.get().defaultBlockState().isRandomlyTicking(),
                "Existing weeds could still change season through Minecraft random ticks");

        var attempts = PastureGrassGrowthManager.class.getDeclaredMethod(
                "rollDailyGrassAttempts", RandomSource.class);
        attempts.setAccessible(true);
        RandomSource attemptRandom = RandomSource.create(511L);
        boolean sawFive = false;
        boolean sawEleven = false;
        for (int i = 0; i < 256; i++) {
            int value = (int) attempts.invoke(null, attemptRandom);
            helper.assertTrue(value >= 5 && value <= 11,
                    "Daily pasture attempt count escaped the original 5-11 range");
            sawFive |= value == 5;
            sawEleven |= value == 11;
        }
        helper.assertTrue(sawFive && sawEleven,
                "Daily pasture attempt range did not include both original endpoints");

        var grow = PastureGrassGrowthManager.class.getDeclaredMethod(
                "growClumpCountForDay", int.class, RandomSource.class);
        grow.setAccessible(true);
        RandomSource growthRandom = RandomSource.create(134L);
        for (int clumps = 1; clumps <= 3; clumps++) {
            int grown = (int) grow.invoke(null, clumps, growthRandom);
            helper.assertTrue(grown > clumps && grown <= 4,
                    "Partial pasture did not gain the original 1-3 daily clumps");
        }
        helper.assertTrue((int) grow.invoke(null, 4, growthRandom) == 4,
                "Full pasture exceeded four clumps");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void weedsSwitchSeasonalModelFamilyWithoutChangingVariant(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos weed = ground.above();
        level.setBlock(ground, ModBlocks.DIRT.get().defaultBlockState(), 3);
        level.setBlock(weed, ModBlocks.WILD_WEEDS.get().defaultBlockState()
                .setValue(WildWeedsBlock.SEASON, 0)
                .setValue(WildWeedsBlock.VARIANT, 2), 3);

        WildWeedsBlock.refreshChunkWeedsForSeason(level, level.getChunkAt(weed), 2);

        BlockState refreshed = level.getBlockState(weed);
        helper.assertTrue(refreshed.getValue(WildWeedsBlock.SEASON) == 2,
                "Loaded weeds did not switch to the current seasonal model family");
        helper.assertTrue(refreshed.getValue(WildWeedsBlock.VARIANT) == 2,
                "Season refresh replaced the individual weed variant");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void legacySeasonalWeedIdsMigrateToUnifiedBlock(
            GameTestHelper helper
    ) {
        CompoundTag data = new CompoundTag();
        CompoundTag section = new CompoundTag();
        CompoundTag blockStates = new CompoundTag();
        ListTag palette = new ListTag();
        CompoundTag legacyPaletteEntry = new CompoundTag();
        legacyPaletteEntry.putString("Name", "stardewcraft:wild_weeds_summer_2");
        palette.add(legacyPaletteEntry);
        blockStates.put("palette", palette);
        section.put("block_states", blockStates);
        ListTag sections = new ListTag();
        sections.add(section);
        data.put("sections", sections);

        ListTag blockEntities = new ListTag();
        CompoundTag legacyEntity = new CompoundTag();
        legacyEntity.putString("id", "stardewcraft:wild_weeds_summer_2");
        blockEntities.add(legacyEntity);
        data.put("block_entities", blockEntities);

        int changed = WildWeedSeasonEvents.migrateLegacyChunkData(data);
        CompoundTag migratedPalette = data.getList("sections", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getCompound("block_states")
                .getList("palette", Tag.TAG_COMPOUND)
                .getCompound(0);
        CompoundTag migratedProperties = migratedPalette.getCompound("Properties");

        helper.assertTrue(changed == 2, "Legacy weed palette and block entity were not both migrated");
        helper.assertTrue("stardewcraft:wild_weeds".equals(migratedPalette.getString("Name")),
                "Legacy seasonal weed block ID survived migration");
        helper.assertTrue("1".equals(migratedProperties.getString("season"))
                        && "2".equals(migratedProperties.getString("variant")),
                "Legacy seasonal weed state was not preserved");
        helper.assertTrue("stardewcraft:wild_weeds".equals(
                        data.getList("block_entities", Tag.TAG_COMPOUND)
                                .getCompound(0).getString("id")),
                "Legacy seasonal weed block entity ID survived migration");
        helper.succeed();
    }
}
