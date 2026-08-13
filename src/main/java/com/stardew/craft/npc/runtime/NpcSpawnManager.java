package com.stardew.craft.npc.runtime;

import com.stardew.craft.StardewCraft;
import com.google.gson.JsonObject;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prototype spawner for a limited roster so NPCs are visible in-world quickly.
 */
@SuppressWarnings("null")
public final class NpcSpawnManager {
    private static final int FULL_SWEEP_INTERVAL_TICKS = 200;
    private static final int SPAWN_CHECK_INTERVAL_TICKS = 20;
    private static final int RESPAWN_CONFIRM_MISSES = 10;
    private static final int RESPAWN_COOLDOWN_TICKS = 400;
    private static final int TRACKED_ENTITY_RECOVERY_MISSES = 30;
    private static final Set<String> FORCE_SPAWN_IDS = java.util.Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> SUPPRESSED_SPAWN_IDS = java.util.Collections.synchronizedSet(new HashSet<>());
    private static final AABB GLOBAL_NPC_SCAN = new AABB(-3.0E7, -2048.0, -3.0E7, 3.0E7, 4096.0, 3.0E7);

    /** NPCs that live in the mining dimension instead of Stardew Valley. */
    private static final Set<String> MINING_DIM_NPC_IDS = Set.of("dwarf");

    /** Public accessor for other services that need to skip mining-dimension NPCs. */
    public static boolean isMiningDimensionNpc(String npcId) {
        return MINING_DIM_NPC_IDS.contains(npcId);
    }

    /** Dwarf spawn: mine floor, near the shop area. */
    private static final double DWARF_SPAWN_X = 22.0;
    private static final double DWARF_SPAWN_Y = 66.0;
    private static final double DWARF_SPAWN_Z = -16.0;
    private static final float  DWARF_SPAWN_YAW = 180.0F;

    private static final String KROBUS_NPC_ID = "krobus";
    private static final double KROBUS_FIXED_X = 30.5D;
    private static final double KROBUS_FIXED_Y = 51.0D;
    private static final double KROBUS_FIXED_Z = 54.5D;
    private static final float KROBUS_FIXED_YAW = 0.0F;
    private static final double KROBUS_FIXED_EPSILON_SQ = 1.0e-4D;

    private static int tickCounter;
    private static boolean initialSweepDone;
    private static MinecraftServer activeServer;
    private static final Map<String, UUID> TRACKED_NPC_UUIDS = new LinkedHashMap<>();
    private static final Map<String, Integer> TRACKED_MISS_COUNTS = new LinkedHashMap<>();
    private static final Map<String, Long> LAST_SPAWN_GAME_TIME = new LinkedHashMap<>();
    private static final Map<String, FestivalSpawnTarget> FESTIVAL_SPAWN_TARGETS = new LinkedHashMap<>();

    // ── Tick-scoped caches to avoid redundant world entity scans ──
    // Valley and mining levels share the same game clock, so they cannot share
    // one entity list without occasionally reading entities from the wrong level.
    private static final Map<ServerLevel, EntityScanCache> ENTITY_SCAN_CACHES = new IdentityHashMap<>();
    private static Set<String> cachedImplementedIds = Set.of();
    private static Map<String, NpcCapabilityProfile> cachedImplementedCapabilities = Map.of();

    private NpcSpawnManager() {
    }

    public static void prepareServerContext(ServerLevel level) {
        if (level != null) {
            ensureServerContext(level);
        }
    }

    public static void claimFestivalSpawnTarget(String owner, String npcId, Vec3 position, float yaw) {
        String canonicalId = canonicalNpcId(npcId);
        if (owner == null || owner.isBlank() || canonicalId == null || canonicalId.isBlank() || position == null) {
            return;
        }
        FESTIVAL_SPAWN_TARGETS.put(canonicalId, new FestivalSpawnTarget(owner.trim().toLowerCase(java.util.Locale.ROOT), position, yaw));
    }

    public static void releaseFestivalSpawnTargets(String owner) {
        if (owner == null || owner.isBlank()) {
            return;
        }
        String canonicalOwner = owner.trim().toLowerCase(java.util.Locale.ROOT);
        FESTIVAL_SPAWN_TARGETS.entrySet().removeIf(entry -> entry.getValue().owner().equals(canonicalOwner));
    }

    /**
     * Returns a cached list of all loaded StardewNpcEntity instances.
     * The list is refreshed at most once every 10 ticks to avoid expensive
     * world-scan overhead every single tick.
     */
    private static List<StardewNpcEntity> getCachedAllNpcs(ServerLevel level) {
        long gameTime = level.getGameTime();
        EntityScanCache cache = ENTITY_SCAN_CACHES.computeIfAbsent(level, ignored -> new EntityScanCache());
        if (cache.gameTime == Long.MIN_VALUE
                || gameTime < cache.gameTime
                || gameTime - cache.gameTime >= 10) {
            cache.entities = level.getEntitiesOfClass(StardewNpcEntity.class, GLOBAL_NPC_SCAN);
            cache.gameTime = gameTime;
        }
        return cache.entities;
    }

    /**
     * Returns cached implemented NPC IDs, refreshed when NpcDataRegistry changes.
     */
    private static Set<String> getCachedImplementedIds() {
        Map<String, NpcCapabilityProfile> capabilities = NpcDataRegistry.capabilities();
        if (capabilities != cachedImplementedCapabilities) {
            Set<String> ids = new HashSet<>();
            for (Map.Entry<String, NpcCapabilityProfile> entry : capabilities.entrySet()) {
                if (entry.getValue().implemented()) {
                    ids.add(canonicalNpcId(entry.getValue().npcId()));
                }
            }
            cachedImplementedIds = ids;
            cachedImplementedCapabilities = capabilities;
        }
        return cachedImplementedIds;
    }

    /**
     * 标记指定 NPC 下次 tick 时立即生成，跳过 miss-count 和冷却检测。
     */
    public static void forceSpawnNpc(String npcId) {
        String canonicalId = canonicalNpcId(npcId);
        if (canonicalId == null || canonicalId.isBlank() || SUPPRESSED_SPAWN_IDS.contains(canonicalId)) {
            return;
        }
        FORCE_SPAWN_IDS.add(canonicalId);
    }

    public static void temporarilyRemoveNpc(ServerLevel level, String npcId) {
        if (level == null) {
            return;
        }
        String canonicalId = canonicalNpcId(npcId);
        if (canonicalId == null || canonicalId.isBlank()) {
            return;
        }
        SUPPRESSED_SPAWN_IDS.add(canonicalId);
        FORCE_SPAWN_IDS.remove(canonicalId);
        UUID trackedUuid = TRACKED_NPC_UUIDS.remove(canonicalId);
        TRACKED_MISS_COUNTS.remove(canonicalId);
        LAST_SPAWN_GAME_TIME.remove(canonicalId);

        if (trackedUuid != null && level.getEntity(trackedUuid) instanceof StardewNpcEntity trackedNpc) {
            discardWithReason(trackedNpc, "temporarily_removed");
        }
        for (StardewNpcEntity entity : getCachedAllNpcs(level)) {
            if (canonicalId.equals(canonicalNpcId(entity.getNpcId()))) {
                discardWithReason(entity, "temporarily_removed_duplicate");
            }
        }
    }

    public static void resumeNpcSpawn(String npcId) {
        String canonicalId = canonicalNpcId(npcId);
        if (canonicalId == null || canonicalId.isBlank()) {
            return;
        }
        SUPPRESSED_SPAWN_IDS.remove(canonicalId);
    }

    /**
     * Called when the last player leaves the Stardew Valley dimension.
     * Teleports every tracked NPC to their current schedule target so that
     * when the player returns they are at the correct position.
     */
    public static void onAllPlayersLeft(ServerLevel level) {
        for (Map.Entry<String, UUID> entry : new LinkedHashMap<>(TRACKED_NPC_UUIDS).entrySet()) {
            String npcId = entry.getKey();
            // Mining-dimension NPCs don't live in the main level; skip to avoid
            // getTrackedNpc evicting their tracked UUID.
            if (MINING_DIM_NPC_IDS.contains(npcId)) continue;
            // 根因修复：没有 schedule 的 NPC 不应被"关服回位"代码挪动。
            if (NpcDataRegistry.schedules().get(npcId) == null) continue;
            StardewNpcEntity npc = getTrackedNpc(level, npcId);
            if (npc == null) continue;
            NpcRuntimeState state = NpcRuntimeDataManager.get(level).states().get(npcId);
            Vec3 sharedSpawn = Vec3.atCenterOf(level.getSharedSpawnPos());
            NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, sharedSpawn);
            if (target != null && target.position() != null) {
                npc.setPos(target.position().x, target.position().y, target.position().z);
                npc.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * Overnight recovery: after the date, weather, and 6:00 clock are authoritative,
     * snap loaded scheduled NPCs back to their resolved morning schedule target.
     */
    public static void resetScheduledNpcsForNewDay(ServerLevel level) {
        if (level == null || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        ensureServerContext(level);
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);

        Set<String> implementedIds = getCachedImplementedIds();
        Map<String, StardewNpcEntity> loadedByNpcId = collectAndDeduplicateLoaded(level, implementedIds);
        Map<String, NpcRuntimeState> runtimeStates = NpcRuntimeDataManager.get(level).states();
        Vec3 sharedSpawn = Vec3.atCenterOf(level.getSharedSpawnPos());

        int moved = 0;
        int skipped = 0;
        for (Map.Entry<String, NpcCapabilityProfile> entry : NpcDataRegistry.capabilities().entrySet()) {
            NpcCapabilityProfile profile = entry.getValue();
            if (profile == null || !profile.implemented()) {
                continue;
            }

            String npcId = canonicalNpcId(profile.npcId());
            if (npcId == null || npcId.isBlank()) {
                continue;
            }
            if (MINING_DIM_NPC_IDS.contains(npcId)) {
                continue;
            }
            if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(npcId)) {
                continue;
            }
            if (NpcDataRegistry.schedules().get(npcId) == null) {
                continue;
            }

            NpcRuntimeState state = runtimeStates.get(npcId);
            if (state == null) {
                skipped++;
                continue;
            }
            NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, sharedSpawn);
            if (target == null || target.position() == null) {
                skipped++;
                continue;
            }

            StardewNpcEntity npc = getTrackedNpc(level, npcId);
            if (npc == null) {
                npc = loadedByNpcId.get(npcId);
            }
            if (npc == null || npc.isRemoved() || !npc.isAlive()) {
                skipped++;
                continue;
            }

            NpcChunkForceManager.ensureRouteTargetChunkForced(level, npcId, target.position());
            moveNpcToScheduleTarget(level, npc, target.position(), state);
            TRACKED_NPC_UUIDS.put(npcId, npc.getUUID());
            TRACKED_MISS_COUNTS.put(npcId, 0);
            NpcCentralMovementService.resetMovementPlan(npcId);
            moved++;
        }

        if (moved > 0 || skipped > 0) {
            StardewCraft.LOGGER.info("[NPC_DAILY_RESET] reset scheduled NPCs for new day: moved={}, skipped={}", moved, skipped);
        }
    }

    /**
     * Called when a player enters the Stardew Valley dimension.
     * Forces an immediate full sweep to clean up duplicates and snap NPCs.
     */
    public static void onPlayerEntered(ServerLevel level) {
        ENTITY_SCAN_CACHES.remove(level);
        Set<String> implementedIds = getCachedImplementedIds();
        Map<String, StardewNpcEntity> loadedByNpcId = collectAndDeduplicateLoaded(level, implementedIds);
        for (NpcCapabilityProfile profile : NpcDataRegistry.capabilities().values()) {
            if (profile == null || !profile.implemented()) {
                continue;
            }
            String npcId = canonicalNpcId(profile.npcId());
            if (npcId == null || npcId.isBlank()
                    || MINING_DIM_NPC_IDS.contains(npcId)
                    || SUPPRESSED_SPAWN_IDS.contains(npcId)
                    || com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(npcId)
                    || loadedByNpcId.containsKey(npcId)) {
                continue;
            }
            // Ordinary NPCs are intentionally not serialized. Once their chunk unloads
            // there is no old entity to wait for, so recover immediately on re-entry.
            TRACKED_NPC_UUIDS.remove(npcId);
            TRACKED_MISS_COUNTS.remove(npcId);
            LAST_SPAWN_GAME_TIME.remove(npcId);
            FORCE_SPAWN_IDS.add(npcId);
        }
        initialSweepDone = true;
    }

    /**
     * Called when a StardewNpcEntity is about to join the level.
     * @return true if the entity should be rejected (event should be cancelled)
     */
    public static boolean onNpcJoin(ServerLevel level, StardewNpcEntity npc) {
        boolean isStardew = ModDimensions.STARDEW_VALLEY.equals(level.dimension());
        boolean isMining  = ModMiningDimensions.STARDEW_MINING.equals(level.dimension());
        if (!isStardew && !isMining) {
            return false;
        }

        ensureServerContext(level);

        String npcId = canonicalNpcId(npc.getNpcId());
        if (npcId == null || npcId.isBlank()) {
            return true;
        }
        // Joja NPCs 完全由 JojaNpcEvents 管理 —— 不进老追踪系统。
        // 顺带：加载出来的位置若偏离目标 → 立即 snap 到 Joja Mart，断开陈旧 NBT 的权威。
        if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(npcId)) {
            com.stardew.craft.joja.JojaNpcEvents.onJojaNpcJoin(npc, npcId);
            return false;
        }

        if (isKrobus(npcId)) {
            enforceKrobusFixedPosition(npc);
        }

        UUID tracked = TRACKED_NPC_UUIDS.get(npcId);
        if (tracked == null) {
            TRACKED_NPC_UUIDS.put(npcId, npc.getUUID());
            reconcileSingletonByNpcId(level, npcId, npc.getUUID());
            return false;
        }

        if (tracked.equals(npc.getUUID())) {
            reconcileSingletonByNpcId(level, npcId, npc.getUUID());
            return false;
        }

        // A different entity with the same npcId is joining.
        // Check if the currently tracked entity is still alive and loaded.
        var existing = level.getEntity(tracked);
        if (existing instanceof StardewNpcEntity existingNpc
                && !existingNpc.isRemoved() && existingNpc.isAlive()) {
            // Tracked entity is alive → reject the newcomer entirely.
            return true;
        }

        // Tracked entity is gone or unloaded. However, it might still exist
        // serialised in an unloaded chunk. Reject this newcomer too if we spawned
        // one very recently (within cooldown), to avoid the classic
        // "spawn new → old chunk loads → 2 NPCs" race.
        Long lastSpawn = LAST_SPAWN_GAME_TIME.get(npcId);
        if (lastSpawn != null && (level.getGameTime() - lastSpawn) < RESPAWN_COOLDOWN_TICKS) {
            return true;
        }

        // Accept the newcomer as the canonical instance.
        TRACKED_NPC_UUIDS.put(npcId, npc.getUUID());
        reconcileSingletonByNpcId(level, npcId, npc.getUUID());
        return false;
    }

    public static void tick(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }

        ensureServerContext(level);

        JsonObject spawnRoot = NpcDataRegistry.events().get("default_spawns");
        JsonObject spawns = null;
        if (spawnRoot != null && spawnRoot.has("spawns") && spawnRoot.get("spawns").isJsonObject()) {
            spawns = spawnRoot.getAsJsonObject("spawns");
        }
        Set<String> implementedIds = getCachedImplementedIds();

        tickCounter++;

        if (!initialSweepDone) {
            cleanupUnknownAndDuplicated(level, implementedIds);
            initialSweepDone = true;
        } else if (tickCounter % FULL_SWEEP_INTERVAL_TICKS == 0) {
            cleanupUnknownAndDuplicated(level, implementedIds);
            // Proactively evict stale UUID mappings pointing to confirmed-dead entities.
            // Skip mining-dimension NPCs — they live in a different level so
            // level.getEntity() would return null and incorrectly evict them.
            // IMPORTANT: null means the entity's chunk is unloaded, NOT that it's gone.
            // Only evict when we have a positive confirmation of death/removal.
            TRACKED_NPC_UUIDS.entrySet().removeIf(e -> {
                if (MINING_DIM_NPC_IDS.contains(e.getKey())) return false;
                net.minecraft.world.entity.Entity ent = level.getEntity(e.getValue());
                return ent != null && (ent.isRemoved() || !ent.isAlive());
            });
        }

        if (tickCounter % SPAWN_CHECK_INTERVAL_TICKS != 0 && FORCE_SPAWN_IDS.isEmpty()) {
            return;
        }

        Map<String, StardewNpcEntity> loadedByNpcId = collectAndDeduplicateLoaded(level, implementedIds);

        for (Map.Entry<String, NpcCapabilityProfile> entry : NpcDataRegistry.capabilities().entrySet()) {
            NpcCapabilityProfile profile = entry.getValue();
            if (!profile.implemented()) {
                continue;
            }
            String npcId = canonicalNpcId(profile.npcId());
            // Skip NPCs that belong in the mining dimension
            if (MINING_DIM_NPC_IDS.contains(npcId)) {
                continue;
            }
            if (SUPPRESSED_SPAWN_IDS.contains(npcId)) {
                FORCE_SPAWN_IDS.remove(npcId);
                StardewNpcEntity existing = getTrackedNpc(level, npcId);
                if (existing != null) {
                    discardWithReason(existing, "suppressed_spawn_tick");
                }
                TRACKED_NPC_UUIDS.remove(npcId);
                TRACKED_MISS_COUNTS.remove(npcId);
                continue;
            }
            // Skip Joja Mart NPCs — handled by JojaNpcEvents (固定生成，与骆驼商人同套机制)
            if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(npcId)) {
                continue;
            }
            JsonObject pos = resolveSpawnPos(spawns, npcId, profile.npcId());

            double x = 150.0D;
            double y = -13.0D;
            double z = 119.0D;
            float yaw = 0.0F;
            if (pos != null) {
                x = pos.has("x") ? pos.get("x").getAsDouble() : x;
                y = pos.has("y") ? pos.get("y").getAsDouble() : y;
                z = pos.has("z") ? pos.get("z").getAsDouble() : z;
                yaw = pos.has("yaw") ? pos.get("yaw").getAsFloat() : yaw;
            }

            if (isKrobus(npcId)) {
                x = KROBUS_FIXED_X;
                y = KROBUS_FIXED_Y;
                z = KROBUS_FIXED_Z;
                yaw = KROBUS_FIXED_YAW;
            } else {
                // Prefer current runtime schedule target so newly spawned NPC starts at the
                // correct active location instead of a stale static bootstrap point.
                Vec3 runtimeScheduleSpawn = resolveRuntimeScheduleSpawn(level, npcId);
                if (runtimeScheduleSpawn != null) {
                    x = runtimeScheduleSpawn.x;
                    y = runtimeScheduleSpawn.y;
                    z = runtimeScheduleSpawn.z;
                } else if (pos == null) {
                    continue;
                }
            }

            double spawnX = x;
            double spawnY = y;
            double spawnZ = z;
            boolean desertFestivalMarlon = NpcScheduleRuntimeService.isDesertFestivalMarlonOverride(npcId);
            boolean troutDerbyWilly = NpcScheduleRuntimeService.isTroutDerbyWillyOverride(npcId);
            boolean squidFestWilly = NpcScheduleRuntimeService.isSquidFestWillyOverride(npcId);
            if (desertFestivalMarlon) {
                NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveDesertFestivalMarlonTarget();
                spawnX = target.position().x;
                spawnY = target.position().y;
                spawnZ = target.position().z;
                yaw = yawFromSdvFacing(NpcScheduleRuntimeService.desertFestivalMarlonFacing());
            } else if (troutDerbyWilly) {
                Vec3 target = com.stardew.craft.festival.trout.TroutDerbyService.willyPosition();
                spawnX = target.x;
                spawnY = target.y;
                spawnZ = target.z;
                yaw = yawFromSdvFacing(com.stardew.craft.festival.trout.TroutDerbyService.WILLY_FACING);
            } else if (squidFestWilly) {
                Vec3 target = com.stardew.craft.festival.squid.SquidFestService.willyPosition();
                spawnX = target.x;
                spawnY = target.y;
                spawnZ = target.z;
                yaw = yawFromSdvFacing(com.stardew.craft.festival.squid.SquidFestService.WILLY_FACING);
            }
            FestivalSpawnTarget festivalTarget = FESTIVAL_SPAWN_TARGETS.get(npcId);
            if (festivalTarget != null) {
                spawnX = festivalTarget.position().x;
                spawnY = festivalTarget.position().y;
                spawnZ = festivalTarget.position().z;
                yaw = festivalTarget.yaw();
            }

            // Chunk forcing is fully managed by NpcChunkForceManager now.
            // SpawnManager only tracks UUID→entity mapping.

            // Force-load the chunk at the NPC's expected position BEFORE checking
            // hasTrackedNpc so that serialised-but-unloaded entities become visible
            // to UUID / getEntitiesOfClass lookups, preventing false misses that
            // would otherwise trigger duplicate spawns.
            NpcChunkForceManager.ensureRouteTargetChunkForced(level, npcId,
                new Vec3(spawnX, spawnY, spawnZ));

            if (hasTrackedNpc(level, npcId)) {
                FORCE_SPAWN_IDS.remove(npcId);
                if (desertFestivalMarlon) {
                    enforceDesertFestivalMarlonPosition(level, getTrackedNpc(level, npcId));
                }
                if (troutDerbyWilly) {
                    enforceTroutDerbyWillyPosition(level, getTrackedNpc(level, npcId));
                }
                if (squidFestWilly) {
                    enforceSquidFestWillyPosition(level, getTrackedNpc(level, npcId));
                }
                if (isKrobus(npcId)) {
                    enforceKrobusFixedPosition(getTrackedNpc(level, npcId));
                }
                TRACKED_MISS_COUNTS.put(npcId, 0);
                continue;
            }

            StardewNpcEntity existing = loadedByNpcId.get(npcId);
            if (existing != null) {
                FORCE_SPAWN_IDS.remove(npcId);
                if (desertFestivalMarlon) {
                    enforceDesertFestivalMarlonPosition(level, existing);
                }
                if (troutDerbyWilly) {
                    enforceTroutDerbyWillyPosition(level, existing);
                }
                if (squidFestWilly) {
                    enforceSquidFestWillyPosition(level, existing);
                }
                if (isKrobus(npcId)) {
                    enforceKrobusFixedPosition(existing);
                }
                TRACKED_NPC_UUIDS.put(npcId, existing.getUUID());
                TRACKED_MISS_COUNTS.put(npcId, 0);
                continue;
            }

            boolean forced = FORCE_SPAWN_IDS.remove(npcId);
            if (desertFestivalMarlon) {
                TRACKED_NPC_UUIDS.remove(npcId);
                TRACKED_MISS_COUNTS.remove(npcId);
                forced = true;
            }
            if (troutDerbyWilly) {
                TRACKED_NPC_UUIDS.remove(npcId);
                TRACKED_MISS_COUNTS.remove(npcId);
                forced = true;
            }
            if (squidFestWilly) {
                TRACKED_NPC_UUIDS.remove(npcId);
                TRACKED_MISS_COUNTS.remove(npcId);
                forced = true;
            }
            if (!forced) {
                // If a tracked UUID still exists for this NPC but the entity wasn't
                // found above, give it a short grace window for chunk reload.
                // If it remains unresolved for long enough, treat it as truly
                // missing and self-heal by clearing the stale tracked UUID.
                UUID stillTracked = TRACKED_NPC_UUIDS.get(npcId);
                if (stillTracked != null) {
                    if (isKrobus(npcId)) {
                        TRACKED_NPC_UUIDS.remove(npcId, stillTracked);
                    } else if (!promoteStaleTrackedNpcToMissing(level, npcId, stillTracked, false)) {
                        continue;
                    }
                }

                int misses = TRACKED_MISS_COUNTS.getOrDefault(npcId, 0) + 1;
                TRACKED_MISS_COUNTS.put(npcId, misses);
                if (misses < RESPAWN_CONFIRM_MISSES) {
                    continue;
                }

                long now = level.getGameTime();
                Long lastSpawn = LAST_SPAWN_GAME_TIME.get(npcId);
                if (lastSpawn != null) {
                    long delta = now - lastSpawn;
                    if (delta < RESPAWN_COOLDOWN_TICKS) {
                        continue;
                    }
                }
            }

            StardewNpcEntity npc = ModEntities.STARDEW_NPC.get().create(level);
            if (npc == null) {
                continue;
            }

            npc.setNpcId(npcId);
            npc.moveTo(spawnX, spawnY, spawnZ, yaw, 0.0F);
            npc.setCustomNameVisible(false);
            if (isKrobus(npcId)) {
                enforceKrobusFixedPosition(npc);
            } else {
                // Snap to block surface (fixes floating on slabs/stairs)
                NpcCentralMovementService.snapToSurface(level, npc);
            }
            boolean added = level.addFreshEntity(npc);
            if (!added) {
                TRACKED_NPC_UUIDS.remove(npcId);
                continue;
            }
            TRACKED_NPC_UUIDS.put(npcId, npc.getUUID());
            TRACKED_MISS_COUNTS.put(npcId, 0);
            LAST_SPAWN_GAME_TIME.put(npcId, level.getGameTime());
            com.stardew.craft.api.v1.npc.StardewNpcLifecycleEvents
                    .announceSpawned(npcId, npc, "scheduled_spawn");
        }
    }

    /**
     * Tick for NPCs that live in the mining dimension (currently only "dwarf").
     * Called from NpcSystem when any player is in stardew_mining.
     */
    public static void tickMiningDimension(ServerLevel mineLevel) {
        if (!ModMiningDimensions.STARDEW_MINING.equals(mineLevel.dimension())) {
            return;
        }

        for (String npcId : MINING_DIM_NPC_IDS) {
            NpcCapabilityProfile profile = NpcDataRegistry.capabilities().get(npcId);
            if (profile == null || !profile.implemented()) {
                continue;
            }

            // Force-load the spawn chunk so the entity can be found
            NpcChunkForceManager.ensureRouteTargetChunkForced(mineLevel, npcId,
                new Vec3(DWARF_SPAWN_X, DWARF_SPAWN_Y, DWARF_SPAWN_Z));

            if (hasTrackedNpc(mineLevel, npcId)) {
                // Already alive — only log occasionally
                continue;
            }

            UUID stillTracked = TRACKED_NPC_UUIDS.get(npcId);
            if (stillTracked != null) {
                if (!promoteStaleTrackedNpcToMissing(mineLevel, npcId, stillTracked, true)) {
                    continue;
                }
            }

            // Check if already loaded
            StardewNpcEntity existing = findLoadedNpcById(mineLevel, npcId, null);
            if (existing != null) {
                TRACKED_NPC_UUIDS.put(npcId, existing.getUUID());
                TRACKED_MISS_COUNTS.put(npcId, 0);
                continue;
            }

            // Miss-count / cooldown
            int misses = TRACKED_MISS_COUNTS.getOrDefault(npcId, 0) + 1;
            TRACKED_MISS_COUNTS.put(npcId, misses);
            if (misses <= RESPAWN_CONFIRM_MISSES) {
            }
            if (misses < RESPAWN_CONFIRM_MISSES) {
                continue;
            }
            Long lastSpawn = LAST_SPAWN_GAME_TIME.get(npcId);
            if (lastSpawn != null && (mineLevel.getGameTime() - lastSpawn) < RESPAWN_COOLDOWN_TICKS) {
                continue;
            }

            StardewNpcEntity npc = ModEntities.STARDEW_NPC.get().create(mineLevel);
            if (npc == null) continue;

            npc.setNpcId(npcId);
            npc.moveTo(DWARF_SPAWN_X, DWARF_SPAWN_Y, DWARF_SPAWN_Z, DWARF_SPAWN_YAW, 0.0F);
            npc.setCustomNameVisible(false);
            NpcCentralMovementService.snapToSurface(mineLevel, npc);
            boolean added = mineLevel.addFreshEntity(npc);
            if (added) {
                TRACKED_NPC_UUIDS.put(npcId, npc.getUUID());
                TRACKED_MISS_COUNTS.put(npcId, 0);
                LAST_SPAWN_GAME_TIME.put(npcId, mineLevel.getGameTime());
                com.stardew.craft.api.v1.npc.StardewNpcLifecycleEvents
                        .announceSpawned(
                                npcId, npc, "mining_dimension_spawn");
            } else {
            }
        }
    }

    private static Map<String, StardewNpcEntity> collectAndDeduplicateLoaded(ServerLevel level, Set<String> implementedIds) {
        List<StardewNpcEntity> all = getCachedAllNpcs(level);
        Map<String, StardewNpcEntity> byId = new LinkedHashMap<>();
        List<StardewNpcEntity> toDiscard = new ArrayList<>();

        for (StardewNpcEntity entity : all) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            if (entity.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)) {
                continue;
            }
            String id = entity.getNpcId();
            String canonicalId = canonicalNpcId(id);
            // Joja NPCs 完全由 JojaNpcEvents 管理 —— 不入这里的 byId 去重表也不加入 discard 列表。
            if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(canonicalId)) {
                continue;
            }
            if (canonicalId == null || canonicalId.isBlank() || !implementedIds.contains(canonicalId)) {
                toDiscard.add(entity);
                continue;
            }

            StardewNpcEntity keep = byId.putIfAbsent(canonicalId, entity);
            if (keep != null && keep != entity) {
                toDiscard.add(entity);
            }
        }

        for (StardewNpcEntity duplicate : toDiscard) {
            discardWithReason(duplicate, "collect_deduplicate");
        }

        for (Map.Entry<String, StardewNpcEntity> entry : byId.entrySet()) {
            TRACKED_NPC_UUIDS.put(entry.getKey(), entry.getValue().getUUID());
        }

        return byId;
    }

    private static void cleanupUnknownAndDuplicated(ServerLevel level, Set<String> implementedIds) {
        List<StardewNpcEntity> all = getCachedAllNpcs(level);
        Set<String> seen = new HashSet<>();

        for (StardewNpcEntity entity : all) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            if (entity.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)) {
                continue;
            }
            String id = canonicalNpcId(entity.getNpcId());
            // Joja Mart NPCs are managed exclusively by JojaNpcEvents (骆驼商人同款独立生命周期)。
            // 不要让通用 sweep 碰它们 —— 否则 profile 尚未加载时会被当作 "unknown" 清掉。
            if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(id)) {
                continue;
            }
            if (id == null || id.isBlank() || !implementedIds.contains(id)) {
                discardWithReason(entity, "sweep_unknown_or_unimplemented");
                continue;
            }

            if (!seen.add(id)) {
                discardWithReason(entity, "sweep_duplicate_same_id");
                continue;
            }

            TRACKED_NPC_UUIDS.put(id, entity.getUUID());
        }
    }

    private static boolean hasTrackedNpc(ServerLevel level, String npcId) {
        String canonicalId = canonicalNpcId(npcId);
        UUID tracked = TRACKED_NPC_UUIDS.get(canonicalId);
        if (tracked == null) {
            StardewNpcEntity byId = findLoadedNpcById(level, canonicalId, null);
            if (byId != null) {
                TRACKED_NPC_UUIDS.put(canonicalId, byId.getUUID());
                TRACKED_MISS_COUNTS.put(canonicalId, 0);
                return true;
            }
            return false;
        }

        var entity = level.getEntity(tracked);
        if (!(entity instanceof StardewNpcEntity npc)) {
            StardewNpcEntity byId = findLoadedNpcById(level, canonicalId, null);
            if (byId != null) {
                TRACKED_NPC_UUIDS.put(canonicalId, byId.getUUID());
                TRACKED_MISS_COUNTS.put(canonicalId, 0);
                return true;
            }
            // Entity not found — likely in an unloaded chunk. Do NOT evict the
            // tracked UUID or increment miss count here; let the caller's
            // miss-count logic handle respawn decisions. Evicting prematurely
            // causes the classic "spawn new → old chunk loads → duplicate" race.
            return false;
        }

        // Removed/dead entities can remain addressable by UUID briefly; treat them as missing
        // so prototype respawn can recover immediately.
        if (npc.isRemoved() || !npc.isAlive()) {
            TRACKED_NPC_UUIDS.remove(canonicalId);
            TRACKED_MISS_COUNTS.put(canonicalId, TRACKED_MISS_COUNTS.getOrDefault(canonicalId, 0) + 1);
            return false;
        }

        if (!canonicalId.equals(canonicalNpcId(npc.getNpcId()))) {
            TRACKED_NPC_UUIDS.remove(canonicalId);
            TRACKED_MISS_COUNTS.put(canonicalId, TRACKED_MISS_COUNTS.getOrDefault(canonicalId, 0) + 1);
            return false;
        }
        TRACKED_MISS_COUNTS.put(canonicalId, 0);
        return true;
    }

    private static StardewNpcEntity findLoadedNpcById(ServerLevel level, String canonicalNpcId, UUID excludeUuid) {
        if (level == null || canonicalNpcId == null || canonicalNpcId.isBlank()) {
            return null;
        }
        for (StardewNpcEntity entity : getCachedAllNpcs(level)) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            if (entity.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)) {
                continue;
            }
            if (excludeUuid != null && excludeUuid.equals(entity.getUUID())) {
                continue;
            }
            if (canonicalNpcId.equals(canonicalNpcId(entity.getNpcId()))) {
                return entity;
            }
        }
        return null;
    }

    private static void reconcileSingletonByNpcId(ServerLevel level, String canonicalNpcId, UUID preferredUuid) {
        if (level == null || canonicalNpcId == null || canonicalNpcId.isBlank()) {
            return;
        }

        StardewNpcEntity keeper = null;
        if (preferredUuid != null) {
            var preferred = level.getEntity(preferredUuid);
            if (preferred instanceof StardewNpcEntity preferredNpc
                && !preferredNpc.isRemoved()
                && preferredNpc.isAlive()
                && canonicalNpcId.equals(canonicalNpcId(preferredNpc.getNpcId()))) {
                keeper = preferredNpc;
            }
        }

        List<StardewNpcEntity> sameId = new ArrayList<>();
        for (StardewNpcEntity entity : getCachedAllNpcs(level)) {
            if (entity.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)) {
                continue;
            }
            if (!canonicalNpcId.equals(canonicalNpcId(entity.getNpcId()))) {
                continue;
            }
            sameId.add(entity);
            if (keeper == null && !entity.isRemoved() && entity.isAlive()) {
                keeper = entity;
            }
        }

        if (keeper == null) {
            return;
        }

        for (StardewNpcEntity entity : sameId) {
            if (entity == keeper) {
                continue;
            }
            discardWithReason(entity, "join_singleton_reconcile");
        }

        TRACKED_NPC_UUIDS.put(canonicalNpcId, keeper.getUUID());
        TRACKED_MISS_COUNTS.put(canonicalNpcId, 0);
    }

    public static StardewNpcEntity getTrackedNpc(ServerLevel level, String npcId) {
        if (level == null || npcId == null || npcId.isBlank()) {
            return null;
        }

        String canonicalId = canonicalNpcId(npcId);
        UUID tracked = TRACKED_NPC_UUIDS.get(canonicalId);
        if (tracked == null) {
            return null;
        }

        var entity = level.getEntity(tracked);
        // entity == null almost always means "chunk currently unloaded", NOT "entity gone".
        // Do NOT evict the tracked UUID here — doing so caused duplicate prototype
        // respawn (e.g. Sandy) when her Oasis chunk unloaded while the player was in
        // another dimension. Mirror the safe behaviour already present in hasTrackedNpc.
        if (entity == null) {
            return null;
        }

        if (!(entity instanceof StardewNpcEntity npc) || npc.isRemoved() || !npc.isAlive()) {
            TRACKED_NPC_UUIDS.remove(canonicalId);
            return null;
        }

        if (!canonicalId.equals(canonicalNpcId(npc.getNpcId()))) {
            TRACKED_NPC_UUIDS.remove(canonicalId);
            return null;
        }

        return npc;
    }

    public static boolean snapNpcToCurrentSchedule(ServerLevel level, String npcId) {
        if (level == null || npcId == null || npcId.isBlank()) {
            return false;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return false;
        }
        String canonicalId = canonicalNpcId(npcId);
        if (canonicalId == null || canonicalId.isBlank()) {
            return false;
        }
        if (MINING_DIM_NPC_IDS.contains(canonicalId) || com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(canonicalId)) {
            return false;
        }
        if (NpcDataRegistry.schedules().get(canonicalId) == null) {
            return false;
        }
        StardewNpcEntity npc = getTrackedNpc(level, canonicalId);
        if (npc == null) {
            return false;
        }
        NpcRuntimeState state = NpcRuntimeDataManager.get(level).states().get(canonicalId);
        if (state == null) {
            return false;
        }
        Vec3 sharedSpawn = Vec3.atCenterOf(level.getSharedSpawnPos());
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, sharedSpawn);
        if (target == null || target.position() == null) {
            return false;
        }
        NpcChunkForceManager.ensureRouteTargetChunkForced(level, canonicalId, target.position());
        moveNpcToScheduleTarget(level, npc, target.position(), state);
        TRACKED_NPC_UUIDS.put(canonicalId, npc.getUUID());
        TRACKED_MISS_COUNTS.put(canonicalId, 0);
        NpcCentralMovementService.resetMovementPlan(canonicalId);
        return true;
    }

    public static void prepareCurrentScheduleTargets(ServerLevel level) {
        if (level == null || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);
    }

    public static boolean forceNpcToCurrentSchedule(ServerLevel level, String npcId) {
        if (level == null || npcId == null || npcId.isBlank()) {
            return false;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return false;
        }
        ensureServerContext(level);
        String canonicalId = canonicalNpcId(npcId);
        if (canonicalId == null || canonicalId.isBlank()) {
            return false;
        }
        if (MINING_DIM_NPC_IDS.contains(canonicalId) || com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(canonicalId)) {
            return false;
        }
        if (NpcDataRegistry.schedules().get(canonicalId) == null) {
            return false;
        }

        NpcScheduleRuntimeService.invalidateCache();
        NpcScheduleRuntimeService.tick(level);
        NpcRuntimeState state = NpcRuntimeDataManager.get(level).states().get(canonicalId);
        if (state == null) {
            return false;
        }
        Vec3 sharedSpawn = Vec3.atCenterOf(level.getSharedSpawnPos());
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, sharedSpawn);
        if (target == null || target.position() == null) {
            return false;
        }

        NpcChunkForceManager.ensureRouteTargetChunkForced(level, canonicalId, target.position());
        StardewNpcEntity npc = getTrackedNpc(level, canonicalId);
        if (npc == null) {
            npc = findLoadedNpcById(level, canonicalId, null);
        }
        if (npc != null) {
            moveNpcToScheduleTarget(level, npc, target.position(), state);
            TRACKED_NPC_UUIDS.put(canonicalId, npc.getUUID());
            TRACKED_MISS_COUNTS.put(canonicalId, 0);
            NpcCentralMovementService.resetMovementPlan(canonicalId);
            return true;
        }

        TRACKED_NPC_UUIDS.remove(canonicalId);
        TRACKED_MISS_COUNTS.remove(canonicalId);
        LAST_SPAWN_GAME_TIME.remove(canonicalId);
        FORCE_SPAWN_IDS.add(canonicalId);
        tick(level);

        npc = getTrackedNpc(level, canonicalId);
        if (npc == null) {
            npc = findLoadedNpcById(level, canonicalId, null);
        }
        if (npc == null) {
            return false;
        }
        moveNpcToScheduleTarget(level, npc, target.position(), state);
        TRACKED_NPC_UUIDS.put(canonicalId, npc.getUUID());
        TRACKED_MISS_COUNTS.put(canonicalId, 0);
        NpcCentralMovementService.resetMovementPlan(canonicalId);
        return true;
    }

    private static JsonObject resolveSpawnPos(JsonObject spawns, String canonicalNpcId, String rawNpcId) {
        if (spawns == null) {
            return null;
        }
        if (spawns.has(canonicalNpcId) && spawns.get(canonicalNpcId).isJsonObject()) {
            return spawns.getAsJsonObject(canonicalNpcId);
        }
        if (rawNpcId != null && spawns.has(rawNpcId) && spawns.get(rawNpcId).isJsonObject()) {
            return spawns.getAsJsonObject(rawNpcId);
        }
        return null;
    }

    private static Vec3 resolveRuntimeScheduleSpawn(ServerLevel level, String npcId) {
        if (level == null || npcId == null || npcId.isBlank()) {
            return null;
        }
        if (NpcDataRegistry.schedules().get(npcId) == null) {
            return null;
        }
        NpcRuntimeState state = NpcRuntimeDataManager.get(level).states().get(npcId);
        if (state == null) {
            return null;
        }
        Vec3 sharedSpawn = Vec3.atCenterOf(level.getSharedSpawnPos());
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, sharedSpawn);
        if (target == null || target.position() == null) {
            return null;
        }

        // If schedule resolution returns the world shared spawn, let static/explicit spawn points
        // handle bootstrap instead.
        if (target.position().distanceToSqr(sharedSpawn) < 0.25D) {
            return null;
        }
        return target.position();
    }

    private static void moveNpcToScheduleTarget(ServerLevel level, StardewNpcEntity npc, Vec3 target, NpcRuntimeState state) {
        npc.getNavigation().stop();
        npc.moveTo(target.x, target.y, target.z, yawFromSdvFacing(state.facing()), 0.0F);
        NpcCentralMovementService.snapToSurface(level, npc);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.hasImpulse = false;
        applyScheduleFacing(npc, state);
        npc.setWalking(false);
    }

    private static void applyScheduleFacing(StardewNpcEntity npc, NpcRuntimeState state) {
        if (npc == null || state == null || npc.isFacingOverrideActive() || npc.isIdleLookActive()) {
            return;
        }
        float yaw = yawFromSdvFacing(state.facing());
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
    }

    private static void enforceDesertFestivalMarlonPosition(ServerLevel level, StardewNpcEntity npc) {
        if (level == null || npc == null || npc.isRemoved() || !npc.isAlive()) {
            return;
        }
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveDesertFestivalMarlonTarget();
        Vec3 pos = target.position();
        float yaw = yawFromSdvFacing(NpcScheduleRuntimeService.desertFestivalMarlonFacing());
        npc.getNavigation().stop();
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.hasImpulse = false;
        npc.setWalking(false);
        NpcCentralMovementService.resetMovementPlan("marlon");
    }

    private static void enforceTroutDerbyWillyPosition(ServerLevel level, StardewNpcEntity npc) {
        if (level == null || npc == null || npc.isRemoved() || !npc.isAlive()) {
            return;
        }
        Vec3 pos = com.stardew.craft.festival.trout.TroutDerbyService.willyPosition();
        float yaw = yawFromSdvFacing(com.stardew.craft.festival.trout.TroutDerbyService.WILLY_FACING);
        npc.getNavigation().stop();
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.hasImpulse = false;
        npc.setWalking(false);
        NpcCentralMovementService.resetMovementPlan(com.stardew.craft.festival.trout.TroutDerbyService.WILLY_NPC_ID);
    }

    private static void enforceSquidFestWillyPosition(ServerLevel level, StardewNpcEntity npc) {
        if (level == null || npc == null || npc.isRemoved() || !npc.isAlive()) {
            return;
        }
        Vec3 pos = com.stardew.craft.festival.squid.SquidFestService.willyPosition();
        float yaw = yawFromSdvFacing(com.stardew.craft.festival.squid.SquidFestService.WILLY_FACING);
        npc.getNavigation().stop();
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.hasImpulse = false;
        npc.setWalking(false);
        NpcCentralMovementService.resetMovementPlan(com.stardew.craft.festival.squid.SquidFestService.WILLY_NPC_ID);
    }

    private static float yawFromSdvFacing(int facing) {
        return switch (facing) {
            case 0 -> 180.0F;
            case 1 -> -90.0F;
            case 2 -> 0.0F;
            case 3 -> 90.0F;
            default -> 0.0F;
        };
    }

    private static String canonicalNpcId(String npcId) {
        if (npcId == null) {
            return null;
        }
        String result = NpcRoutePlanner.canonicalNpcId(npcId);
        return result.isEmpty() ? null : result;
    }

    private static boolean isKrobus(String npcId) {
        return KROBUS_NPC_ID.equals(canonicalNpcId(npcId));
    }

    private static void enforceKrobusFixedPosition(StardewNpcEntity npc) {
        if (npc == null || npc.isRemoved() || !npc.isAlive()) {
            return;
        }

        npc.getNavigation().stop();
        double dx = npc.getX() - KROBUS_FIXED_X;
        double dy = npc.getY() - KROBUS_FIXED_Y;
        double dz = npc.getZ() - KROBUS_FIXED_Z;
        if (dx * dx + dy * dy + dz * dz > KROBUS_FIXED_EPSILON_SQ) {
            npc.moveTo(KROBUS_FIXED_X, KROBUS_FIXED_Y, KROBUS_FIXED_Z, KROBUS_FIXED_YAW, 0.0F);
        }
        npc.setYRot(KROBUS_FIXED_YAW);
        npc.setYHeadRot(KROBUS_FIXED_YAW);
        npc.setYBodyRot(KROBUS_FIXED_YAW);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.hasImpulse = false;
    }

    private static boolean promoteStaleTrackedNpcToMissing(ServerLevel level,
                                                           String npcId,
                                                           UUID trackedUuid,
                                                           boolean miningDimension) {
        int unresolvedMisses = TRACKED_MISS_COUNTS.getOrDefault(npcId, 0) + 1;
        TRACKED_MISS_COUNTS.put(npcId, unresolvedMisses);
        if (unresolvedMisses < TRACKED_ENTITY_RECOVERY_MISSES) {
            return false;
        }

        Long lastSpawn = LAST_SPAWN_GAME_TIME.get(npcId);
        long now = level.getGameTime();
        if (lastSpawn != null && (now - lastSpawn) < RESPAWN_COOLDOWN_TICKS) {
            return false;
        }

        TRACKED_NPC_UUIDS.remove(npcId, trackedUuid);
        StardewCraft.LOGGER.warn(
            "[NPC_SPAWN] Recovering stale tracked NPC {} in {} after {} unresolved checks (tracked UUID: {})",
            npcId,
            miningDimension ? "mining" : "valley",
            unresolvedMisses,
            trackedUuid
        );
        return true;
    }

    private static void ensureServerContext(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (activeServer == server) {
            return;
        }

        activeServer = server;
        tickCounter = 0;
        initialSweepDone = false;
        TRACKED_NPC_UUIDS.clear();
        TRACKED_MISS_COUNTS.clear();
        LAST_SPAWN_GAME_TIME.clear();
        SUPPRESSED_SPAWN_IDS.clear();
        FORCE_SPAWN_IDS.clear();
        FESTIVAL_SPAWN_TARGETS.clear();
        ENTITY_SCAN_CACHES.clear();
        cachedImplementedCapabilities = Map.of();
    }

    public static void onServerStopped(MinecraftServer server) {
        if (activeServer != server) {
            return;
        }
        activeServer = null;
        tickCounter = 0;
        initialSweepDone = false;
        TRACKED_NPC_UUIDS.clear();
        TRACKED_MISS_COUNTS.clear();
        LAST_SPAWN_GAME_TIME.clear();
        SUPPRESSED_SPAWN_IDS.clear();
        FORCE_SPAWN_IDS.clear();
        FESTIVAL_SPAWN_TARGETS.clear();
        ENTITY_SCAN_CACHES.clear();
        cachedImplementedIds = Set.of();
        cachedImplementedCapabilities = Map.of();
    }

    private static final class EntityScanCache {
        private long gameTime = Long.MIN_VALUE;
        private List<StardewNpcEntity> entities = List.of();
    }

    private record FestivalSpawnTarget(String owner, Vec3 position, float yaw) {
    }

    private static void discardWithReason(StardewNpcEntity npc, String reason) {
        if (npc == null || npc.isRemoved()) {
            return;
        }
        // discard() directly removes the entity from the world.
        // kill() does NOT work here because StardewNpcEntity.hurt() returns false (invulnerable).
        com.stardew.craft.api.v1.npc.StardewNpcLifecycleEvents
                .announceRemoved(npc.getNpcId(), npc, reason);
        npc.discard();
    }

    public static boolean isOfficialInstance(StardewNpcEntity npc) {
        if (npc == null || npc.isRemoved() || npc.level() == null || npc.level().isClientSide) {
            return false;
        }
        if (npc.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)) {
            return true;
        }
        String id = canonicalNpcId(npc.getNpcId());
        if (id == null || id.isBlank()) {
            return false;
        }
        // Joja NPCs 由 JojaNpcEvents 独立管理（不进 TRACKED_NPC_UUIDS）—— 始终视为官方实例。
        if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(id)) {
            return true;
        }
        UUID tracked = TRACKED_NPC_UUIDS.get(id);
        if (tracked == null) {
            // Only forgive during early boot (first 60 ticks) before initial sweep runs.
            // After that, untracked entities are NOT official.
            return !initialSweepDone;
        }
        return tracked.equals(npc.getUUID());
    }

    public static SpawnDebugSnapshot getDebugSnapshot(ServerLevel level, String npcId) {
        if (level == null || npcId == null || npcId.isBlank()) {
            return null;
        }

        String canonicalId = canonicalNpcId(npcId);
        UUID trackedUuid = TRACKED_NPC_UUIDS.get(canonicalId);
        int missCount = TRACKED_MISS_COUNTS.getOrDefault(canonicalId, 0);
        Long lastSpawn = LAST_SPAWN_GAME_TIME.get(canonicalId);
        long spawnAge = lastSpawn == null ? -1L : (level.getGameTime() - lastSpawn);

        int loadedCount = 0;
        UUID firstLoadedUuid = null;
        for (StardewNpcEntity entity : getCachedAllNpcs(level)) {
            if (!canonicalId.equals(canonicalNpcId(entity.getNpcId()))) {
                continue;
            }
            loadedCount++;
            if (firstLoadedUuid == null) {
                firstLoadedUuid = entity.getUUID();
            }
        }

        String chunk = NpcChunkForceManager.currentForcedTargetChunk(level, canonicalId);

        boolean trackedAlive = false;
        if (trackedUuid != null) {
            var trackedEntity = level.getEntity(trackedUuid);
            trackedAlive = trackedEntity instanceof StardewNpcEntity npc && !npc.isRemoved() && npc.isAlive();
        }

        return new SpawnDebugSnapshot(
            canonicalId,
            trackedUuid == null ? "<none>" : trackedUuid.toString(),
            trackedAlive,
            loadedCount,
            firstLoadedUuid == null ? "<none>" : firstLoadedUuid.toString(),
            missCount,
            spawnAge,
            chunk
        );
    }

    public record SpawnDebugSnapshot(
        String npcId,
        String trackedUuid,
        boolean trackedAlive,
        int loadedCount,
        String firstLoadedUuid,
        int missCount,
        long spawnAgeTicks,
        String forcedChunk
    ) {
    }

}
