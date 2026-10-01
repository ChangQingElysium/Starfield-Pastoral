package com.stardew.craft.animal.runtime;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.model.*;
import com.stardew.craft.blockentity.AnimalProduceSpotBlockEntity;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** One-way import. The immutable source archive is saved before either target ledger changes. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class LegacyLivestockMigration extends SavedData {
    private static final String DATA_NAME = "stardew_livestock_migration";
    private CompoundTag source = new CompoundTag();
    private boolean initialized;
    private boolean complete;
    private final Map<String, String> issues = new LinkedHashMap<>();
    private int lastTick = Integer.MIN_VALUE;
    private boolean running;

    public LegacyLivestockMigration() {}

    /** Detached archive constructor also used by regression fixtures; never mutates the supplied NBT. */
    public LegacyLivestockMigration(CompoundTag snapshot) {
        source = snapshot.copy(); initialized = true; setDirty();
    }

    public static LegacyLivestockMigration get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                com.stardew.craft.port.PortSavedData.loader(new com.stardew.craft.port.PortSavedData.Factory<>(LegacyLivestockMigration::new, LegacyLivestockMigration::load)), com.stardew.craft.port.PortSavedData.constructor(new com.stardew.craft.port.PortSavedData.Factory<>(LegacyLivestockMigration::new, LegacyLivestockMigration::load)), DATA_NAME);
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) { LivestockService.recover(event.getServer()); }

    public static void ensure(MinecraftServer server) {
        var migration = get(server);
        if (migration.running || migration.complete) return;
        int tick = server.getTickCount();
        if (migration.lastTick != Integer.MIN_VALUE && tick - migration.lastTick < 100) return;
        migration.lastTick = tick;
        migration.running = true;
        try {
            if (!migration.initialized) {
                var path = server.getWorldPath(LevelResource.ROOT).resolve("data/stardew_animal_world.dat");
                // Read the raw source, before its old loader can clamp hay or discard unknown fields.
                if (Files.exists(path)) {
                    try (var input = Files.newInputStream(path)) {
                        var root = com.stardew.craft.port.PortNbtIo.readCompressed(input, new net.minecraft.nbt.NbtAccounter(64L * 1024 * 1024));
                        if (!root.contains("data", Tag.TAG_COMPOUND)) throw new IllegalStateException("Invalid legacy animal save root");
                        migration.source = root.getCompound("data").copy();
                        if (migration.source.getInt("animalSchemaVersion") > com.stardew.craft.animal.data.AnimalWorldDataMigrations.CURRENT_VERSION)
                            throw new IllegalStateException("Legacy animal save is newer than this migration supports");
                    } catch (IOException exception) {
                        throw new IllegalStateException("Cannot archive legacy animal save", exception);
                    }
                }
                migration.initialized = true; migration.setDirty();
            }
            // Also retry an archive write that failed after in-memory initialization.
            checkpoint(server, DATA_NAME, migration);
            migration.apply(server);
            checkpoint(server, "stardew_buildings", BuildingWorldData.get(server));
            checkpointAnimals(server);
            checkpoint(server, DATA_NAME, migration);
            // Only projections are removed. Receipts survive sale, collection, rehoming and reload.
            for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
                if (entity instanceof BaseCoopAnimalEntity animal && superseded(server, animal)) animal.discard();
            }
        } catch (RuntimeException exception) {
            migration.complete = false; migration.setDirty();
            BuildingWorldData.get(server).setDirty(); LivestockWorldData.get(server).setDirty();
            throw exception;
        } finally { migration.running = false; }
    }

    /** SavedData.save queues writes and swallows I/O errors; projection retirement needs a checked barrier. */
    private static void checkpoint(MinecraftServer server, String name, SavedData data) {
        com.stardew.craft.port.net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        if (!data.isDirty()) return;
        var root = new CompoundTag(); root.put("data", data.save(new CompoundTag(), server.registryAccess()));
        NbtUtils.addCurrentDataVersion(root);
        var path = server.getWorldPath(LevelResource.ROOT).resolve("data/" + name + ".dat");
        try {
            Files.createDirectories(path.getParent());
            com.stardew.craft.port.net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(root, path);
            data.setDirty(false);
        } catch (IOException exception) { throw new IllegalStateException("Cannot checkpoint animal migration: " + name, exception); }
    }

    public static void checkpointAnimals(MinecraftServer server) {
        checkpoint(server, "stardew_livestock", LivestockWorldData.get(server));
    }

    public Map<String, String> issues() { return Map.copyOf(issues); }
    public CompoundTag sourceSnapshot() { return source.copy(); }
    public boolean containsSource(String collection, String field, long id) {
        return source.getList(collection, Tag.TAG_COMPOUND).stream().anyMatch(row -> ((CompoundTag) row).getLong(field) == id);
    }

    /** Applies from the frozen archive; each target writes its receipt together with its imported value. */
    public void apply(MinecraftServer server) {
        apply(server, BuildingWorldData.get(server), LivestockWorldData.get(server));
    }

    void apply(MinecraftServer server, BuildingWorldData buildings, LivestockWorldData livestock) {
        boolean previouslyComplete = complete;
        var previousIssues = new LinkedHashMap<>(issues); issues.clear();
        var farms = FarmInstanceRegistry.get(server);
        int day = StardewTimeManager.get().getAbsoluteDay();
        Map<String, AnimalBuildingRecord> oldHomes = new LinkedHashMap<>();
        for (var raw : source.getList("buildings", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw; String key = "home:" + tag.getString("buildingId");
            try {
                var old = AnimalBuildingRecord.load(tag); oldHomes.put(old.buildingId(), old);
                if (buildings.legacyImport(old.buildingId()) != null) continue;
                migrateHome(server, farms, buildings, old);
            } catch (RuntimeException exception) { issue(key, exception); }
        }
        for (var old : oldHomes.values()) {
            UUID id = buildings.legacyImport(old.buildingId());
            // Existing modern residences keep their current door/feed state.
            if (id != null && id.equals(stableId("home:" + old.buildingId())))
                livestock.importLegacyHome("door:" + old.buildingId(), id, old.doorOpen(), day);
        }
        for (var raw : source.getList("animals", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw; String key = "animal:" + tag.getLong("animalId");
            if (livestock.legacyImport(key) != null) continue;
            try {
                var old = FarmAnimalRecord.load(tag);
                if (old.animalId() <= 0) throw new IllegalArgumentException("invalid_animal_id");
                var oldHome = oldHomes.get(old.buildingId());
                var farm = resolveFarm(farms, oldHome == null ? old.ownerPlayerUuid() : oldHome.ownerPlayerUuid());
                if (farm == null) farm = resolveFarm(farms, old.ownerPlayerUuid());
                if (farm == null) throw new IllegalArgumentException("farm_unresolved");
                UUID homeId = buildings.legacyImport(old.buildingId());
                if (homeId == null) homeId = stableId("home:" + old.buildingId());
                var home = buildings.find(homeId);
                var species = LivestockSpecies.parse(old.animalTypeId());
                var extra = new CompoundTag(); extra.put("LegacyAnimal", tag.copy());
                extra.put("AddonData", old.persistentData().toTag());
                var care = new LivestockCare(old.ageDays(), old.daysOwned(), old.friendship(), old.happiness(),
                        old.fullness(), old.daysSinceLastProduce(), old.produceQuality(), old.wasPetToday(), old.wasAutoPetToday());
                var location = location(server, old, home, day);
                var animal = new LivestockRecord(stableId(key), farm.getOwnerUUID(), farm.getInstanceId(), homeId,
                        old.customName(), livestock.allocateRandomId(), Math.max(day, old.lastProcessedAbsDay()), care,
                        location, species, old.currentProduceId(), old.hasEatenAnimalCracker(), old.allowReproduction(), extra);
                livestock.importLegacyAnimal(key, animal, false);
                livestock.birthDay(farm.getInstanceId(), Math.max(livestock.birthDay(farm.getInstanceId()), day));
            } catch (RuntimeException exception) { issue(key, exception); }
        }
        for (var raw : source.getList("animalProduceLedger", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw; String key = "product:" + tag.getLong("entryId");
            if (livestock.legacyImport(key) != null) continue;
            try {
                var entry = AnimalProduceLedgerEntry.load(tag);
                UUID animalId = livestock.legacyImport("animal:" + entry.animalId());
                UUID homeId = buildings.legacyImport(entry.buildingId());
                if (animalId == null) animalId = stableId("animal:" + entry.animalId());
                if (homeId == null) homeId = stableId("home:" + entry.buildingId());
                // A sold legacy parent can still have uncollected floor produce.
                if (buildings.find(homeId) == null && livestock.find(animalId) == null)
                    throw new IllegalArgumentException("produce_home_unresolved");
                var home = buildings.find(homeId);
                BlockPos position = home != null && entry.isProjected() && home.dimension().toString().equals(entry.projectedDimensionId())
                        ? entry.projectedPos() : null;
                livestock.importLegacyProduct(key, new LivestockWorldData.Product(stableId(key), animalId, homeId,
                        false, entry.quality(), entry.itemId().toString(), 1, position));
            } catch (RuntimeException exception) { issue(key, exception); }
        }
        for (var raw : source.getList("pendingAnimalBirths", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw; String key = "birth:" + tag.getLong("eventId");
            if (livestock.legacyImport(key) != null) continue;
            try {
                var birth = AnimalPendingBirth.load(tag);
                var parentId = livestock.legacyImport("animal:" + birth.parentAnimalId());
                var parent = parentId == null ? null : livestock.find(parentId);
                if (parent == null) throw new IllegalArgumentException("birth_parent_unresolved");
                var extra = new CompoundTag(); extra.put("LegacyBirth", tag.copy());
                var baby = new LivestockRecord(stableId(key), parent.owner(), parent.farm(), parent.home(), "",
                        livestock.allocateRandomId(), day, LivestockCare.purchased(), null,
                        LivestockSpecies.parse(birth.animalTypeId()), "", false, true, extra);
                livestock.importLegacyAnimal(key, baby, true);
            } catch (RuntimeException exception) { issue(key, exception); }
        }
        for (var raw : source.getList("hayByOwner", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw; String key = "hay:" + tag.getString("ownerPlayerUuid");
            if (livestock.legacyImport(key) != null) continue;
            try {
                if (tag.getInt("pieces") <= 0) continue;
                var farm = resolveFarm(farms, tag.getString("ownerPlayerUuid"));
                if (farm == null) throw new IllegalArgumentException("hay_owner_unresolved");
                // Legacy balances may exceed today's usable silo capacity; withdrawal remains possible.
                livestock.importLegacyHay(key, farm.getInstanceId(), tag.getInt("pieces"));
            } catch (RuntimeException exception) { issue(key, exception); }
        }
        complete = issues.isEmpty();
        if (complete != previouslyComplete || !previousIssues.equals(issues)) setDirty();
        if (!previousIssues.equals(issues)) StardewCraft.LOGGER.warn("[ANIMAL_MIGRATION] Retained unresolved source records: {}", issues);
    }

    private void migrateHome(MinecraftServer server, FarmInstanceRegistry farms, BuildingWorldData buildings, AnimalBuildingRecord old) {
        if (old.buildingId().isBlank()) throw new IllegalArgumentException("missing_building_id");
        var farm = resolveFarm(farms, old.ownerPlayerUuid());
        if (farm == null) throw new IllegalArgumentException("farm_unresolved");
        var dimension = new ResourceLocation(old.dimensionId());
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) throw new IllegalArgumentException("dimension_unavailable");
        var manager = old.managerPos();
        var family = new ResourceLocation(StardewCraft.MODID, old.buildingType().family());
        // Old bounds describe interior air, which can exclude the manager and utility cells in walls.
        var min = new BlockPos(Math.min(manager.getX(), old.minX() - 1), Math.min(manager.getY(), old.minY() - 1), Math.min(manager.getZ(), old.minZ() - 1));
        var max = new BlockPos(Math.max(manager.getX() + 1, old.maxX() + 2), Math.max(manager.getY() + 1, old.maxY() + 2), Math.max(manager.getZ() + 1, old.maxZ() + 2));
        var bounds = new BuildingBounds(min, max);
        // Never claim somebody else's farm or synchronously load an unbounded corrupt envelope.
        boolean inside = farm.contains(min) && farm.contains(bounds.maxInclusive());
        boolean bounded = max.getX() - min.getX() <= 128 && max.getY() - min.getY() <= 128 && max.getZ() - min.getZ() <= 128;
        if (!bounded) throw new IllegalArgumentException("invalid_legacy_bounds");
        if (inside) LivestockHomes.load(level, bounds);
        boolean present = inside && old.active() && level.getBlockState(manager).is(PrefabDefinitions.managerBlock(family));
        var facing = level.getBlockState(manager).hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                ? level.getBlockState(manager).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) : Direction.SOUTH;
        var residence = BuildingRecord.Residence.INVALID;
        if (present) {
            if (UtilityBuildings.supported(family)) {
                var scan = UtilityBuildings.scanSilo(level, manager, bounds);
                if (scan.valid()) { bounds = scan.column(); residence = BuildingRecord.Residence.VALID; }
            } else if (BuildingResidence.scan(level, bounds, family).eligibleTier() >= old.buildingType().tier()) residence = BuildingRecord.Residence.VALID;
        }
        var candidate = new BuildingRecord(stableId("home:" + old.buildingId()), farm.getInstanceId(), farm.getSlotIndex(), family,
                BuildingRecord.Mode.SELF_BUILT, dimension, manager, manager, facing, bounds,
                present ? BuildingRecord.Phase.READY : BuildingRecord.Phase.MISSING, old.buildingType().tier(), residence, 0, cleanBuildingName(old.customName()));
        var result = buildings.importLegacy(old.buildingId(), candidate);
        if (result != BuildingWorldData.Result.SUCCESS) throw new IllegalArgumentException("building_" + result.name().toLowerCase(Locale.ROOT));
    }

    private static String cleanBuildingName(String name) {
        if (name == null) return "";
        String clean = name.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xA7).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        int end = Math.min(clean.length(), 32);
        if (end < clean.length() && Character.isHighSurrogate(clean.charAt(end - 1))) end--;
        return clean.substring(0, end);
    }

    private static FarmInstance resolveFarm(FarmInstanceRegistry farms, String owner) {
        try { return farms.getFarmForPlayer(UUID.fromString(owner)); } catch (IllegalArgumentException exception) { return null; }
    }

    private static LivestockLocation location(MinecraftServer server, FarmAnimalRecord old, BuildingRecord home, int day) {
        if (home == null || home.phase() == BuildingRecord.Phase.MISSING) return null;
        BlockPos pos = old.hasProjectionAnchor() && home.dimension().toString().equals(old.projectionDimensionId()) ? old.projectionPos() : null;
        var level = LivestockService.level(server, home);
        if (pos == null) for (var entity : level.getAllEntities()) {
            if (entity instanceof BaseCoopAnimalEntity animal && animal.getManagedAnimalId() == old.animalId()) { pos = animal.blockPosition(); break; }
        }
        if (pos == null) return null;
        var farm = LivestockOutdoors.farm(server, home);
        if (farm == null || !farm.contains(pos)) return null;
        return new LivestockLocation(pos, home.anchor(), !home.claim().contains(pos), day * 144 + 36, false);
    }

    private void issue(String key, RuntimeException exception) { issues.put(key, Objects.toString(exception.getMessage(), exception.getClass().getSimpleName())); }
    public static UUID stableId(String source) { return UUID.nameUUIDFromBytes(("stardewcraft:legacy_livestock:" + source).getBytes(StandardCharsets.UTF_8)); }
    public static boolean superseded(MinecraftServer server, BaseCoopAnimalEntity animal) {
        return !animal.getTags().contains(com.stardew.craft.festival.FairFestivalService.FAIR_ANIMAL_MARKER_TAG)
                && !animal.getPersistentData().getBoolean(com.stardew.craft.festival.FairFestivalService.FAIR_ANIMAL_PERSISTENT_FLAG)
                && animal.getManagedAnimalId() > 0 && LivestockWorldData.get(server).legacyImport("animal:" + animal.getManagedAnimalId()) != null;
    }
    @SubscribeEvent public static void entityJoined(EntityJoinLevelEvent event) {
        // Do not load chunks or start migration from a chunk-load callback.
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof BaseCoopAnimalEntity animal && superseded(level.getServer(), animal)) event.setCanceled(true);
    }
    public static boolean openLegacy(ServerPlayer player, BaseCoopAnimalEntity animal) {
        LivestockService.recover(player.server);
        var id = LivestockWorldData.get(player.server).legacyImport("animal:" + animal.getManagedAnimalId());
        if (id == null) {
            if (animal.getManagedAnimalId() <= 0) return false;
            if (!get(player.server).containsSource("animals", "animalId", animal.getManagedAnimalId())) return false;
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("livestock.stardewcraft.migration_pending"), true);
            return true;
        }
        LivestockManagement.open(player, id.toString()); return true;
    }

    /** Suppress every old projection, even if its new product has already been collected. */
    public static boolean supersededProduct(ServerLevel level, AnimalProduceSpotBlockEntity spot) {
        return spot.getProduceLedgerEntryId() > 0 && LivestockWorldData.get(level.getServer()).legacyImport("product:" + spot.getProduceLedgerEntryId()) != null;
    }

    /** Pre-ledger saves kept the only copy of a floor product in its block entity. */
    public static boolean migrateProductSpot(ServerLevel level, AnimalProduceSpotBlockEntity spot) {
        var data = LivestockWorldData.get(level.getServer());
        if (spot.getProduceLedgerEntryId() <= 0 && !spot.getProduceStack().isEmpty()) {
            var id = data.legacyImport("animal:" + spot.getAnimalId());
            var animal = id == null ? null : data.find(id);
            var home = BuildingWorldData.get(level.getServer()).legacyImport(spot.getBuildingId());
            var residence = home == null ? null : BuildingWorldData.get(level.getServer()).find(home);
            boolean relocated = animal != null && (residence == null || residence.phase() == BuildingRecord.Phase.MISSING)
                    && !animal.home().equals(home);
            if (relocated) home = animal.home();
            if (home == null) return false;
            String key = "spot:" + level.dimension().location() + ":" + spot.getBlockPos().asLong() + ":" + spot.getAnimalId();
            var stack = spot.getProduceStack();
            data.importLegacyProduct(key, new LivestockWorldData.Product(stableId(key),
                    id == null ? stableId("animal:" + spot.getAnimalId()) : id, home, false,
                    com.stardew.craft.item.quality.QualityHelper.getQuality(stack),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(),
                    relocated ? null : spot.getBlockPos(), (CompoundTag) PortItemStacks.save(stack, level.registryAccess())));
            // Retry a failed checkpoint even when its in-memory receipt already exists.
            checkpointAnimals(level.getServer());
            level.removeBlock(spot.getBlockPos(), false); return true;
        }
        if (!supersededProduct(level, spot)) return false;
        level.removeBlock(spot.getBlockPos(), false); return true;
    }

    @Override public CompoundTag save(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        tag.putInt("Format", 1); tag.putBoolean("Initialized", initialized); tag.putBoolean("Complete", complete); tag.put("Source", source.copy());
        var report = new CompoundTag(); issues.forEach(report::putString); tag.put("Issues", report); return tag;
    }
    public static LegacyLivestockMigration load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("Format") != 1) throw new IllegalArgumentException("Unsupported livestock migration format");
        var data = new LegacyLivestockMigration(tag.getCompound("Source")); data.initialized = tag.getBoolean("Initialized");
        // Target receipts, rather than a third file's completion bit, decide whether replay is needed.
        data.complete = false;
        var report = tag.getCompound("Issues"); for (var key : report.getAllKeys()) data.issues.put(key, report.getString(key)); return data;
    }
}
