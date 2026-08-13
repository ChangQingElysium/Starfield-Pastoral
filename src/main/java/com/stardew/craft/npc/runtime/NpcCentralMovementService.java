package com.stardew.craft.npc.runtime;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NPC movement controller driven by schedule targets and data-defined route profiles.
 *
 * <h2>Architecture (Citizens2-inspired rewrite)</h2>
 * <p>All pathfinding is delegated to vanilla MC's {@code GroundPathNavigation}.
 * For each route step, we call {@code npc.getNavigation().moveTo(stepTarget)}
 * directly — <b>no intermediate waypoints</b>. Vanilla nav handles terrain,
 * elevation, doors, slabs, stairs, jumping, and entity-width collision natively.</p>
 *
 * <p>Key mechanisms:</p>
 * <ul>
 *   <li><b>Periodic re-path</b>: every {@code REPATH_INTERVAL_TICKS} ticks, re-issue
 *       {@code moveTo()} so the NPC always has a fresh path toward the target.</li>
 *   <li><b>Block-level stuck detection</b>: track NPC block position each tick.
 *       If stationary for too long, recalculate the current path.</li>
 *   <li><b>WARP steps</b>: instant teleport for indoor/outdoor transitions (unchanged).</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class NpcCentralMovementService {
    /** Distance² threshold for considering a step target reached (2D horizontal). */
    private static final double STEP_REACH_SQR = 4.0D; // 2 blocks
    /** Final schedule target must be reached tightly enough for display placement. */
    private static final double FINAL_STEP_REACH_SQR = 0.25D; // 0.5 blocks
    private static final double NAV_DONE_STEP_REACH_SQR = 2.25D; // 1.5 blocks
    private static final double FINAL_APPROACH_DISTANCE_SQR = 1.0D; // final 1 block only
    private static final double FINAL_APPROACH_SPEED = 0.9D;
    /** Periodic re-path interval (~1.0s). Citizens2 uses updatePathRate=20 by default. */
    private static final int REPATH_INTERVAL_TICKS = 20;
    /**
     * Displacement-based progress check interval (ticks).
     * Every N ticks, measure how far the NPC has actually moved.
     * If displacement is below threshold → stuck.
     */
    private static final int PROGRESS_CHECK_INTERVAL = 20;
    /**
    * Minimum displacement² over a check interval to count as "making progress".
    * About 0.2 blocks over 20 ticks. Below this means the NPC should recalculate.
     */
    private static final double PROGRESS_MIN_DISP_SQR = 0.04D;
    /** How many consecutive "no progress" checks before re-pathing. */
    private static final int STUCK_REPATH_CHECKS = 4;
    /** How many consecutive moveTo() failures before surfacing a throttled path warning. */
    private static final int NAV_FAIL_REPATH_THRESHOLD = 3;
    /** Door close timeout after an NPC opened the door. */
    private static final int DOOR_CLOSE_TIMEOUT_TICKS = 30;
    private static final double DOOR_OPEN_PROBE_REACH_SQR = 16.0D;
    private static final int MOVE_STATUS_LOG_INTERVAL_TICKS = 100;
    private static final int NO_PROGRESS_LOG_INTERVAL_TICKS = 40;
    private static final boolean MOVEMENT_DEBUG_ENABLED = Boolean.getBoolean("stardewcraft.npcMovementDebug");

    private static final Map<String, NpcRoutePlan> ACTIVE_PLANS = new HashMap<>();
    private static final Map<String, NpcRoutePlan> AUTHORED_PLANS = new HashMap<>();
    private static final Map<String, AuthoredDebugSnapshot> AUTHORED_DEBUG_SNAPSHOTS = new HashMap<>();
    private static final Map<String, String> LAST_NODE_SIGNATURE = new HashMap<>();
    private static final Map<String, DebugSnapshot> DEBUG_SNAPSHOTS = new HashMap<>();
    private static Map<String, NpcCapabilityProfile> cachedCapabilities = Map.of();
    private static List<NpcMovementEntry> cachedMovementEntries = List.of();
    private static MinecraftServer activeServer;

    private NpcCentralMovementService() {
    }

    public static DebugSnapshot getDebugSnapshot(String npcId) {
        if (!movementDebugEnabled()) {
            return null;
        }
        return DEBUG_SNAPSHOTS.get(npcId == null ? "" : npcId.toLowerCase());
    }

    public static void resetMovementPlan(String npcId) {
        String key = NpcRoutePlanner.canonicalNpcId(npcId);
        if (key.isBlank()) {
            return;
        }
        ACTIVE_PLANS.remove(key);
        AUTHORED_PLANS.keySet().removeIf(planKey -> planKey.startsWith(key + ":"));
        AUTHORED_DEBUG_SNAPSHOTS.keySet().removeIf(planKey -> planKey.startsWith(key + ":"));
        LAST_NODE_SIGNATURE.remove(key);
        DEBUG_SNAPSHOTS.remove(key);
    }

    public static void resetAuthoredMovementPlan(String npcId, String owner) {
        String key = authoredPlanKey(npcId, owner);
        if (key.isBlank()) {
            return;
        }
        AUTHORED_PLANS.remove(key);
        AUTHORED_DEBUG_SNAPSHOTS.remove(key);
    }

    public static AuthoredDebugSnapshot getAuthoredDebugSnapshot(String npcId, String owner) {
        String key = authoredPlanKey(npcId, owner);
        return key.isBlank() ? null : AUTHORED_DEBUG_SNAPSHOTS.get(key);
    }

    public static boolean tickAuthoredWalkTarget(ServerLevel level,
                                                StardewNpcEntity npc,
                                                String owner,
                                                String pointId,
                                                Vec3 target) {
        if (level == null || npc == null || target == null) {
            return false;
        }
        ensureServerContext(level);
        String key = authoredPlanKey(npc.getNpcId(), owner);
        if (key.isBlank()) {
            return false;
        }

        long now = level.getGameTime();
        String signature = key + "#" + pointId + "#"
            + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", target.x, target.y, target.z);
        NpcRoutePlan plan = AUTHORED_PLANS.get(key);
        boolean needsNewPlan = plan == null
            || !signature.equals(plan.signature)
            || !npc.getUUID().equals(plan.boundEntityUuid);
        if (needsNewPlan) {
            if (plan != null) {
                closeOpenedDoors(level, npc, plan, now, true);
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
            }
            plan = new NpcRoutePlan(signature, npc.getUUID(), List.of(NpcRoutePlanner.NpcRouteStep.walk(pointId, target)), now);
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            AUTHORED_PLANS.put(key, plan);
        }

        executePlanTick(level, npc, plan);
        return plan.currentStepIndex >= plan.steps.size();
    }

    public static int tickAuthoredWalkRoute(ServerLevel level,
                                           StardewNpcEntity npc,
                                           String owner,
                                           String routeId,
                                           List<Vec3> targets,
                                           boolean loop) {
        if (level == null || npc == null || targets == null || targets.isEmpty()) {
            return -1;
        }
        ensureServerContext(level);
        String key = authoredPlanKey(npc.getNpcId(), owner);
        if (key.isBlank()) {
            return -1;
        }

        long now = level.getGameTime();
        String signature = authoredRouteSignature(key, routeId, targets, loop);
        NpcRoutePlan plan = AUTHORED_PLANS.get(key);
        boolean needsNewPlan = plan == null
            || !signature.equals(plan.signature)
            || !npc.getUUID().equals(plan.boundEntityUuid);
        if (needsNewPlan) {
            if (plan != null) {
                closeOpenedDoors(level, npc, plan, now, true);
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
            }
            List<NpcRoutePlanner.NpcRouteStep> steps = new ArrayList<>();
            String pointPrefix = routeId == null || routeId.isBlank() ? "authored" : routeId.trim();
            for (int i = 0; i < targets.size(); i++) {
                steps.add(NpcRoutePlanner.NpcRouteStep.walk(pointPrefix + "_" + i, targets.get(i)));
            }
            plan = new NpcRoutePlan(signature, npc.getUUID(), steps, now);
            plan.tightStepArrival = true;
            plan.acceptNavigationDoneArrival = true;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            AUTHORED_PLANS.put(key, plan);
        }

        if (plan.currentStepIndex >= plan.steps.size()) {
            if (!loop) {
                return -1;
            }
            restartAuthoredRoutePlan(level, npc, plan, now);
        }

        int previousStepIndex = plan.currentStepIndex;
        executePlanTick(level, npc, plan);
        int reachedStepIndex = plan.currentStepIndex > previousStepIndex ? previousStepIndex : -1;
        if (loop && plan.currentStepIndex >= plan.steps.size()) {
            restartAuthoredRoutePlan(level, npc, plan, now);
        }
        updateAuthoredDebugSnapshot(key, npc, plan, now);
        return reachedStepIndex;
    }

    private static String authoredRouteSignature(String key, String routeId, List<Vec3> targets, boolean loop) {
        StringBuilder signature = new StringBuilder(key)
            .append('#')
            .append(routeId == null ? "" : routeId.trim())
            .append("#loop=")
            .append(loop);
        for (Vec3 target : targets) {
            signature.append('#').append(String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", target.x, target.y, target.z));
        }
        return signature.toString();
    }

    private static void restartAuthoredRoutePlan(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, long now) {
        closeOpenedDoors(level, npc, plan, now, true);
        npc.getNavigation().stop();
        stopHorizontalMotionPreserveGravity(npc);
        plan.currentStepIndex = 0;
        plan.consecutiveNavFailures = 0;
        plan.lastProgressTick = now;
        plan.stuckCheckCount = 0;
        plan.progressCheckTick = now;
        plan.progressCheckX = npc.getX();
        plan.progressCheckZ = npc.getZ();
        plan.lastRepathTick = now - REPATH_INTERVAL_TICKS;
        plan.lastSuccessfulMoveCommandStep = -1;
        plan.debugStage = "loop_restart";
    }

    private static void updateAuthoredDebugSnapshot(String key, StardewNpcEntity npc, NpcRoutePlan plan, long now) {
        if (key == null || key.isBlank() || npc == null || plan == null) {
            return;
        }
        boolean hasPath = npc.getNavigation().getPath() != null;
        boolean navDone = npc.getNavigation().isDone();
        BlockPos navTarget = npc.getNavigation().getTargetPos();
        AuthoredDebugSnapshot snapshot = AUTHORED_DEBUG_SNAPSHOTS.computeIfAbsent(key, ignored -> new AuthoredDebugSnapshot());
        snapshot.stage = plan.debugStage;
        snapshot.pointId = plan.debugPointId;
        snapshot.pathSize = plan.steps.size();
        snapshot.pathIndex = Math.min(plan.currentStepIndex, plan.steps.size());
        snapshot.target = plan.debugTarget;
        snapshot.nextWaypoint = plan.debugNextWaypoint;
        snapshot.repathReason = plan.debugRepathReason;
        snapshot.navFailures = plan.consecutiveNavFailures;
        snapshot.hasPath = hasPath;
        snapshot.navDone = navDone;
        snapshot.navTarget = navTarget == null ? "<none>" : navTarget.toShortString();
        snapshot.position = npc.position();
        snapshot.lastTick = now;
    }

    public static void stopAuthoredMovement(StardewNpcEntity npc) {
        if (npc == null) {
            return;
        }
        npc.getNavigation().stop();
        stopHorizontalMotionPreserveGravity(npc);
    }

    private static String authoredPlanKey(String npcId, String owner) {
        String npcKey = NpcRoutePlanner.canonicalNpcId(npcId);
        if (npcKey.isBlank() || owner == null || owner.isBlank()) {
            return "";
        }
        return npcKey + ":" + owner.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean movementDebugEnabled() {
        return MOVEMENT_DEBUG_ENABLED;
    }

    public static void tick(ServerLevel level) {
        ensureServerContext(level);
        Map<String, NpcRuntimeState> runtimeStates = NpcRuntimeDataManager.get(level).states();
        Set<String> activeNpcIds = new HashSet<>();

        for (NpcMovementEntry movementEntry : movementEntries()) {
            String npcId = movementEntry.npcId();
            NpcCapabilityProfile profile = movementEntry.profile();
            activeNpcIds.add(npcId);
            // Skip NPCs that live in a different dimension (e.g. dwarf in mining)
            if (NpcSpawnManager.isMiningDimensionNpc(npcId)) continue;
            // Joja Mart NPCs 由 JojaNpcEvents 独立管理（骆驼商人同款），
            // 不能让本服务的路径规划 / teleport 干预它们。
            if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(npcId)) continue;
            StardewNpcEntity npc = NpcSpawnManager.getTrackedNpc(level, npcId);
            if (npc == null) {
                continue;
            }

            if (com.stardew.craft.festival.FestivalNpcController.controlsNpc(npcId)) {
                ACTIVE_PLANS.remove(npcId);
                LAST_NODE_SIGNATURE.remove(npcId);
                continue;
            }

            // Dialogue screens can outlive the short turning animation. Keep the NPC
            // frozen for the full conversation, not just the initial face-player hold.
            if (NpcInteractionService.isDialogueMovementLocked(npcId) || npc.isFacingOverrideActive()) {
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                if (movementDebugEnabled()) {
                    updateDebugSnapshot(npcId, "interaction_pause", "<none>", "<none>", 0, 0, false, npc.position(), npc.position(), "none", 0, "<none>", null, null);
                }
                continue;
            }

            NpcRuntimeState state = runtimeStates.get(npcId);
            boolean pathingSuppressed = !profile.canRunPathing() || (state != null && state.pathingSuppressed());
            if (pathingSuppressed) {
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                applyFacing(npc, state);
                if (movementDebugEnabled()) {
                    updateDebugSnapshot(npcId, "pathing_disabled", "<none>", "<none>", 0, 0, false, npc.position(), npc.position(), "none", 0, "<none>", null, null);
                }
                continue;
            }

            NpcRoutePlanner.NpcRouteContext route = NpcRoutePlanner.resolveRoute(level, npcId, state, npc.blockPosition());
            if (route == null || !route.ready()) {
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                applyFacing(npc, state);
                if (movementDebugEnabled()) {
                    updateDebugSnapshot(npcId,
                        route == null ? "no_route" : route.status.name().toLowerCase(java.util.Locale.ROOT),
                        route == null ? "<none>" : route.canonicalLocation,
                        route == null ? "<none>" : route.missingPointId,
                        0,
                        0,
                        false,
                        npc.position(),
                        npc.position(),
                        route == null ? "none" : route.diagnosticReason,
                        0,
                        "<none>",
                        route,
                        null);
                }
                continue;
            }
            boolean nodeChanged = markAndCheckScheduleNodeChange(npcId, state);
            String signature = buildPlanSignature(state, route);

            NpcRoutePlan plan = ACTIVE_PLANS.get(npcId);
            boolean entityReplaced = plan != null && !npc.getUUID().equals(plan.boundEntityUuid);
            boolean needNewPlan = plan == null || !signature.equals(plan.signature);
            // When the entity UUID changed (e.g. NPC respawned) but the plan
            // signature is identical, just rebind instead of rebuilding the
            // entire plan. This avoids the massive re-plan spam when the spawn
            // manager replaces entities.
            if (entityReplaced && !needNewPlan) {
                plan.boundEntityUuid = npc.getUUID();
            }
            if (needNewPlan || nodeChanged) {
                plan = buildPlan(level, npc, route, signature, level.getGameTime());
                ACTIVE_PLANS.put(npcId, plan);
            }

            if (plan == null || plan.steps.isEmpty()) {
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                applyFacing(npc, state);
                if (movementDebugEnabled()) {
                    updateDebugSnapshot(npcId, "empty_plan", route.canonicalLocation,
                        plan == null ? "<none>" : plan.missingPointId,
                        0, 0, false, npc.position(), npc.position(),
                        plan == null ? "none" : plan.routeDiagnosticReason,
                        0, "<none>", route, plan);
                }
                continue;
            }

            executePlanTick(level, npc, plan);
            if ("done".equals(plan.debugStage)) {
                applyFacing(npc, state);
            }
            if (movementDebugEnabled()) {
                updateDebugSnapshot(npcId,
                    plan.debugStage,
                    route.canonicalLocation,
                    plan.debugPointId,
                    plan.steps.size(),
                    plan.currentStepIndex,
                    plan.lastForcedTeleportUsed,
                    plan.debugTarget,
                    plan.debugNextWaypoint,
                    plan.debugRepathReason,
                    level.getGameTime() - plan.lastProgressTick,
                    NpcChunkForceManager.currentForcedTargetChunk(level, npcId),
                    route,
                    plan
                );
            }
        }

        NpcChunkForceManager.releaseInactiveForcedChunks(level, activeNpcIds);



        // Avoid mass forcing interior chunks per tick; indoor transitions are handled by
        // explicit route steps and bounded target chunk forcing.
        InteriorSubspaceManager.setInteriorChunksForced(level, false, "npc_runtime");
    }

    private static List<NpcMovementEntry> movementEntries() {
        Map<String, NpcCapabilityProfile> capabilities = NpcDataRegistry.capabilities();
        if (capabilities == cachedCapabilities) {
            return cachedMovementEntries;
        }

        List<NpcMovementEntry> entries = new ArrayList<>();
        for (NpcCapabilityProfile profile : capabilities.values()) {
            if (profile == null || !profile.implemented()) {
                continue;
            }
            entries.add(new NpcMovementEntry(NpcRoutePlanner.canonicalNpcId(profile.npcId()), profile));
        }
        cachedCapabilities = capabilities;
        cachedMovementEntries = List.copyOf(entries);
        return cachedMovementEntries;
    }

    private record NpcMovementEntry(String npcId, NpcCapabilityProfile profile) {
    }

    private static void updateDebugSnapshot(String npcId,
                                            String stage,
                                            String location,
                                            String pointId,
                                            int pathSize,
                                            int pathIndex,
                                            boolean forcedTeleportUsed,
                                            Vec3 target,
                                            Vec3 nextWaypoint,
                                            String repathReason,
                                            long noPathTicks,
                                            String forcedTargetChunk,
                                            NpcRoutePlanner.NpcRouteContext route,
                                            NpcRoutePlan plan) {
        if (!movementDebugEnabled()) {
            return;
        }
        DebugSnapshot snapshot = DEBUG_SNAPSHOTS.computeIfAbsent(npcId, k -> new DebugSnapshot());
        snapshot.update(stage, location, pointId, pathSize, pathIndex, forcedTeleportUsed, target, nextWaypoint, repathReason, noPathTicks, forcedTargetChunk);
        snapshot.updateRoute(route);
        snapshot.updatePlan(plan);
    }

    private static void ensureServerContext(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return;
        }
        if (activeServer == level.getServer()) {
            return;
        }

        activeServer = level.getServer();
        resetState();
    }

    public static void onServerStopped(MinecraftServer server) {
        if (activeServer != server) {
            return;
        }
        activeServer = null;
        resetState();
    }

    private static void resetState() {
        ACTIVE_PLANS.clear();
        AUTHORED_PLANS.clear();
        AUTHORED_DEBUG_SNAPSHOTS.clear();
        LAST_NODE_SIGNATURE.clear();
        DEBUG_SNAPSHOTS.clear();
        cachedCapabilities = Map.of();
        cachedMovementEntries = List.of();
        NpcRoutePlanner.resetState();
        NpcChunkForceManager.resetState();
        NpcScheduleRuntimeService.invalidateCache();
        NpcPathfinder.resetState();
    }

    private static NpcRoutePlan buildPlan(ServerLevel level,
                                            StardewNpcEntity npc,
                                            NpcRoutePlanner.NpcRouteContext route,
                                            String signature,
                                            long gameTime) {
        List<NpcRoutePlanner.NpcRouteStep> expanded = new ArrayList<>();

        String npcFixedInterior = InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition());
        String npcInteriorLocation = NpcRoutePlanner.fixedInteriorLocationAt(npc.blockPosition());
        boolean npcInFixedInterior = !npcFixedInterior.isBlank();
        boolean npcIndoors = npcInFixedInterior;
        Vec3 finalTarget = route.destinationSteps.get(route.destinationSteps.size() - 1).target;
        String destFixedInterior = InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(finalTarget));
        boolean destInFixedInterior = !destFixedInterior.isBlank();
        boolean destIndoors = destInFixedInterior;

        String routeStatus = route.status.name();
        String routeDiagnosticReason = route.diagnosticReason;
        String missingPointId = route.missingPointId;
        String missingPortalLinkId = route.missingPortalLinkId;

        if (npcIndoors && destIndoors) {
            boolean sameFixedInterior = npcInFixedInterior && destInFixedInterior && npcFixedInterior.equals(destFixedInterior);
            if (sameFixedInterior) {
                expanded.add(NpcRoutePlanner.NpcRouteStep.walk("indoor_direct", finalTarget));
            } else {
                Vec3 exitIndoor = NpcRoutePlanner.indoorExitForLocation(npcInteriorLocation);
                Vec3 exitOutdoor = NpcRoutePlanner.outdoorExitForLocation(npcInteriorLocation);
                if (exitIndoor != null && exitOutdoor != null) {
                    expanded.add(NpcRoutePlanner.NpcRouteStep.walk(npcInteriorLocation + "_indoor_exit", exitIndoor));
                    expanded.add(NpcRoutePlanner.NpcRouteStep.warp(npcInteriorLocation + "_outdoor_door", exitOutdoor));
                    // Then follow the destination steps (skip the outdoor door we already warped to)
                    for (NpcRoutePlanner.NpcRouteStep ds : route.destinationSteps) {
                        if (ds.mode == NpcRoutePlanner.RouteStepMode.WALK
                            && ds.target.distanceToSqr(exitOutdoor) < 4.0D) {
                            continue;
                        }
                        expanded.add(ds);
                    }
                } else {
                    routeStatus = NpcRoutePlanner.RouteStatus.WAITING_FOR_COORDINATES.name();
                    routeDiagnosticReason = exitIndoor == null ? "missing_indoor_exit_walk_target" : "missing_outdoor_exit_landing";
                    missingPortalLinkId = npcInteriorLocation.isBlank() ? npcFixedInterior : npcInteriorLocation;
                }
            }
        } else if (npcIndoors) {
            Vec3 exitIndoor = NpcRoutePlanner.indoorExitForLocation(npcInteriorLocation);
            Vec3 exitOutdoor = NpcRoutePlanner.outdoorExitForLocation(npcInteriorLocation);
            if (exitIndoor != null && exitOutdoor != null) {
                expanded.add(NpcRoutePlanner.NpcRouteStep.walk(npcInteriorLocation + "_indoor_exit", exitIndoor));
                expanded.add(NpcRoutePlanner.NpcRouteStep.warp(npcInteriorLocation + "_outdoor_door", exitOutdoor));
                for (NpcRoutePlanner.NpcRouteStep ds : route.destinationSteps) {
                    if (ds.mode == NpcRoutePlanner.RouteStepMode.WALK
                        && ds.target.distanceToSqr(exitOutdoor) < 4.0D) {
                        continue;
                    }
                    expanded.add(ds);
                }
            } else {
                routeStatus = NpcRoutePlanner.RouteStatus.WAITING_FOR_COORDINATES.name();
                routeDiagnosticReason = exitIndoor == null ? "missing_indoor_exit_walk_target" : "missing_outdoor_exit_landing";
                missingPortalLinkId = npcInteriorLocation.isBlank() ? npcFixedInterior : npcInteriorLocation;
            }
        } else {
            expanded.addAll(route.destinationSteps);
        }
        NpcRoutePlan plan = new NpcRoutePlan(signature, npc.getUUID(), expanded, gameTime);
        plan.progressCheckX = npc.getX();
        plan.progressCheckZ = npc.getZ();
        plan.routeStatus = routeStatus;
        plan.routeDiagnosticReason = routeDiagnosticReason;
        plan.missingPointId = missingPointId;
        plan.missingPortalLinkId = missingPortalLinkId;

        if (movementDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[NPC_PLAN] npc=").append(npc.getNpcId())
              .append(" indoor=").append(npcIndoors)
              .append(" destIndoor=").append(destIndoors)
              .append(" fixedInterior=").append(npcFixedInterior.isBlank() ? "<none>" : npcFixedInterior)
              .append(" destFixedInterior=").append(destFixedInterior.isBlank() ? "<none>" : destFixedInterior)
              .append(" pos=").append(fmt(npc.position()))
              .append(" steps=[");
            for (int i = 0; i < expanded.size(); i++) {
                NpcRoutePlanner.NpcRouteStep s = expanded.get(i);
                if (i > 0) sb.append(", ");
                sb.append(s.mode).append(':').append(s.pointId).append('@').append(fmt(s.target));
            }
            sb.append(']');
            StardewCraft.LOGGER.info(sb.toString());

            if (!NpcRoutePlanner.RouteStatus.READY.name().equals(routeStatus) || expanded.isEmpty()) {
                StardewCraft.LOGGER.warn("[NPC_MOVE] {} plan not ready status={} reason={} missingPoint={} missingLink={} npcInterior={} npcFixed={} destFixed={} pos={} target={}",
                    npc.getNpcId(), routeStatus, routeDiagnosticReason,
                    missingPointId == null || missingPointId.isBlank() ? "<none>" : missingPointId,
                    missingPortalLinkId == null || missingPortalLinkId.isBlank() ? "<none>" : missingPortalLinkId,
                    npcInteriorLocation.isBlank() ? "<none>" : npcInteriorLocation,
                    npcFixedInterior.isBlank() ? "<none>" : npcFixedInterior,
                    destFixedInterior.isBlank() ? "<none>" : destFixedInterior,
                    fmt(npc.position()), fmt(finalTarget));
            }
        }

        return plan;
    }

    private static String fmt(Vec3 v) {
        return v == null ? "null" : String.format(java.util.Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x, v.y, v.z);
    }

    // ──── Plan execution helpers ────

    /** Teleport NPC to the current step target and advance to the next step. */
    private static void advanceStepByTeleport(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, Vec3 target, long now) {
        npc.getNavigation().stop();
        stopHorizontalMotionPreserveGravity(npc);
        npc.setPos(target.x, target.y, target.z);
        snapToSurface(level, npc);
        plan.currentStepIndex++;
        plan.consecutiveNavFailures = 0;
        plan.lastProgressTick = now;
        plan.stuckCheckCount = 0;
        plan.progressCheckTick = now;
        plan.progressCheckX = target.x;
        plan.progressCheckZ = target.z;
        plan.lastRepathTick = now;
    }


    private static boolean executePlanTick(ServerLevel level,
                                           StardewNpcEntity npc,
                                           NpcRoutePlan plan) {
        if (plan.currentStepIndex >= plan.steps.size()) {
            closeOpenedDoors(level, npc, plan, level.getGameTime(), true);
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.debugStage = "done";
            return false;
        }

        plan.lastForcedTeleportUsed = false;
        plan.debugRepathReason = "none";
        NpcRoutePlanner.NpcRouteStep step = plan.steps.get(plan.currentStepIndex);
        Vec3 target = step.target;
        plan.debugPointId = step.pointId;
        plan.debugTarget = target;
        plan.debugNextWaypoint = target;
        long now = level.getGameTime();

        // ── WARP steps: instant teleport ──
        if (step.mode == NpcRoutePlanner.RouteStepMode.WARP) {
            NpcChunkForceManager.ensureRouteTargetChunkForced(level, npc.getNpcId(), target);
            plan.debugStage = "warp";
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.info("[NPC_MOVE] {} warp step={}/{} point={} from={} to={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId, fmt(npc.position()), fmt(target));
            }
            advanceStepByTeleport(level, npc, plan, target, now);
            return false;
        }

        // ── WALK steps: delegate to vanilla GroundPathNavigation ──
        NpcChunkForceManager.ensureRouteTargetChunkForced(level, npc.getNpcId(), target);
        NpcChunkForceManager.ensureRouteCorridorChunksForced(level, npc.getNpcId(), npc.position(), target);

        // Open doors near NPC and toward target
        long npcBlockKey = npc.blockPosition().asLong();
        Vec3 nextPathNode = nextPathNodeTarget(npc);
        plan.debugNextWaypoint = nextPathNode == null ? target : nextPathNode;
        if (npcBlockKey != plan.lastDoorCheckBlockKey) {
            plan.lastDoorCheckBlockKey = npcBlockKey;
            tryOpenNearbyDoors(level, npc, plan, target, nextPathNode);
        }
        closeOpenedDoors(level, npc, plan, now, false);

        // Check horizontal arrival at step target
        Vec3 toTarget = new Vec3(target.x - npc.getX(), 0.0D, target.z - npc.getZ());
        double distSqr = toTarget.lengthSqr();
        boolean finalStep = plan.currentStepIndex == plan.steps.size() - 1;
        boolean tightStep = finalStep || plan.tightStepArrival;
        double reachSqr = tightStep ? FINAL_STEP_REACH_SQR : STEP_REACH_SQR;
        if (distSqr <= reachSqr) {
            // Arrived at step target — advance
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.info("[NPC_MOVE] {} reached step={}/{} point={} pos={} target={} dist2d={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                    fmt(npc.position()), fmt(target), String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distSqr)));
            }
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.currentStepIndex++;
            plan.consecutiveNavFailures = 0;
            plan.lastProgressTick = now;
            plan.stuckCheckCount = 0;
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            plan.lastRepathTick = now;
            plan.debugStage = "step_reached";
            return false;
        }

        if (plan.acceptNavigationDoneArrival
            && plan.lastSuccessfulMoveCommandStep == plan.currentStepIndex
            && npc.getNavigation().isDone()
            && distSqr <= NAV_DONE_STEP_REACH_SQR) {
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.currentStepIndex++;
            plan.consecutiveNavFailures = 0;
            plan.lastProgressTick = now;
            plan.stuckCheckCount = 0;
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            plan.lastRepathTick = now;
            plan.debugStage = "nav_done_reached";
            plan.debugRepathReason = "navigation_completed";
            return false;
        }

        plan.debugStage = finalStep ? "final_walk" : "walk";

        // Issue moveTo only when navigation has no active path. Rebuilding an
        // already active path every second resets vanilla's internal progress.
        boolean navIdle = npc.getNavigation().isDone();
        boolean hasActivePath = npc.getNavigation().getPath() != null && !navIdle;
        boolean repathDue = now - plan.lastRepathTick >= REPATH_INTERVAL_TICKS;
        if (!hasActivePath && repathDue) {
            // moveTo() returns true if a path was successfully created
            double speed = movementSpeedForStep(tightStep, distSqr);
            boolean pathFound = npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
            plan.lastRepathTick = now;
            boolean shouldLogMove = movementDebugEnabled()
                && (plan.lastMoveCommandLoggedStep != plan.currentStepIndex
                    || !pathFound
                    || now - plan.lastMoveStatusLogTick >= MOVE_STATUS_LOG_INTERVAL_TICKS);

            if (!pathFound) {
                plan.consecutiveNavFailures++;
                plan.debugRepathReason = "moveTo_fail_" + plan.consecutiveNavFailures;
            } else {
                plan.lastSuccessfulMoveCommandStep = plan.currentStepIndex;
                plan.consecutiveNavFailures = 0;
                if (plan.debugRepathReason.equals("none")) {
                    plan.debugRepathReason = navIdle ? "nav_idle_repath" : "path_missing_repath";
                }
            }
            if (shouldLogMove) {
                StardewCraft.LOGGER.info("[NPC_MOVE] {} moveTo step={}/{} point={} result={} reason={} pos={} target={} dist2d={} navIdle={} hasPath={} path={} next={} failures={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                    pathFound, plan.debugRepathReason, fmt(npc.position()), fmt(target),
                    String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distSqr)), navIdle,
                    npc.getNavigation().getPath() != null,
                    pathSummary(npc), fmt(nextPathNodeTarget(npc)), plan.consecutiveNavFailures);
                plan.lastMoveCommandLoggedStep = plan.currentStepIndex;
                plan.lastMoveStatusLogTick = now;
            }
        }

        // Update facing from movement velocity
        Vec3 vel = npc.getDeltaMovement();
        if (vel.x != 0.0D || vel.z != 0.0D) {
            float moveYaw = (float) (Math.toDegrees(Math.atan2(-vel.x, vel.z)));
            npc.setYRot(moveYaw);
            npc.setYHeadRot(moveYaw);
        }

        // ── Displacement-based progress detection ──
        // Every PROGRESS_CHECK_INTERVAL ticks, measure how far the NPC actually
        // moved (2D). This catches BOTH stationary AND oscillating-between-blocks
        // stuck patterns that block-level detection misses.
        if (now - plan.progressCheckTick >= PROGRESS_CHECK_INTERVAL) {
            double dx = npc.getX() - plan.progressCheckX;
            double dz = npc.getZ() - plan.progressCheckZ;
            double dispSqr = dx * dx + dz * dz;

            if (dispSqr < PROGRESS_MIN_DISP_SQR) {
                // No meaningful progress in this check window
                plan.stuckCheckCount++;
                if (movementDebugEnabled() && now - plan.lastNoProgressLogTick >= NO_PROGRESS_LOG_INTERVAL_TICKS) {
                    StardewCraft.LOGGER.info("[NPC_MOVE] {} no_progress step={}/{} point={} checks={} disp={} pos={} target={} navDone={} hasPath={} path={} next={}",
                        npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                        plan.stuckCheckCount,
                        String.format(java.util.Locale.ROOT, "%.3f", Math.sqrt(dispSqr)),
                        fmt(npc.position()), fmt(target), npc.getNavigation().isDone(),
                        npc.getNavigation().getPath() != null, pathSummary(npc), fmt(nextPathNodeTarget(npc)));
                    plan.lastNoProgressLogTick = now;
                }
            } else {
                // Making real progress — reset
                if (movementDebugEnabled() && plan.stuckCheckCount > 0) {
                    StardewCraft.LOGGER.info("[NPC_MOVE] {} progress_resumed step={}/{} point={} disp={} pos={} target={}",
                        npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                        String.format(java.util.Locale.ROOT, "%.3f", Math.sqrt(dispSqr)),
                        fmt(npc.position()), fmt(target));
                }
                plan.stuckCheckCount = 0;
                plan.lastProgressTick = now;
            }
            // Update checkpoint for next measurement
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
        }

        boolean navigationStuck = npc.getNavigation().isStuck();
        boolean noProgressStuck = plan.stuckCheckCount >= STUCK_REPATH_CHECKS;
        if (noProgressStuck || navigationStuck) {
            if (navigationStuck) {
                npc.getNavigation().stop();
            }
            double speed = movementSpeedForStep(tightStep, distSqr);
            boolean pathFound = npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
            plan.lastRepathTick = now;
            plan.debugStage = "stuck_repath";
            if (pathFound) {
                plan.lastSuccessfulMoveCommandStep = plan.currentStepIndex;
                plan.consecutiveNavFailures = 0;
            } else {
                plan.consecutiveNavFailures++;
            }
            plan.debugRepathReason = navigationStuck
                ? "navigation_stuck"
                : "stuck_repath_check" + plan.stuckCheckCount;
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.warn("[NPC_MOVE] {} stuck_repath step={}/{} point={} reason={} pathFound={} pos={} target={} navStuck={} hasPath={} path={} next={} failures={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                    plan.debugRepathReason, pathFound, fmt(npc.position()), fmt(target), navigationStuck,
                    npc.getNavigation().getPath() != null, pathSummary(npc), fmt(nextPathNodeTarget(npc)), plan.consecutiveNavFailures);
            }
            plan.stuckCheckCount = 0;
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
        } else if (plan.consecutiveNavFailures >= NAV_FAIL_REPATH_THRESHOLD
            && now - plan.lastNavFailureLogTick >= MOVE_STATUS_LOG_INTERVAL_TICKS) {
            plan.debugStage = "waiting_for_path";
            plan.debugRepathReason = "nav_fail_wait_" + plan.consecutiveNavFailures;
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.warn("[NPC_MOVE] {} waiting_for_path step={}/{} point={} failures={} pos={} target={} hasPath={} path={} next={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                    plan.consecutiveNavFailures, fmt(npc.position()), fmt(target),
                    npc.getNavigation().getPath() != null, pathSummary(npc), fmt(nextPathNodeTarget(npc)));
            }
            plan.lastNavFailureLogTick = now;
        }

        return false;
    }

    private static void stopHorizontalMotionPreserveGravity(StardewNpcEntity npc) {
        Vec3 movement = npc.getDeltaMovement();
        npc.setDeltaMovement(0.0D, movement.y, 0.0D);
    }

    private static double movementSpeedForStep(boolean tightStep, double distSqr) {
        return tightStep && distSqr <= FINAL_APPROACH_DISTANCE_SQR ? FINAL_APPROACH_SPEED : 1.0D;
    }

    private static String buildPlanSignature(NpcRuntimeState state, NpcRoutePlanner.NpcRouteContext route) {
        return state.activeScheduleKey() + "#" + state.scheduleCheckpoint() + "#" + state.scheduleNodeIndex()
            + "#" + route.canonicalLocation + "#" + route.status + "#" + route.diagnosticReason + "#" + route.missingPointId + "#" + route.missingPortalLinkId;
    }

    private static String pathSummary(StardewNpcEntity npc) {
        if (npc == null || npc.getNavigation() == null || npc.getNavigation().getPath() == null) {
            return "<none>";
        }
        net.minecraft.world.level.pathfinder.Path path = npc.getNavigation().getPath();
        return path.getNextNodeIndex() + "/" + path.getNodeCount();
    }

    @SuppressWarnings("unused")
    private static boolean shouldAxisLockNearOpenDoor(ServerLevel level,
                                                      StardewNpcEntity npc,
                                                      Vec3 target) {
        if (level == null || npc == null || target == null) {
            return false;
        }
        BlockPos npcPos = npc.blockPosition();
        BlockPos targetPos = BlockPos.containing(target);
        if (!isOpenDoorNearby(level, npcPos) && !isOpenDoorNearby(level, targetPos)) {
            return false;
        }
        return npc.position().distanceToSqr(target) <= 4.0D;
    }

    private static boolean isOpenDoorNearby(ServerLevel level, BlockPos center) {
        if (level == null || center == null) {
            return false;
        }
        int[][] offsets = {
            {0, 0},
            {1, 0}, {-1, 0},
            {0, 1}, {0, -1}
        };
        for (int[] off : offsets) {
            BlockPos p = center.offset(off[0], 0, off[1]);
            if (isOpenDoorBlock(level.getBlockState(p))
                || isOpenDoorBlock(level.getBlockState(p.above()))
                || isOpenDoorBlock(level.getBlockState(p.below()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpenDoorBlock(BlockState state) {
        if (!(state.getBlock() instanceof DoorBlock)) {
            return false;
        }
        return state.hasProperty(DoorBlock.OPEN) && state.getValue(DoorBlock.OPEN);
    }

    private static void tryOpenNearbyDoors(ServerLevel level,
                                           StardewNpcEntity npc,
                                           NpcRoutePlan plan,
                                           Vec3 stepTarget,
                                           Vec3 nextPathNode) {
        if (level == null || npc == null) {
            return;
        }
        tryOpenDoorsAround(level, npc, plan, npc.blockPosition());

        if (nextPathNode != null && isDoorProbeInReach(npc, nextPathNode)) {
            tryOpenDoorsAround(level, npc, plan, BlockPos.containing(nextPathNode));
        }

        if (stepTarget != null && isDoorProbeInReach(npc, stepTarget)) {
            tryOpenDoorsAround(level, npc, plan, BlockPos.containing(stepTarget));
        }
    }

    private static boolean isDoorProbeInReach(StardewNpcEntity npc, Vec3 probe) {
        double dx = probe.x - npc.getX();
        double dz = probe.z - npc.getZ();
        return dx * dx + dz * dz <= DOOR_OPEN_PROBE_REACH_SQR;
    }

    private static Vec3 nextPathNodeTarget(StardewNpcEntity npc) {
        if (npc == null || npc.getNavigation() == null) {
            return null;
        }
        net.minecraft.world.level.pathfinder.Path path = npc.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return null;
        }
        int index = path.getNextNodeIndex();
        if (index < 0 || index >= path.getNodeCount()) {
            return null;
        }
        BlockPos nodePos = path.getNodePos(index);
        return new Vec3(nodePos.getX() + 0.5D, nodePos.getY(), nodePos.getZ() + 0.5D);
    }

    private static void tryOpenDoorsAround(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, BlockPos center) {
        if (center == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos base = center.offset(dx, 0, dz);
                tryOpenDoorAt(level, npc, plan, base.below());
                tryOpenDoorAt(level, npc, plan, base);
                tryOpenDoorAt(level, npc, plan, base.above());
            }
        }
    }

    private static void tryOpenDoorAt(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, BlockPos probePos) {
        if (probePos == null) {
            return;
        }

        BlockState state = level.getBlockState(probePos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return;
        }
        DoorBlock door = (DoorBlock) state.getBlock();

        BlockPos lowerPos = probePos;
        if (state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            lowerPos = probePos.below();
            state = level.getBlockState(lowerPos);
            if (!(state.getBlock() instanceof DoorBlock lowerDoor)) {
                return;
            }
            door = lowerDoor;
        }

        if (state.getBlock() == Blocks.IRON_DOOR) {
            return;
        }

        if (!state.hasProperty(DoorBlock.OPEN) || state.getValue(DoorBlock.OPEN)) {
            return;
        }

        door.setOpen(npc, level, state, lowerPos, true);
        plan.openedDoors.put(lowerPos.immutable(), level.getGameTime());
        if (movementDebugEnabled()) {
            StardewCraft.LOGGER.info("[NPC_MOVE] {} open_door pos={} block={} step={}/{} point={}",
                npc.getNpcId(), lowerPos.toShortString(), BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                plan.currentStepIndex, plan.steps.size(), plan.debugPointId);
        }
    }

    private static void closeOpenedDoors(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, long now, boolean force) {
        if (plan.openedDoors.isEmpty()) {
            return;
        }
        List<BlockPos> closed = new ArrayList<>();
        for (Map.Entry<BlockPos, Long> entry : plan.openedDoors.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!force && now - entry.getValue() < DOOR_CLOSE_TIMEOUT_TICKS && npc.blockPosition().distSqr(pos) <= 4.0D) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock door) || !state.hasProperty(DoorBlock.OPEN) || !state.getValue(DoorBlock.OPEN)) {
                closed.add(pos);
                continue;
            }
            if (isDoorwayOccupied(level, pos)) {
                continue;
            }
            door.setOpen(npc, level, state, pos, false);
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.info("[NPC_MOVE] {} close_door pos={} block={} force={}",
                    npc.getNpcId(), pos.toShortString(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), force);
            }
            closed.add(pos);
        }
        for (BlockPos pos : closed) {
            plan.openedDoors.remove(pos);
        }
    }

    private static boolean isDoorwayOccupied(ServerLevel level, BlockPos pos) {
        AABB doorway = new AABB(pos).inflate(0.2D, 0.0D, 0.2D).expandTowards(0.0D, 1.0D, 0.0D);
        return !level.getEntitiesOfClass(LivingEntity.class, doorway, LivingEntity::isAlive).isEmpty();
    }

    private static boolean markAndCheckScheduleNodeChange(String npcId, NpcRuntimeState state) {
        if (state == null || npcId == null || npcId.isBlank()) {
            return false;
        }
        String signature = state.activeScheduleKey() + "#" + state.scheduleCheckpoint() + "#" + state.scheduleNodeIndex()
            + "#" + state.locationName() + "#" + state.tileX() + "#" + state.tileY();
        String previous = LAST_NODE_SIGNATURE.put(npcId, signature);
        return previous != null && !previous.equals(signature);
    }

    private static void applyFacing(StardewNpcEntity npc, NpcRuntimeState state) {
        if (state == null) {
            return;
        }
        // Don't override yaw while the NPC is turning to face a player (dialogue / gift)
        // or idle-looking at a nearby player.
        if (npc.isFacingOverrideActive() || npc.isIdleLookActive()) {
            return;
        }

        // SDV facing: 0=north(up), 1=east(right), 2=south(down), 3=west(left)
        // MC yaw:     0°=south, 90°=west, 180°=north, -90°=east
        float yaw = switch (state.facing()) {
            case 0 -> 180.0F;   // north
            case 1 -> -90.0F;   // east
            case 2 -> 0.0F;     // south
            case 3 -> 90.0F;    // west
            default -> npc.getYRot();
        };
        npc.setYRot(yaw);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
    }

    private static final class NpcRoutePlan {
        private final String signature;
        private UUID boundEntityUuid;
        private final List<NpcRoutePlanner.NpcRouteStep> steps;
        private int currentStepIndex;
        private long lastProgressTick;
        private long lastRepathTick;
        private int consecutiveNavFailures;
        private String debugStage;
        private String debugPointId;
        private boolean lastForcedTeleportUsed;
        private Vec3 debugTarget;
        private Vec3 debugNextWaypoint;
        private String debugRepathReason;
        private String routeStatus;
        private String routeDiagnosticReason;
        private String missingPointId;
        private String missingPortalLinkId;
        private boolean tightStepArrival;
        private boolean acceptNavigationDoneArrival;
        private int lastSuccessfulMoveCommandStep;
        /** Displacement-based progress detection: consecutive "no progress" checks. */
        private int stuckCheckCount;
        /** Tick when last progress check was performed. */
        private long progressCheckTick;
        /** X/Z position at last progress checkpoint. */
        private double progressCheckX, progressCheckZ;
        /** Block key where door detection last ran; skip when unchanged. */
        private long lastDoorCheckBlockKey = Long.MIN_VALUE;
        private int lastMoveCommandLoggedStep = -1;
        private long lastMoveStatusLogTick = Long.MIN_VALUE;
        private long lastNoProgressLogTick = Long.MIN_VALUE;
        private long lastNavFailureLogTick = Long.MIN_VALUE;
        private final Map<BlockPos, Long> openedDoors = new HashMap<>();

        private NpcRoutePlan(String signature, UUID boundEntityUuid, List<NpcRoutePlanner.NpcRouteStep> steps, long now) {
            this.signature = signature;
            this.boundEntityUuid = boundEntityUuid;
            this.steps = steps;
            this.currentStepIndex = 0;
            this.lastProgressTick = now;
            this.lastRepathTick = now - REPATH_INTERVAL_TICKS;
            this.consecutiveNavFailures = 0;
            this.debugStage = "init";
            this.debugPointId = "<none>";
            this.lastForcedTeleportUsed = false;
            this.debugTarget = Vec3.ZERO;
            this.debugNextWaypoint = Vec3.ZERO;
            this.debugRepathReason = "none";
            this.routeStatus = NpcRoutePlanner.RouteStatus.READY.name();
            this.routeDiagnosticReason = "none";
            this.missingPointId = "";
            this.missingPortalLinkId = "";
            this.tightStepArrival = false;
            this.acceptNavigationDoneArrival = false;
            this.lastSuccessfulMoveCommandStep = -1;
            this.stuckCheckCount = 0;
            this.progressCheckTick = now;
            this.progressCheckX = 0.0D;
            this.progressCheckZ = 0.0D;
        }
    }

    public static final class DebugSnapshot {
        public String stage;
        public String location;
        public String pointId;
        public int pathSize;
        public int pathIndex;
        public boolean forcedTeleportUsed;
        public Vec3 target;
        public Vec3 nextWaypoint;
        public String repathReason;
        public long noPathTicks;
        public String forcedTargetChunk;
        public String routeStatus;
        public String routeDiagnosticReason;
        public String missingPointId;
        public String missingPortalLinkId;

        public DebugSnapshot() {}

        public void update(String stage, String location, String pointId,
                           int pathSize, int pathIndex, boolean forcedTeleportUsed,
                           Vec3 target, Vec3 nextWaypoint, String repathReason,
                           long noPathTicks, String forcedTargetChunk) {
            this.stage = stage;
            this.location = location;
            this.pointId = pointId;
            this.pathSize = pathSize;
            this.pathIndex = pathIndex;
            this.forcedTeleportUsed = forcedTeleportUsed;
            this.target = target;
            this.nextWaypoint = nextWaypoint;
            this.repathReason = repathReason;
            this.noPathTicks = noPathTicks;
            this.forcedTargetChunk = forcedTargetChunk;
            this.routeStatus = NpcRoutePlanner.RouteStatus.READY.name();
            this.routeDiagnosticReason = "none";
            this.missingPointId = "";
            this.missingPortalLinkId = "";
        }

        public void updateRoute(NpcRoutePlanner.NpcRouteContext route) {
            if (route == null) {
                this.routeStatus = "NO_ROUTE";
                this.routeDiagnosticReason = "route_context_missing";
                this.missingPointId = "";
                this.missingPortalLinkId = "";
                return;
            }
            this.routeStatus = route.status.name();
            this.routeDiagnosticReason = route.diagnosticReason;
            this.missingPointId = route.missingPointId;
            this.missingPortalLinkId = route.missingPortalLinkId;
        }

        private void updatePlan(NpcRoutePlan plan) {
            if (plan == null || NpcRoutePlanner.RouteStatus.READY.name().equals(plan.routeStatus)) {
                return;
            }
            this.routeStatus = plan.routeStatus;
            this.routeDiagnosticReason = plan.routeDiagnosticReason;
            this.missingPointId = plan.missingPointId;
            this.missingPortalLinkId = plan.missingPortalLinkId;
        }
    }

    public static final class AuthoredDebugSnapshot {
        public String stage = "<none>";
        public String pointId = "<none>";
        public int pathSize;
        public int pathIndex;
        public Vec3 target = Vec3.ZERO;
        public Vec3 nextWaypoint = Vec3.ZERO;
        public String repathReason = "none";
        public int navFailures;
        public boolean hasPath;
        public boolean navDone;
        public String navTarget = "<none>";
        public Vec3 position = Vec3.ZERO;
        public long lastTick;
    }

    /**
     * Snap an NPC entity down to the top of the block surface below it,
     * fixing floating on slabs/stairs after spawn or warp.
     */
    public static void snapToSurface(ServerLevel level, net.minecraft.world.entity.Mob npc) {
        BlockPos below = npc.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(below);
        if (!state.isAir()) {
            net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(level, below);
            if (!shape.isEmpty()) {
                double surfaceY = below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                npc.setPos(npc.getX(), surfaceY, npc.getZ());
            }
        }
    }
}
