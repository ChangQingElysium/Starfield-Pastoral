package com.stardew.craft.gametest;

import com.stardew.craft.api.v1.agriculture.*;
import com.stardew.craft.api.v1.internal.giant.GiantCropGrowth;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("stardewcraft_giant_growth")
@PrefixGameTestTemplate(false)
public final class GiantCropGrowthGameTests {
    private static final class Fixture {
        final Set<BlockPos> roots = new HashSet<>();
        final List<StardewCropDailyContext> dates = new ArrayList<>();
        final ResourceLocation id = new ResourceLocation("giant_test", UUID.randomUUID().toString());
        final BlockPos anchor;
        boolean irrigatesDuringUpdate;
        Fixture(GameTestHelper h, int age, double chance, boolean outside, boolean malformed) {
            var base = h.absolutePos(new BlockPos(4, 3, 4));
            // The 3x3 crosses both chunk axes, with each root tracked before processing.
            anchor = new BlockPos((base.getX() & ~15) + 15, base.getY(), (base.getZ() & ~15) + 15);
            for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) roots.add(anchor.offset(x, 0, z));
            StardewCropTypes.register(new StardewCropType(id, "block.minecraft.wheat", 8,
                    List.of(BuiltInRegistries.BLOCK.getKey(Blocks.WHEAT)), new StardewCropData(
                    List.of("spring", "summer", "fall", "winter"), List.of(1), -1, 0,
                    new ResourceLocation("stardewcraft:grab"), id, new ResourceLocation("minecraft:wheat_seeds"))), 0,
                    new StardewCropRuntimeAdapter() {
                public StardewCropState inspect(net.minecraft.world.level.LevelReader level, BlockPos pos) {
                    var state = level.getBlockState(pos);
                    return !roots.contains(pos) || !state.is(Blocks.WHEAT) ? null : new StardewCropState(id, pos,
                            StardewCropState.Part.ROOT, state.getValue(CropBlock.AGE), state.getValue(CropBlock.AGE) == 7, List.of(pos.below()));
                }
                public DailyResult growOneDay(net.minecraft.server.level.ServerLevel level, StardewCropState crop, StardewCropDailyContext context) {
                    dates.add(context);
                    if (irrigatesDuringUpdate) level.setBlock(crop.root().below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
                    if (context.watered()) level.setBlock(crop.root(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, Math.min(7, crop.visualStage() + 1)), 18);
                    return DailyResult.CHANGED;
                }
            });
            StardewGiantCrops.register(new StardewGiantCrops.Definition(id, id, 3, 3, 2, chance, true, new StardewGiantCrops.Handler() {
                public boolean allowsOutsideFarm(StardewGiantCrops.Context c) { return outside; }
                public List<StardewGiantCrops.Cell> plan(StardewGiantCrops.Context c, List<StardewCropState> crops) {
                    if (malformed) return List.of(new StardewGiantCrops.Cell(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState()));
                    var plan = new ArrayList<StardewGiantCrops.Cell>();
                    for (int y = 0; y < 2; y++) for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++)
                        plan.add(new StardewGiantCrops.Cell(new BlockPos(x, y, z), Blocks.HAY_BLOCK.defaultBlockState()));
                    return plan;
                }
            }));
            for (var pos : roots) {
                h.getLevel().setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
                h.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 18);
                h.getLevel().setBlock(pos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age), 18);
                StardewCropRuntime.track(h.getLevel(), pos);
            }
        }
        List<GlobalPos> positions(GameTestHelper h) { return roots.stream().map(p -> GlobalPos.of(h.getLevel().dimension(), p)).toList(); }
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void immatureNeighborsCanJoinMatureWateredTriggerAcrossFourChunks(GameTestHelper h) {
        var f = new Fixture(h, 0, 1, true, false);
        h.getLevel().setBlock(f.anchor, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 18);
        var result = GiantCropGrowth.process(h.getLevel(), f.roots, Set.of(f.anchor), 100, false);
        h.assertTrue(result.grown() == 1 && result.spaceCells() == 18, "Cross-chunk crop grid failed or scanned beyond the volume");
        for (var pos : f.roots) h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.HAY_BLOCK)
                && h.getLevel().getBlockState(pos.above()).is(Blocks.HAY_BLOCK), "Incomplete three-dimensional replacement");
        h.assertTrue(CropGrowthManager.get(h.getLevel()).getAllCropPositions().stream().noneMatch(p -> f.roots.contains(p.pos())), "Consumed roots remained scheduled");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void dryTriggerWaitsUntilAnotherDay(GameTestHelper h) {
        var f = new Fixture(h, 7, 1, true, false);
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, Set.of(), 100, false).grown() == 0, "Dry crops grew giant");
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, f.roots, 100, false).rolls() == 0, "Same day was rerolled");
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, f.roots, 101, false).grown() == 1, "Mature crops lost future chances");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void irrigationDuringDailyUpdateCountsForGiantGrowth(GameTestHelper h) {
        var f = new Fixture(h, 7, 1, true, false);
        f.irrigatesDuringUpdate = true;
        for (var pos : f.roots) h.getLevel().setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 18);
        var result = CropGrowthManager.get(h.getLevel()).settleCrops(h.getLevel(), f.positions(h), 100, 3, false);
        h.assertTrue(result.grown() == 1, "Daily self-irrigation was ignored by giant growth");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="empty")
    @SuppressWarnings("unchecked")
    public static void nativeSeasonRulesUseHistoricalDateWithoutChangingWorldClock(GameTestHelper h) throws ReflectiveOperationException {
        var field = com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry.class.getDeclaredField("ACTIVE_DAY");
        field.setAccessible(true);
        var scoped = (ThreadLocal<StardewCropDailyContext>) field.get(null);
        var previous = scoped.get();
        int liveSeason = com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason();
        var method = com.stardew.craft.block.crop.StardewCropBlock.class.getDeclaredMethod("isInSeason", net.minecraft.world.level.Level.class);
        method.setAccessible(true);
        try {
            for (int season = 0; season < 4; season++) {
                scoped.set(new StardewCropDailyContext(true, season, false, season * 28 + 1, true));
                for (var entry : Map.of("cauliflower", 0, "melon", 1, "pumpkin", 2, "powder_melon", 3, "qi_fruit", -1).entrySet()) {
                    var block = BuiltInRegistries.BLOCK.get(new ResourceLocation("stardewcraft:" + entry.getKey() + "_crop"));
                    h.assertTrue((boolean) method.invoke(block, h.getLevel()) == (entry.getValue() == -1 || entry.getValue() == season), "Native crop used the live season: " + entry.getKey());
                }
            }
            h.assertTrue(com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason() == liveSeason, "Historical lookup changed the global season");
        } finally {
            if (previous == null) scoped.remove(); else scoped.set(previous);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void malformedAddonPlanCannotConsumeCrops(GameTestHelper h) {
        var f = new Fixture(h, 7, 1, true, true);
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, f.roots, 100, false).grown() == 0, "Out-of-bounds plan accepted");
        for (var pos : f.roots) h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.WHEAT), "Invalid plan consumed source crop");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void ceilingsAndContainersBlockGrowth(GameTestHelper h) {
        var f = new Fixture(h, 7, 1, true, false);
        var obstruction = f.anchor.offset(1, 1, 1);
        h.getLevel().setBlock(obstruction, Blocks.CHEST.defaultBlockState(), 18);
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, f.roots, 100, false).grown() == 0, "Container overwritten");
        h.getLevel().setBlock(obstruction, Blocks.STONE.defaultBlockState(), 18);
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, f.roots, 101, false).grown() == 0, "Ceiling overwritten");
        for (var pos : f.roots) h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.WHEAT), "Blocked growth consumed crop");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void otherLocationsNeedExplicitPermission(GameTestHelper h) {
        var f = new Fixture(h, 7, 1, false, false);
        h.assertTrue(GiantCropGrowth.process(h.getLevel(), f.roots, f.roots, 100, false).grown() == 0, "Default rule grew outside a farm");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void dailyReplayAndSavedDatesCannotAdvanceAgain(GameTestHelper h) {
        var f = new Fixture(h, 7, 0, true, false);
        var manager = CropGrowthManager.get(h.getLevel());
        var positions = f.positions(h);
        manager.settleCrops(h.getLevel(), positions, 100, 3, false);
        manager.settleCrops(h.getLevel(), positions.reversed(), 100, 3, false);
        h.assertTrue(f.dates.size() == 9, "A repeated day grew the same plants twice");
        var loaded = CropGrowthManager.load(manager.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(loaded.getState(h.getLevel(), f.anchor).lastDailyDay == 100 && loaded.getState(h.getLevel(), f.anchor).lastGiantDay == 100, "Daily stamp lost on save");
        manager.settleCrops(h.getLevel(), positions, 101, 3, false);
        h.assertTrue(f.dates.size() == 18, "Next day did not advance");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void offlineDaysGrowWholeGridUsingHistoricalDates(GameTestHelper h) {
        var f = new Fixture(h, 5, 1, true, false);
        var manager = CropGrowthManager.get(h.getLevel());
        int today = com.stardew.craft.farm.OfflineFarmCatchUp.computeAbsoluteDay();
        h.assertTrue(manager.settleCrops(h.getLevel(), f.positions(h), 29, 1, true).grown() == 0, "Giant grew before trigger maturity");
        h.assertTrue(manager.settleCrops(h.getLevel(), f.positions(h).reversed(), 30, 1, true).grown() == 1, "Offline giant chance omitted");
        h.assertTrue(f.dates.size() == 18 && f.dates.subList(0,9).stream().allMatch(d -> d.absoluteDay() == 29 && d.season() == 1 && d.offlineCatchUp())
                && f.dates.subList(9,18).stream().allMatch(d -> d.absoluteDay() == 30), "Addon received live date during catch-up");
        h.assertTrue(com.stardew.craft.farm.OfflineFarmCatchUp.computeAbsoluteDay() == today, "Catch-up modified global calendar");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="empty", timeoutTicks=200)
    public static void activeFarmLoadsOnlyRootChunkAndPreservesExistingTickets(GameTestHelper h) {
        var level = h.getLevel();
        var registry = com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer());
        var owner = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Giant loading", "Giant loading", com.stardew.craft.farm.FarmType.STANDARD);
        var min = farm.getFarmBoundsMin();
        var root = new BlockPos((min.getX() & ~15) + 47, 80, (min.getZ() & ~15) + 47);
        var chunk = new net.minecraft.world.level.ChunkPos(root);
        boolean rootWasForced = level.getForcedChunks().contains(chunk.toLong());
        boolean eastWasForced = level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(chunk.x + 1, chunk.z));
        farm.markActiveOnDay(Math.max(1, com.stardew.craft.farm.OfflineFarmCatchUp.computeAbsoluteDay() - 1));
        level.setChunkForced(chunk.x + 1, chunk.z, true);
        try {
            com.stardew.craft.farm.FarmDailyProcessHelper.beginDailyProcess(level);
            try {
                h.assertTrue(com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, root, 0)
                        && level.hasChunkAt(root) && level.getForcedChunks().contains(chunk.toLong()), "Remote active farm root was not loaded");
                h.assertTrue(!level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(chunk.x, chunk.z + 1)), "Crop acquired an unnecessary neighbor ticket");
            } finally { com.stardew.craft.farm.FarmDailyProcessHelper.endDailyProcess(level); }
            h.assertTrue(level.getForcedChunks().contains(chunk.toLong()) == rootWasForced, "Daily crop ticket leaked");
            h.assertTrue(level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(chunk.x + 1, chunk.z)), "Released another system's ticket");
        } finally {
            if (!eastWasForced) level.setChunkForced(chunk.x + 1, chunk.z, false);
            registry.deleteFarm(owner);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="construction_site")
    public static void allFiveCoreCropsUseTheirRealProduceAndFullModelHeight(GameTestHelper h) {
        var level = h.getLevel();
        var core = StardewGiantCrops.definitions().stream().filter(d -> d.id().getNamespace().equals("stardewcraft")).toList();
        int index = 0;
        for (var definition : core) {
            var cropId = new ResourceLocation("stardewcraft", definition.produce().getPath() + "_crop");
            var cropBlock = BuiltInRegistries.BLOCK.get(cropId);
            h.assertTrue(cropBlock instanceof com.stardew.craft.block.crop.StardewCropBlock, "Giant produce does not resolve a native crop: " + cropId);
            var anchor = h.absolutePos(new BlockPos(4 + index++ * 5, 3, 4));
            var positions = new HashSet<BlockPos>();
            for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) {
                var pos = anchor.offset(x, 0, z); positions.add(pos);
                level.setBlock(pos.below(), com.stardew.craft.block.ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
                level.setBlock(pos, cropBlock.defaultBlockState().setValue(com.stardew.craft.block.crop.StardewCropBlock.AGE, 3), 18);
                StardewCropRuntime.track(level, pos);
            }
            var inspected = StardewCropRuntime.inspect(level, anchor);
            h.assertTrue(definition.produce().equals(GiantCropGrowth.produce(level, anchor, inspected)), "Native produce mapping differs from giant definition");
            var testId = new ResourceLocation("giant_test", "native_" + definition.produce().getPath());
            StardewGiantCrops.register(new StardewGiantCrops.Definition(testId, definition.produce(), 3, 3, definition.height(), 1, true, new StardewGiantCrops.Handler() {
                public boolean allowsOutsideFarm(StardewGiantCrops.Context c) { return c.anchor().equals(anchor); }
                public List<StardewGiantCrops.Cell> plan(StardewGiantCrops.Context c, List<StardewCropState> crops) { return definition.handler().plan(c, crops); }
            }));
            for (var pos : positions) level.setBlock(pos.below(),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
            h.assertTrue(GiantCropGrowth.process(level, positions, positions, 100, false).grown() == 0,
                    "Native giant grew on imported farmland: " + definition.id());
            for (var pos : positions) level.setBlock(pos.below(),
                    com.stardew.craft.block.ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 18);
            h.assertTrue(GiantCropGrowth.process(level, positions, positions, 100, false).grown() == 1, "Native giant failed: " + definition.id());
            for (var pos : BlockPos.betweenClosed(anchor, anchor.offset(2, definition.height() - 1, 2)))
                h.assertTrue(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(definition.id()), "Native giant lost an extension");
            h.assertTrue(level.getBlockState(anchor.offset(1, 0, 1)).getValue(com.stardew.craft.block.crop.giant.GiantCropBlock.PART)
                    == com.stardew.craft.block.crop.giant.GiantCropBlock.Part.MAIN, "Northwest trigger was not converted to model center");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_giant_growth", template="empty")
    public static void stableRandomAndDefaultCatalogKeepSourceParameters(GameTestHelper h) {
        var id = new ResourceLocation("stardewcraft:giant_melon"); var dimension = new ResourceLocation("stardewcraft:stardew_valley");
        int winners = 0, changedWorld = 0;
        for (int i = 0; i < 100000; i++) {
            var pos = new BlockPos(i % 1000, 64, i / 1000);
            boolean won = GiantCropGrowth.wins(42, dimension, pos, id, 20, .01);
            if (won) winners++;
            h.assertTrue(won == GiantCropGrowth.wins(42, new ResourceLocation(dimension.toString()), pos.immutable(), new ResourceLocation(id.toString()), 20, .01), "Roll depends on object identity");
            if (won != GiantCropGrowth.wins(43, dimension, pos, id, 20, .01)) changedWorld++;
        }
        h.assertTrue(winners > 850 && winners < 1150 && changedWorld > 1000, "Wrong chance or world seed ignored");
        var core = StardewGiantCrops.definitions().stream().filter(d -> d.id().getNamespace().equals("stardewcraft")).toList();
        h.assertTrue(core.size() == 5 && core.stream().allMatch(d -> d.width() == 3 && d.depth() == 3 && d.chance() == .01 && d.requiresWater()), "Core giant parameters changed");
        com.stardew.craft.StardewCraft.LOGGER.info("[GIANT-PROBABILITY] 100000 candidates, {} passed the 1% gate", winners);
        h.succeed();
    }
}
