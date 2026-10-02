package com.stardew.craft.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.TapperBlock;
import com.stardew.craft.blockentity.*;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.production.MachineProductionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MachineProductionGameTests {
    private MachineProductionGameTests() {}
    private static JsonElement json(String value) { return JsonParser.parseString(value); }
    private static ResourceLocation id(String value) { return MachineProductionData.id(value); }

    // Settings are restored synchronously; other tests never observe this test's overrides.
    @GameTest(batch = "production_data", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void solarMinuteChargingPersistsAndFinishesWithinOneDay(GameTestHelper helper) {
        String previous = MachineProductionData.getCachedJson();
        var level = helper.getLevel();
        var time = com.stardew.craft.time.StardewTimeManager.get();
        int previousTime = time.getCurrentTime();
        String previousWeather = com.stardew.craft.weather.WeatherManager.getCurrentWeather(level);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 12, 2));
        try {
            MachineProductionData.reload(Map.of(id("solar_panel"), json("{\"minutes\":120}")));
            time.setCurrentTime(420);
            com.stardew.craft.weather.WeatherManager.setWeather(level, "Sun");
            var state = ModBlocks.SOLAR_PANEL.get().defaultBlockState();
            var solar = new SolarPanelBlockEntity(pos, state);
            solar.setLevel(level);
            SolarPanelBlockEntity.serverTick(level, pos, state, solar);
            helper.assertTrue(!solar.isPaused() && solar.getRemainingAbsMinutes() == 120,
                "Clear-sky panel should start the configured minute cycle");
            time.setCurrentTime(480);
            SolarPanelBlockEntity.serverTick(level, pos, state, solar);
            helper.assertTrue(solar.getRemainingAbsMinutes() == 60, "Panel did not charge during the same day");
            var saved = solar.saveWithoutMetadata();
            MachineProductionData.reload(Map.of(id("solar_panel"), json("{\"minutes\":5}")));
            var restored = new SolarPanelBlockEntity(pos, state);
            restored.setLevel(level);
            restored.load(saved);
            helper.assertTrue(restored.getRemainingAbsMinutes() == 60, "Reload changed an existing panel's progress");
            time.setCurrentTime(540);
            SolarPanelBlockEntity.serverTick(level, pos, state, restored);
            helper.assertTrue(restored.isReady() && restored.getProduct().is(ModItems.BATTERY_PACK.get()),
                "Saved panel should finish after its remaining hour, without waiting for a new day");
            restored.harvestOne();
            SolarPanelBlockEntity.serverTick(level, pos, state, restored);
            helper.assertTrue(restored.getRemainingAbsMinutes() == 5, "Next charge should use the new duration");
        } finally {
            level.removeBlock(pos, false);
            time.setCurrentTime(previousTime);
            com.stardew.craft.weather.WeatherManager.setWeather(level, previousWeather);
            MachineProductionData.applyFromJson(previous);
        }
        helper.succeed();
    }

    @GameTest(batch = "production_data", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void productionOverridesApplyToRealMachinesAndSurviveReload(GameTestHelper helper) {
        String previous = MachineProductionData.getCachedJson();
        var level = helper.getLevel();
        try {
            helper.assertTrue(MachineProductionData.reload(Map.of(
                id("tapper"), json("""
                    {"cycles":{"oak":{"output":"minecraft:diamond","min_count":2,"max_count":2,"duration":{"minutes":30}}}}
                    """),
                id("worm_bin"), json("""
                    {"time_multiplier":0.5,"cycles":{"default":{"duration":{"minutes":120},"min_count":6,"max_count":6}}}
                    """),
                id("heavy_furnace"), json("{\"time_multiplier\":0.5,\"coal_per_batch\":2}"),
                id("cask"), json("{\"time_multiplier\":0.5,\"aging_rates\":{\"minecraft:diamond\":4}}")
            )), "Valid production overrides should apply");
            helper.assertTrue(MachineProductionData.cycle("tapper", "maple") != null, "Partial override erased other trees");
            BlockPos tree = helper.absolutePos(new BlockPos(2, 2, 2));
            level.setBlock(tree.below(), ModBlocks.OAK_ROOT.get().defaultBlockState(), 2 | 16);
            level.setBlock(tree, ModBlocks.OAK_LOG.get().defaultBlockState(), 2 | 16);
            var registry = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(level);
            registry.register(tree.below(), "oak", 1, java.util.Set.of(tree.below(), tree));
            com.stardew.craft.tree.WildTrees.markGeneratedModernTree(level, tree.below(), com.stardew.craft.tree.WildTrees.OAK);
            BlockPos tapperPos = tree.east();
            var tapperState = ModBlocks.TAPPER.get().defaultBlockState().setValue(TapperBlock.FACING, Direction.WEST);
            level.setBlock(tapperPos, tapperState, 2 | 16);
            var tapper = (TapperBlockEntity) level.getBlockEntity(tapperPos);
            helper.assertTrue(tapper != null, "Tapper entity missing");
            tapper.ensureCycleStarted(tapperState);
            helper.assertTrue(tapper.getProduct().is(Items.DIAMOND) && tapper.getProduct().getCount() == 2,
                "Real tapper did not use the configured output");
            helper.assertTrue(tapper.getRemainingAbsMinutes() == 30, "Tapper must allow a sub-day cycle");
            long deadline = tapper.stardewReadyAtAbsoluteMinute();
            var saved = tapper.saveWithoutMetadata();

            var worm = new WormBinBlockEntity(tree, ModBlocks.WORM_BIN.get().defaultBlockState());
            worm.setLevel(level);
            WormBinBlockEntity.serverTick(level, tree, worm.getBlockState(), worm);
            helper.assertTrue(worm.getRemainingAbsMinutes() == 60 && worm.getProduct().getCount() == 6,
                "Passive cycle must apply configured time exactly once and preserve count");

            var furnace = new HeavyFurnaceBlockEntity(tree, ModBlocks.HEAVY_FURNACE.get().defaultBlockState());
            furnace.setLevel(level);
            furnace.insertAutomation(new ItemStack(ModItems.COAL.get(), 2), false);
            var ores = new ItemStack(ModItems.COPPER_ORE.get(), 25);
            helper.assertTrue(furnace.insertAutomation(ores, true).isEmpty(), "Heavy recipe simulation should accept configured fuel");
            helper.assertTrue(furnace.getProduct().isEmpty(), "Simulation must not start a batch");
            helper.assertTrue(furnace.insertAutomation(ores, false).isEmpty(), "Heavy recipe should consume 25 ore");
            helper.assertTrue(furnace.getRemainingAbsMinutes() == 15, "Heavy furnace time multiplier did not apply");
            helper.assertTrue(furnace.getProduct().getCount() >= 5 && furnace.getProduct().getCount() <= 6,
                "Heavy recipe lost its original random yield range");

            var cask = new CaskBlockEntity(tree, ModBlocks.CASK.get().defaultBlockState());
            cask.setLevel(level);
            helper.assertTrue(cask.tryInsert(new ItemStack(Items.DIAMOND), null), "Cask should accept a data-defined aging item");
            helper.assertTrue(Math.abs(cask.getRemainingDaysToNextQuality() - 1.75f) < 0.001f,
                "Cask tooltip must account for configured rate and multiplier");
            cask.advanceDays(2);
            helper.assertTrue(cask.isReady(), "Configured cask should advance quality after two days");

            helper.assertTrue(!MachineProductionData.reload(Map.of(id("tapper"), json("{\"time_multiplier\":-1}"))),
                "Invalid multiplier should reject the candidate");
            helper.assertTrue(MachineProductionData.minutes("heavy_furnace", 30) == 15,
                "Rejected reload modified another machine's active settings");
            helper.assertTrue(MachineProductionData.reload(Map.of(id("tapper"), json("{\"minutes\":5}"))), "Replacement reload failed");
            helper.assertTrue(tapper.stardewReadyAtAbsoluteMinute() == deadline && tapper.getProduct().is(Items.DIAMOND),
                "Reload must not rewrite an in-flight product or deadline");
            var restored = new TapperBlockEntity(tapperPos, tapperState);
            restored.setLevel(level);
            restored.load(saved);
            helper.assertTrue(restored.stardewReadyAtAbsoluteMinute() == deadline && restored.getProduct().getCount() == 2,
                "Save/load must preserve the already selected cycle");
            tapper.advanceDays(1);
            helper.assertTrue(tapper.harvestOne().is(Items.DIAMOND), "Old cycle should still deliver its selected product");
            helper.assertTrue(tapper.getProduct().is(ModItems.OAK_RESIN.get()) && tapper.getRemainingAbsMinutes() == 5,
                "Next tapper cycle must use the replacement configuration");
            level.removeBlock(tapperPos, false);
            registry.unregister(registry.getByRoot(tree.below()));
            level.removeBlock(tree.below(), false);
            level.removeBlock(tree, false);
        } finally {
            MachineProductionData.applyFromJson(previous);
        }
        helper.succeed();
    }

    @GameTest(batch = "production_data", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void morningSemanticsValidationAndSyncAreStable(GameTestHelper helper) {
        String previous = MachineProductionData.getCachedJson();
        try {
            MachineProductionData.reload(Map.of());
            var oak = MachineProductionData.cycle("tapper", "oak");
            helper.assertTrue(oak.deadline(600, "tapper") == 7 * 1600, "Seven nights must finish at the seventh morning, not the eighth");
            helper.assertTrue(!MachineProductionData.reload(Map.of(id("tapper"), json("""
                {"cycles":{"oak":{"duration":{"minutes":30,"mornings":1}}}}
                """))), "Mixed clock units must be rejected");
            helper.assertTrue(!MachineProductionData.reload(Map.of(id("tapper"), json("""
                {"cycles":{"oak":{"output":"missing:unknown_item"}}}
                """))), "Unknown outputs must be rejected");
            helper.assertTrue(!MachineProductionData.reload(Map.of(id("tapper"), json("{\"time_mulitplier\":0.5}"))),
                "Misspelled settings must not silently do nothing");
            MachineProductionData.reload(Map.of(id("keg"), json("{\"time_multiplier\":0.25}")));
            String synced = MachineProductionData.getCachedJson();
            MachineProductionData.reload(Map.of());
            MachineProductionData.applyFromJson(synced);
            helper.assertTrue(MachineProductionData.minutes("keg", 100) == 25, "Server settings did not survive network document replay");
            var recipe = ArtisanRecipeDataManager.getRecipe("heavy_furnace", new ItemStack(ModItems.FIRE_QUARTZ.get())).orElseThrow();
            helper.assertTrue(recipe.consumeCount() == 5 && recipe.outputCount() == 15 && recipe.maxOutputCount() == 20,
                "Heavy furnace bundled recipe must preserve its input and output range");
        } finally {
            MachineProductionData.applyFromJson(previous);
        }
        helper.succeed();
    }
    @GameTest(batch = "production_data", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void machineClockFollowsVanillaAndMigratesLegacyDeadlinesOnce(GameTestHelper helper) {
        // Utility.CalculateMinutesUntilMorning: every machine day totals 1600 minutes whatever the bedtime.
        helper.assertTrue(TimedProductionBlockEntity.minutesUntilMorning(0, 1) == 1600
                && TimedProductionBlockEntity.minutesUntilMorning(960, 1) == 640
                && TimedProductionBlockEntity.minutesUntilMorning(1200, 3) == 3600,
                "Overnight top-up must follow CalculateMinutesUntilMorning");
        helper.assertTrue(recipeMinutes("keg", new ItemStack(ModItems.WHEAT.get())) == 1750
                && recipeMinutes("keg", new ItemStack(ModItems.HOPS.get())) == 2250
                && recipeMinutes("cheese_press", new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(new ResourceLocation(StardewCraft.MODID, "milk")))) == 200,
                "Recipe durations must match Data/Machines.json");
        var raisins = ArtisanRecipeDataManager.getRecipe("dehydrator", new ItemStack(ModItems.GRAPE.get(), 5)).orElseThrow();
        helper.assertTrue(raisins.days() == 1, "Dehydrator must use DaysUntilReady 1");
        helper.assertTrue(MachineProductionData.cycle("bee_house", "default").mornings()
                && MachineProductionData.cycle("bee_house", "default").duration() == 4
                && MachineProductionData.cycle("worm_bin", "default").mornings(),
                "Morning-based machines must not use fixed minutes");

        // Pre-F-1 day 3 at 10:00 (offset 240) and day 3 overnight (offset 1230) keep their moment.
        long legacyDay = 2L * TimedProductionBlockEntity.LEGACY_MINUTES_PER_DAY;
        helper.assertTrue(TimedProductionBlockEntity.migrateLegacyAbsMinute(legacyDay + 240) == 2L * 1600 + 240
                && TimedProductionBlockEntity.migrateLegacyAbsMinute(legacyDay + 1230) == 2L * 1600 + 1400
                && TimedProductionBlockEntity.migrateLegacyAbsMinute(legacyDay + 1260) == 3L * 1600
                && TimedProductionBlockEntity.migrateLegacyAbsMinute(-1) == -1,
                "Legacy deadline must keep its day and clock time");
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var state = ModBlocks.KEG.get().defaultBlockState();
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.put("input", com.stardew.craft.port.PortItemStacks.save(new ItemStack(ModItems.WHEAT.get()), level.registryAccess()));
        legacy.put("product", com.stardew.craft.port.PortItemStacks.save(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(StardewCraft.MODID, "beer"))), level.registryAccess()));
        legacy.putLong("readyAtAbsMinute", legacyDay + 240);
        var keg = new KegBlockEntity(pos, state);
        keg.setLevel(level);
        keg.load(legacy);
        helper.assertTrue(keg.stardewReadyAtAbsoluteMinute() == 2L * 1600 + 240, "Legacy keg deadline was not migrated");
        var saved = keg.saveWithoutMetadata();
        helper.assertTrue(saved.getInt(TimedProductionBlockEntity.TAG_MACHINE_CLOCK) == 1600
                && saved.getLong("readyAtAbsMinute") == 2L * 1600 + 240, "Migrated keg must be saved with the clock marker");
        var restored = new KegBlockEntity(pos, state);
        restored.setLevel(level);
        restored.load(saved);
        helper.assertTrue(restored.stardewReadyAtAbsoluteMinute() == 2L * 1600 + 240, "Marked keg deadline was migrated twice");
        helper.succeed();
    }

    private static int recipeMinutes(String machine, ItemStack input) {
        return ArtisanRecipeDataManager.getRecipe(machine, input).orElseThrow().minutes();
    }
}
