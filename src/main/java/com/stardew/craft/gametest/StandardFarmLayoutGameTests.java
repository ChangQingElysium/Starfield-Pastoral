package com.stardew.craft.gametest;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.farm.BeachFarmDailyService;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceInitializer;
import com.stardew.craft.farm.FarmDebrisPlacementRules;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.farm.FarmOreDailyService;
import com.stardew.craft.farm.FarmSpawnLayoutData;
import com.stardew.craft.fishing.WaterFeatureSpawnRules;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.mining.StructureLoader;
import com.stardew.craft.manager.ForageSpawnService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.Map;
import java.util.Set;

@GameTestHolder("stardewcraft_farm_debris")
@PrefixGameTestTemplate(false)
public final class StandardFarmLayoutGameTests {
    private StandardFarmLayoutGameTests() {
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void everyFarmShipsItsRegionalEcologyProfile(GameTestHelper helper)
            throws Exception {
        Map<FarmType, Integer> expectedSourceMarkers = Map.of(
                FarmType.STANDARD, 1455,
                FarmType.RIVERLAND, 699,
                FarmType.FOREST, 704,
                FarmType.HILLTOP, 613,
                FarmType.WILDERNESS, 937,
                FarmType.FOUR_CORNERS, 1221,
                FarmType.BEACH, 706,
                FarmType.MEADOWLANDS, 1730);
        Map<FarmType, Integer> expectedZones = Map.of(
                FarmType.STANDARD, 56,
                FarmType.RIVERLAND, 56,
                FarmType.FOREST, 56,
                FarmType.HILLTOP, 56,
                FarmType.WILDERNESS, 56,
                FarmType.FOUR_CORNERS, 64,
                FarmType.BEACH, 121,
                FarmType.MEADOWLANDS, 80);
        Set<String> validKinds = Set.of(
                "oak", "maple", "pine", "weeds", "stones", "twigs",
                "pasture", "blue_pasture", "saplings", "large_bush", "small_bush");
        var resources = helper.getLevel().getServer().getResourceManager();
        for (var entry : expectedSourceMarkers.entrySet()) {
            FarmType type = entry.getKey();
            ResourceLocation id = new ResourceLocation(
                    "stardewcraft", "farm_ecology/" + type.getId() + ".json");
            var resource = resources.getResource(id).orElseThrow();
            try (var reader = new java.io.InputStreamReader(
                    resource.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                var zones = root.getAsJsonArray("zones");
                helper.assertTrue(root.get("format").getAsInt() == 3
                                && !root.has("points")
                                && !root.has("density_scale")
                                && zones.size() == expectedZones.get(type),
                        type.getId() + " no longer covers every source-map neighborhood");
                FarmType.FarmLayout layout = type.getLayout();
                int sourceMarkers = root.getAsJsonObject("source_totals").entrySet().stream()
                        .mapToInt(total -> total.getValue().getAsInt())
                        .sum();
                for (var element : zones) {
                    var zone = element.getAsJsonObject();
                    var bounds = zone.getAsJsonArray("bounds");
                    helper.assertTrue(bounds.get(0).getAsInt() >= 0
                                    && bounds.get(0).getAsInt() <= bounds.get(2).getAsInt()
                                    && bounds.get(2).getAsInt() < layout.schemWidth()
                                    && bounds.get(1).getAsInt() >= 0
                                    && bounds.get(1).getAsInt() <= bounds.get(3).getAsInt()
                                    && bounds.get(3).getAsInt() < layout.schemLength(),
                            type.getId() + " contains an invalid ecology density zone");
                    var rates = zone.getAsJsonObject("rates");
                    helper.assertTrue(rates.has("dirt") && rates.has("grass"),
                            type.getId() + " lost a source-ground rate table");
                    for (String ground : Set.of("dirt", "grass")) {
                        for (var rate : rates.getAsJsonObject(ground).entrySet()) {
                            helper.assertTrue(validKinds.contains(rate.getKey())
                                            && rate.getValue().getAsDouble() >= 0.0D,
                                    type.getId() + " contains an invalid ecology rate");
                        }
                    }
                }
                helper.assertTrue(sourceMarkers == entry.getValue(),
                        type.getId() + " regional ecology no longer matches source statistics");
                helper.assertTrue(root.getAsJsonObject("source_totals").has("large_bush")
                                && root.getAsJsonObject("source_totals").has("small_bush"),
                        type.getId() + " dropped original Paths-layer bushes");
                if (type == FarmType.BEACH) {
                    var finalBounds = zones.get(zones.size() - 1).getAsJsonObject()
                            .getAsJsonArray("bounds");
                    helper.assertTrue(finalBounds.get(2).getAsInt() == 225
                                    && finalBounds.get(3).getAsInt() == 204
                                    && root.getAsJsonObject("source_totals")
                                    .get("pasture").getAsInt() == 74,
                            "Beach ecology no longer reaches the lower-right sand and pasture region");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void authoredCoordinatesAndTemporaryRegistration(GameTestHelper helper)
            throws ReflectiveOperationException {
        var standardId = StardewFarmLayoutRegistry.builtinId(FarmType.STANDARD);
        var layout = StardewFarmLayouts.find(standardId).orElseThrow();
        helper.assertTrue(layout.width() == 288 && layout.height() == 81
                        && layout.length() == 272,
                "Standard farm resource dimensions changed");
        helper.assertTrue(layout.spawnOffset().equals(new BlockPos(199, 25, 94))
                        && layout.spawnYaw() == 180.0F,
                "Standard farm spawn is not the authored farmhouse landing");
        helper.assertTrue(layout.greenhouseOffset().equals(new BlockPos(101, 24, 81))
                        && layout.totemOffset().equals(new BlockPos(175, 25, 71)),
                "Greenhouse or farm totem anchor changed");
        helper.assertTrue(layout.caveBlackWall().min().equals(new BlockPos(128, 25, 70))
                        && layout.cavePortalWall().max().equals(new BlockPos(130, 26, 71))
                        && layout.caveExitSpawn().equals(new BlockPos(129, 25, 72)),
                "Farm cave exterior no longer matches the authored opening");
        helper.assertTrue(layout.entrySouth().exitMin().equals(new BlockPos(141, 25, 36))
                        && layout.entryEast().exitMax().equals(new BlockPos(145, 27, 237))
                        && layout.entryWest().exitMax().equals(new BlockPos(251, 27, 96)),
                "Farm exits no longer sit near the middle of the north, south and east roads");
        helper.assertTrue(layout.entrySouth().barrierMin().equals(new BlockPos(0, 0, 35))
                        && layout.entrySouth().barrierMax().equals(new BlockPos(287, 80, 35))
                        && layout.entryEast().barrierMax().equals(new BlockPos(287, 80, 238))
                        && layout.entryWest().barrierMax().equals(new BlockPos(252, 80, 271)),
                "Standard farm exit protection no longer closes the full outer scenery boundary");
        CompoundTag raw = new CompoundTag();
        raw.putString("Id", "stardewcraft:material_template");
        raw.putIntArray("Pos", new int[]{2, 3, 4});
        CompoundTag data = new CompoundTag();
        data.putString("material", "minecraft:oak_planks");
        raw.put("Data", data);
        var normalize = StructureLoader.class.getDeclaredMethod(
                "normalizeSchematicBlockEntity", CompoundTag.class, BlockPos.class);
        normalize.setAccessible(true);
        CompoundTag normalized = (CompoundTag) normalize.invoke(
                null, raw, new BlockPos(7, 8, 9));
        helper.assertTrue(normalized.getString("id").equals("stardewcraft:material_template")
                        && normalized.getString("material").equals("minecraft:oak_planks")
                        && normalized.getInt("x") == 7 && normalized.getInt("y") == 8
                        && normalized.getInt("z") == 9,
                "Sponge v3 block-entity Data was not expanded at its world position");

        var saveEntry = FarmInstance.class.getDeclaredMethod(
                "saveEntry", StardewFarmLayout.Entry.class);
        var loadEntry = FarmInstance.class.getDeclaredMethod(
                "loadEntry", CompoundTag.class);
        saveEntry.setAccessible(true);
        loadEntry.setAccessible(true);
        CompoundTag savedEntry = (CompoundTag) saveEntry.invoke(
                null, layout.entrySouth());
        var reloadedEntry = (StardewFarmLayout.Entry) loadEntry.invoke(
                null, savedEntry);
        helper.assertTrue(reloadedEntry.barrierMin().equals(
                        layout.entrySouth().barrierMin())
                        && reloadedEntry.barrierMax().equals(
                        layout.entrySouth().barrierMax()),
                "Farm snapshot lost its authored exit protection bounds");
        savedEntry.remove("BarrierMin");
        savedEntry.remove("BarrierMax");
        var legacyEntry = (StardewFarmLayout.Entry) loadEntry.invoke(
                null, savedEntry);
        helper.assertTrue(legacyEntry.barrierMin().equals(
                        new BlockPos(141, 25, 35))
                        && legacyEntry.barrierMax().equals(
                        new BlockPos(143, 27, 35)),
                "Legacy farm snapshot did not retain its safe fallback wall");

        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void debugFarmsKeepIndependentIdentitySelectionAndPersistence(GameTestHelper helper)
            throws ReflectiveOperationException {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get(helper.getLevel().getServer());
        UUID player = UUID.randomUUID();
        FarmInstance primary = registry.createFarm(player, "DebugOwner", "Primary", FarmType.STANDARD);
        FarmInstance beach = registry.createDebugFarm(player, "DebugOwner", "Beach Debug", FarmType.BEACH);
        FarmInstance meadow = registry.createDebugFarm(player, "DebugOwner", "Meadow Debug", FarmType.MEADOWLANDS);
        try {
            helper.assertTrue(registry.getFarmsForPlayer(player).size() == 3,
                    "Debug farm creation replaced another farm instead of adding one");
            helper.assertTrue(registry.getFarmForPlayer(player).getInstanceId().equals(meadow.getInstanceId()),
                    "Newest debug farm was not selected");
            UUID coordinateKey = registry.getOwnerAt(beach.getOrigin());
            helper.assertTrue(coordinateKey != null
                            && registry.getFarm(coordinateKey).getInstanceId().equals(beach.getInstanceId()),
                    "Coordinate lookup resolved an extra farm as the player's primary farm");
            helper.assertTrue(registry.selectFarm(player, beach.getInstanceId())
                            && registry.getFarmForPlayer(player).getInstanceId().equals(beach.getInstanceId()),
                    "Explicit debug farm selection did not change the active farm");

            CompoundTag saved = registry.save(new CompoundTag(), helper.getLevel().registryAccess());
            var load = FarmInstanceRegistry.class.getDeclaredMethod(
                    "load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
            load.setAccessible(true);
            FarmInstanceRegistry restored = (FarmInstanceRegistry) load.invoke(
                    null, saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.getDebugFarms(player).size() == 2
                            && restored.getFarmForPlayer(player).getInstanceId().equals(beach.getInstanceId()),
                    "Debug ownership or active selection did not survive serialization");
            helper.assertTrue(registry.deleteDebugFarm(player, beach.getInstanceId()) != null
                            && registry.getFarmByInstanceId(beach.getInstanceId()) == null
                            && registry.getFarmForPlayer(player) != null,
                    "Deleting one debug farm removed or invalidated the player's other farms");
        } finally {
            registry.deleteDebugFarm(player, beach.getInstanceId());
            registry.deleteDebugFarm(player, meadow.getInstanceId());
            registry.deleteFarm(primary.getOwnerUUID());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void forestCoordinatesAndSpecialRules(GameTestHelper helper)
            throws ReflectiveOperationException {
        var forestId = StardewFarmLayoutRegistry.builtinId(FarmType.FOREST);
        var layout = StardewFarmLayouts.find(forestId).orElseThrow();
        helper.assertTrue(layout.selectable()
                        && layout.width() == 288 && layout.height() == 81
                        && layout.length() == 272,
                "Forest farm was not registered with the imported resource dimensions");
        helper.assertTrue(layout.spawnOffset().equals(new BlockPos(199, 25, 93))
                        && layout.spawnYaw() == 180.0F,
                "Forest farm spawn is not the authored farmhouse landing");
        helper.assertTrue(layout.greenhouseOffset().equals(new BlockPos(101, 24, 81))
                        && layout.totemOffset().equals(new BlockPos(218, 25, 74)),
                "Forest greenhouse or farm totem anchor changed");
        helper.assertTrue(layout.forageZoneMin().equals(new BlockPos(65, 24, 72))
                        && layout.forageZoneMax().equals(new BlockPos(216, 28, 202)),
                "Forest playable forage projection changed");
        helper.assertTrue(layout.caveBlackWall().min().equals(new BlockPos(124, 25, 72))
                        && layout.cavePortalWall().max().equals(new BlockPos(126, 26, 73))
                        && layout.caveExitSpawn().equals(new BlockPos(125, 25, 74)),
                "Forest cave exterior no longer matches the authored opening");
        helper.assertTrue(layout.entrySouth().exitMin().equals(new BlockPos(140, 25, 37))
                        && layout.entryEast().exitMax().equals(new BlockPos(146, 27, 237))
                        && layout.entryWest().exitMax().equals(new BlockPos(251, 27, 97)),
                "Forest exits no longer sit near the middle of the three roads");

        UUID owner = UUID.randomUUID();
        var farm = FarmInstanceRegistry.get(helper.getLevel().getServer())
                .createFarm(owner, "Forest test", "Forest test", FarmType.FOREST);
        helper.assertTrue(com.stardew.craft.pet.PetHomes.authoredBowl(farm)
                        .equals(farm.getOrigin().offset(178, 25, 70)),
                "Forest pet bowl is not at the authored site");

        helper.assertTrue(FarmDebrisPlacementRules.groundKind(
                        ModBlocks.DIRT.get().defaultBlockState())
                        == FarmDebrisPlacementRules.GroundKind.DIRT
                        && FarmDebrisPlacementRules.groundKind(
                        ModBlocks.GRASS_BLOCK.get().defaultBlockState())
                        == FarmDebrisPlacementRules.GroundKind.GRASS
                        && FarmDebrisPlacementRules.groundKind(
                        ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState())
                        == FarmDebrisPlacementRules.GroundKind.DARK_GRASS
                        && FarmDebrisPlacementRules.groundKind(
                        ModBlocks.SAND.get().defaultBlockState())
                        == FarmDebrisPlacementRules.GroundKind.SAND
                        && FarmDebrisPlacementRules.groundKind(
                        ModBlocks.HARD_SOIL.get().defaultBlockState())
                        == FarmDebrisPlacementRules.GroundKind.OTHER,
                "Farm initial ecology no longer distinguishes dirt, grass and dark grass");

        RandomSource random = RandomSource.create(0x464f524553544641L);
        int woodskip = 0, forest = 0, junk = 0;
        BlockPos insideFarm = farm.getOrigin().offset(150, 25, 150);
        for (int i = 0; i < 2000; i++) {
            var roll = FishingDataManager.resolveForestFarmFishingRoll(
                    helper.getLevel(), insideFarm, random).orElseThrow();
            switch (roll) {
                case WOODSKIP -> woodskip++;
                case FOREST -> forest++;
                case JUNK -> junk++;
            }
        }
        helper.assertTrue(woodskip >= 70 && woodskip <= 130
                        && forest >= 820 && forest <= 980
                        && junk >= 900 && junk <= 1100,
                "Forest fishing no longer follows the original 5/45/50 split");
        helper.assertTrue(FishingDataManager.resolveForestFarmFishingRoll(
                        helper.getLevel(), BlockPos.ZERO, random).isEmpty(),
                "Forest fishing override leaked outside the farm instance");

        var forageField = ForageSpawnService.class.getDeclaredField(
                "FOREST_FARM_FORAGE");
        forageField.setAccessible(true);
        var seasons = (java.util.List<?>) forageField.get(null);
        var spring = (java.util.List<?>) seasons.get(0);
        var summer = (java.util.List<?>) seasons.get(1);
        var fall = (java.util.List<?>) seasons.get(2);
        helper.assertTrue(spring.contains(ModBlocks.FORAGE_MOREL)
                        && !spring.contains(ModBlocks.FORAGE_DAFFODIL)
                        && summer.contains(ModBlocks.FORAGE_GRAPE)
                        && !summer.contains(ModBlocks.FORAGE_FIDDLEHEAD_FERN)
                        && fall.contains(ModBlocks.FORAGE_RED_MUSHROOM)
                        && fall.contains(ModBlocks.FORAGE_PURPLE_MUSHROOM)
                        && !fall.contains(ModBlocks.FORAGE_WILD_PLUM),
                "Forest seasonal forage no longer matches Farm.DayUpdate");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void specialFarmSpawnMasksFollowAuthoredMaps(GameTestHelper helper)
            throws ReflectiveOperationException {
        var forest = FarmSpawnLayoutData.forType(FarmType.FOREST);
        var forestGeneral = forest.positions("general");
        var forestStrip = forest.positions("forest_strip");
        helper.assertTrue(forestGeneral.size() == 9367 && forestStrip.size() == 5106,
                "Forest forage masks no longer match farm_3's authored natural ground");
        helper.assertTrue(forestGeneral.stream().anyMatch(pos -> pos.getX() > 190)
                        && forestGeneral.stream().anyMatch(pos -> pos.getX() < 85)
                        && forestGeneral.stream().allMatch(pos -> pos.getX() >= 65
                        && pos.getX() <= 216 && pos.getZ() >= 72 && pos.getZ() <= 202),
                "Forest forage was clipped to one grass patch or escaped the playable basin");

        var beach = FarmSpawnLayoutData.forType(FarmType.BEACH);
        var shore = beach.positions("shore_water");
        helper.assertTrue(shore.size() == 1380
                        && beach.positions("beach_spawn").size() == 1597
                        && beach.positions("seasonal_grass").size() == 1108,
                "Beach Farm spawn masks no longer match farm_7's coast and forage patch");
        helper.assertTrue(shore.stream().allMatch(pos -> pos.getY() == 25)
                        && shore.stream().noneMatch(pos -> pos.getX() >= 115 && pos.getX() <= 181
                        && pos.getZ() >= 34 && pos.getZ() <= 79)
                        && shore.stream().anyMatch(pos -> pos.getX() < 20)
                        && shore.stream().anyMatch(pos -> pos.getZ() > 200),
                "Supply crates are not constrained to the broad ocean shoreline or leaked into freshwater");

        var beachChance = BeachFarmDailyService.class.getDeclaredField("BEACH_SPAWN_TILE_CHANCE");
        var grassChance = BeachFarmDailyService.class.getDeclaredField("SEASONAL_GRASS_TILE_CHANCE");
        var beachPool = BeachFarmDailyService.class.getDeclaredField("BEACH_BASE_FORAGE");
        beachChance.setAccessible(true);
        grassChance.setAccessible(true);
        beachPool.setAccessible(true);
        var pool = (java.util.List<?>) beachPool.get(null);
        helper.assertTrue(Math.abs(beachChance.getDouble(null) - 802.0D / 12100.0D) < 1.0E-12
                        && Math.abs(grassChance.getDouble(null) - 337.0D / 12100.0D) < 1.0E-12
                        && pool.contains(ModBlocks.FORAGE_CORAL)
                        && pool.contains(ModBlocks.FORAGE_MUSSEL)
                        && pool.contains(ModBlocks.FORAGE_COCKLE)
                        && pool.contains(ModBlocks.FORAGE_OYSTER)
                        && java.util.Collections.frequency(pool, ModBlocks.FORAGE_SEAWEED) == 2,
                "Beach Farm daily rolls no longer match Farm.DayUpdate's source weights and pool");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void riverlandCoordinatesAndFishingPool(GameTestHelper helper)
            throws ReflectiveOperationException {
        var riverlandId = StardewFarmLayoutRegistry.builtinId(FarmType.RIVERLAND);
        var layout = StardewFarmLayouts.find(riverlandId).orElseThrow();
        helper.assertTrue(layout.selectable()
                        && layout.width() == 288 && layout.height() == 81
                        && layout.length() == 272,
                "Riverland farm was not registered with the imported resource dimensions");
        helper.assertTrue(layout.spawnOffset().equals(new BlockPos(199, 25, 93))
                        && layout.spawnYaw() == 180.0F,
                "Riverland spawn is not the authored farmhouse landing");
        helper.assertTrue(layout.greenhouseOffset().equals(new BlockPos(101, 24, 81))
                        && layout.totemOffset().equals(new BlockPos(221, 25, 81)),
                "Riverland greenhouse or farm totem anchor changed");
        helper.assertTrue(layout.caveBlackWall().min().equals(new BlockPos(118, 25, 77))
                        && layout.cavePortalWall().max().equals(new BlockPos(120, 26, 78))
                        && layout.caveExitSpawn().equals(new BlockPos(119, 25, 79)),
                "Riverland cave exterior no longer matches the authored opening");
        helper.assertTrue(layout.entrySouth().exitMin().equals(new BlockPos(139, 25, 37))
                        && layout.entryEast().exitMax().equals(new BlockPos(147, 27, 237))
                        && layout.entryWest().exitMax().equals(new BlockPos(251, 27, 97)),
                "Riverland exits no longer sit near the middle of the north, south and east roads");
        helper.assertTrue(layout.entrySouth().barrierMin().equals(new BlockPos(0, 0, 36))
                        && layout.entrySouth().barrierMax().equals(new BlockPos(287, 80, 36))
                        && layout.entryEast().barrierMax().equals(new BlockPos(287, 80, 238))
                        && layout.entryWest().barrierMin().equals(new BlockPos(252, 0, 0)),
                "Riverland exit protection no longer closes the full outer scenery boundary");

        UUID owner = UUID.randomUUID();
        var farm = FarmInstanceRegistry.get(helper.getLevel().getServer())
                .createFarm(owner, "Riverland test", "Riverland test", FarmType.RIVERLAND);
        helper.assertTrue(com.stardew.craft.pet.PetHomes.authoredBowl(farm)
                        .equals(farm.getOrigin().offset(182, 25, 83)),
                "Riverland pet bowl is not at the authored site");

        var totem = ModBlocks.TOTEM_POLE_FARM.get();
        BlockPos totemPos = farm.getFarmTotemPos();
        var westMain = totem.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.WEST);
        helper.getLevel().setBlockAndUpdate(totemPos, westMain);
        totem.setPlacedBy(helper.getLevel(), totemPos, westMain, null,
                net.minecraft.world.item.ItemStack.EMPTY);
        var repair = FarmInstanceInitializer.class.getDeclaredMethod(
                "ensureFarmTotemFacesSouth", net.minecraft.server.level.ServerLevel.class,
                UUID.class);
        repair.setAccessible(true);
        repair.invoke(null, helper.getLevel(), owner);
        helper.assertTrue(helper.getLevel().getBlockState(totemPos)
                        .getValue(MapDecorStaticBlock.FACING) == Direction.SOUTH,
                "Existing farm totem was not repaired to face south");
        var bushRepair = FarmInstanceInitializer.class.getDeclaredMethod(
                "ensureFarmTotemBushes", net.minecraft.server.level.ServerLevel.class,
                UUID.class);
        bushRepair.setAccessible(true);
        bushRepair.invoke(null, helper.getLevel(), owner);
        helper.assertTrue(helper.getLevel().getBlockState(totemPos.west())
                        .is(ModBlocks.SMALL_BUSH.get())
                        && helper.getLevel().getBlockState(totemPos.east())
                        .is(ModBlocks.SMALL_BUSH.get()),
                "Every farm totem must have a small bush on both sides");

        BlockPos waterInsideFarm = farm.getOrigin().offset(20, 25, 20);
        helper.assertTrue(WaterFeatureSpawnRules.isRiverlandFarmArea(
                        helper.getLevel(), waterInsideFarm),
                "Riverland farm was not recognized for its original fish splash exception");
        RandomSource random = RandomSource.create(0x52495645524c414eL);
        int forestRolls = 0;
        for (int i = 0; i < 1000; i++) {
            var pool = FishingDataManager.resolveRiverlandFarmFishingPool(
                    helper.getLevel(), waterInsideFarm, random);
            helper.assertTrue(pool.isPresent(), "Riverland cast did not receive a farm fishing pool");
            if (pool.orElseThrow().equals("Forest")) forestRolls++;
        }
        helper.assertTrue(forestRolls >= 250 && forestRolls <= 350,
                "Riverland fishing no longer follows the original 30/70 Forest/Town split");
        helper.assertTrue(FishingDataManager.resolveRiverlandFarmFishingPool(
                        helper.getLevel(), BlockPos.ZERO, random).isEmpty(),
                "Riverland fishing override leaked outside the farm instance");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void hilltopWildernessAndFourCornersParity(GameTestHelper helper) {
        var hilltop = StardewFarmLayouts.find(
                StardewFarmLayoutRegistry.builtinId(FarmType.HILLTOP)).orElseThrow();
        var wilderness = StardewFarmLayouts.find(
                StardewFarmLayoutRegistry.builtinId(FarmType.WILDERNESS)).orElseThrow();
        var fourCorners = StardewFarmLayouts.find(
                StardewFarmLayoutRegistry.builtinId(FarmType.FOUR_CORNERS)).orElseThrow();
        helper.assertTrue(hilltop.selectable() && wilderness.selectable() && fourCorners.selectable(),
                "Imported farm layouts were not selectable");
        helper.assertTrue(hilltop.width() == 288 && hilltop.height() == 83 && hilltop.length() == 272
                        && wilderness.height() == 81 && fourCorners.height() == 81,
                "Imported farm resource dimensions changed");
        helper.assertTrue(hilltop.spawnOffset().equals(new BlockPos(199, 25, 93))
                        && hilltop.totemOffset().equals(new BlockPos(163, 25, 77))
                        && wilderness.totemOffset().equals(new BlockPos(175, 25, 70))
                        && fourCorners.totemOffset().equals(new BlockPos(150, 25, 140)),
                "Spawn or south-facing totem anchor changed");
        helper.assertTrue(fourCorners.greenhouseOffset().equals(new BlockPos(132, 24, 110))
                        && fourCorners.caveBlackWall().min().equals(new BlockPos(122, 25, 134))
                        && fourCorners.cavePortalWall().min().equals(new BlockPos(122, 25, 139))
                        && fourCorners.caveExitSpawn().equals(new BlockPos(123, 25, 140)),
                "Four Corners cave portal is no longer in front of the authored glass layers");
        helper.assertTrue(fourCorners.entryEast().teleportOffset().equals(new BlockPos(144, 29, 236))
                        && fourCorners.entryEast().exitMin().equals(new BlockPos(143, 29, 237))
                        && fourCorners.entryEast().exitMax().equals(new BlockPos(145, 31, 237))
                        && fourCorners.entryEast().barrierMax().equals(new BlockPos(287, 80, 238))
                        && StardewFarmLayouts.findRegistration(
                                StardewFarmLayoutRegistry.builtinId(FarmType.FOUR_CORNERS))
                                .orElseThrow().version() == 2,
                "Four Corners south road no longer follows its elevated road center");

        var registry = FarmInstanceRegistry.get(helper.getLevel().getServer());
        FarmInstance hillFarm = registry.createFarm(UUID.randomUUID(), "Hilltop test", "Hilltop test", FarmType.HILLTOP);
        FarmInstance wildFarm = registry.createFarm(UUID.randomUUID(), "Wilderness test", "Wilderness test", FarmType.WILDERNESS);
        FarmInstance fourFarm = registry.createFarm(UUID.randomUUID(), "Four test", "Four test", FarmType.FOUR_CORNERS);
        helper.assertTrue(com.stardew.craft.pet.PetHomes.authoredBowl(hillFarm)
                        .equals(hillFarm.getOrigin().offset(178, 25, 78))
                        && com.stardew.craft.pet.PetHomes.authoredBowl(wildFarm)
                        .equals(wildFarm.getOrigin().offset(177, 25, 70))
                        && com.stardew.craft.pet.PetHomes.authoredBowl(fourFarm)
                        .equals(fourFarm.getOrigin().offset(155, 25, 142)),
                "One of the three authored pet-bowl sites changed");

        RandomSource random = RandomSource.create(0x4641524d343536L);
        int hillForest = 0, wildMountain = 0, fourForest = 0;
        for (int i = 0; i < 2000; i++) {
            if (FishingDataManager.resolveSpecialFarmFishingRoll(helper.getLevel(),
                    hillFarm.getOrigin().offset(100, 25, 200), random).orElseThrow()
                    == FishingDataManager.SpecialFarmFishingRoll.FOREST) hillForest++;
            if (FishingDataManager.resolveSpecialFarmFishingRoll(helper.getLevel(),
                    wildFarm.getOrigin().offset(100, 25, 200), random).orElseThrow()
                    == FishingDataManager.SpecialFarmFishingRoll.MOUNTAIN) wildMountain++;
            if (FishingDataManager.resolveSpecialFarmFishingRoll(helper.getLevel(),
                    fourFarm.getOrigin().offset(100, 25, 220), random).orElseThrow()
                    == FishingDataManager.SpecialFarmFishingRoll.FOREST) fourForest++;
        }
        helper.assertTrue(hillForest >= 900 && hillForest <= 1100
                        && wildMountain >= 620 && wildMountain <= 780
                        && fourForest >= 900 && fourForest <= 1100,
                "Hilltop/Wilderness/Four Corners fishing splits diverged from 50/35/50 percent");
        RandomSource fourCornerA = RandomSource.create(0x464f5552434f524eL);
        RandomSource fourCornerB = RandomSource.create(0x464f5552434f524eL);
        helper.assertTrue(FishingDataManager.resolveSpecialFarmFishingRoll(helper.getLevel(),
                        fourFarm.getOrigin().offset(100, 25, 220), fourCornerA).orElseThrow()
                        == FishingDataManager.resolveSpecialFarmFishingRoll(helper.getLevel(),
                        fourFarm.getOrigin().offset(200, 25, 100), fourCornerB).orElseThrow()
                        && FishingDataManager.isFourCornersFishingRegion(helper.getLevel(),
                        fourFarm.getOrigin().offset(200, 25, 100)),
                "Four Corners fishing still depends on the old southwest pond rectangle");

        int artifacts = 0;
        java.util.Set<String> highLevelSources = new java.util.HashSet<>();
        for (int i = 0; i < 20000; i++) {
            var low = FarmOreDailyService.rollOre(random, 0);
            if (low.artifactSpot()) artifacts++;
            else helper.assertTrue(!java.util.Set.of("76", "77", "290", "764", "765").contains(low.sourceId()),
                    "Mining-level-gated farm ore spawned at level zero: " + low.sourceId());
            var high = FarmOreDailyService.rollOre(random, 10);
            if (!high.artifactSpot()) highLevelSources.add(high.sourceId());
        }
        helper.assertTrue(artifacts >= 2700 && artifacts <= 3300
                        && highLevelSources.containsAll(java.util.Set.of(
                        "668", "670", "75", "76", "77", "751", "290", "764", "765")),
                "Farm ore probability chain lost its artifact rate or a source node");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void wildernessMonsterSurfaceUsesWalkableFarmGround(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        FarmInstance farm = new FarmInstance(
                UUID.randomUUID(), "Wilderness surface", "Wilderness surface",
                0, origin, FarmType.WILDERNESS);
        BlockPos grass = origin.offset(3, 2, 3);
        BlockPos water = origin.offset(5, 2, 3);
        BlockPos roof = origin.offset(7, 2, 3);
        for (BlockPos column : java.util.List.of(grass, water, roof)) {
            for (int y = column.getY(); y <= origin.getY() + 80; y++) {
                helper.getLevel().setBlock(new BlockPos(column.getX(), y, column.getZ()),
                        Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(grass, ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(grass.above(), Blocks.SHORT_GRASS.defaultBlockState(), 3);
        helper.assertTrue(grass.above().equals(FarmDebrisPlacementRules.findMonsterSurface(
                        helper.getLevel(), farm, grass.getX(), grass.getZ())),
                "Collision-free farm vegetation incorrectly blocked Wilderness monsters");

        helper.getLevel().setBlock(water, ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(water.above(), Blocks.WATER.defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findMonsterSurface(
                        helper.getLevel(), farm, water.getX(), water.getZ()) == null,
                "Wilderness monster surface accepted water");

        helper.getLevel().setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(FarmDebrisPlacementRules.findMonsterSurface(
                        helper.getLevel(), farm, roof.getX(), roof.getZ()) == null,
                "Wilderness monster surface accepted constructed scenery");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void beachAndMeadowlandsCoordinatesAndFishing(GameTestHelper helper) throws Exception {
        var beach = StardewFarmLayouts.find(
                StardewFarmLayoutRegistry.builtinId(FarmType.BEACH)).orElseThrow();
        var meadowlands = StardewFarmLayouts.find(
                StardewFarmLayoutRegistry.builtinId(FarmType.MEADOWLANDS)).orElseThrow();
        helper.assertTrue(beach.selectable() && meadowlands.selectable()
                        && beach.width() == 288 && beach.height() == 78 && beach.length() == 272
                        && meadowlands.width() == 288 && meadowlands.height() == 81
                        && meadowlands.length() == 272,
                "Beach or Meadowlands imported dimensions/registration changed");
        helper.assertTrue(beach.spawnOffset().equals(new BlockPos(154, 25, 99))
                        && beach.greenhouseOffset().equals(new BlockPos(72, 24, 85))
                        && beach.totemOffset().equals(new BlockPos(183, 25, 111))
                        && beach.caveExitSpawn().equals(new BlockPos(108, 25, 87)),
                "Beach Farm authored anchors changed");
        helper.assertTrue(meadowlands.spawnOffset().equals(new BlockPos(199, 25, 93))
                        && meadowlands.greenhouseOffset().equals(new BlockPos(118, 24, 103))
                        && meadowlands.totemOffset().equals(new BlockPos(175, 25, 77))
                        && meadowlands.caveExitSpawn().equals(new BlockPos(207, 25, 173)),
                "Meadowlands authored anchors changed");

        var registry = FarmInstanceRegistry.get(helper.getLevel().getServer());
        FarmInstance beachFarm = registry.createFarm(
                UUID.randomUUID(), "Beach test", "Beach test", FarmType.BEACH);
        FarmInstance meadowFarm = registry.createFarm(
                UUID.randomUUID(), "Meadow test", "Meadow test", FarmType.MEADOWLANDS);
        helper.assertTrue(com.stardew.craft.pet.PetHomes.authoredBowl(beachFarm)
                        .equals(beachFarm.getOrigin().offset(178, 25, 102))
                        && com.stardew.craft.pet.PetHomes.authoredBowl(meadowFarm)
                        .equals(meadowFarm.getOrigin().offset(221, 25, 79)),
                "Beach or Meadowlands pet-bowl anchor changed");

        var tier = com.stardew.craft.building.runtime.PrefabDefinitions
                .get(com.stardew.craft.building.runtime.PrefabDefinitions.COOP).tier(1);
        BlockPos starterAnchor = meadowFarm.getOrigin().offset(144, 24, 75);
        helper.assertTrue(com.stardew.craft.building.runtime.PrefabDefinitions.world(
                        tier.manager(), tier.anchor(), starterAnchor,
                        net.minecraft.world.level.block.Rotation.NONE)
                        .equals(meadowFarm.getOrigin().offset(150, 25, 83)),
                "Meadowlands starter coop manager no longer matches the authored template");

        RandomSource beachRandom = RandomSource.create(0x4245414348464152L);
        int seaweed = 0, forage = 0, beachFish = 0, junk = 0;
        BlockPos ocean = beachFarm.getOrigin().offset(40, 25, 150);
        for (int i = 0; i < 10000; i++) {
            switch (FishingDataManager.resolveBeachFarmFishingRoll(
                    helper.getLevel(), ocean, beachRandom).orElseThrow()) {
                case SEAWEED -> seaweed++;
                case BEACH_FORAGE -> forage++;
                case BEACH -> beachFish++;
                case JUNK -> junk++;
            }
        }
        helper.assertTrue(seaweed >= 1400 && seaweed <= 1600
                        && forage >= 430 && forage <= 590
                        && beachFish >= 5100 && beachFish <= 5450
                        && junk >= 2550 && junk <= 2900,
                "Beach ocean fishing no longer follows the source's sequential rules");
        RandomSource beachAreaA = RandomSource.create(0x4245414348415245L);
        RandomSource beachAreaB = RandomSource.create(0x4245414348415245L);
        helper.assertTrue(FishingDataManager.resolveBeachFarmFishingRoll(
                        helper.getLevel(), beachFarm.getOrigin().offset(40, 25, 150), beachAreaA)
                        .orElseThrow() == FishingDataManager.resolveBeachFarmFishingRoll(
                        helper.getLevel(), beachFarm.getOrigin().offset(140, 25, 60), beachAreaB)
                        .orElseThrow(),
                "Beach farm fishing still depends on a hard-coded water rectangle");

        var biomeMethod = FishingDataManager.class.getDeclaredMethod(
                "biomeForLocationPool", net.minecraft.server.level.ServerLevel.class, String.class);
        biomeMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        var forestBiome = (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>)
                biomeMethod.invoke(null, helper.getLevel(), "Forest");
        @SuppressWarnings("unchecked")
        var beachBiome = (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>)
                biomeMethod.invoke(null, helper.getLevel(), "Beach");
        @SuppressWarnings("unchecked")
        var mountainBiome = (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>)
                biomeMethod.invoke(null, helper.getLevel(), "Mountain");
        @SuppressWarnings("unchecked")
        var townBiome = (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>)
                biomeMethod.invoke(null, helper.getLevel(), "Town");
        helper.assertTrue(forestBiome.is(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        new ResourceLocation("stardewcraft:is_forest_river")))
                        && beachBiome.is(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        new ResourceLocation("stardewcraft:is_ocean")))
                        && mountainBiome.is(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        new ResourceLocation("stardewcraft:is_mountain_lake")))
                        && townBiome.is(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        new ResourceLocation("stardewcraft:is_town_river"))),
                "Farm LOCATION_FISH pools no longer receive their virtual fishing biome");

        RandomSource meadowRandom = RandomSource.create(0x4d4541444f574c41L);
        int forest = 0;
        for (int i = 0; i < 2000; i++) {
            if (FishingDataManager.resolveSpecialFarmFishingRoll(
                    helper.getLevel(), meadowFarm.getOrigin().offset(100, 25, 150), meadowRandom)
                    .orElseThrow() == FishingDataManager.SpecialFarmFishingRoll.FOREST) forest++;
        }
        helper.assertTrue(forest >= 740 && forest <= 860,
                "Meadowlands fishing no longer follows the original 40/60 Forest/trash split");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void meadowlandsStarterCoopIsACompletedManagedBuilding(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        FarmInstance farm = FarmInstanceRegistry.get(helper.getLevel().getServer())
                .createFarm(owner, "Starter coop test", "Starter coop test", FarmType.MEADOWLANDS);
        // The real schematic supplies this terrain. The focused test provides
        // only the paddock support so the completed prefab can be exercised.
        for (int x = 132; x <= 164; x++) {
            for (int z = 69; z <= 101; z++) {
                helper.getLevel().setBlock(farm.getOrigin().offset(x, 24, z),
                        ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 2);
            }
        }
        com.stardew.craft.farm.MeadowlandsStarterService.ensureCurrent(
                helper.getLevel(), owner);
        var buildings = com.stardew.craft.building.runtime.BuildingWorldData
                .get(helper.getLevel().getServer());
        var home = buildings.all().stream()
                .filter(record -> record.farmId().equals(farm.getInstanceId())
                        && record.family().equals(
                        com.stardew.craft.building.runtime.PrefabDefinitions.COOP))
                .findFirst().orElseThrow();
        helper.assertTrue(home.phase()
                        == com.stardew.craft.building.runtime.BuildingRecord.Phase.READY
                        && home.residence()
                        == com.stardew.craft.building.runtime.BuildingRecord.Residence.VALID
                        && home.anchor().equals(farm.getOrigin().offset(144, 24, 75))
                        && home.manager().equals(farm.getOrigin().offset(150, 25, 83)),
                "Starter coop was not registered as the authored completed prefab");
        helper.assertTrue(helper.getLevel().getBlockState(home.manager()).is(
                        com.stardew.craft.building.runtime.PrefabDefinitions.managerBlock(home.family())),
                "Starter coop is missing its building manager");
        var animals = com.stardew.craft.animal.runtime.LivestockWorldData
                .get(helper.getLevel().getServer());
        var residents = animals.all().stream()
                .filter(animal -> animal.home().equals(home.id())).toList();
        helper.assertTrue(residents.size() == 2
                        && residents.stream().anyMatch(animal -> animal.species()
                        == com.stardew.craft.animal.runtime.LivestockSpecies.WHITE_CHICKEN)
                        && residents.stream().anyMatch(animal -> animal.species()
                        == com.stardew.craft.animal.runtime.LivestockSpecies.BROWN_CHICKEN),
                "Starter coop did not receive one white and one brown chicken");
        com.stardew.craft.farm.MeadowlandsStarterService.ensureCurrent(
                helper.getLevel(), owner);
        helper.assertTrue(buildings.all().stream()
                        .filter(record -> record.farmId().equals(farm.getInstanceId())
                                && record.family().equals(
                                com.stardew.craft.building.runtime.PrefabDefinitions.COOP)).count() == 1
                        && animals.all().stream().filter(animal -> animal.home().equals(home.id())).count() == 2,
                "Retrying Meadowlands migration duplicated its building or chickens");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void exitProtectionFollowsTerrainWithoutDeletingIt(GameTestHelper helper)
            throws ReflectiveOperationException {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        StardewFarmLayout.Entry entry = new StardewFarmLayout.Entry(
                new BlockPos(0, 0, 1), 0.0F,
                new BlockPos(0, 0, 0), new BlockPos(0, 2, 0),
                new BlockPos(-2, 0, 0), new BlockPos(2, 2, 0));
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(entry.barrierMin()),
                origin.offset(entry.barrierMax()))) {
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        BlockPos terrain = origin;
        helper.getLevel().setBlockAndUpdate(terrain, Blocks.STONE.defaultBlockState());

        var placeWall = FarmInstanceInitializer.class.getDeclaredMethod(
                "placeExitBarrierWall",
                net.minecraft.server.level.ServerLevel.class,
                BlockPos.class,
                StardewFarmLayout.Entry.class);
        placeWall.setAccessible(true);
        placeWall.invoke(null, helper.getLevel(), origin, entry);

        helper.assertTrue(helper.getLevel().getBlockState(terrain).is(Blocks.STONE),
                "Exit protection deleted solid authored scenery");
        for (int x = -2; x <= 2; x++) {
            helper.assertTrue(helper.getLevel().getBlockState(
                            origin.offset(x, 2, 0)).is(Blocks.BARRIER),
                    "Exit protection left a bypass above the road bank at x=" + x);
        }
        helper.assertTrue(helper.getLevel().getBlockState(
                        origin.offset(-2, 0, 0)).is(Blocks.BARRIER)
                        && helper.getLevel().getBlockState(
                        origin.offset(2, 0, 0)).is(Blocks.BARRIER),
                "Exit protection did not span the authored curtain endpoints");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_debris", template = "ring_utilities")
    public static void farmBulkPlacementPreservesAuthoredBlocksAndClearsAir(
            GameTestHelper helper
    ) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos referenceOrigin = origin.offset(16, 0, 0);
        BlockPos authoredAir = origin.offset(4, 1, 4);
        BlockPos referenceAir = referenceOrigin.offset(4, 1, 4);
        helper.getLevel().setBlockAndUpdate(
                authoredAir, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(
                referenceAir, Blocks.STONE.defaultBlockState());

        ResourceLocation cave = new ResourceLocation(
                "stardewcraft", "farm/cave.schem");
        boolean referencePlaced = StructureLoader.loadAndPlaceWithResult(
                helper.getLevel(), cave, referenceOrigin);
        boolean bulkPlaced = StructureLoader.loadAndPlaceFarmSchematicWithResult(
                helper.getLevel(),
                cave,
                origin);
        helper.assertTrue(referencePlaced && bulkPlaced,
                "A valid cave schematic was rejected during placement parity testing");
        for (BlockPos relative : BlockPos.betweenClosed(
                BlockPos.ZERO, new BlockPos(8, 5, 9))) {
            var regular = helper.getLevel().getBlockState(
                    referenceOrigin.offset(relative));
            var bulk = helper.getLevel().getBlockState(origin.offset(relative));
            helper.assertTrue(regular.getBlock() == bulk.getBlock(),
                    "Farm bulk placement changed the authored block at "
                            + relative + ": expected " + regular.getBlock()
                            + ", got " + bulk.getBlock());
        }
        helper.assertTrue(helper.getLevel().getBlockState(authoredAir).isAir(),
                "Farm bulk placement did not preserve authored air clearing");
        helper.succeed();
    }
}
