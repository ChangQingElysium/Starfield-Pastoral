package com.stardew.craft.building.runtime;

import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** New building storage. Use BuildingService for player requests and importLegacy for saved residences. */
public final class BuildingWorldData extends SavedData {
    private static final String DATA_NAME = "stardew_buildings";
    private static final int FORMAT = 1;
    private static final Map<MinecraftServer, BuildingWorldData> LIVE = new java.util.WeakHashMap<>();
    public static BuildingWorldData peek(MinecraftServer server) { return LIVE.get(server); }
    public static void unload(MinecraftServer server) { LIVE.remove(server); }
    private final Map<UUID, BuildingRecord> buildings = new LinkedHashMap<>();
    // Kept after demolition/farm deletion, so an archived legacy residence cannot return.
    private final Map<String, UUID> legacyImports = new LinkedHashMap<>();
    public synchronized UUID legacyImport(String sourceId) { return legacyImports.get(sourceId); }

    /** Migration only: retains a paid tier without replaying construction or replacing world blocks. */
    public synchronized Result importLegacy(String sourceId, BuildingRecord candidate) {
        if (legacyImports.containsKey(sourceId)) return Result.SUCCESS;
        if (candidate.mode() != BuildingRecord.Mode.SELF_BUILT
                || candidate.phase() != BuildingRecord.Phase.READY && candidate.phase() != BuildingRecord.Phase.MISSING) return Result.INVALID_STATE;
        for (var existing : buildings.values()) {
            if (existing.dimension().equals(candidate.dimension()) && existing.manager().equals(candidate.manager())
                    && existing.phase() != BuildingRecord.Phase.MISSING) {
                if (!existing.farmId().equals(candidate.farmId()) || !existing.family().equals(candidate.family())) return Result.OVERLAP;
                legacyImports.put(sourceId, existing.id()); setDirty(); return Result.SUCCESS;
            }
        }
        if (buildings.containsKey(candidate.id())) return Result.DUPLICATE_ID;
        if (candidate.phase() != BuildingRecord.Phase.MISSING && conflicting(candidate.dimension(), candidate.claim()) != null) return Result.OVERLAP;
        insert(candidate); legacyImports.put(sourceId, candidate.id()); setDirty(); return Result.SUCCESS;
    }

    /** Migration only: registers an already completed prefab without creating a Robin order. */
    public synchronized Result importCompletedPrefab(String sourceId, BuildingRecord candidate) {
        if (legacyImports.containsKey(sourceId)) return Result.SUCCESS;
        if (candidate.mode() != BuildingRecord.Mode.PREFAB
                || candidate.phase() != BuildingRecord.Phase.READY
                || candidate.residence() != BuildingRecord.Residence.VALID) {
            return Result.INVALID_STATE;
        }
        for (var existing : buildings.values()) {
            if (existing.farmId().equals(candidate.farmId())
                    && existing.family().equals(candidate.family())
                    && existing.anchor().equals(candidate.anchor())
                    && existing.phase() != BuildingRecord.Phase.MISSING) {
                legacyImports.put(sourceId, existing.id());
                setDirty();
                return Result.SUCCESS;
            }
        }
        if (buildings.containsKey(candidate.id())) return Result.DUPLICATE_ID;
        if (conflicting(candidate.dimension(), candidate.claim()) != null) return Result.OVERLAP;
        insert(candidate);
        legacyImports.put(sourceId, candidate.id());
        setDirty();
        return Result.SUCCESS;
    }
    private final Map<UUID, BuildingTransfer> transfers = new HashMap<>();
    public synchronized BuildingTransfer transfer(UUID id) { return transfers.get(id); }
    private final Map<UUID, BuildingMoveLift> moveLifts = new HashMap<>();
    public synchronized BuildingMoveLift moveLift(UUID id) { return moveLifts.get(id); }
    public synchronized List<BuildingMoveLift> moveLifts() { return List.copyOf(moveLifts.values()); }
    private final Map<UUID, ConstructionOrder> orders = new HashMap<>();
    private final Map<UUID, UUID> permits = new HashMap<>();
    private final Set<Integer> pausedDays = new java.util.HashSet<>();
    private final Set<UUID> purchases = new java.util.HashSet<>();
    // Derived only from records. Dimension buckets keep claims from unrelated worlds independent.
    private final Map<ResourceLocation, Map<UUID, BuildingBounds>> claims = new HashMap<>();
    private final Map<ResourceLocation, Map<Long, Set<UUID>>> claimChunks = new HashMap<>();

    public enum Result { SUCCESS, NOT_FOUND, STALE_REVISION, OVERLAP, DUPLICATE_ID, INVALID_STATE }

    public static BuildingWorldData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Building access requires the server thread");
        BuildingWorldData data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(BuildingWorldData::new, BuildingWorldData::load), DATA_NAME);
        // Reconcile deletion even if a shutdown saved farms before the building file.
        Set<UUID> liveFarms = FarmInstanceRegistry.get(server).getAllFarms().stream()
                .map(farm -> farm.getInstanceId()).collect(Collectors.toSet());
        data.removeMissingFarms(liveFarms);
        LIVE.put(server, data);
        return data;
    }

    public synchronized BuildingRecord find(UUID id) {
        return buildings.get(id);
    }

    public synchronized List<BuildingRecord> all() {
        return List.copyOf(buildings.values());
    }

    public synchronized UUID occupying(ResourceLocation dimension, BlockPos pos) {
        for (var transfer : transfers.values()) if (transfer.after().dimension().equals(dimension) && transfer.after().claim().contains(pos)) return transfer.after().id();
        long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        for (UUID id : claimChunks.getOrDefault(dimension, Map.of()).getOrDefault(chunk, Set.of())) {
            if (claims.get(dimension).get(id).contains(pos)) return id;
        }
        return null;
    }

    public synchronized UUID conflicting(ResourceLocation dimension, BuildingBounds bounds) { return conflicting(dimension, bounds, null); }
    public synchronized UUID conflicting(ResourceLocation dimension, BuildingBounds bounds, UUID ignored) {
        for (var transfer : transfers.values()) if (!transfer.after().id().equals(ignored) && transfer.after().dimension().equals(dimension)
                && transfer.after().claim().intersects(bounds)) return transfer.after().id();
        Set<UUID> checked = new java.util.HashSet<>();
        var chunks = claimChunks.getOrDefault(dimension, Map.of());
        for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++) {
            for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) {
                for (UUID id : chunks.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), Set.of())) {
                    if (!id.equals(ignored) && checked.add(id) && claims.get(dimension).get(id).intersects(bounds)) return id;
                }
            }
        }
        return null;
    }

    private final Map<UUID, ResourceLocation> permitFamilies = new HashMap<>();
    private final Map<UUID, Integer> permitTiers = new HashMap<>();

    public synchronized boolean hasPurchase(UUID requestId) { return purchases.contains(requestId); }

    public synchronized void recordPurchase(UUID requestId, UUID farmId, boolean prefab, ResourceLocation family) {
        if (!purchases.add(requestId)) throw new IllegalStateException("Purchase already processed");
        if (prefab) { permits.put(requestId, farmId); permitFamilies.put(requestId, family); }
        setDirty();
    }

    public synchronized boolean permits(UUID permit, UUID farmId, ResourceLocation family) {
        return farmId.equals(permits.get(permit)) && family.equals(permitFamilies.get(permit)) && permitTiers.getOrDefault(permit, 0) == 0;
    }

    /**
     * A paid blueprint may be placed on another farm that the same player currently manages.
     * This matters for the debug multi-farm workflow, where the selected farm can change between
     * Robin's shop and the physical placement site. The permit is still family-specific and cannot
     * cross to an unrelated player's farm.
     */
    public synchronized boolean permitsPlacement(UUID permit, UUID targetFarmId, ResourceLocation family,
                                                     UUID actor, FarmInstanceRegistry registry) {
        if (permit == null || targetFarmId == null || actor == null
                || !family.equals(permitFamilies.get(permit)) || permitTiers.getOrDefault(permit, 0) != 0) return false;
        UUID sourceFarmId = permits.get(permit);
        if (targetFarmId.equals(sourceFarmId)) return true;
        var source = registry.getFarmByInstanceId(sourceFarmId);
        var target = registry.getFarmByInstanceId(targetFarmId);
        return source != null && target != null && source.isFarmer(actor) && target.isFarmer(actor);
    }

    public synchronized void recordUpgradePurchase(UUID requestId, UUID farmId, ResourceLocation family, int tier) {
        if (tier < 2 || tier > PrefabDefinitions.maxTier(family)) throw new IllegalArgumentException("Invalid permit tier");
        recordPurchase(requestId, farmId, true, family); permitTiers.put(requestId, tier); setDirty();
    }
    public synchronized boolean permitsUpgrade(UUID permit, UUID farm, ResourceLocation family, int tier) {
        return permit != null && farm.equals(permits.get(permit)) && family.equals(permitFamilies.get(permit))
                && permitTiers.getOrDefault(permit, 0) == tier;
    }
    public synchronized boolean hasUpgradePermit(UUID farm, ResourceLocation family, int tier) {
        return permits.keySet().stream().anyMatch(id -> permitsUpgrade(id, farm, family, tier));
    }
    public synchronized Result beginPermittedUpgrade(UUID id, long revision, UUID permit, int tier, int day) {
        var record = buildings.get(id);
        if (record == null || record.tier() + 1 != tier || !permitsUpgrade(permit, record.farmId(), record.family(), tier)) return Result.INVALID_STATE;
        Result result = beginUpgrade(id, revision, day);
        if (result == Result.SUCCESS) { permits.remove(permit); permitFamilies.remove(permit); permitTiers.remove(permit); setDirty(); }
        return result;
    }

    public synchronized ConstructionOrder order(UUID buildingId) { return orders.get(buildingId); }

    /** Returns whether Robin already has unfinished work on this farm. */
    public synchronized boolean hasActiveConstruction(UUID farmId) {
        if (farmId == null) return false;
        return buildings.values().stream().anyMatch(record ->
                farmId.equals(record.farmId())
                        && orders.containsKey(record.id())
                        && (record.phase() == BuildingRecord.Phase.CONSTRUCTING
                            || record.phase() == BuildingRecord.Phase.UPGRADING));
    }

    public synchronized Result beginPrefab(BuildingRecord record, UUID permit, int absoluteDay) {
        if (record.mode() != BuildingRecord.Mode.PREFAB || !permits(permit, record.farmId(), record.family())) return Result.INVALID_STATE;
        return beginPrefabValidated(record, permit, absoluteDay);
    }

    public synchronized Result beginPrefab(BuildingRecord record, UUID permit, int absoluteDay,
                                           UUID actor, FarmInstanceRegistry registry) {
        if (record.mode() != BuildingRecord.Mode.PREFAB
                || !permitsPlacement(permit, record.farmId(), record.family(), actor, registry)) return Result.INVALID_STATE;
        return beginPrefabValidated(record, permit, absoluteDay);
    }

    private Result beginPrefabValidated(BuildingRecord record, UUID permit, int absoluteDay) {
        if (hasActiveConstruction(record.farmId())) return Result.INVALID_STATE;
        ConstructionOrder order = new ConstructionOrder(newConstructionDays(record.family()), absoluteDay, false);
        Result result = register(record);
        if (result != Result.SUCCESS) return result;
        buildings.put(record.id(), record.advance(BuildingRecord.Action.START_CONSTRUCTION));
        orders.put(record.id(), order);
        permits.remove(permit); permitFamilies.remove(permit);
        setDirty();
        return Result.SUCCESS;
    }

    /** Original Buildings.json BuildDays: Coop/Barn 3 days, Silo and Fish Pond 2 days. */
    private static int newConstructionDays(net.minecraft.resources.ResourceLocation family) {
        return UtilityBuildings.SILO.equals(family)
                || FishPondPrefabs.FAMILY.equals(family) ? 2 : 3;
    }

    public synchronized Result beginUpgrade(UUID id, long revision, int absoluteDay) {
        var record = buildings.get(id);
        if (record == null || transfers.containsKey(id) || moveLifts.containsKey(id) || orders.containsKey(id)) return Result.INVALID_STATE;
        if (hasActiveConstruction(record.farmId())) return Result.INVALID_STATE;
        if (record.tier() >= PrefabDefinitions.maxTier(record.family())) return Result.INVALID_STATE;
        var order = new ConstructionOrder(PrefabDefinitions.get(record.family()).tier(record.tier() + 1).upgrade().days(), absoluteDay, false);
        Result result = advance(id, revision, BuildingRecord.Action.START_UPGRADE);
        if (result == Result.SUCCESS) {
            orders.put(id, order);
            setDirty();
        }
        return result;
    }
    public synchronized Result beginTransfer(BuildingTransfer transfer) {
        var before = buildings.get(transfer.before().id());
        if (before == null || !before.equals(transfer.before()) || transfers.containsKey(before.id())
                || moveLifts.containsKey(before.id())) return Result.STALE_REVISION;
        if (before.phase() != BuildingRecord.Phase.READY && before.phase() != BuildingRecord.Phase.UPGRADING) return Result.INVALID_STATE;
        if (before.phase() == BuildingRecord.Phase.UPGRADING && (orders.get(before.id()) == null || orders.get(before.id()).remainingDays() != 0)) return Result.INVALID_STATE;
        if (conflicting(before.dimension(), transfer.after().claim(), before.id()) != null) return Result.OVERLAP;
        transfers.put(before.id(), transfer); setDirty(); return Result.SUCCESS;
    }

    public synchronized Result beginMoveLift(BuildingMoveLift lift) {
        var before = buildings.get(lift.snapshot().before().id());
        if (before == null || !before.equals(lift.snapshot().before())
                || transfers.containsKey(before.id()) || moveLifts.containsKey(before.id())) {
            return Result.STALE_REVISION;
        }
        moveLifts.put(before.id(), lift);
        setDirty();
        return Result.SUCCESS;
    }

    public synchronized Result promoteMoveLift(UUID id, UUID owner, BuildingTransfer transfer) {
        BuildingMoveLift lift = moveLifts.get(id);
        BuildingRecord before = buildings.get(id);
        if (lift == null || !lift.owner().equals(owner) || before == null
                || !before.equals(transfer.before()) || transfers.containsKey(id)) {
            return Result.STALE_REVISION;
        }
        if (conflicting(before.dimension(), transfer.after().claim(), before.id()) != null) {
            return Result.OVERLAP;
        }
        transfers.put(id, transfer);
        moveLifts.remove(id);
        setDirty();
        return Result.SUCCESS;
    }

    public synchronized BuildingMoveLift finishMoveLift(UUID id, UUID owner, UUID document) {
        BuildingMoveLift lift = moveLifts.get(id);
        if (lift == null || !lift.owner().equals(owner) || !lift.document().equals(document)) return null;
        moveLifts.remove(id);
        setDirty();
        return lift;
    }
    public synchronized void finishTransfer(UUID id) {
        BuildingTransfer transfer = transfers.get(id);
        if (transfer == null) throw new IllegalStateException("Missing transfer");
        remove(id); insert(transfer.after()); BuildingProtection.clearMasks(); setDirty();
    }

    public synchronized void markScaffold(UUID id) {
        ConstructionOrder order = orders.get(id);
        if (order != null && !order.scaffoldReady()) { orders.put(id, order.withScaffold()); setDirty(); }
    }

    public synchronized void constructionDay(int absoluteDay, boolean workingDay) {
        orders.replaceAll((id, order) -> order.onDay(absoluteDay, workingDay));
        if (!orders.isEmpty()) setDirty();
    }

    public synchronized void constructionThrough(int day, boolean workingToday, java.util.function.IntPredicate calendar) {
        if (!workingToday && pausedDays.add(day)) setDirty();
        orders.replaceAll((id, order) -> {
            ConstructionOrder next = order.through(day, date -> !pausedDays.contains(date) && calendar.test(date));
            if (!next.equals(order)) setDirty(); return next;
        });
    }

    public synchronized Result finishPrefab(UUID id) {
        BuildingRecord record = buildings.get(id);
        ConstructionOrder order = orders.get(id);
        if (record == null || order == null || order.remainingDays() != 0 || !order.scaffoldReady()) return Result.INVALID_STATE;
        Result result = advance(id, record.revision(), BuildingRecord.Action.FINISH_CONSTRUCTION);
        if (result == Result.SUCCESS) orders.remove(id);
        return result;
    }

    public synchronized boolean removeSelfBuilt(UUID id) {
        BuildingRecord record = buildings.get(id);
        if (record == null || record.mode() != BuildingRecord.Mode.SELF_BUILT) return false;
        remove(id); setDirty(); return true;
    }

    /** Claim check and record insertion are one operation; previews confer no reservation. */
    public synchronized Result register(BuildingRecord record) {
        if (buildings.containsKey(record.id())) return Result.DUPLICATE_ID;
        if (record.phase() != BuildingRecord.Phase.WAITING || record.tier() != 1
                || record.residence() != BuildingRecord.Residence.UNCHECKED || record.revision() != 0) {
            return Result.INVALID_STATE;
        }
        if (conflicting(record.dimension(), record.claim()) != null) return Result.OVERLAP;
        insert(record);
        setDirty();
        return Result.SUCCESS;
    }

    /** Server order runner only: elapsed days and world placement are verified by that runner. */
    public synchronized Result advance(UUID id, long expectedRevision, BuildingRecord.Action action) {
        return update(id, expectedRevision, record -> record.advance(action));
    }

    /** Server residence scanner only; never accept eligibleTier directly from a client packet. */
    public synchronized Result assessResidence(UUID id, long expectedRevision, int eligibleTier) {
        return update(id, expectedRevision, record -> record.assessResidence(eligibleTier));
    }

    public synchronized Result acceptSelf(UUID id, long revision, int eligibleTier) {
        return update(id, revision, record -> record.acceptSelf(eligibleTier));
    }
    /** Finalize the validated silo column, releasing the temporary candidate search envelope. */
    public synchronized Result acceptSelfColumn(UUID id, long revision, BuildingBounds column) {
        var record = buildings.get(id);
        if (record == null) return Result.NOT_FOUND;
        if (record.revision() != revision) return Result.STALE_REVISION;
        if (!UtilityBuildings.supported(record.family()) || record.phase() != BuildingRecord.Phase.WAITING
                || record.mode() != BuildingRecord.Mode.SELF_BUILT || !column.contains(record.manager())
                || !record.claim().contains(column.min()) || !record.claim().contains(column.maxInclusive())
                || column.maxExclusive().getX()-column.min().getX()!=2 || column.maxExclusive().getY()-column.min().getY()!=10
                || column.maxExclusive().getZ()-column.min().getZ()!=2) return Result.INVALID_STATE;
        var accepted = new BuildingRecord(record.id(),record.farmId(),record.farmSlot(),record.family(),record.mode(),record.dimension(),
                record.anchor(),record.manager(),record.facing(),column,BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,revision+1,record.displayName());
        remove(id); insert(accepted); setDirty(); return Result.SUCCESS;
    }
    public synchronized Result rename(UUID id, long revision, String name) {
        return update(id, revision, record -> record.rename(name));
    }
    public synchronized void detachSelf(UUID id) {
        var record = buildings.get(id);
        if (record == null || record.mode() != BuildingRecord.Mode.SELF_BUILT || record.phase() == BuildingRecord.Phase.MISSING || transfers.containsKey(id)) return;
        remove(id); insert(record.missing()); setDirty();
    }
    public synchronized Result restoreSelf(BuildingRecord old, BlockPos manager, net.minecraft.core.Direction facing, BuildingBounds claim) {
        if (!old.equals(buildings.get(old.id())) || old.phase() != BuildingRecord.Phase.MISSING) return Result.INVALID_STATE;
        if (conflicting(old.dimension(), claim) != null) return Result.OVERLAP;
        var restored = new BuildingRecord(old.id(), old.farmId(), old.farmSlot(), old.family(), old.mode(), old.dimension(), manager, manager, facing, claim,
                old.residence() == BuildingRecord.Residence.UNCHECKED || UtilityBuildings.supported(old.family()) ? BuildingRecord.Phase.WAITING : BuildingRecord.Phase.READY, old.tier(), BuildingRecord.Residence.INVALID, old.revision() + 1, old.displayName());
        remove(old.id()); insert(restored); setDirty(); return Result.SUCCESS;
    }
    public synchronized void demolish(UUID id) { if (buildings.containsKey(id)) { remove(id); setDirty(); } }

    private Result update(UUID id, long expectedRevision, UnaryOperator<BuildingRecord> update) {
        BuildingRecord before = buildings.get(id);
        if (before == null) return Result.NOT_FOUND;
        if (before.revision() != expectedRevision) return Result.STALE_REVISION;
        BuildingRecord after;
        try {
            after = update.apply(before);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return Result.INVALID_STATE;
        }
        buildings.put(id, after);
        setDirty();
        return Result.SUCCESS;
    }

    /** Authoritative farm deletion; player demolition will use its own checked service. */
    public synchronized void removeFarm(UUID farmId) {
        List<UUID> removed = buildings.values().stream().filter(record -> record.farmId().equals(farmId))
                .map(BuildingRecord::id).toList();
        removed.forEach(this::remove);
        boolean removedPermits = permits.values().removeIf(farmId::equals);
        permitFamilies.keySet().retainAll(permits.keySet()); permitTiers.keySet().retainAll(permits.keySet());
        if (!removed.isEmpty() || removedPermits) setDirty();
    }

    private synchronized void removeMissingFarms(Set<UUID> liveFarms) {
        List<UUID> removed = buildings.values().stream().filter(record -> !liveFarms.contains(record.farmId()))
                .map(BuildingRecord::id).toList();
        removed.forEach(this::remove);
        boolean removedPermits = permits.values().removeIf(farmId -> !liveFarms.contains(farmId));
        permitFamilies.keySet().retainAll(permits.keySet()); permitTiers.keySet().retainAll(permits.keySet());
        if (!removed.isEmpty() || removedPermits) setDirty();
    }

    private void remove(UUID id) {
        BuildingRecord record = buildings.remove(id);
        orders.remove(id); transfers.remove(id); moveLifts.remove(id);
        if (record.phase() == BuildingRecord.Phase.MISSING) return;
        Map<UUID, BuildingBounds> bucket = claims.get(record.dimension());
        bucket.remove(id);
        if (bucket.isEmpty()) claims.remove(record.dimension());
        var chunks = claimChunks.get(record.dimension());
        visitChunks(record.claim(), key -> {
            Set<UUID> ids = chunks.get(key);
            ids.remove(id);
            if (ids.isEmpty()) chunks.remove(key);
        });
        if (chunks.isEmpty()) claimChunks.remove(record.dimension());
    }

    private void insert(BuildingRecord record) {
        buildings.put(record.id(), record);
        if (record.phase() == BuildingRecord.Phase.MISSING) return;
        claims.computeIfAbsent(record.dimension(), ignored -> new LinkedHashMap<>())
                .put(record.id(), record.claim());
        var chunks = claimChunks.computeIfAbsent(record.dimension(), ignored -> new HashMap<>());
        visitChunks(record.claim(), key -> chunks.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(record.id()));
    }

    private static void visitChunks(BuildingBounds bounds, java.util.function.LongConsumer consumer) {
        for (int x = bounds.min().getX() >> 4; x <= bounds.maxInclusive().getX() >> 4; x++) {
            for (int z = bounds.min().getZ() >> 4; z <= bounds.maxInclusive().getZ() >> 4; z++) {
                consumer.accept(net.minecraft.world.level.ChunkPos.asLong(x, z));
            }
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Format", FORMAT);
        var imported = new CompoundTag(); legacyImports.forEach(imported::putUUID); tag.put("LegacyImports", imported);
        ListTag list = new ListTag();
        buildings.values().forEach(record -> list.add(record.save()));
        tag.put("Buildings", list);
        ListTag orderTags = new ListTag();
        orders.forEach((id, order) -> { CompoundTag value = order.save(); value.putUUID("Id", id); orderTags.add(value); });
        tag.put("Orders", orderTags);
        ListTag transferTags = new ListTag(); transfers.values().forEach(value -> transferTags.add(value.save()));
        tag.put("Transfers", transferTags);
        ListTag liftTags = new ListTag(); moveLifts.values().forEach(value -> liftTags.add(value.save()));
        tag.put("MoveLifts", liftTags);
        ListTag permitTags = new ListTag();
        permits.forEach((id, farm) -> { CompoundTag value = new CompoundTag(); value.putUUID("Id", id); value.putUUID("Farm", farm); value.putString("Family", permitFamilies.get(id).toString()); value.putInt("TargetTier", permitTiers.getOrDefault(id, 0)); permitTags.add(value); });
        tag.put("Permits", permitTags);
        ListTag purchaseTags = new ListTag();
        purchases.forEach(id -> { CompoundTag value = new CompoundTag(); value.putUUID("Id", id); purchaseTags.add(value); });
        tag.put("Purchases", purchaseTags);
        tag.putIntArray("PausedDays", pausedDays.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    public static BuildingWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("Format") != FORMAT || !tag.contains("Buildings", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Unsupported building storage format");
        }
        BuildingWorldData data = new BuildingWorldData();
        var imported = tag.getCompound("LegacyImports");
        for (var key : imported.getAllKeys()) data.legacyImports.put(key, imported.getUUID(key));
        for (int day : tag.getIntArray("PausedDays")) if (day > 0) data.pausedDays.add(day);
        ListTag list = (ListTag) tag.get("Buildings");
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid building record list");
        }
        for (int i = 0; i < list.size(); i++) {
            BuildingRecord record = BuildingRecord.load(list.getCompound(i));
            if (data.buildings.containsKey(record.id()) || record.phase() != BuildingRecord.Phase.MISSING && data.conflicting(record.dimension(), record.claim()) != null) {
                throw new IllegalArgumentException("Duplicate or overlapping saved building: " + record.id());
            }
            data.insert(record);
        }
        for (var entry : tag.getList("Orders", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) entry;
            UUID id = value.getUUID("Id");
            BuildingRecord record = data.find(id);
            if (record == null || record.mode() != BuildingRecord.Mode.PREFAB
                    || record.phase() != BuildingRecord.Phase.CONSTRUCTING && record.phase() != BuildingRecord.Phase.UPGRADING) throw new IllegalArgumentException("Orphan construction order");
            data.orders.put(id, ConstructionOrder.load(value));
        }
        for (var entry : tag.getList("Transfers", Tag.TAG_COMPOUND)) {
            BuildingTransfer transfer = BuildingTransfer.load((CompoundTag) entry, registries);
            if (!transfer.before().equals(data.find(transfer.before().id()))
                    || data.conflicting(transfer.after().dimension(), transfer.after().claim(), transfer.before().id()) != null) throw new IllegalArgumentException("Invalid saved transfer");
            data.transfers.put(transfer.before().id(), transfer);
        }
        for (var entry : tag.getList("MoveLifts", Tag.TAG_COMPOUND)) {
            BuildingMoveLift lift = BuildingMoveLift.load((CompoundTag) entry, registries);
            UUID id = lift.snapshot().before().id();
            if (!lift.snapshot().before().equals(data.find(id)) || data.transfers.containsKey(id)
                    || data.moveLifts.put(id, lift) != null) {
                throw new IllegalArgumentException("Invalid saved building move lift");
            }
        }
        for (var entry : tag.getList("Permits", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) entry;
            data.permitTiers.put(value.getUUID("Id"), value.getInt("TargetTier"));
            data.permits.put(value.getUUID("Id"), value.getUUID("Farm"));
            data.permitFamilies.put(value.getUUID("Id"), ResourceLocation.parse(value.getString("Family")));
        }
        for (var entry : tag.getList("Purchases", Tag.TAG_COMPOUND)) data.purchases.add(((CompoundTag) entry).getUUID("Id"));
        return data;
    }
}
