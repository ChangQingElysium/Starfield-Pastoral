package com.stardew.craft.animal.runtime;

import com.stardew.craft.port.PortItemStacks;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.animal.model.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.AnimalProduceSpotBlockEntity;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import com.stardew.craft.blockentity.TimedProductionBlockEntity;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.*;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder("stardewcraft_livestock")
@PrefixGameTestTemplate(false)
public final class LegacyLivestockMigrationGameTests {
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void legacyFieldsBirthProduceAndHaySurviveImportAndReload(GameTestHelper h) {
        try (var fixture = new Fixture(h, true, 2)) {
            var raw = fixture.snapshot(); var original = raw.copy();
            var buildings = new BuildingWorldData(); var animals = new LivestockWorldData();
            UUID fresh = UUID.randomUUID();
            animals.put(new LivestockRecord(fresh, fixture.owner, fixture.farm.getInstanceId(), UUID.randomUUID(), "New animal", 2, fixture.day, LivestockCare.purchased()));
            animals.hay(fixture.farm.getInstanceId(), 17);
            var migration = new LegacyLivestockMigration(raw);
            migration.apply(fixture.level.getServer(), buildings, animals);
            h.assertTrue(migration.issues().isEmpty(), "Migration issues: " + migration.issues());
            var migrated = animals.find(animals.legacyImport(fixture.animalKey()));
            h.assertTrue(migrated != null && migrated.name().equals("Old friend") && migrated.species() == LivestockSpecies.WHITE_COW, "Identity/species alias lost");
            h.assertTrue(migrated.care().equals(new LivestockCare(12, 10, 731, 212, 207, 3, 4, true, true)), "Care state changed during migration");
            h.assertTrue(migrated.cracker() && !migrated.reproduction() && migrated.produce().equals("milk"), "Cracker/reproduction/held product lost");
            h.assertTrue(migrated.settledDay() == fixture.day && migrated.extra().getCompound("LegacyAnimal").getString("UnknownField").equals("keep me"), "Baseline/raw extension state lost");
            h.assertTrue(animals.all().size() == 3 && animals.find(fresh) != null && animals.eggs().size() == 1, "Mixed save or pending birth duplicated/lost");
            h.assertTrue(animals.hay(fixture.farm.getInstanceId()) == 517, "Hay clamped or modern balance overwritten");
            var home = buildings.find(buildings.legacyImport(fixture.homeId));
            h.assertTrue(home.tier() == 2 && home.mode() == BuildingRecord.Mode.SELF_BUILT && home.phase() == BuildingRecord.Phase.READY, "Paid tier reset or construction replayed");
            h.assertTrue(raw.equals(original) && migration.sourceSnapshot().equals(original), "Archive mutated");
            buildings = BuildingWorldData.load(buildings.save(new CompoundTag()), fixture.level.registryAccess());
            animals = LivestockWorldData.load(animals.save(new CompoundTag()), fixture.level.registryAccess());
            migration = LegacyLivestockMigration.load(migration.save(new CompoundTag()), fixture.level.registryAccess());
            migration.apply(fixture.level.getServer(), buildings, animals);
            h.assertTrue(animals.all().size() == 3 && animals.eggs().size() == 1 && animals.hay(fixture.farm.getInstanceId()) == 517, "Reload replayed import");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void splitLedgerSaveSaleCollectionAndRehomeCannotResurrectLegacyState(GameTestHelper h) {
        try (var fixture = new Fixture(h, true, 1)) {
            var migration = new LegacyLivestockMigration(fixture.snapshot());
            var buildings = new BuildingWorldData(); var animals = new LivestockWorldData();
            migration.apply(fixture.level.getServer(), buildings, animals);
            UUID id = animals.legacyImport(fixture.animalKey());
            // Simulate only the animal ledger surviving an interrupted cross-file save.
            buildings = new BuildingWorldData();
            animals = LivestockWorldData.load(animals.save(new CompoundTag()), fixture.level.registryAccess());
            UUID movedHome = UUID.randomUUID(); animals.put(animals.find(id).rehome(movedHome).rename("Moved"));
            migration.apply(fixture.level.getServer(), buildings, animals);
            h.assertTrue(animals.find(id).home().equals(movedHome) && animals.find(id).name().equals("Moved"), "Replay reverted a rehome/rename");
            animals.remove(id); for (var product : animals.eggs()) animals.collect(product.id());
            animals.hay(fixture.farm.getInstanceId(), 0);
            animals = LivestockWorldData.load(animals.save(new CompoundTag()), fixture.level.registryAccess());
            migration.apply(fixture.level.getServer(), buildings, animals);
            h.assertTrue(animals.find(id) == null && animals.eggs().isEmpty() && animals.hay(fixture.farm.getInstanceId()) == 0, "Sold animal/collected product/spent hay resurrected");
            // The opposite partial save must recover animals without repeating building registration.
            var onlyBuildings = BuildingWorldData.load(buildings.save(new CompoundTag()), fixture.level.registryAccess());
            var emptyAnimals = new LivestockWorldData();
            migration.apply(fixture.level.getServer(), onlyBuildings, emptyAnimals);
            h.assertTrue(onlyBuildings.all().size() == 1 && emptyAnimals.find(id) != null, "Building-only save could not resume");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void missingAndConflictingHomesRetainAnimalsAndUnknownSpecies(GameTestHelper h) {
        try (var fixture = new Fixture(h, false, 1)) {
            var raw = fixture.snapshot(); raw.getList("animals", 10).getCompound(0).putString("animalTypeId", "missing_addon:alpaca");
            var migration = new LegacyLivestockMigration(raw); var buildings = new BuildingWorldData(); var animals = new LivestockWorldData();
            migration.apply(fixture.level.getServer(), buildings, animals);
            var animal = animals.find(animals.legacyImport(fixture.animalKey()));
            h.assertTrue(animal != null && animal.species().id().equals("missing_addon:alpaca") && !animal.species().known(), "Missing addon became a chicken or was lost");
            h.assertTrue(buildings.find(animal.home()).phase() == BuildingRecord.Phase.MISSING, "Missing manager accepted as working home");
            fixture.level.setBlock(fixture.manager, ModBlocks.BARN_MANAGER.get().defaultBlockState(), 3);
            var conflicting = BuildingRecord.waiting(fixture.farm.getInstanceId(), fixture.farm.getSlotIndex(), PrefabDefinitions.COOP,
                    BuildingRecord.Mode.SELF_BUILT, fixture.level.dimension().location(), fixture.manager.east(), fixture.manager.east(), Direction.SOUTH,
                    new BuildingBounds(fixture.manager.offset(-5, -1, -5), fixture.manager.offset(6, 4, 6)));
            buildings = new BuildingWorldData(); buildings.register(conflicting); animals = new LivestockWorldData();
            migration = new LegacyLivestockMigration(fixture.snapshot()); migration.apply(fixture.level.getServer(), buildings, animals);
            h.assertTrue(migration.issues().get("home:" + fixture.homeId).equals("building_overlap"), "Overlap not reported");
            h.assertTrue(buildings.all().size() == 1 && animals.find(animals.legacyImport(fixture.animalKey())) != null, "Conflict lost animal or stole existing claim");
            var unresolved = fixture.snapshot(); unresolved.getList("animals", 10).getCompound(0).putString("ownerPlayerUuid", UUID.randomUUID().toString());
            unresolved.remove("buildings"); migration = new LegacyLivestockMigration(unresolved);
            var target = new LivestockWorldData(); migration.apply(fixture.level.getServer(), new BuildingWorldData(), target);
            h.assertTrue(target.all().isEmpty() && migration.issues().containsKey(fixture.animalKey()) && migration.sourceSnapshot().equals(unresolved), "Unknown owner guessed or discarded");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void existingModernResidenceIsReusedWithoutResettingTierOrDoor(GameTestHelper h) {
        try (var fixture = new Fixture(h, true, 1)) {
            var buildings = new BuildingWorldData(); var animals = new LivestockWorldData();
            var existing = BuildingRecord.waiting(fixture.farm.getInstanceId(), fixture.farm.getSlotIndex(), PrefabDefinitions.BARN,
                    BuildingRecord.Mode.SELF_BUILT, fixture.level.dimension().location(), fixture.manager, fixture.manager, Direction.SOUTH,
                    new BuildingBounds(fixture.manager.offset(-5, -1, -5), fixture.manager.offset(6, 4, 6)));
            buildings.register(existing); buildings.acceptSelf(existing.id(), 0, 3);
            var before = buildings.find(existing.id()); animals.outdoorsAllowed(existing.id(), false);
            new LegacyLivestockMigration(fixture.snapshot()).apply(fixture.level.getServer(), buildings, animals);
            h.assertTrue(buildings.find(existing.id()).equals(before) && buildings.all().size() == 1, "Existing building replaced");
            h.assertTrue(animals.find(animals.legacyImport(fixture.animalKey())).home().equals(existing.id()) && !animals.outdoorsAllowed(existing.id()), "Existing home mapping/door changed");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void liveProjectionRejectsStaleEntitiesAndReplayedFloorProducts(GameTestHelper h) {
        var fixture = new Fixture(h, true, 1);
        fixture.awaitEntityChunks(h, () -> {
            try (fixture) {
                var snapshot = fixture.snapshot(); snapshot.remove("pendingAnimalBirths");
                var migration = new LegacyLivestockMigration(snapshot); migration.apply(fixture.level.getServer());
                var data = LivestockWorldData.get(fixture.level.getServer()); UUID id = data.legacyImport(fixture.animalKey());
                LivestockService.project(fixture.level.getServer());
                h.assertTrue(fixture.level.getEntity(id) instanceof LivestockEntity, "Migrated animal not projected into current runtime");
                var stale = ModEntities.COW.get().create(fixture.level); stale.setManagedAnimalId(fixture.animalId);
                stale.moveTo(fixture.manager.getX() + .5, fixture.manager.getY(), fixture.manager.getZ() + 1.5, 0, 0);
                h.assertTrue(!fixture.level.addFreshEntity(stale), "Unloaded legacy entity duplicated migrated animal");
                var position = fixture.manager.south(2);
                var egg = data.eggs().stream().filter(p -> p.animal().equals(id)).findFirst().orElseThrow();
                data.collect(egg.id());
                fixture.level.setBlock(position, ModBlocks.ANIMAL_PRODUCE_SPOT.get().defaultBlockState(), 3);
                var spot = (AnimalProduceSpotBlockEntity) fixture.level.getBlockEntity(position);
                spot.setAnimalId(fixture.animalId); spot.setBuildingId(fixture.homeId); spot.setProduceLedgerEntryId(fixture.animalId);
                spot.setProduceStack(new ItemStack(ModItems.EGG_WHITE.get()));
                h.assertTrue(LegacyLivestockMigration.migrateProductSpot(fixture.level, spot) && data.egg(egg.id()) == null, "Collected ledger product reappeared from stale block");
                fixture.level.setBlock(position, ModBlocks.ANIMAL_PRODUCE_SPOT.get().defaultBlockState(), 3);
                spot = (AnimalProduceSpotBlockEntity) fixture.level.getBlockEntity(position);
                spot.setAnimalId(fixture.animalId); spot.setBuildingId(fixture.homeId); spot.setProduceStack(new ItemStack(ModItems.EGG_WHITE.get()));
                var oldSpot = spot.saveWithFullMetadata();
                LegacyLivestockMigration.migrateProductSpot(fixture.level, spot);
                long count = data.eggs().stream().filter(p -> p.animal().equals(id)).count();
                var path = fixture.level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/stardew_livestock.dat");
                try {
                    var disk = com.stardew.craft.port.PortNbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.UNLIMITED).getCompound("data");
                    var saved = LivestockWorldData.load(disk, fixture.level.registryAccess());
                    h.assertTrue(saved.eggs().stream().anyMatch(p -> p.animal().equals(id)), "Old floor block retired before its product reached disk");
                } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                fixture.level.setBlock(position, ModBlocks.ANIMAL_PRODUCE_SPOT.get().defaultBlockState(), 3);
                spot = (AnimalProduceSpotBlockEntity) fixture.level.getBlockEntity(position); spot.load(oldSpot);
                LegacyLivestockMigration.migrateProductSpot(fixture.level, spot);
                h.assertTrue(data.eggs().stream().filter(p -> p.animal().equals(id)).count() == count && count == 1, "Pre-ledger spot duplicated across reload");
                data.remove(id);
                var sold = ModEntities.COW.get().create(fixture.level); sold.setManagedAnimalId(fixture.animalId);
                h.assertTrue(!fixture.level.addFreshEntity(sold), "Sold legacy animal respawned");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void missingHomePausesCareAndManagementRehousesWithoutCatchup(GameTestHelper h) {
        try (var fixture = new Fixture(h, false, 1)) {
            var raw = fixture.snapshot(); raw.remove("pendingAnimalBirths");
            new LegacyLivestockMigration(raw).apply(fixture.level.getServer());
            var data = LivestockWorldData.get(fixture.level.getServer());
            var animal = data.find(data.legacyImport(fixture.animalKey()));
            animal = animal.withCare(fixture.day - 5, animal.care()); data.put(animal);
            LivestockService.onNewDay(fixture.level);
            h.assertTrue(data.find(animal.id()).equals(animal), "Missing home consumed days of care");
            fixture.level.setBlock(fixture.manager, ModBlocks.BARN_MANAGER.get().defaultBlockState(), 3);
            var buildings = BuildingWorldData.get(fixture.level.getServer());
            var home = BuildingRecord.waiting(fixture.farm.getInstanceId(), fixture.farm.getSlotIndex(), PrefabDefinitions.BARN,
                    BuildingRecord.Mode.SELF_BUILT, fixture.level.dimension().location(), fixture.manager, fixture.manager, Direction.SOUTH,
                    new BuildingBounds(fixture.manager.offset(-5, -1, -5), fixture.manager.offset(6, 4, 6)));
            h.assertTrue(buildings.register(home) == BuildingWorldData.Result.SUCCESS, "Cannot register replacement home");
            buildings.acceptSelf(home.id(), 0, 1);
            var player = FakePlayerFactory.get(fixture.level, new GameProfile(fixture.owner, "RehomeKeeper")); player.moveTo(fixture.manager.getCenter());
            var nonce = LivestockManagement.open(player, animal.id().toString());
            var result = LivestockManagement.apply(player, new LivestockManagePayload(nonce, animal.id(), home.id(), "move", ""));
            h.assertTrue(result.isEmpty(), "Migrated orphan could not use management: " + result);
            var moved = data.find(animal.id());
            h.assertTrue(moved.home().equals(home.id()) && moved.settledDay() == fixture.day && moved.care().equals(animal.care()), "Rehousing penalized paused days");
            h.assertTrue(data.eggs().stream().filter(p -> p.animal().equals(moved.id())).allMatch(p -> p.home().equals(home.id())), "Stranded products did not follow animal");
            var position = fixture.manager.south(2);
            fixture.level.setBlock(position, ModBlocks.ANIMAL_PRODUCE_SPOT.get().defaultBlockState(), 3);
            var spot = (AnimalProduceSpotBlockEntity) fixture.level.getBlockEntity(position);
            spot.setAnimalId(fixture.animalId); spot.setBuildingId(fixture.homeId); spot.setProduceStack(new ItemStack(ModItems.EGG_WHITE.get()));
            LegacyLivestockMigration.migrateProductSpot(fixture.level, spot);
            h.assertTrue(data.eggs().stream().filter(p -> p.animal().equals(moved.id())).allMatch(p -> p.home().equals(home.id())), "Late-loaded product was stranded in removed home");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void legacyIncubationKeepsRemainingTimeAndCannotHatchAgainAfterSale(GameTestHelper h) {
        try (var fixture = new Fixture(h, true, 2, "coop")) {
            new LegacyLivestockMigration(fixture.snapshot()).apply(fixture.level.getServer());
            var position = fixture.manager.west(3);
            fixture.level.setBlock(position, ModBlocks.INCUBATOR.get().defaultBlockState(), 3);
            var incubator = (IncubatorBlockEntity) fixture.level.getBlockEntity(position);
            var raw = new CompoundTag(); raw.put("input", PortItemStacks.save(new ItemStack(ModItems.OSTRICH_EGG.get()), fixture.level.registryAccess()));
            long oldMinute = (fixture.day - 1L) * TimedProductionBlockEntity.LEGACY_MINUTES_PER_DAY + Math.max(0, StardewTimeManager.get().getCurrentTime() - 360);
            raw.putLong("readyAtAbsMinute", oldMinute + 500); raw.putBoolean("ready", false);
            incubator.load(raw);
            var player = FakePlayerFactory.get(fixture.level, new GameProfile(fixture.owner, "LegacyKeeper")); player.moveTo(position.getCenter());
            // The old completion moment (day + clock time) is kept on the 1600-minute machine clock.
            long remaining = TimedProductionBlockEntity.migrateLegacyAbsMinute(oldMinute + 500) - TimedProductionBlockEntity.getCurrentAbsMinute();
            h.assertTrue(remaining >= 500 && incubator.hasInput() && incubator.getRemainingAbsMinutes() == remaining, "Legacy input cleared or old clock misread");
            h.assertTrue(incubator.claimReadyAnimal(player, "Early") == IncubatorBlockEntity.ClaimResult.NOT_READY, "Unfinished egg hatched early");
            h.assertTrue(incubator.getRemainingAbsMinutes() == remaining, "Clock conversion restarted/shortened incubation");
            raw.putBoolean("ready", true); incubator.load(raw);
            var result = incubator.claimReadyAnimal(player, "Legacy hatch");
            h.assertTrue(result == IncubatorBlockEntity.ClaimResult.SUCCESS, "Ready legacy egg not claimable: " + result);
            var data = LivestockWorldData.get(fixture.level.getServer());
            var baby = data.all().stream().filter(a -> a.name().equals("Legacy hatch")).findFirst().orElseThrow();
            data.remove(baby.id()); incubator.load(raw);
            h.assertTrue(incubator.claimReadyAnimal(player, "Duplicate") == IncubatorBlockEntity.ClaimResult.SUCCESS && !incubator.hasInput(), "Old incubation receipt not recovered");
            h.assertTrue(data.find(baby.id()) == null, "Sold hatchling resurrected from old incubator NBT");
        }
        h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final UUID owner = UUID.randomUUID();
        final FarmInstance farm;
        final BlockPos manager;
        final String homeId = "barn_" + UUID.randomUUID();
        final long animalId = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), Long.MAX_VALUE - 1) + 1;
        final int day = StardewTimeManager.get().getAbsoluteDay();
        final int tier;
        final String family;
        final Set<ChunkPos> forcedChunks = new LinkedHashSet<>();
        boolean closed;
        Fixture(GameTestHelper h, boolean managerPresent, int tier) {
            this(h, managerPresent, tier, "barn");
        }
        Fixture(GameTestHelper h, boolean managerPresent, int tier, String family) {
            this.level = h.getLevel(); this.tier = tier; this.family = family;
            farm = FarmInstanceRegistry.get(level.getServer()).createFarm(owner, "Legacy", "Migration", FarmType.STANDARD);
            manager = farm.getFarmBoundsMin().offset(12, 3, 12);
            var bounds = new BuildingBounds(manager.offset(-5, -1, -5), manager.offset(6, 4, 6)); LivestockHomes.load(level, bounds);
            for (var pos : BlockPos.betweenClosed(bounds.min(), bounds.maxInclusive())) level.setBlock(pos, pos.getY() == manager.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            if (managerPresent) level.setBlock(manager, (family.equals("coop") ? ModBlocks.COOP_MANAGER : ModBlocks.BARN_MANAGER).get().defaultBlockState(), 3);
            for (int x = -4; x <= 3; x++) level.setBlock(manager.offset(x, 0, -3), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
            level.setBlock(manager.north(2), ModBlocks.HAY_HOPPER.get().defaultBlockState(), 3);
            if (family.equals("coop")) level.setBlock(manager.west(3), ModBlocks.INCUBATOR.get().defaultBlockState(), 3);
        }
        String animalKey() { return "animal:" + animalId; }
        void awaitEntityChunks(GameTestHelper h, Runnable action) {
            var chunks = new ArrayList<ChunkPos>();
            var fullNeighbors = new LinkedHashSet<ChunkPos>();
            try {
                var bounds = new BuildingBounds(manager.offset(-5, -1, -5), manager.offset(6, 4, 6));
                for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++) {
                    for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) {
                        var chunk = new ChunkPos(x, z); chunks.add(chunk);
                        if (!level.getForcedChunks().contains(chunk.toLong())) {
                            forcedChunks.add(chunk);
                            level.setChunkForced(x, z, true);
                        }
                        // Both versions' ChunkMap#prepareEntityTickingChunk require radius-two FULL
                        // neighbors before the asynchronous full-status callback can expose entities.
                        // Prepare that dependency before spending this remote fixture's bounded tick
                        // budget; synchronously loading only the home is not entity-ticking readiness.
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) fullNeighbors.add(new ChunkPos(x + dx, z + dz));
                        }
                    }
                }
                for (var chunk : fullNeighbors) level.getChunk(chunk.x, chunk.z);
                awaitEntityChunks(h, chunks, action, 0);
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        private void awaitEntityChunks(GameTestHelper h, List<ChunkPos> chunks, Runnable action, int waitedTicks) {
            try {
                boolean ready = chunks.stream().allMatch(chunk -> level.areEntitiesLoaded(chunk.toLong())
                        && level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX(), manager.getY(), chunk.getMinBlockZ())));
                if (ready) {
                    com.stardew.craft.StardewCraft.LOGGER.info(
                            "[LEGACY-LIVESTOCK-GAMETEST] Entity chunks ready after {} ticks; {} fixture-owned tickets",
                            waitedTicks, forcedChunks.size());
                    action.run();
                    return;
                }
                h.assertTrue(waitedTicks < 100, "Timed out waiting for entity-ready fixture chunks: " + chunks
                        + "; states=" + chunks.stream().map(chunk -> chunk + "{loaded="
                        + level.areEntitiesLoaded(chunk.toLong()) + ", ticking="
                        + level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX(), manager.getY(), chunk.getMinBlockZ()))
                        + ", forced=" + level.getForcedChunks().contains(chunk.toLong()) + "}").toList());
                h.runAfterDelay(1, () -> awaitEntityChunks(h, chunks, action, waitedTicks + 1));
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        CompoundTag snapshot() {
            var home = new AnimalBuildingRecord(homeId, owner.toString(), AnimalBuildingType.of(family, tier), "Old barn", level.dimension().location().toString(),
                    manager, 4, manager.getX()-4, manager.getY(), manager.getZ()-4, manager.getX()+4, manager.getY()+2, manager.getZ()+4,
                    tier*4, 0, true, true, Set.of(), Set.of(), Set.of(animalId));
            var animal = new FarmAnimalRecord(animalId, "cow", "Old friend", homeId, AnimalAcquisitionSource.PURCHASE,
                    1, 0, 1, 12, 5, true, true, 731, false, 10, true, 207, 212, 2, 3, "milk", 4, true);
            animal.setOwnerPlayerUuid(owner.toString()); animal.setLastProcessedAbsDay(0);
            var row = animal.save(); row.putString("UnknownField", "keep me");
            var root = new CompoundTag(); list(root, "buildings", home.save()); list(root, "animals", row);
            list(root, "animalProduceLedger", new AnimalProduceLedgerEntry(animalId, homeId, animalId, day,
                    new net.minecraft.resources.ResourceLocation("stardewcraft:egg_white"), 4, "", 0).save());
            list(root, "pendingAnimalBirths", new AnimalPendingBirth(animalId, owner.toString(), homeId, animalId, "cow", day).save());
            var hay = new CompoundTag(); hay.putString("ownerPlayerUuid", owner.toString()); hay.putInt("pieces", 500); list(root, "hayByOwner", hay);
            return root;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try { FarmInstanceRegistry.get(level.getServer()).deleteFarm(owner); }
            finally {
                for (var chunk : forcedChunks) level.setChunkForced(chunk.x, chunk.z, false);
                forcedChunks.clear();
            }
        }
    }
    private static void list(CompoundTag parent, String key, CompoundTag row) { var list = new ListTag(); list.add(row); parent.put(key, list); }
}
