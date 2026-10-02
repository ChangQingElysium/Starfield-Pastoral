package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.greenhouse.GreenhouseBuildings;
import com.stardew.craft.greenhouse.GreenhouseManager;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.FertilizerManager;
import com.stardew.craft.mining.StructureLoader;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The enabled 48x24x48 building fixture contains the entire authored 19x11x20 interior. */
@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class GreenhouseSoilGameTests {
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private GreenhouseSoilGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void authoredInteriorContainsOwnPlantableDirtWithoutChangingDecoration(GameTestHelper helper)
            throws Exception {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 0, 6));
        CompoundTag schematic;
        try (var stream = GreenhouseSoilGameTests.class.getClassLoader()
                .getResourceAsStream(GreenhouseManager.INTERIOR_STRUCTURE_PATH)) {
            helper.assertTrue(stream != null, "Missing shipped greenhouse schematic");
            schematic = NbtIo.readCompressed(stream);
        }
        if (schematic.contains("Schematic", Tag.TAG_COMPOUND)) schematic = schematic.getCompound("Schematic");
        int width = schematic.getShort("Width") & 0xffff;
        int height = schematic.getShort("Height") & 0xffff;
        int length = schematic.getShort("Length") & 0xffff;
        helper.assertTrue(width == 19 && height == 11 && length == 20,
                "Greenhouse migration bounds do not match the shipped schematic");
        CompoundTag palette = schematic.getCompound("Palette");
        byte[] bytes = schematic.getByteArray("BlockData");
        if (palette.isEmpty()) palette = schematic.getCompound("Blocks").getCompound("Palette");
        if (bytes.length == 0) bytes = schematic.getCompound("Blocks").getByteArray("Data");
        Map<Integer, ResourceLocation> ids = new java.util.HashMap<>();
        Map<Integer, BlockState> authoredStates = new java.util.HashMap<>();
        var parseState = StructureLoader.class.getDeclaredMethod("parseBlockState", String.class);
        parseState.setAccessible(true);
        for (String state : palette.getAllKeys()) {
            String name = state.split("\\[", 2)[0];
            ResourceLocation id = new ResourceLocation(name);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Unknown authored greenhouse block " + id);
            ids.put(palette.getInt(state), id);
            authoredStates.put(palette.getInt(state), (BlockState) parseState.invoke(null, state));
        }
        helper.assertTrue(StructureLoader.loadAndPlaceWithResult(
                level, GreenhouseManager.INTERIOR_STRUCTURE_PATH, origin), "Shipped greenhouse could not be placed");
        int byteIndex = 0;
        int ownDirt = 0;
        int normalizedUpperPlanterExtensions = 0;
        for (int index = 0; index < width * height * length; index++) {
            int paletteIndex = 0;
            int shift = 0;
            int part;
            do {
                helper.assertTrue(byteIndex < bytes.length && shift < 35, "Incomplete greenhouse block data");
                part = bytes[byteIndex++] & 0xff;
                paletteIndex |= (part & 0x7f) << shift;
                shift += 7;
            } while ((part & 0x80) != 0);
            ResourceLocation id = ids.get(paletteIndex);
            helper.assertTrue(id != null, "Missing greenhouse palette index " + paletteIndex);
            helper.assertTrue(!id.equals(BuiltInRegistries.BLOCK.getKey(Blocks.DIRT))
                            && !id.equals(BuiltInRegistries.BLOCK.getKey(Blocks.FARMLAND))
                            && !id.equals(BuiltInRegistries.BLOCK.getKey(ModBlocks.YELLOW_DIRT.get())),
                    "Authored greenhouse still contains foreign or special yellow planting soil: " + id);
            BlockPos pos = origin.offset(index % width, index / (width * length), (index / width) % length);
            Block expected = BuiltInRegistries.BLOCK.get(id);
            BlockState authored = authoredStates.get(paletteIndex);
            BlockState actual = level.getBlockState(pos);
            // Four legacy planters each have three upper extension cells outside the current
            // collision footprint. Existing loader shape normalization prunes only unsupported
            // extensions; the planter MAIN and every supported extension must still retain identity.
            boolean normalizedUpperPlanterExtension = authored.is(ModBlocks.LONG_POTTED_PLANT.get())
                    && authored.hasProperty(MapDecorStaticBlock.PART)
                    && authored.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION
                    && !authored.canSurvive(level, pos) && actual.isAir();
            helper.assertTrue(actual.is(expected) || normalizedUpperPlanterExtension,
                    "Schematic placement changed block identity at " + pos + ": authored=" + authored + ", actual=" + actual);
            if (normalizedUpperPlanterExtension) normalizedUpperPlanterExtensions++;
            if (expected == ModBlocks.DIRT.get()) ownDirt++;
        }
        helper.assertTrue(ownDirt == 120, "Greenhouse schematic no longer has its 120 ordinary mod dirt planting cells");
        helper.assertTrue(normalizedUpperPlanterExtensions == 12,
                "Shape normalization did not prune exactly the twelve legacy upper planter extensions");
        helper.assertTrue(GreenhouseBuildings.migrateInteriorSoil(level, origin) == 0,
                "A freshly placed current greenhouse still requires legacy soil conversion");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void legacyMigrationPreservesMoisturePlantsSavedDataContainersAndBoundaries(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos dirt = origin.offset(2, 1, 2), yellow = origin.offset(3, 1, 2);
        level.setBlock(dirt, Blocks.DIRT.defaultBlockState(), QUIET_FLAGS);
        level.setBlock(yellow, ModBlocks.YELLOW_DIRT.get().defaultBlockState(), QUIET_FLAGS);
        level.setBlock(origin, Blocks.DIRT.defaultBlockState(), QUIET_FLAGS);
        level.setBlock(origin.offset(18, 10, 19), Blocks.DIRT.defaultBlockState(), QUIET_FLAGS);
        for (int moisture = 0; moisture <= 7; moisture++) {
            level.setBlock(origin.offset(2 + moisture, 1, 4),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture), QUIET_FLAGS);
        }
        Map<BlockPos, BlockState> retained = new LinkedHashMap<>();
        Block[] ownAndDecorative = {ModBlocks.DIRT.get(), ModBlocks.FARMLAND.get(),
                ModBlocks.SANDY_FARMLAND.get(), ModBlocks.INFERTILE_FARMLAND.get(), ModBlocks.HARD_SOIL.get(),
                Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MUD,
                Blocks.STONE, Blocks.GLASS, Blocks.STONE_BRICKS};
        for (int index = 0; index < ownAndDecorative.length; index++) {
            Block block = ownAndDecorative[index];
            var states = block.getStateDefinition().getPossibleStates();
            BlockState state = states.get(states.size() - 1);
            retain(level, retained, origin.offset(2 + index, 1, 12), state);
        }
        // All writes remain within the air fixture. The upper outside cell is observed, not claimed.
        for (BlockPos outside : new BlockPos[]{origin.offset(-1, 1, 3), origin.offset(19, 1, 3),
                origin.offset(3, 1, -1), origin.offset(3, 1, 20), origin.offset(3, -1, 3)}) {
            retain(level, retained, outside, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 6));
        }
        BlockPos aboveBounds = origin.offset(3, 11, 3);
        retained.put(aboveBounds, level.getBlockState(aboveBounds));
        BlockPos chestPos = origin.offset(3, 1, 15);
        retain(level, retained, chestPos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        helper.assertTrue(chest != null, "Missing legacy container fixture");
        CompoundTag namedChest = chest.saveWithoutMetadata();
        namedChest.putString("CustomName", "{\"text\":\"Greenhouse migration fixture\"}");
        chest.load(namedChest);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
        chest.setItem(13, new ItemStack(ModItems.PARSNIP_SEEDS.get(), 6));
        CompoundTag chestSaved = chest.saveWithoutMetadata();
        BlockPos cropSoil = origin.offset(8, 1, 8), cropPos = cropSoil.above();
        level.setBlock(cropSoil, ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 7), QUIET_FLAGS);
        BlockState crop = ModBlocks.PARSNIP_CROP.get().defaultBlockState().setValue(StardewCropBlock.AGE, 2);
        level.setBlock(cropPos, crop, QUIET_FLAGS);
        // Emulate a legacy save silently retaining its existing crop over vanilla farmland.
        level.setBlock(cropSoil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), QUIET_FLAGS);
        var crops = CropGrowthManager.get(level);
        var fertilizer = FertilizerManager.get(level);
        var growth = crops.getOrCreateState(level, cropPos);
        growth.planterUuid = UUID.randomUUID();
        growth.phase = 2;
        growth.dayInPhase = 3;
        growth.infertileProgress = 1;
        growth.lastDailyDay = 31;
        growth.lastDailyWatered = true;
        growth.lastGiantDay = 30;
        try {
            helper.assertTrue(fertilizer.tryApplyFertilizer(level, cropSoil, FertilizerType.DELUXE_SPEED_GRO),
                    "Could not prepare legacy fertilizer data");
            CompoundTag cropsSaved = crops.save(new CompoundTag());
            CompoundTag fertilizerSaved = fertilizer.save(new CompoundTag());
            helper.assertTrue(GreenhouseBuildings.migrateInteriorSoil(level, origin) == 13,
                    "Migration did not convert exactly the 13 legacy soil cells");
            helper.assertTrue(level.getBlockState(dirt).is(ModBlocks.DIRT.get())
                            && level.getBlockState(yellow).is(ModBlocks.DIRT.get())
                            && level.getBlockState(origin).is(ModBlocks.DIRT.get())
                            && level.getBlockState(origin.offset(18, 10, 19)).is(ModBlocks.DIRT.get()),
                    "Legacy dirt conversion missed a cell or changed ordinary dirt into special soil");
            for (int moisture = 0; moisture <= 7; moisture++) {
                BlockState converted = level.getBlockState(origin.offset(2 + moisture, 1, 4));
                helper.assertTrue(converted.is(ModBlocks.FARMLAND.get()) && converted.getValue(FarmBlock.MOISTURE) == moisture,
                        "Legacy farmland lost moisture " + moisture);
            }
            helper.assertTrue(level.getBlockState(cropSoil).is(ModBlocks.FARMLAND.get())
                            && level.getBlockState(cropSoil).getValue(FarmBlock.MOISTURE) == 7
                            && level.getBlockState(cropPos).equals(crop) && crop.canSurvive(level, cropPos),
                    "Legacy crop was broken or left on an unplantable substrate");
            helper.assertTrue(crops.getState(level, cropPos) == growth
                            && crops.getAllCropPositions().contains(GlobalPos.of(level.dimension(), cropPos))
                            && cropsSaved.equals(crops.save(new CompoundTag()))
                            && fertilizerSaved.equals(fertilizer.save(new CompoundTag()))
                            && fertilizer.getFertilizer(level, cropSoil) == FertilizerType.DELUXE_SPEED_GRO,
                    "Soil migration altered crop registration, growth history, ownership or fertilizer data");
            helper.assertTrue(level.getBlockEntity(chestPos) == chest
                            && chestSaved.equals(chest.saveWithoutMetadata()),
                    "Soil migration changed the container, contents or block entity data");
            for (var entry : retained.entrySet()) {
                helper.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                        "Migration changed current soil, decoration or an outside cell at " + entry.getKey());
            }
            helper.assertTrue(GreenhouseBuildings.migrateInteriorSoil(level, origin) == 0,
                    "Legacy greenhouse soil migration is not idempotent");
        } finally {
            fertilizer.removeFertilizer(level, cropSoil);
            crops.removeCrop(level, cropPos);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void migratedLegacySoilWorksWithActualHoeAndSeedsWithoutAllowingForeignSoil(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 0, 6));
        BlockPos[] soils = {origin.offset(3, 1, 3), origin.offset(5, 1, 3), origin.offset(7, 1, 3)};
        BlockPos outsideTop = origin.offset(7, 11, 3);
        BlockState outsideSoil = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 4);
        var player = PortGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        try {
            clock.setCurrentSeason(0);
            level.setBlock(soils[0], Blocks.DIRT.defaultBlockState(), QUIET_FLAGS);
            level.setBlock(soils[1], ModBlocks.YELLOW_DIRT.get().defaultBlockState(), QUIET_FLAGS);
            level.setBlock(soils[2], Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), QUIET_FLAGS);
            // This lower origin leaves room for a true upper-boundary legacy soil sentinel inside the fixture.
            level.setBlock(outsideTop, outsideSoil, QUIET_FLAGS);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PARSNIP_SEEDS.get(), 2));
            var seed = ModItems.PARSNIP_SEEDS.get();
            helper.assertTrue(!seed.useOn(context(player, soils[2])).consumesAction()
                            && player.getMainHandItem().getCount() == 2 && level.getBlockState(soils[2].above()).isAir(),
                    "Fix allowed ordinary imported farmland to bypass the foreign-soil planting rule");
            helper.assertTrue(GreenhouseBuildings.migrateInteriorSoil(level, origin) == 3, "Planting fixture was not migrated");
            helper.assertTrue(level.getBlockState(outsideTop).equals(outsideSoil),
                    "Greenhouse migration converted soil above the fixed interior bounds");
            for (int index = 0; index < soils.length; index++) {
                BlockPos soil = soils[index];
                if (index < 2) {
                    var hoe = ModItems.HOE.get();
                    player.getCooldowns().removeCooldown(hoe);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hoe));
                    helper.assertTrue(hoe.useOn(context(player, soil)).consumesAction()
                                    && level.getBlockState(soil).is(ModBlocks.FARMLAND.get()),
                            "Actual starter hoe could not till migrated ordinary mod dirt");
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed, 2));
                helper.assertTrue(seed.useOn(context(player, soil)).consumesAction()
                                && player.getMainHandItem().getCount() == 1
                                && level.getBlockState(soil.above()).is(ModBlocks.PARSNIP_CROP.get())
                                && level.getBlockState(soil.above()).canSurvive(level, soil.above()),
                        "Actual seed did not plant and survive on migrated greenhouse soil " + index);
            }
        } finally {
            clock.setCurrentSeason(oldSeason);
            for (BlockPos soil : soils) CropGrowthManager.get(level).removeCrop(level, soil.above());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void readyExteriorMigratesOnlyAuthoredSoilInAllFacingsWithoutReplayingItsFoundation(GameTestHelper helper) {
        var level = helper.getLevel();
        var family = PrefabDefinitions.get(GreenhouseBuildings.FAMILY);
        var tier = family.tier(1);
        var template = PrefabDefinitions.template(level, tier);
        var soilCells = template.cells().stream().filter(cell -> cell.state().is(ModBlocks.DIRT.get())
                || cell.state().is(ModBlocks.FARMLAND.get())).toList();
        helper.assertTrue(soilCells.size() == 23 && template.retainedGround().size() == 52,
                "Exterior soil projection or retained-ground fixture coverage changed");
        BlockPos anchor = helper.absolutePos(new BlockPos(24, 3, 24));
        var dirtVariants = ModBlocks.DIRT.get().getStateDefinition().getPossibleStates();
        BlockState authoredDirtVariant = dirtVariants.get(dirtVariants.size() - 1);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var rotation = PrefabDefinitions.rotation(facing);
            var record = new BuildingRecord(UUID.randomUUID(), UUID.randomUUID(), 0, GreenhouseBuildings.FAMILY,
                    BuildingRecord.Mode.PREFAB, level.dimension().location(), anchor,
                    PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, rotation), facing,
                    PrefabDefinitions.transform(family.reservation(), anchor, rotation),
                    BuildingRecord.Phase.READY, 1, BuildingRecord.Residence.VALID, 0, "");
            Map<BlockPos, BlockState> expected = new LinkedHashMap<>();
            Map<BlockPos, BlockState> retained = new LinkedHashMap<>();
            for (int index = 0; index < soilCells.size(); index++) {
                BlockPos pos = PrefabDefinitions.world(soilCells.get(index).pos(), tier.anchor(), anchor, rotation);
                BlockState actual;
                BlockState converted;
                if (index < 8) {
                    actual = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, index);
                    converted = ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, index);
                } else if (index >= 14 && index <= 20) {
                    actual = switch (index) {
                        case 14 -> authoredDirtVariant;
                        case 15 -> ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 5);
                        case 16 -> ModBlocks.SANDY_FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 6);
                        case 17 -> ModBlocks.INFERTILE_FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 2);
                        case 18 -> Blocks.STONE_BRICKS.defaultBlockState();
                        case 19 -> Blocks.COARSE_DIRT.defaultBlockState();
                        default -> Blocks.ROOTED_DIRT.defaultBlockState();
                    };
                    converted = actual;
                } else {
                    actual = index % 2 == 0 ? Blocks.DIRT.defaultBlockState() : ModBlocks.YELLOW_DIRT.get().defaultBlockState();
                    converted = ModBlocks.DIRT.get().defaultBlockState();
                }
                level.setBlock(pos, actual, QUIET_FLAGS);
                expected.put(pos, converted);
            }
            int groundIndex = 0;
            for (BlockPos local : template.retainedGround()) {
                BlockState actual = switch (groundIndex++ % 3) {
                    case 0 -> Blocks.DIRT.defaultBlockState();
                    case 1 -> ModBlocks.YELLOW_DIRT.get().defaultBlockState();
                    default -> authoredDirtVariant;
                };
                retain(level, retained, PrefabDefinitions.world(local, tier.anchor(), anchor, rotation), actual);
            }
            // This is inside the claim but not an authored planting-soil cell: no volume-wide rewrite is allowed.
            retain(level, retained, PrefabDefinitions.world(new BlockPos(1, 3, 1), tier.anchor(), anchor, rotation),
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 6));
            BlockPos firstSoil = PrefabDefinitions.world(soilCells.get(0).pos(), tier.anchor(), anchor, rotation);
            BlockPos cropPos = firstSoil.above();
            BlockState crop = ModBlocks.PARSNIP_CROP.get().defaultBlockState().setValue(StardewCropBlock.AGE, 2);
            level.setBlock(cropPos, crop, QUIET_FLAGS);
            try {
                if (facing == Direction.SOUTH) {
                    BlockState before = level.getBlockState(firstSoil);
                    for (int guard = 0; guard < 3; guard++) {
                        var unrelated = new BuildingRecord(record.id(), record.farmId(), record.farmSlot(),
                                guard == 1 ? PrefabDefinitions.BARN : record.family(),
                                guard == 0 ? BuildingRecord.Mode.SELF_BUILT : record.mode(),
                                guard == 2 ? new ResourceLocation("minecraft:the_nether") : record.dimension(),
                                record.anchor(), record.manager(), record.facing(), record.claim(),
                                record.phase(), record.tier(), record.residence(), record.revision(), record.displayName());
                        GreenhouseBuildings.ensurePortal(level, unrelated);
                        helper.assertTrue(level.getBlockState(firstSoil).equals(before),
                                "Exterior soil migration bypassed mode, family or dimension guard " + guard);
                    }
                }
                GreenhouseBuildings.ensurePortal(level, record);
                for (var entry : expected.entrySet()) {
                    helper.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                            "Exterior soil identity, variant or moisture was lost for " + facing + " at " + entry.getKey());
                }
                for (var entry : retained.entrySet()) {
                    helper.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                            "Exterior migration replayed the foundation or rewrote non-soil decoration for " + facing);
                }
                helper.assertTrue(level.getBlockState(cropPos).equals(crop) && crop.canSurvive(level, cropPos)
                                && CropGrowthManager.get(level).getState(level, cropPos) != null,
                        "Exterior migration destroyed an existing crop or its registration for " + facing);
                GreenhouseBuildings.ensurePortal(level, record);
                for (var entry : expected.entrySet()) {
                    helper.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                            "READY exterior soil migration changed its result on a second pass for " + facing);
                }
            } finally {
                InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(level, GreenhouseBuildings.portal(record));
                CropGrowthManager.get(level).removeCrop(level, cropPos);
            }
        }
        helper.succeed();
    }

    private static void retain(net.minecraft.server.level.ServerLevel level,
            Map<BlockPos, BlockState> retained, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, QUIET_FLAGS);
        retained.put(pos, state);
    }

    private static UseOnContext context(net.minecraft.world.entity.player.Player player, BlockPos soil) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(soil), Direction.UP, soil, false));
    }
}
