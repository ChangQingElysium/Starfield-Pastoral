package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.TeaBushManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

@GameTestHolder("stardewcraft_tea_growth")
@PrefixGameTestTemplate(false)
public final class TeaBushGrowthGameTests {
    private TeaBushGrowthGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_tea_growth", template = "ring_utilities")
    public static void plantingDayAndHarvestSurviveSaveReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var clock = StardewTimeManager.get();
        int[] previous = date(clock);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        try {
            setDate(clock, 1, 0, 1);
            plant(helper, pos);
            var manager = TeaBushManager.get(level);
            setDate(clock, 1, 0, 11);
            var saved = manager.save(new CompoundTag(), level.registryAccess());
            var restored = TeaBushManager.load(saved.copy(), level.registryAccess());
            helper.assertTrue(restored.getAgeDays(level, pos) == 10,
                    "Saving and reopening discarded or reset the planting day");
            helper.assertTrue(restored.save(new CompoundTag(), level.registryAccess()).equals(saved),
                    "Reading the same day mutated persisted planting records");
            restored.growDaily(level);
            assertStage(helper, pos, 1);

            setDate(clock, 1, 0, 21);
            restored.growDaily(level);
            helper.assertTrue(restored.getAgeDays(level, pos) == 20 && !restored.isReadyForHarvest(level, pos),
                    "Tea must mature after 20 days and wait for the 22nd to produce leaves");
            assertStage(helper, pos, 2);
            setDate(clock, 1, 0, 22);
            restored.growDaily(level);
            assertStage(helper, pos, 3);
            helper.assertTrue(restored.harvest(level, pos), "Mature tea did not produce on the 22nd");
            restored = TeaBushManager.load(restored.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            helper.assertTrue(restored.getAgeDays(level, pos) == 21 && !restored.harvest(level, pos),
                    "Reopening reset the age or allowed the same day's second harvest");
            setDate(clock, 1, 0, 23);
            restored.growDaily(level);
            helper.assertTrue(restored.harvest(level, pos), "Next day did not restore one tea leaf harvest");
            setDate(clock, 1, 1, 1);
            restored.growDaily(level);
            helper.assertTrue(!restored.harvest(level, pos), "Tea produced outside days 22–28");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            setDate(clock, previous[0], previous[1], previous[2]);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tea_growth", template = "ring_utilities")
    public static void legacyCompoundCoordinatesKeepTheOriginalAge(GameTestHelper helper) {
        var level = helper.getLevel();
        var clock = StardewTimeManager.get();
        int[] previous = date(clock);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        try {
            setDate(clock, 1, 0, 22);
            plant(helper, pos);
            CompoundTag legacyPos = new CompoundTag();
            legacyPos.putInt("X", pos.getX());
            legacyPos.putInt("Y", pos.getY());
            legacyPos.putInt("Z", pos.getZ());
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", level.dimension().location().toString());
            entry.put("Pos", legacyPos);
            entry.putInt("PlantedDay", 1);
            entry.putInt("HarvestedDay", 22);
            ListTag entries = new ListTag();
            entries.add(entry);
            CompoundTag legacy = new CompoundTag();
            legacy.put("Bushes", entries);
            var restored = TeaBushManager.load(legacy, level.registryAccess());
            helper.assertTrue(restored.getAgeDays(level, pos) == 21 && !restored.harvest(level, pos),
                    "Legacy coordinate recovery lost planting or harvest date");
            var migrated = restored.save(new CompoundTag(), level.registryAccess()).getList("Bushes", Tag.TAG_COMPOUND);
            helper.assertTrue(migrated.size() == 1 && migrated.getCompound(0).contains("Pos", Tag.TAG_INT_ARRAY),
                    "Legacy record did not migrate to the native 1.21 coordinate format");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            setDate(clock, previous[0], previous[1], previous[2]);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tea_growth", template = "ring_utilities")
    public static void missingRegistrationsRecoverOnceFromSavedStages(GameTestHelper helper) {
        var level = helper.getLevel();
        var clock = StardewTimeManager.get();
        int[] previous = date(clock);
        BlockPos young = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos growing = young.east(2);
        try {
            setDate(clock, 1, 0, 11);
            plant(helper, young);
            plant(helper, growing);
            var state = level.getBlockState(growing).setValue(TeaBushBlock.STAGE, 1);
            level.setBlock(growing, state, Block.UPDATE_ALL);
            level.setBlock(growing.above(), state.setValue(TeaBushBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            var recovered = new TeaBushManager();
            recovered.synchronizeChunk(level, level.getChunkAt(young));
            recovered.synchronizeChunk(level, level.getChunkAt(growing));
            helper.assertTrue(recovered.getAgeDays(level, young) == 0 && recovered.getAgeDays(level, growing) == 10,
                    "Missing records invented unsupported growth or lost the saved stage");
            recovered = TeaBushManager.load(recovered.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            setDate(clock, 1, 0, 12);
            recovered.synchronizeChunk(level, level.getChunkAt(young));
            recovered.synchronizeChunk(level, level.getChunkAt(growing));
            helper.assertTrue(recovered.getAgeDays(level, young) == 1 && recovered.getAgeDays(level, growing) == 11,
                    "Chunk reload reset recovered planting days");
            setDate(clock, 1, 0, 22);
            recovered.growDaily(level);
            helper.assertTrue(recovered.harvest(level, growing) && !recovered.harvest(level, young),
                    "Recovered bushes failed to grow or an unsupported young bush matured immediately");
        } finally {
            level.setBlock(young, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(growing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            setDate(clock, previous[0], previous[1], previous[2]);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tea_growth", template = "ring_utilities")
    public static void winterGrowthContinuesButHarvestRequiresShelter(GameTestHelper helper) {
        var level = helper.getLevel();
        var clock = StardewTimeManager.get();
        int[] previous = date(clock);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var sheltered = new AtomicBoolean(false);
        SeasonLocationRules.registerIgnoreSeasonsRule((world, at) -> sheltered.get() && world == level && at.equals(pos));
        try {
            setDate(clock, 1, 3, 1);
            plant(helper, pos);
            var manager = TeaBushManager.get(level);
            setDate(clock, 1, 3, 22);
            manager.growDaily(level);
            assertStage(helper, pos, 2);
            helper.assertTrue(manager.getAgeDays(level, pos) == 21 && !manager.harvest(level, pos),
                    "Winter stopped growth or exposed tea produced leaves");
            sheltered.set(true);
            manager.synchronize(level, pos);
            assertStage(helper, pos, 3);
            helper.assertTrue(level.getBlockState(pos).getValue(TeaBushBlock.SEASON) == 0 && manager.harvest(level, pos),
                    "Sheltered winter tea did not retain spring appearance and produce leaves");
            setDate(clock, 1, 3, 23);
            manager.growDaily(level);
            helper.assertTrue(manager.harvest(level, pos), "Sheltered winter tea did not produce daily");
        } finally {
            sheltered.set(false);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            setDate(clock, previous[0], previous[1], previous[2]);
        }
        helper.succeed();
    }

    private static void plant(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        level.setBlock(pos.below(), ModBlocks.DIRT.get().defaultBlockState(), Block.UPDATE_ALL);
        var sapling = new ItemStack(ModItems.TEA_SAPLING.get(), 2);
        var context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, sapling,
                new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
        helper.assertTrue(sapling.useOn(context).consumesAction() && sapling.getCount() == 1,
                "Tea sapling planting failed to register the bush");
        assertStage(helper, pos, 0);
    }

    private static void assertStage(GameTestHelper helper, BlockPos pos, int expected) {
        for (var part : new BlockPos[]{pos, pos.above()}) {
            var state = helper.getLevel().getBlockState(part);
            helper.assertTrue(state.is(ModBlocks.TEA_BUSH.get()) && state.getValue(TeaBushBlock.STAGE) == expected,
                    "Tea bush parts did not synchronize to stage " + expected);
        }
    }

    private static int[] date(StardewTimeManager clock) {
        return new int[]{clock.getCurrentYear(), clock.getCurrentSeason(), clock.getCurrentDay()};
    }

    private static void setDate(StardewTimeManager clock, int year, int season, int day) {
        clock.setCurrentYear(year);
        clock.setCurrentSeason(season);
        clock.setCurrentDay(day);
    }
}
