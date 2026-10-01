package com.stardew.craft.npc.runtime;

import com.stardew.craft.port.PortLevels;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.entity.npc.NpcPathNavigation;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.interior.door.TownDoorRuntime;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
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

/** Executes validated region steps through Minecraft collision-aware navigation.
 * Admission is shared across actors; incomplete paths never count as arrival.
 */
@SuppressWarnings("null")
public final class NpcCentralMovementService {
    private static final double FINAL_APPROACH_DISTANCE_SQR = 1.0D; // final 1 block only
    private static final double BLOCKED_FINAL_APPROACH_DISTANCE_SQR = 2.25D;
    private static final double BLOCKED_FINAL_VERTICAL_TOLERANCE = 1.25D;
    private static final double FINAL_APPROACH_SPEED = 0.9D;
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
    private static final double DOOR_OPEN_PROBE_REACH_SQR = 4.0D;
    private static final double TOWN_DOOR_PRELOAD_DISTANCE_SQR = 64.0D;
    private static final double TOWN_DOOR_OPEN_DISTANCE_SQR = 9.0D;
    // Entrances retain the shipped approach range; work/activity destinations require exact standing.
    private static final double PORTAL_APPROACH_RADIUS = 2.0D;
    private static final int MOVE_STATUS_LOG_INTERVAL_TICKS = 100;
    private static final int NO_PROGRESS_LOG_INTERVAL_TICKS = 40;
    /** Give a passing actor time to clear a reserved destination before settling beside it. */
    private static final int DESTINATION_WAIT_TICKS = 60;
    /** Long outdoor routes are validated in small, separately budgeted A* segments. */
    private static final int MAX_STAGED_CONTINUATION_SEGMENTS = 6;
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

    /** Traffic influences route cost; two scheduled travellers must not deadlock by mutually pushing. */
    public static boolean isScheduleTravelling(StardewNpcEntity npc) {
        var plan = ACTIVE_PLANS.get(npc.getNpcId());
        return plan != null && plan.boundEntityUuid.equals(npc.getUUID())
                && plan.allowNearestReachableFinal && plan.currentStepIndex < plan.steps.size()
                && !"destination_wait".equals(plan.debugStage)
                && !npc.isNativeActivityMovementLocked() && !npc.isFacingOverrideActive();
    }

    public static DebugSnapshot getDebugSnapshot(String npcId) {
        return DEBUG_SNAPSHOTS.get(npcId == null ? "" : npcId.toLowerCase());
    }

    public static void resetMovementPlan(String npcId) {
        String key = NpcRoutePlanner.canonicalNpcId(npcId);
        if (key.isBlank()) {
            return;
        }
        disposePlan(ACTIVE_PLANS.remove(key));
        LAST_NODE_SIGNATURE.remove(key);
        DEBUG_SNAPSHOTS.remove(key);
    }

    public static void resetAuthoredMovementPlan(String npcId, String owner) {
        String key = authoredPlanKey(npcId, owner);
        if (key.isBlank()) {
            return;
        }
        disposePlan(AUTHORED_PLANS.remove(key));
        AUTHORED_DEBUG_SNAPSHOTS.remove(key);
    }

    private static void disposePlan(NpcRoutePlan plan) {
        if (plan==null || activeServer==null) return;
        if(plan.squareLeg!=null)disposePlan(plan.squareLeg);
        for (ServerLevel level:activeServer.getAllLevels()) {
            var entity=level.getEntity(plan.boundEntityUuid);
            if (entity instanceof StardewNpcEntity npc) {
                closeOpenedDoors(level,npc,plan,true);
                // Disposing one controller's plan must not stop another controller's path.
                NpcChunkForceManager.releaseRouteCorridor(level,npc.getNpcId());
                NpcNavigationBudget.cancel(level.getServer(),level.dimension().location()+"/"+npc.getNpcId());
                return;
            }
        }
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
        if (NpcInteractionService.isDialogueMovementLocked(npc.getNpcId()) || npc.isFacingOverrideActive()
                || npc.isNativeActivityMovementLocked()) {
            stopAuthoredMovement(npc);
            return false;
        }
        if (NpcExecutionCoordinator.claim(npc,"authored:"+owner,50,2)<0) return false;
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
                closeOpenedDoors(level, npc, plan, true);
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
        if (NpcInteractionService.isDialogueMovementLocked(npc.getNpcId()) || npc.isFacingOverrideActive()
                || npc.isNativeActivityMovementLocked()) {
            stopAuthoredMovement(npc);
            return -1;
        }
        if (NpcExecutionCoordinator.claim(npc,"authored:"+owner,50,2)<0) return -1;
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
                closeOpenedDoors(level, npc, plan, true);
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
        closeOpenedDoors(level, npc, plan, true);
        npc.getNavigation().stop();
        stopHorizontalMotionPreserveGravity(npc);
        plan.currentStepIndex = 0;
        plan.stagedPath = null;
        plan.consecutiveNavFailures = 0;
        plan.lastProgressTick = now;
        plan.stuckCheckCount = 0;
        plan.progressCheckTick = now;
        plan.progressCheckX = npc.getX();
        plan.progressCheckZ = npc.getZ();
        plan.lastRepathTick = now - NpcNavigationPolicy.current().retryTicks();
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

            // Skip NPCs that live in a different dimension (e.g. dwarf in mining)
            if (NpcSpawnManager.isMiningDimensionNpc(npcId)) continue;
            // Joja Mart NPCs 由 JojaNpcEvents 独立管理（骆驼商人同款），
            // 不能让本服务的路径规划 / teleport 干预它们。
            if (com.stardew.craft.joja.JojaNpcEvents.isJojaMartNpc(npcId)) continue;
            StardewNpcEntity npc = NpcSpawnManager.getTrackedNpc(level, npcId);
            if (npc == null) {
                activeNpcIds.add(npcId); // A pending spawn still needs its target chunk.
                NpcChunkForceManager.releaseRouteCorridor(level,npcId);
                NpcNavigationBudget.cancel(level.getServer(),level.dimension().location()+"/"+npcId);
                continue;
            }
            // Presence belongs to the actor, not its destination. Pausing a trip or
            // releasing its corridor must not unload the NPC's current chunk.
            NpcChunkForceManager.ensureResidentChunkForced(level, npcId, npc.position());

            if (com.stardew.craft.festival.FestivalNpcController.controlsNpc(npcId)) {
                activeNpcIds.add(npcId);
                ACTIVE_PLANS.remove(npcId);
                LAST_NODE_SIGNATURE.remove(npcId);
                continue;
            }

            // Dialogue screens can outlive the short turning animation. Keep the NPC
            // frozen for the full conversation, not just the initial face-player hold.
            if(!NpcExecutionCoordinator.autonomous(npc)) {
                activeNpcIds.add(npcId);
                updateDebugSnapshot(npcId,"controlled","<none>","<none>",0,0,false,npc.position(),npc.position(),
                        NpcExecutionCoordinator.owner(npc),0,"<none>",null,null);
                continue;
            }
            if (NpcInteractionService.isDialogueMovementLocked(npcId) || (npc.isFacingOverrideActive() || npc.isNativeActivityMovementLocked())) {
                activeNpcIds.add(npcId);
                NpcChunkForceManager.releaseRouteCorridor(level,npcId);
                NpcNavigationBudget.cancel(level.getServer(),level.dimension().location()+"/"+npcId);
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                {
                    updateDebugSnapshot(npcId, "interaction_pause", "<none>", "<none>", 0, 0, false, npc.position(), npc.position(), "none", 0, "<none>", null, null);
                }
                continue;
            }

            if (NpcExecutionCoordinator.claim(npc,NpcExecutionCoordinator.SCHEDULE,0,2)<0) {
                activeNpcIds.add(npcId); // The controlling owner may be using the shared travel lease.
                updateDebugSnapshot(npcId,"controlled","<none>","<none>",0,0,false,npc.position(),npc.position(),
                        NpcExecutionCoordinator.owner(npc),0,"<none>",null,null);
                continue;
            }
            NpcRuntimeState state = runtimeStates.get(npcId);
            boolean pathingSuppressed = !profile.canRunPathing() || (state != null && state.pathingSuppressed());
            if (pathingSuppressed) {
                activeNpcIds.add(npcId);
                NpcChunkForceManager.ensureRouteTargetChunkForced(level,npcId,npc.position());
                NpcChunkForceManager.releaseRouteCorridor(level,npcId);
                NpcNavigationBudget.cancel(level.getServer(),level.dimension().location()+"/"+npcId);
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                applyFacing(npc, state);
                {
                    updateDebugSnapshot(npcId, "pathing_disabled", "<none>", "<none>", 0, 0, false, npc.position(), npc.position(), "none", 0, "<none>", null, null);
                }
                continue;
            }

            NpcRoutePlanner.NpcRouteContext route = NpcRoutePlanner.resolveRoute(level, npcId, state, npc.blockPosition());
            if (route == null || !route.ready()) {
                activeNpcIds.add(npcId);
                Vec3 furniture=state==null?null:NpcSupportTarget.pendingFurniturePosition(level,state.namedPointId());
                NpcChunkForceManager.ensureRouteTargetChunkForced(level,npcId,furniture==null?npc.position():furniture);
                if(furniture!=null) route=NpcRoutePlanner.NpcRouteContext.waitingForCoordinates(
                        state.locationName(),"furniture_unavailable",state.namedPointId(),"");
                resetMovementPlan(npcId);
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                applyFacing(npc, state);
                {
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
            activeNpcIds.add(npcId);

            // A loaded NPC can retain a stale false onGround flag even while its feet
            // rest on a real collision surface. Repair it before validating a completed
            // plan too; otherwise the completed plan is rebuilt every tick and the next
            // GroundPathNavigation search is rejected before it starts.
            if (!npc.onGround()) {
                restoreGroundContact(level, npc);
            }
            boolean nodeChanged = markAndCheckScheduleNodeChange(npcId, state);
            String signature = buildPlanSignature(state, route);

            NpcRoutePlan plan = ACTIVE_PLANS.get(npcId);
            boolean entityReplaced = plan != null && !npc.getUUID().equals(plan.boundEntityUuid);
            boolean needNewPlan = plan == null || entityReplaced || !signature.equals(plan.signature);
            if (plan != null && plan.currentStepIndex >= plan.steps.size() && !plan.steps.isEmpty()
                    && !plan.settledAtNearestReachable
                    && (plan.square==null || !plan.square.area.contains(npc.position(),npc.getBbWidth()/2.))) {
                Vec3 goal=plan.steps.getLast().target;
                boolean holdWorkpoint = plan.exactWorkpoint && npc.position().distanceToSqr(goal) <= 9
                        && InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition()).equals(
                            InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(goal)));
                if (!holdWorkpoint && !arrivedAt(npc,goal,NpcNavigationPolicy.current().arrivalRadius()))
                    needNewPlan=true;
            }
            if (needNewPlan || nodeChanged) {
                NpcTravelStatus.clear(npcId);
                if (plan != null) {closeOpenedDoors(level,npc,plan,true);if(plan.squareLeg!=null)closeOpenedDoors(level,npc,plan.squareLeg,true);}
                npc.getNavigation().stop();
                plan = buildPlan(level, npc, route, signature, level.getGameTime());
                ACTIVE_PLANS.put(npcId, plan);
            }

            if (plan == null || plan.steps.isEmpty()) {
                npc.getNavigation().stop();
                stopHorizontalMotionPreserveGravity(npc);
                applyFacing(npc, state);
                {
                    updateDebugSnapshot(npcId, "empty_plan", route.canonicalLocation,
                        plan == null ? "<none>" : plan.missingPointId,
                        0, 0, false, npc.position(), npc.position(),
                        plan == null ? "none" : plan.routeDiagnosticReason,
                        0, "<none>", route, plan);
                }
                continue;
            }

            if(plan.currentStepIndex<plan.steps.size())executePlanTick(level, npc, plan);
            if (plan.currentStepIndex >= plan.steps.size() && plan.arrivalPosition == null)
                plan.arrivalPosition = npc.position();
            if(plan.currentStepIndex>=plan.steps.size()&&!tickSquare(level,npc,state,plan))executePlanTick(level,npc,plan);
            if ("done".equals(plan.debugStage)) {
                applyFacing(npc, state);
            }
            {
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
        NpcTravelStatus.logSummary(level.getGameTime());



        // Avoid mass forcing interior chunks per tick; indoor transitions are handled by
        // explicit route steps and bounded target chunk forcing.
        // Interior residency belongs to the interior system; NPCs release only their own tickets.
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

    private static boolean tickSquare(ServerLevel level,StardewNpcEntity npc,NpcRuntimeState state,NpcRoutePlan plan) {
        var behavior=NpcSquareMovement.parse(state.routeBehaviorToken());if(behavior==null)return false;
        if(!plan.squareAttempted) {
            plan.squareAttempted=true;var area=NpcSquareArea.forPoint(state.namedPointId());
            var anchor=plan.steps.getLast().target;
            if(area!=null&&area.contains(anchor,npc.getBbWidth()/2.))plan.square=new NpcSquareMovement(area,behavior,BlockPos.containing(anchor));
        }
        if(plan.square==null) {
            npc.getNavigation().stop();stopHorizontalMotionPreserveGravity(npc);applyFacing(npc,state);
            plan.debugStage="square_missing_area";plan.debugRepathReason="confirmed_square_area_required";return true;
        }
        var square=plan.square;long now=level.getGameTime();square.resume(now);
        if(square.target()==null&&!square.waiting(now)) {
            var target=square.choose(level,npc,now);
            if(target!=null){plan.squareLeg=new NpcRoutePlan(plan.signature,npc.getUUID(),List.of(NpcRoutePlanner.NpcRouteStep.walk(state.namedPointId(),target)),now);plan.squareLeg.squareBounds=square.area;}
        }
        var leg=plan.squareLeg;
        if(square.target()==null||leg==null) {
            plan.debugStage="square_wait";npc.getNavigation().stop();stopHorizontalMotionPreserveGravity(npc);return true;
        }
        executePlanTick(level,npc,leg);
        plan.debugTarget=leg.debugTarget;plan.debugNextWaypoint=leg.debugNextWaypoint;plan.lastProgressTick=now;
        plan.debugRepathReason=leg.debugRepathReason;plan.debugStage="square_walk";
        if(leg.currentStepIndex>=leg.steps.size()) {
            closeOpenedDoors(level,npc,leg,true);plan.debugStage="square_pause";
            if(square.behavior.facing()>=0&&!npc.isIdleLookActive()) {
                float yaw=switch(square.behavior.facing()){case 0->180;case 1->-90;case 3->90;default->0;};
                npc.setYRot(yaw);npc.setYHeadRot(yaw);npc.setYBodyRot(yaw);
            }
            if(square.arrived(now)){square.next();plan.squareLeg=null;}
        } else if(square.timedOut(now)||leg.consecutiveNavFailures>=3) {
            closeOpenedDoors(level,npc,leg,true);npc.getNavigation().stop();stopHorizontalMotionPreserveGravity(npc);
            square.reject(now);plan.squareLeg=null;plan.debugStage="square_retry";
        }
        return true;
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
        DebugSnapshot snapshot = DEBUG_SNAPSHOTS.computeIfAbsent(npcId, k -> new DebugSnapshot());
        if(activeServer!=null) {
            var level=activeServer.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if(level!=null) {
                String reason=route!=null && !route.ready() && !"no_schedule".equals(route.diagnosticReason)?route.diagnosticReason
                        : plan!=null && plan.steps.isEmpty() && !"none".equals(plan.routeDiagnosticReason)?plan.routeDiagnosticReason
                        : plan!=null && plan.currentStepIndex<plan.steps.size()
                            && level.getGameTime()-plan.lastProgressTick>=PROGRESS_CHECK_INTERVAL*3?"no_progress"
                        : plan!=null && plan.consecutiveNavFailures>0?"path_unavailable"
                        : "";
                var task=NpcRuntimeDataManager.get(level).states().get(npcId);
                String identity=task==null?location:task.activeScheduleKey()+"/"+task.scheduleCheckpoint()+"/"+task.namedPointId();
                NpcTravelStatus.observe(npcId,identity,reason,level.getGameTime());
            }
        }
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
        NpcTravelStatus.clear();
        AUTHORED_PLANS.clear();
        AUTHORED_DEBUG_SNAPSHOTS.clear();
        LAST_NODE_SIGNATURE.clear();
        DEBUG_SNAPSHOTS.clear();
        cachedCapabilities = Map.of();
        cachedMovementEntries = List.of();
        NpcRoutePlanner.resetState();
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
                expanded.add(NpcRoutePlanner.NpcRouteStep.walk(route.destinationSteps.getLast().pointId, finalTarget));
            } else {
                Vec3 exitIndoor = NpcRoutePlanner.indoorExitForLocation(level,npcInteriorLocation);
                Vec3 exitOutdoor = NpcRoutePlanner.outdoorExitForLocation(level,npcInteriorLocation);
                if (exitIndoor != null && exitOutdoor != null) {
                    expanded.add(NpcRoutePlanner.NpcRouteStep.walk(npcInteriorLocation + "_indoor_exit", exitIndoor));
                    expanded.add(NpcRoutePlanner.NpcRouteStep.warp(npcInteriorLocation + "_outdoor_door", exitOutdoor));
                    // Keep every authored destination, including jobs immediately beside the exit.
                    for (NpcRoutePlanner.NpcRouteStep ds : route.destinationSteps) {
                        expanded.add(ds);
                    }
                } else {
                    routeStatus = NpcRoutePlanner.RouteStatus.WAITING_FOR_COORDINATES.name();
                    routeDiagnosticReason = exitIndoor == null ? "missing_indoor_exit_walk_target" : "missing_outdoor_exit_landing";
                    missingPortalLinkId = npcInteriorLocation.isBlank() ? npcFixedInterior : npcInteriorLocation;
                }
            }
        } else if (npcIndoors) {
            Vec3 exitIndoor = NpcRoutePlanner.indoorExitForLocation(level,npcInteriorLocation);
            Vec3 exitOutdoor = NpcRoutePlanner.outdoorExitForLocation(level,npcInteriorLocation);
            if (exitIndoor != null && exitOutdoor != null) {
                expanded.add(NpcRoutePlanner.NpcRouteStep.walk(npcInteriorLocation + "_indoor_exit", exitIndoor));
                expanded.add(NpcRoutePlanner.NpcRouteStep.warp(npcInteriorLocation + "_outdoor_door", exitOutdoor));
                for (NpcRoutePlanner.NpcRouteStep ds : route.destinationSteps) {
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
        // Schedule completion has one contract, shared by routing, activities and
        // the cursor. Authored scenes keep their stricter exact-walk semantics.
        plan.allowNearestReachableFinal = true;
        var finalPoint = expanded.isEmpty() ? null : NpcSupportTarget.point(expanded.getLast().pointId);
        plan.exactWorkpoint = finalPoint != null && finalPoint.has("arrival")
                && "exact_work".equals(finalPoint.get("arrival").getAsString());
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
        npc.getMoveControl().setWantedPosition(target.x, target.y, target.z, 0.0D);
        npc.setSpeed(0.0F);
        npc.setZza(0.0F);
        npc.setXxa(0.0F);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.setOnGround(true);
        npc.fallDistance = 0.0F;
        plan.currentStepIndex++;
        plan.stagedPath = null;
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
            if (plan.exactWorkpoint && !plan.steps.isEmpty()) {
                npc.setPos(plan.steps.getLast().target);
                npc.setDeltaMovement(Vec3.ZERO);
            }
            closeOpenedDoors(level, npc, plan, true);
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            NpcChunkForceManager.releaseRouteCorridor(level,npc.getNpcId());
            if (plan.arrivalPosition == null) plan.arrivalPosition = npc.position();
            plan.debugStage = plan.settledAtNearestReachable ? "done_nearest_reachable" : "done";
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
            // WARP is an authored point-to-point link, not a request to find a new standing cell.
            Vec3 entrance = plan.currentStepIndex > 0
                    ? plan.steps.get(plan.currentStepIndex - 1).target : npc.position();
            advanceStepByTeleport(level, npc, plan, target, now);
            playPortalDoorSound(level, entrance, target);
            closeOpenedDoors(level, npc, plan, true);
            return false;
        }

        // ── WALK steps: delegate to vanilla GroundPathNavigation ──
        boolean finalStep = plan.currentStepIndex == plan.steps.size() - 1;
        boolean portalApproach = !finalStep && plan.steps.get(plan.currentStepIndex + 1).mode == NpcRoutePlanner.RouteStepMode.WARP;
        double portalDistanceSqr = target.subtract(npc.position()).horizontalDistanceSqr();
        boolean preloadPortalDestination = portalApproach
                && portalDistanceSqr <= TOWN_DOOR_PRELOAD_DISTANCE_SQR
                && Math.abs(target.y - npc.getY()) <= 4.0D;
        Vec3 chunkTarget = preloadPortalDestination ? plan.steps.get(plan.currentStepIndex + 1).target : target;
        NpcChunkForceManager.ensureRouteTargetChunkForced(level, npc.getNpcId(), chunkTarget);
        NpcChunkForceManager.ensureRouteCorridorChunksForced(level, npc.getNpcId(), npc.position(), target);
        if (preloadPortalDestination && portalDistanceSqr <= TOWN_DOOR_OPEN_DISTANCE_SQR) {
            TownDoorRuntime.openForNpc(level, BlockPos.containing(target));
        }

        // Authored movement controllers enter through this executor directly, without
        // the daily-schedule preflight above. Keep the same supported-ground repair here
        // so a persisted false flag cannot prevent either route family from starting.
        if (!npc.onGround()) {
            restoreGroundContact(level, npc);
        }

        // A saved position, furniture activity, or exact workpoint can leave the
        // body embedded in a collision shape. A* cannot walk out of that shape.
        // Repair only the starting overlap, within the same room and nearby floor.
        boolean aligningWorkpoint = finalStep && plan.exactWorkpoint
                && (target.subtract(npc.position()).horizontalDistanceSqr() <= 9
                    || plan.fallbackStep == plan.currentStepIndex);
        if (plan.allowNearestReachableFinal && !aligningWorkpoint
                && now - plan.lastCollisionRecoveryTick >= 20
                && now - plan.lastProgressTick >= 40
                && !PortLevels.noBlockCollision(level, npc, npc.getBoundingBox().deflate(1.0E-5D))) {
            plan.lastCollisionRecoveryTick = now;
            Vec3 clearStart = nearestClearStart(level, npc);
            if (clearStart != null) {
                npc.getNavigation().stop();
                npc.setPos(clearStart);
                npc.setDeltaMovement(Vec3.ZERO);
                npc.setOnGround(true);
                plan.stagedPath = null;
                plan.fallbackEndpoint = null;
                plan.fallbackStep = -1;
                plan.debugRepathReason = "recovered_embedded_start";
            }
        }

        Vec3 nextPathNode = nextPathNodeTarget(npc);
        plan.debugNextWaypoint = nextPathNode == null ? target : nextPathNode;
        closeOpenedDoors(level, npc, plan, false);

        // Check horizontal arrival at step target
        Vec3 toTarget = new Vec3(target.x - npc.getX(), 0.0D, target.z - npc.getZ());
        double distSqr = toTarget.lengthSqr();
        boolean tightStep = finalStep || plan.tightStepArrival;
        var policy=NpcNavigationPolicy.current();
        double radius=portalApproach ? PORTAL_APPROACH_RADIUS : policy.arrivalRadius();
        boolean sameRegion=InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition())
                .equals(InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(target)));
        // Portal markers describe an interaction area and may be above the floor. Reaching
        // that area hands off directly to WARP; it does not require walking through the marker,
        // a grounded flag, an open wooden door, or a newly discovered collision-free landing.
        boolean arrived=portalApproach
                ? distSqr<=radius*radius && Math.abs(target.y-npc.getY())<=2.0D
                : arrivedAt(npc,target,radius);

        if (finalStep && plan.exactWorkpoint && sameRegion
                && (distSqr <= 9 || plan.fallbackStep == plan.currentStepIndex)) {
            // Workpoints explicitly request exact placement, including collision bypass.
            // Advance smoothly on the destination side of the portal; never cross
            // floors/rooms by pretending an outdoor endpoint is an indoor arrival.
            Vec3 delta = target.subtract(npc.position());
            npc.getNavigation().stop();
            npc.setDeltaMovement(Vec3.ZERO);
            if (delta.lengthSqr() <= .000001) {
                npc.setPos(target);
                plan.currentStepIndex++;
                plan.fallbackEndpoint = null;
                plan.fallbackStep = -1;
                plan.debugStage = "workpoint_exact";
            } else {
                npc.setPos(npc.position().add(delta.scale(Math.min(1, .15 / delta.length()))));
                plan.debugStage = "workpoint_align";
            }
            plan.lastProgressTick = now;
            return false;
        }
        if (plan.fallbackStep == plan.currentStepIndex && plan.fallbackEndpoint != null
                && npc.position().distanceToSqr(plan.fallbackEndpoint) <= .64
                && npc.onGround() && sameRegion && PortLevels.noBlockCollision(level, npc, npc.getBoundingBox())) {
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.currentStepIndex++;
            plan.stagedPath = null;
            plan.fallbackEndpoint = null;
            plan.fallbackStep = -1;
            plan.lastProgressTick = now;
            plan.settledAtNearestReachable = finalStep;
            plan.debugStage = finalStep ? "nearest_reachable" : "nearest_portal_approach";
            return false;
        }

        // Detect a reserved final position before accepting geometric arrival. Entity
        // pushes can move a waiting actor just inside the arrival radius while another
        // NPC still owns the target; that remains a wait, not an overlap.
        StardewNpcEntity destinationBlocker = finalStep && distSqr <= 4.0D
                ? destinationBlocker(level, npc, target)
                : null;
        if (destinationBlocker != null) {
            if (!destinationBlocker.getUUID().equals(plan.destinationBlockerUuid)) {
                plan.destinationBlockerUuid = destinationBlocker.getUUID();
                plan.destinationWaitStartTick = now;
            }
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.crowdYieldTarget = null;
            plan.debugStage = "destination_wait";
            plan.debugRepathReason = "occupied_by_" + stableNpcOrder(destinationBlocker);
            plan.debugNextWaypoint = target;
            boolean safelyWaitingBesideTarget = plan.allowNearestReachableFinal && !plan.exactWorkpoint
                    && sameRegion
                    && now - plan.destinationWaitStartTick >= DESTINATION_WAIT_TICKS
                    && npc.onGround()
                    && PortLevels.noBlockCollision(level, npc, npc.getBoundingBox());
            if (safelyWaitingBesideTarget) {
                plan.currentStepIndex++;
                plan.stagedPath = null;
                plan.consecutiveNavFailures = 0;
                plan.settledAtNearestReachable = true;
                plan.debugStage = "nearest_reachable";
                plan.debugRepathReason = "destination_occupied_by_" + stableNpcOrder(destinationBlocker);
                return false;
            }
            plan.lastProgressTick = now;
            plan.localProgressOrigin = npc.position();
            plan.localProgressTick = now;
            plan.stuckCheckCount = 0;
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            return false;
        }
        plan.destinationBlockerUuid = null;
        plan.destinationWaitStartTick = Long.MIN_VALUE;
        if (arrived) {
            // Arrived at step target — advance
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.info("[NPC_MOVE] {} reached step={}/{} point={} pos={} target={} dist2d={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                    fmt(npc.position()), fmt(target), String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distSqr)));
            }
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.currentStepIndex++;
            plan.stagedPath = null;
            plan.consecutiveNavFailures = 0;
            plan.lastProgressTick = now;
            plan.localProgressOrigin = npc.position();
            plan.localProgressTick = now;
            plan.stuckCheckCount = 0;
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            plan.lastRepathTick = now;
            plan.debugStage = "step_reached";
            return false;
        }

        // A daily schedule point may intentionally identify furniture such as an
        // easel or chair rather than a body-clear block centre. Vanilla then returns
        // a partial path ending at the adjacent, closest reachable cell. Once that
        // concrete partial path has actually been walked, accept the safe endpoint
        // instead of running the same impossible A* search forever. This is restricted
        // to the final step of daily plans: doors, portal approaches and authored
        // activity alignment must still reach their exact contract positions.
        var completedPath = npc.getNavigation().getPath();
        boolean completedAtPathEnd = completedPath != null && completedPath.isDone()
                && completedPath.getEndNode() != null
                && npc.position().distanceToSqr(completedPath.getEntityPosAtNode(
                        npc, completedPath.getNodeCount() - 1)) <= 1.0D;
        Vec3 targetDelta = target.subtract(npc.position());
        boolean targetStandingCellUnavailable = !hasUsableStandingCellAt(level, npc, target);
        boolean stalledBesideBlockedTarget = targetStandingCellUnavailable
                && npc.onGround()
                && PortLevels.noBlockCollision(level, npc, npc.getBoundingBox())
                && (plan.stuckCheckCount >= STUCK_REPATH_CHECKS || now - plan.localProgressTick >= 100);
        boolean completedNearestPath = completedPath != null && completedPath.isDone()
                && completedAtPathEnd;
        boolean finishedAtUnsupportedEdge = completedNearestPath
                && now - plan.localProgressTick >= 100 && npc.onGround()
                && PortLevels.noBlockCollision(level, npc, npc.getBoundingBox())
                && !hasSupportedApproach(level, npc, target);
        boolean nearestReachableFinal = plan.allowNearestReachableFinal && !plan.exactWorkpoint
                && finalStep && sameRegion
                && (targetStandingCellUnavailable || finishedAtUnsupportedEdge)
                && distSqr <= BLOCKED_FINAL_APPROACH_DISTANCE_SQR
                && Math.abs(target.y - npc.getY()) <= BLOCKED_FINAL_VERTICAL_TOLERANCE
                && (completedNearestPath || stalledBesideBlockedTarget || finishedAtUnsupportedEdge);
        if (nearestReachableFinal) {
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.currentStepIndex++;
            plan.stagedPath = null;
            plan.consecutiveNavFailures = 0;
            plan.lastProgressTick = now;
            plan.localProgressOrigin = npc.position();
            plan.localProgressTick = now;
            plan.stuckCheckCount = 0;
            plan.progressCheckTick = now;
            plan.progressCheckX = npc.getX();
            plan.progressCheckZ = npc.getZ();
            plan.lastRepathTick = now;
            plan.settledAtNearestReachable = true;
            plan.debugStage = "nearest_reachable";
            plan.debugRepathReason = "final_target_blocked";
            return false;
        }

        if (plan.crowdYieldTarget != null) {
            Vec3 yieldDelta = plan.crowdYieldTarget.subtract(npc.position());
            if (now < plan.crowdYieldUntil && yieldDelta.horizontalDistanceSqr() > 0.04D) {
                npc.getMoveControl().setWantedPosition(
                        plan.crowdYieldTarget.x, plan.crowdYieldTarget.y, plan.crowdYieldTarget.z, 0.7D);
                plan.debugStage = "crowd_yield";
                plan.debugNextWaypoint = plan.crowdYieldTarget;
                return false;
            }
            plan.crowdYieldTarget = null;
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.lastRepathTick = now - policy.retryTicks();
            plan.localProgressOrigin = npc.position();
            plan.localProgressTick = now;
        }

        plan.debugStage = finalStep ? "final_walk" : "walk";
        tryOpenPathDoors(level, npc, plan);

        // Vanilla waypoints can finish near the target (especially on slabs). Finish the
        // unobstructed last metre through MoveControl, never by widening arrival or setPos.
        boolean preciseApproach = npc.getNavigation().isDone() && distSqr<=2.25
                && Math.abs(targetDelta.y)<=policy.verticalTolerance()
                && sameRegion && hasSupportedApproach(level,npc,target)
                && level.noCollision(npc,npc.getBoundingBox().expandTowards(targetDelta.x,0,targetDelta.z));
        if (preciseApproach) {
            npc.getMoveControl().setWantedPosition(target.x,target.y,target.z,FINAL_APPROACH_SPEED);
            plan.debugStage="precise_approach";
        }

        // A small circle can move >0.2 blocks in every short sample. Require
        // leaving the local area as well; detours need not get closer to the destination.
        if (plan.localProgressOrigin == null || npc.position().distanceToSqr(plan.localProgressOrigin) >= 4) {
            plan.localProgressOrigin = npc.position();
            plan.localProgressTick = now;
        }
        boolean localLoop = now - plan.localProgressTick >= 100;

        // Issue moveTo only when navigation has no active path. Rebuilding an
        // already active path every second resets vanilla's internal progress.
        boolean navIdle = npc.getNavigation().isDone();
        boolean hasActivePath = npc.getNavigation().getPath() != null && !navIdle;
        boolean issuedSearch = false;
        boolean repathDue = now - plan.lastRepathTick >= policy.retryDelay(plan.consecutiveNavFailures);
        boolean recoveryPending = localLoop || plan.stuckCheckCount >= STUCK_REPATH_CHECKS || npc.getNavigation().isStuck()
                || npc.getNavigation() instanceof NpcPathNavigation navigation && navigation.needsRecovery();
        if (!preciseApproach && !recoveryPending && !hasActivePath && repathDue && NpcNavigationBudget.acquire(level.getServer(),level.dimension().location()+"/"+npc.getNpcId())) {
            issuedSearch = true;
            // moveTo() returns true if a path was successfully created
            double speed = movementSpeedForStep(tightStep, distSqr);
            boolean pathFound = moveTo(npc,plan,target,speed,portalApproach);
            boolean shouldLogMove = movementDebugEnabled()
                && (plan.lastMoveCommandLoggedStep != plan.currentStepIndex
                    || !pathFound
                    || now - plan.lastMoveStatusLogTick >= MOVE_STATUS_LOG_INTERVAL_TICKS);

            boolean stagedPathPending = plan.stagedPath != null;
            // A staged route is one logical search split across the global per-tick
            // budget. Do not make every segment wait through the normal failed-route
            // retry delay; the budget still guarantees at most one A* search per tick.
            if (!stagedPathPending) {
                plan.lastRepathTick = now;
            }
            if (!pathFound && !stagedPathPending) {
                plan.consecutiveNavFailures++;
                plan.debugRepathReason = "moveTo_fail_" + plan.consecutiveNavFailures;
            } else if (stagedPathPending) {
                plan.consecutiveNavFailures = 0;
                plan.debugRepathReason = "staged_path_pending";
            } else {
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

        // MoveControl owns travel yaw; restoring last tick's velocity yaw here
        // fights its turn towards the next waypoint, especially in tight corners.
        npc.setYHeadRot(npc.getYRot());

        // ── Displacement-based progress detection ──
        // Every PROGRESS_CHECK_INTERVAL ticks, measure how far the NPC actually
        // moved (2D). The longer local-area window above also catches circles
        // whose short samples would otherwise appear to make progress.
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

        boolean navigationStuck = npc.getNavigation().isStuck()
                || npc.getNavigation() instanceof NpcPathNavigation navigation && navigation.needsRecovery();
        boolean noProgressStuck = plan.stuckCheckCount >= STUCK_REPATH_CHECKS;
        if (!issuedSearch && (noProgressStuck || navigationStuck || localLoop) && repathDue
                && NpcNavigationBudget.acquire(level.getServer(),level.dimension().location()+"/"+npc.getNpcId())) {
            if (startCrowdYield(level, npc, plan, target, now)) {
                plan.localProgressOrigin = npc.position();
                plan.localProgressTick = now;
                plan.stuckCheckCount = 0;
                plan.progressCheckTick = now;
                plan.progressCheckX = npc.getX();
                plan.progressCheckZ = npc.getZ();
                return false;
            }
            if (npc.getNavigation() instanceof NpcPathNavigation navigation) navigation.prepareRecovery();
            else npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            double speed = movementSpeedForStep(tightStep, distSqr);
            boolean pathFound = moveTo(npc,plan,target,speed,portalApproach);
            plan.debugStage = "stuck_repath";
            boolean stagedPathPending = plan.stagedPath != null;
            if (!stagedPathPending) {
                plan.lastRepathTick = now;
            }
            if (pathFound || stagedPathPending) {
                plan.consecutiveNavFailures = 0;
            } else {
                plan.consecutiveNavFailures++;
            }
            plan.debugRepathReason = stagedPathPending
                ? "staged_path_pending"
                : navigationStuck ? "navigation_stuck"
                : localLoop ? "local_loop_repath" : "stuck_repath_check" + plan.stuckCheckCount;
            if (movementDebugEnabled()) {
                StardewCraft.LOGGER.warn("[NPC_MOVE] {} stuck_repath step={}/{} point={} reason={} pathFound={} pos={} target={} navStuck={} hasPath={} path={} next={} failures={}",
                    npc.getNpcId(), plan.currentStepIndex, plan.steps.size(), step.pointId,
                    plan.debugRepathReason, pathFound, fmt(npc.position()), fmt(target), navigationStuck,
                    npc.getNavigation().getPath() != null, pathSummary(npc), fmt(nextPathNodeTarget(npc)), plan.consecutiveNavFailures);
            }
            plan.localProgressOrigin = npc.position();
            plan.localProgressTick = now;
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

    // Kept as the single-step/test entry point. Runtime callers already know the
    // step role, while direct callers derive the same portal-approach contract
    // from the plan so both paths exercise identical validation.
    private static boolean moveTo(StardewNpcEntity npc,NpcRoutePlan plan,Vec3 target,double speed) {
        boolean finalStep=plan.currentStepIndex==plan.steps.size()-1;
        boolean portalApproach=!finalStep
                && plan.steps.get(plan.currentStepIndex+1).mode==NpcRoutePlanner.RouteStepMode.WARP;
        return moveTo(npc,plan,target,speed,portalApproach);
    }

    private static boolean moveTo(StardewNpcEntity npc,NpcRoutePlan plan,Vec3 target,double speed,
                                  boolean portalApproach) {
        if(plan.squareBounds!=null)return npc.getNavigation() instanceof NpcPathNavigation navigation
                && navigation.moveWithin(target,speed,plan.squareBounds);
        if (plan.stagedPath != null) {
            var stitched = npc.level() instanceof ServerLevel serverLevel
                    ? continueStagedPartialPath(serverLevel, npc, plan, target) : null;
            npc.getNavigation().stop();
            if (stitched != null) {
                plan.debugRepathReason = "validated_staged_path";
                return npc.getNavigation().moveTo(stitched, speed);
            }
            if (plan.failedStagedPath != null) {
                var partial = plan.failedStagedPath;
                plan.failedStagedPath = null;
                return followReachableEndpoint(npc, plan, partial, target, speed);
            }
            if (plan.stagedPath != null) {
                plan.debugRepathReason = "staged_path_pending";
            }
            return false;
        }
        boolean started = portalApproach && npc.getNavigation() instanceof NpcPathNavigation navigation
                ? navigation.moveToAuthoredPortalApproach(target,speed)
                : plan.allowNearestReachableFinal && npc.getNavigation() instanceof NpcPathNavigation navigation
                    ? navigation.moveToAuthoredTarget(target, speed, 0)
                    : npc.getNavigation().moveTo(target.x,target.y,target.z,speed);
        var path = npc.getNavigation().getPath();
        boolean nearbyDailyEndpoint = false;
        boolean finalStep = plan.currentStepIndex == plan.steps.size() - 1;
        boolean targetStandingCellUnavailable = npc.level() instanceof ServerLevel serverLevel
                && !hasUsableStandingCellAt(serverLevel, npc, target);
        if (started && path != null && path.getEndNode() != null
                && plan.allowNearestReachableFinal
                && finalStep
                && npc.level() instanceof ServerLevel serverLevel
                && targetStandingCellUnavailable) {
            Vec3 endpoint = path.getEntityPosAtNode(npc, path.getNodeCount() - 1);
            nearbyDailyEndpoint = endpoint.subtract(target).horizontalDistanceSqr()
                    <= BLOCKED_FINAL_APPROACH_DISTANCE_SQR
                    && Math.abs(endpoint.y - target.y) <= BLOCKED_FINAL_VERTICAL_TOLERANCE;
        }
        // PathFinder may mark a path as reachable even when its last node is the
        // nearest cell beside a blocked authored tile.  Ordinary schedule points
        // require the actual endpoint; otherwise the executor would stop outside a
        // counter and report success before executePlanTick can recover it.
        if (started && path != null && path.getEndNode() != null && finalStep
                && targetStandingCellUnavailable && !plan.allowNearestReachableFinal) {
            Vec3 endpoint = path.getEntityPosAtNode(npc, path.getNodeCount() - 1);
            if (endpoint.subtract(target).horizontalDistanceSqr() > 0.25D) {
                npc.getNavigation().stop();
                plan.debugRepathReason = "partial_final_endpoint_rejected";
                return false;
            }
        }
        if (started && path != null && !path.canReach() && !nearbyDailyEndpoint) {
            if (npc.level() instanceof ServerLevel serverLevel
                    && beginStagedPartialPath(serverLevel, npc, plan, path, target)) {
                npc.getNavigation().stop();
                plan.debugRepathReason = "staged_path_pending";
                return false;
            }
            // Exhaust continuation first, then physically walk the verified endpoint.
            // Scripted routes retain their exact-target contract.
            if (plan.allowNearestReachableFinal) return followReachableEndpoint(npc, plan, path, target, speed);
            npc.getNavigation().stop();
            return false;
        }
        if (npc.getNavigation() instanceof NpcPathNavigation navigation) {
            navigation.allowIncompleteRecomputation(nearbyDailyEndpoint);
        }
        return started;
    }

    private static boolean followReachableEndpoint(StardewNpcEntity npc, NpcRoutePlan plan,
                                                    net.minecraft.world.level.pathfinder.Path path,
                                                    Vec3 target, double speed) {
        if (!plan.allowNearestReachableFinal || path == null || path.getNodeCount() == 0
                || !(npc.level() instanceof ServerLevel level)) return false;
        Vec3 endpoint = path.getEntityPosAtNode(npc, path.getNodeCount() - 1);
        String expected = InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(target));
        if (!expected.equals(InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(endpoint)))
                || !expected.equals(InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition()))) return false;
        // Preserve the actual floor height (slabs/stairs), rather than rounding it up.
        var support = level.getBlockState(BlockPos.containing(endpoint).below())
                .getCollisionShape(level, BlockPos.containing(endpoint).below());
        if (!support.isEmpty()) endpoint = new Vec3(endpoint.x,
                BlockPos.containing(endpoint).getY() - 1 + support.max(net.minecraft.core.Direction.Axis.Y), endpoint.z);
        if (!hasUsableStandingCellAt(level, npc, endpoint)) return false;
        plan.fallbackEndpoint = endpoint;
        plan.fallbackStep = plan.currentStepIndex;
        npc.getNavigation().stop();
        List<net.minecraft.world.level.pathfinder.Node> endpointNodes = new ArrayList<>();
        appendPathNodes(endpointNodes, path);
        boolean started = npc.getNavigation().moveTo(new net.minecraft.world.level.pathfinder.Path(
                endpointNodes, BlockPos.containing(endpoint), true), speed);
        plan.debugRepathReason = "verified_nearest_endpoint";
        return started;
    }

    private static boolean beginStagedPartialPath(
            ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan,
            net.minecraft.world.level.pathfinder.Path first, Vec3 target) {
        if (first == null || first.getEndNode() == null || first.getNodeCount() == 0) return false;
        List<net.minecraft.world.level.pathfinder.Node> nodes = new ArrayList<>();
        appendPathNodes(nodes, first);
        Vec3 endpoint = first.getEntityPosAtNode(npc, first.getNodeCount() - 1);
        String region = InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition());
        String targetRegion = InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(target));
        if (!region.equals(targetRegion)
                || !usefulPartialSegment(npc.position(), endpoint, target, region)) {
            return false;
        }
        plan.stagedPath = new StagedPath(nodes, endpoint, target, region);
        return true;
    }

    /**
     * Extend at most one A* segment per call. The global navigation budget therefore
     * also bounds staged route verification: a single NPC can no longer hide four
     * searches inside one server tick. Custom road-aware search gets the first chance;
     * vanilla is tried on a later tick only when the custom evaluator cannot progress.
     */
    private static net.minecraft.world.level.pathfinder.Path continueStagedPartialPath(
            ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, Vec3 target) {
        StagedPath staged = plan.stagedPath;
        if (staged == null || staged.target.distanceToSqr(target) > 1.0E-6D) {
            plan.stagedPath = null;
            return null;
        }
        BlockPos endpointPos = BlockPos.containing(staged.endpoint);
        if (staged.continuationSegments >= MAX_STAGED_CONTINUATION_SEGMENTS
                || !staged.tryVanilla && !staged.visitedEndpoints.add(endpointPos)) {
            retainReachableStagedPath(plan, staged);
            return null;
        }

        StardewNpcEntity probe = new StardewNpcEntity(ModEntities.STARDEW_NPC.get(), level);
        probe.setNpcId(npc.getNpcId());
        probe.setPos(staged.endpoint.x, staged.endpoint.y, staged.endpoint.z);
        probe.setOnGround(true);
        probe.refreshDimensions();
        net.minecraft.world.level.pathfinder.Path continuation;
        try {
            continuation = probe.getNavigation() instanceof NpcPathNavigation navigation
                    ? staged.tryVanilla
                        ? navigation.createVanillaDiagnosticPath(BlockPos.containing(target))
                        : navigation.createNpcDiagnosticPath(BlockPos.containing(target))
                    : null;
            if (!validContinuationSeam(continuation, endpointPos)) {
                return deferVanillaOrFail(plan, staged);
            }
            Vec3 nextEndpoint = continuation.getEntityPosAtNode(probe, continuation.getNodeCount() - 1);
            if (!continuation.canReach()
                    && !usefulPartialSegment(staged.endpoint, nextEndpoint, target, staged.region)) {
                return deferVanillaOrFail(plan, staged);
            }
            appendPathNodes(staged.nodes, continuation);
            if (continuation.canReach()) {
                plan.stagedPath = null;
                return new net.minecraft.world.level.pathfinder.Path(
                        staged.nodes, BlockPos.containing(target), true);
            }
            staged.endpoint = nextEndpoint;
            staged.continuationSegments++;
            staged.tryVanilla = false;
            return null;
        } finally {
            probe.discard();
        }
    }

    private static net.minecraft.world.level.pathfinder.Path deferVanillaOrFail(
            NpcRoutePlan plan, StagedPath staged) {
        if (!staged.tryVanilla) {
            staged.tryVanilla = true;
        } else {
            retainReachableStagedPath(plan, staged);
        }
        return null;
    }

    private static void retainReachableStagedPath(NpcRoutePlan plan, StagedPath staged) {
        plan.failedStagedPath = new net.minecraft.world.level.pathfinder.Path(
                new ArrayList<>(staged.nodes), BlockPos.containing(staged.endpoint), false);
        plan.stagedPath = null;
    }

    private static boolean validContinuationSeam(net.minecraft.world.level.pathfinder.Path continuation,
                                                  BlockPos endpointPos) {
        if (continuation == null || continuation.getEndNode() == null || continuation.getNodeCount() == 0) {
            return false;
        }
        BlockPos seam = continuation.getNodePos(0);
        return Math.abs(seam.getX() - endpointPos.getX()) <= 1
                && Math.abs(seam.getY() - endpointPos.getY()) <= 1
                && Math.abs(seam.getZ() - endpointPos.getZ()) <= 1;
    }

    private static boolean usefulPartialSegment(Vec3 start, Vec3 endpoint, Vec3 target, String region) {
        return start.subtract(endpoint).horizontalDistanceSqr() >= 16.0D
                && start.subtract(target).horizontalDistance()
                    - endpoint.subtract(target).horizontalDistance() >= 4.0D
                && region.equals(InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(endpoint)));
    }

    private static void appendPathNodes(List<net.minecraft.world.level.pathfinder.Node> output,
                                        net.minecraft.world.level.pathfinder.Path path) {
        for (int index = 0; index < path.getNodeCount(); index++) {
            var node = path.getNode(index);
            if (!output.isEmpty()) {
                var last = output.getLast();
                if (last.x == node.x && last.y == node.y && last.z == node.z) continue;
            }
            output.add(node);
        }
    }

    private static boolean hasBodyClearanceAt(ServerLevel level, StardewNpcEntity npc, Vec3 target) {
        Vec3 delta = target.subtract(npc.position());
        return PortLevels.noBlockCollision(level, npc, npc.getBoundingBox().move(delta));
    }

    /** Recover only an embedded start, on a supported cell within the same room. */
    private static Vec3 nearestClearStart(ServerLevel level, StardewNpcEntity npc) {
        String room = InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition());
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos origin = npc.blockPosition();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = -4; y <= 8; y++) {
                BlockPos supportPos = origin.offset(x, y, z);
                if (!level.hasChunkAt(supportPos)) continue;
                var shape = level.getBlockState(supportPos).getCollisionShape(level, supportPos,
                        net.minecraft.world.phys.shapes.CollisionContext.of(npc));
                if (shape.isEmpty()) continue;
                Vec3 candidate = new Vec3(supportPos.getX() + .5,
                        supportPos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), supportPos.getZ() + .5);
                double distance = candidate.distanceToSqr(npc.position());
                if (distance >= bestDistance || Math.abs(candidate.y - npc.getY()) > 8
                        || !room.equals(InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(candidate)))
                        || !hasUsableStandingCellAt(level, npc, candidate)) continue;
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Tests the authored standing cell itself. Obstacles between the actor and this
     * cell require a detour; they do not make the destination a furniture anchor.
     */
    private static boolean hasUsableStandingCellAt(ServerLevel level, StardewNpcEntity npc, Vec3 target) {
        Vec3 delta = target.subtract(npc.position());
        AABB targetBody = npc.getBoundingBox().move(delta);
        if (!PortLevels.noBlockCollision(level, npc, targetBody)) return false;
        return !PortLevels.noBlockCollision(level, npc, targetBody.move(0.0D, -0.08D, 0.0D));
    }

    private static double movementSpeedForStep(boolean tightStep, double distSqr) {
        return tightStep && distSqr <= FINAL_APPROACH_DISTANCE_SQR ? FINAL_APPROACH_SPEED : 1.0D;
    }

    /**
     * Break a stable doorway deadlock without teleporting either actor. The NPC that is
     * behind yields first; side-by-side ties use the stable NPC id so both actors never
     * make the same decision. The brief retreat gives the other body enough room to take
     * the shared node, after which normal A* planning resumes.
     */
    private static boolean startCrowdYield(ServerLevel level,
                                           StardewNpcEntity npc,
                                           NpcRoutePlan plan,
                                           Vec3 routeTarget,
                                           long now) {
        Vec3 waypoint = nextPathNodeTarget(npc);
        if (waypoint == null) waypoint = routeTarget;
        Vec3 forward = new Vec3(waypoint.x - npc.getX(), 0.0D, waypoint.z - npc.getZ());
        if (forward.horizontalDistanceSqr() < 1.0E-4D) {
            forward = new Vec3(routeTarget.x - npc.getX(), 0.0D, routeTarget.z - npc.getZ());
        }
        if (forward.horizontalDistanceSqr() < 1.0E-4D) return false;
        forward = forward.normalize();

        List<StardewNpcEntity> nearby = level.getEntitiesOfClass(
                StardewNpcEntity.class,
                npc.getBoundingBox().inflate(1.15D, 0.5D, 1.15D),
                other -> other != npc && other.isAlive() && other.isPushable());
        StardewNpcEntity blocker = null;
        double nearestDistance = Double.MAX_VALUE;
        for (StardewNpcEntity other : nearby) {
            Vec3 relative = other.position().subtract(npc.position());
            double alongRoute = relative.x * forward.x + relative.z * forward.z;
            boolean sideBySide = Math.abs(alongRoute) <= 0.12D;
            boolean mustYield = alongRoute > 0.12D
                    || sideBySide && stableNpcOrder(npc).compareTo(stableNpcOrder(other)) > 0;
            double distance = relative.horizontalDistanceSqr();
            if (mustYield && distance < nearestDistance) {
                blocker = other;
                nearestDistance = distance;
            }
        }
        if (blocker == null) return false;

        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 toBlocker = blocker.position().subtract(npc.position());
        double blockerSide = toBlocker.x * side.x + toBlocker.z * side.z;
        double awaySign = blockerSide >= 0.0D ? -1.0D : 1.0D;
        Vec3[] offsets = {
            forward.scale(-0.75D),
            forward.scale(-0.60D).add(side.scale(0.35D * awaySign)),
            forward.scale(-0.45D).add(side.scale(0.50D * awaySign))
        };
        for (Vec3 offset : offsets) {
            Vec3 candidate = npc.position().add(offset);
            if (!hasSupportedApproach(level, npc, candidate)
                    || !PortLevels.noBlockCollision(level, npc, npc.getBoundingBox()
                        .expandTowards(offset.x, 0.0D, offset.z).deflate(1.0E-7D))) {
                continue;
            }
            npc.getNavigation().stop();
            stopHorizontalMotionPreserveGravity(npc);
            plan.crowdYieldTarget = candidate;
            plan.crowdYieldUntil = now + 24L;
            plan.debugStage = "crowd_yield";
            plan.debugRepathReason = "nearby_npc_" + stableNpcOrder(blocker);
            plan.debugNextWaypoint = candidate;
            return true;
        }
        return false;
    }

    private static String stableNpcOrder(StardewNpcEntity npc) {
        String id = npc.getNpcId();
        return id == null || id.isBlank() ? npc.getUUID().toString() : id;
    }

    private static StardewNpcEntity destinationBlocker(ServerLevel level,
                                                        StardewNpcEntity npc,
                                                        Vec3 target) {
        Vec3 delta = target.subtract(npc.position());
        AABB targetBody = npc.getBoundingBox().move(delta).inflate(0.05D, 0.02D, 0.05D);
        return level.getEntitiesOfClass(
                        StardewNpcEntity.class,
                        targetBody,
                        other -> other != npc && other.isAlive() && other.isPushable())
                .stream()
                .min(java.util.Comparator.comparing(NpcCentralMovementService::stableNpcOrder))
                .orElse(null);
    }

    static boolean hasReachedScheduleTarget(ServerLevel level, StardewNpcEntity npc, NpcRuntimeState state) {
        var plan=ACTIVE_PLANS.get(npc.getNpcId());
        if (plan != null && plan.exactWorkpoint && plan.boundEntityUuid.equals(npc.getUUID())
                && !plan.steps.isEmpty() && plan.steps.getLast().pointId.equals(state.namedPointId())) {
            return plan.currentStepIndex >= plan.steps.size()
                    && npc.position().distanceToSqr(plan.steps.getLast().target) <= 1.0E-6D;
        }
        if (plan != null && plan.boundEntityUuid.equals(npc.getUUID())
                && plan.currentStepIndex >= plan.steps.size() && !plan.steps.isEmpty()
                && plan.arrivalPosition != null && npc.position().distanceToSqr(plan.arrivalPosition) <= 1
                && plan.steps.getLast().pointId.equals(state.namedPointId())
                && InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition()).equals(
                    InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(plan.steps.getLast().target)))) {
            return true;
        }
        if(plan!=null&&plan.boundEntityUuid.equals(npc.getUUID())&&plan.square!=null
                &&plan.square.behavior.equals(NpcSquareMovement.parse(state.routeBehaviorToken()))
                &&plan.steps.getLast().pointId.equals(state.namedPointId())
                &&plan.square.area.contains(npc.position(),npc.getBbWidth()/2.))return true;
        var route = NpcRoutePlanner.resolveRoute(level,npc.getNpcId(),state,npc.blockPosition());
        return route != null && route.ready() && !route.destinationSteps.isEmpty()
                && arrivedAt(npc,route.destinationSteps.getLast().target,NpcNavigationPolicy.current().arrivalRadius());
    }

    private static boolean arrivedAt(StardewNpcEntity npc, Vec3 target, double radius) {
        return npc.onGround()
                && InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition())
                    .equals(InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(target)))
                && NpcNavigationPolicy.current().arrived(target.x-npc.getX(),target.y-npc.getY(),target.z-npc.getZ(),radius);
    }

    /** The activity handoff uses the same floor/region contract and cannot stop outside its alignment range. */
    public static boolean canAlignActivity(StardewNpcEntity npc,Vec3 target) {
        return arrivedAt(npc,target,Math.max(.75,NpcNavigationPolicy.current().arrivalRadius()));
    }

    public static boolean alignActivity(ServerLevel level,StardewNpcEntity npc,Vec3 target) {
        if(!canAlignActivity(npc,target) || !hasSupportedApproach(level,npc,target)) return false;
        Vec3 delta=target.subtract(npc.position());
        Vec3 step=delta.scale(Math.min(1,.045/Math.max(.001,delta.length())));
        if(!level.noCollision(npc,npc.getBoundingBox().expandTowards(step))) return false;
        npc.setPos(npc.position().add(step));
        return true;
    }

    /** The collision-free shortcut is only for supported ground, never an alternative to a detour. */
    private static boolean hasSupportedApproach(ServerLevel level, StardewNpcEntity npc, Vec3 target) {
        if (!npc.onGround()) return false;
        double minY = Math.min(npc.getY(),target.y)-.01, maxY = Math.max(npc.getY(),target.y)+.01;
        int samples = Math.max(1,(int)Math.ceil(target.subtract(npc.position()).horizontalDistance()/.2));
        for (int i=0;i<=samples;i++) {
            Vec3 point = npc.position().lerp(target,(double)i/samples);
            boolean supported = false;
            for (int y=(int)Math.floor(minY)-1;y<=(int)Math.floor(maxY);y++) {
                var pos = BlockPos.containing(point.x,y,point.z);
                if (!level.hasChunkAt(pos)) return false;
                var block = level.getBlockState(pos);
                if (!block.getFluidState().isEmpty()) return false;
                for (var shape:block.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(npc)).toAabbs()) {
                    double top = y+shape.maxY;
                    if (top>=minY && top<=maxY && point.x-pos.getX()>=shape.minX && point.x-pos.getX()<=shape.maxX
                            && point.z-pos.getZ()>=shape.minZ && point.z-pos.getZ()<=shape.maxZ) supported=true;
                }
            }
            if (!supported) return false;
        }
        return true;
    }

    private static String buildPlanSignature(NpcRuntimeState state, NpcRoutePlanner.NpcRouteContext route) {
        return state.activeScheduleKey() + "#" + state.scheduleCheckpoint() + "#" + state.scheduleNodeIndex()
            + "#" + state.namedPointId() + "#" + state.routeBehaviorToken() + "#" + NpcDataRegistry.revision()
            + "#" + route.canonicalLocation + "#" + route.status + "#" + route.diagnosticReason + "#" + route.missingPointId + "#" + route.missingPortalLinkId
            // Intermediate portal steps depend on the actor's current position.
            // Crossing a cell/door must not replace an in-flight plan or restart roaming.
            + "#" + (route.destinationSteps.isEmpty() ? "" : route.destinationSteps.getLast().target);
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

    private static void playPortalDoorSound(ServerLevel level, Vec3 entrance, Vec3 exit) {
        // SDV PathFindController uses doorClose at a location transition. Emit once
        // per nearby listener, even when both ends fit within hearing distance.
        var entranceRegion = InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(entrance));
        var exitRegion = InteriorRegionRegistry.fixedInteriorIdAt(BlockPos.containing(exit));
        for (var player : level.players()) {
            var region = InteriorRegionRegistry.fixedInteriorIdAt(player.blockPosition());
            double fromDistance = region.equals(entranceRegion) ? player.position().distanceToSqr(entrance) : Double.POSITIVE_INFINITY;
            double toDistance = region.equals(exitRegion) ? player.position().distanceToSqr(exit) : Double.POSITIVE_INFINITY;
            if (Math.min(fromDistance, toDistance) >= 16.0D * 16.0D) continue;
            Vec3 source = fromDistance <= toDistance ? entrance : exit;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    com.stardew.craft.sound.ModSounds.DOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.NEUTRAL, source.x, source.y, source.z,
                    0.9F, 1.0F, level.random.nextLong()));
        }
    }

    /** Open only door cells in the upcoming walking path, never doors beside a portal marker. */
    private static void tryOpenPathDoors(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan) {
        // A configured WARP landing may occupy a closed door. That door can prevent
        // even the first path from being created, so inspect actual body overlap too.
        var body = npc.getBoundingBox().deflate(1.0E-7);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY,body.minZ),
                BlockPos.containing(body.maxX,body.maxY,body.maxZ))) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)) continue;
            boolean intersecting = state.getCollisionShape(level,pos).toAabbs().stream()
                    .anyMatch(box -> body.intersects(box.move(pos)));
            if (intersecting) tryOpenDoorAt(level,npc,plan,pos);
        }
        var path=npc.getNavigation().getPath();
        if(path==null || path.isDone()) return;
        for(int i=path.getNextNodeIndex();i<Math.min(path.getNodeCount(),path.getNextNodeIndex()+3);i++) {
            var pos=path.getNodePos(i);
            if(npc.position().distanceToSqr(Vec3.atBottomCenterOf(pos))>DOOR_OPEN_PROBE_REACH_SQR) continue;
            tryOpenDoorAt(level,npc,plan,pos.below());
            tryOpenDoorAt(level,npc,plan,pos);
            tryOpenDoorAt(level,npc,plan,pos.above());
        }
    }

    private static boolean doorStillOnPath(StardewNpcEntity npc, BlockPos door) {
        var path=npc.getNavigation().getPath();
        if(path==null || path.isDone()) return false;
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++) {
            var node=path.getNodePos(i);
            if(node.getX()==door.getX() && node.getZ()==door.getZ() && Math.abs(node.getY()-door.getY())<=1) return true;
        }
        return false;
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

    private static void tryOpenDoorAt(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, BlockPos probePos) {
        if (probePos == null) {
            return;
        }

        BlockState state = level.getBlockState(probePos);
        if (state.getBlock() instanceof FenceGateBlock gate) {
            if (!state.getValue(FenceGateBlock.OPEN)) {
                setFenceGateOpen(level,npc,probePos,state,gate,true);
                plan.openedDoors.add(probePos.immutable());
            }
            return;
        }
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

        if (TownDoorRuntime.openForNpc(level, lowerPos)) return;

        if (!state.hasProperty(DoorBlock.OPEN) || state.getValue(DoorBlock.OPEN)) {
            return;
        }

        door.setOpen(npc, level, state, lowerPos, true);
        plan.openedDoors.add(lowerPos.immutable());
        if (movementDebugEnabled()) {
            StardewCraft.LOGGER.info("[NPC_MOVE] {} open_door pos={} block={} step={}/{} point={}",
                npc.getNpcId(), lowerPos.toShortString(), BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                plan.currentStepIndex, plan.steps.size(), plan.debugPointId);
        }
    }

    private static void setFenceGateOpen(ServerLevel level, StardewNpcEntity npc, BlockPos pos,
                                        BlockState state, FenceGateBlock gate, boolean open) {
        if (open && state.getValue(FenceGateBlock.FACING) == npc.getDirection().getOpposite()) {
            state = state.setValue(FenceGateBlock.FACING,npc.getDirection());
        }
        level.setBlock(pos,state.setValue(FenceGateBlock.OPEN,open),10);
        level.playSound(null,pos,open ? gate.openSound : gate.closeSound,
                net.minecraft.sounds.SoundSource.BLOCKS,1.0F,level.random.nextFloat()*.1F+.9F);
        level.gameEvent(npc,open ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN
                : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE,pos);
    }

    private static void closeOpenedDoors(ServerLevel level, StardewNpcEntity npc, NpcRoutePlan plan, boolean force) {
        if (plan.openedDoors.isEmpty()) {
            return;
        }
        List<BlockPos> closed = new ArrayList<>();
        for (BlockPos pos : plan.openedDoors) {
            if (!force && doorStillOnPath(npc, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            boolean gate = state.getBlock() instanceof FenceGateBlock;
            if ((!(state.getBlock() instanceof DoorBlock) && !gate)
                    || !state.hasProperty(DoorBlock.OPEN) || !state.getValue(DoorBlock.OPEN)
                    || (gate && state.getValue(FenceGateBlock.POWERED))) {
                closed.add(pos);
                continue;
            }
            double doorDx = npc.getX() - (pos.getX() + 0.5D);
            double doorDz = npc.getZ() - (pos.getZ() + 0.5D);
            // A waypoint can advance past the door before the actor's full body has
            // cleared it. Keep a short physical clearance behind the actor so the
            // door cannot clip its back or chatter open/closed at the threshold.
            if (doorDx * doorDx + doorDz * doorDz <= 2.25D
                    || npc.getBoundingBox().intersects(new AABB(pos).inflate(.2,0,.2).expandTowards(0,1,0))
                    || isDoorwayOccupied(level, pos)) {
                continue;
            }
            if (gate) setFenceGateOpen(level,npc,pos,state,(FenceGateBlock)state.getBlock(),false);
            else ((DoorBlock)state.getBlock()).setOpen(npc, level, state, pos, false);
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
            + "#" + state.locationName() + "#" + state.tileX() + "#" + state.tileY() + "#" + state.namedPointId();
        String previous = LAST_NODE_SIGNATURE.put(npcId, signature);
        return previous != null && !previous.equals(signature);
    }

    private static void applyFacing(StardewNpcEntity npc, NpcRuntimeState state) {
        if (state == null) {
            return;
        }
        // Don't override yaw while the NPC is turning to face a player (dialogue / gift)
        // or idle-looking at a nearby player.
        if ((npc.isFacingOverrideActive() || npc.isNativeActivityMovementLocked()) || npc.isIdleLookActive()) {
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
        private boolean exactWorkpoint;
        private long lastCollisionRecoveryTick = Long.MIN_VALUE / 2;
        private Vec3 fallbackEndpoint;
        private Vec3 arrivalPosition;
        private int fallbackStep = -1;
        private net.minecraft.world.level.pathfinder.Path failedStagedPath;
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
        private boolean squareAttempted;
        private NpcSquareMovement square;
        private NpcRoutePlan squareLeg;
        private NpcSquareArea squareBounds;
        /** Displacement-based progress detection: consecutive "no progress" checks. */
        private int stuckCheckCount;
        /** Tick when last progress check was performed. */
        private long progressCheckTick;
        /** X/Z position at last progress checkpoint. */
        private double progressCheckX, progressCheckZ;
        private Vec3 localProgressOrigin;
        private long localProgressTick;
        private int lastMoveCommandLoggedStep = -1;
        private long lastMoveStatusLogTick = Long.MIN_VALUE;
        private long lastNoProgressLogTick = Long.MIN_VALUE;
        private long lastNavFailureLogTick = Long.MIN_VALUE;
        private Vec3 crowdYieldTarget;
        private long crowdYieldUntil;
        private UUID destinationBlockerUuid;
        private long destinationWaitStartTick = Long.MIN_VALUE;
        private boolean allowNearestReachableFinal;
        private boolean settledAtNearestReachable;
        private StagedPath stagedPath;
        private final Set<BlockPos> openedDoors = new HashSet<>();

        private NpcRoutePlan(String signature, UUID boundEntityUuid, List<NpcRoutePlanner.NpcRouteStep> steps, long now) {
            this.signature = signature;
            this.boundEntityUuid = boundEntityUuid;
            this.steps = steps;
            this.currentStepIndex = 0;
            this.lastProgressTick = now;
            this.lastRepathTick = now - NpcNavigationPolicy.current().retryTicks();
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
            this.stuckCheckCount = 0;
            this.progressCheckTick = now;
            this.progressCheckX = 0.0D;
            this.progressCheckZ = 0.0D;
        }
    }

    private static final class StagedPath {
        private final List<net.minecraft.world.level.pathfinder.Node> nodes;
        private Vec3 endpoint;
        private final Vec3 target;
        private final String region;
        private final Set<BlockPos> visitedEndpoints = new HashSet<>();
        private int continuationSegments;
        private boolean tryVanilla;

        private StagedPath(List<net.minecraft.world.level.pathfinder.Node> nodes,
                           Vec3 endpoint, Vec3 target, String region) {
            this.nodes = nodes;
            this.endpoint = endpoint;
            this.target = target;
            this.region = region;
        }
    }

    public static final class DebugSnapshot {
        public long destinationWaitSince;
        public String arrivalPolicy = "", planIdentity = "";
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
            destinationWaitSince = plan == null ? Long.MIN_VALUE : plan.destinationWaitStartTick;
            arrivalPolicy = plan == null ? "" : plan.exactWorkpoint ? "exact_work"
                    : plan.allowNearestReachableFinal ? "nearest_reachable" : "exact_script";
            planIdentity = plan == null ? "" : plan.signature;
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

    /** Snap a newly placed NPC to nearby collision support, including slabs and carpets. */
    public static void snapToSurface(ServerLevel level, net.minecraft.world.entity.Mob npc) {
        snapToNearbySupport(level, npc);
    }

    private static boolean restoreGroundContact(ServerLevel level, net.minecraft.world.entity.Mob npc) {
        if (npc.onGround()) {
            return true;
        }
        if (npc.isPassenger() || npc.isInWaterOrBubble()) {
            return false;
        }
        return snapToNearbySupport(level, npc);
    }

    private static boolean snapToNearbySupport(ServerLevel level, net.minecraft.world.entity.Mob npc) {
        final double maxDrop = 0.75D;
        final double maxRise = 0.25D;
        double feetY = npc.getY();
        double localX;
        double localZ;
        double bestSurfaceY = Double.NEGATIVE_INFINITY;
        int blockX = net.minecraft.util.Mth.floor(npc.getX());
        int blockZ = net.minecraft.util.Mth.floor(npc.getZ());

        for (int blockY = net.minecraft.util.Mth.floor(feetY - maxDrop);
             blockY <= net.minecraft.util.Mth.floor(feetY + maxRise);
             blockY++) {
            BlockPos supportPos = new BlockPos(blockX, blockY, blockZ);
            if (!level.hasChunkAt(supportPos)) {
                continue;
            }
            BlockState state = level.getBlockState(supportPos);
            if (!state.getFluidState().isEmpty()) {
                continue;
            }
            localX = npc.getX() - blockX;
            localZ = npc.getZ() - blockZ;
            for (AABB shape : state.getCollisionShape(
                    level, supportPos, net.minecraft.world.phys.shapes.CollisionContext.of(npc)).toAabbs()) {
                double surfaceY = blockY + shape.maxY;
                if (surfaceY < feetY - maxDrop || surfaceY > feetY + maxRise
                        || localX < shape.minX || localX > shape.maxX
                        || localZ < shape.minZ || localZ > shape.maxZ) {
                    continue;
                }
                bestSurfaceY = Math.max(bestSurfaceY, surfaceY);
            }
        }

        if (!Double.isFinite(bestSurfaceY)) {
            return false;
        }
        AABB settledBody = npc.getBoundingBox().move(0.0D, bestSurfaceY - feetY, 0.0D).deflate(1.0E-7D);
        if (!PortLevels.noBlockCollision(level, npc, settledBody)) {
            return false;
        }
        npc.setPos(npc.getX(), bestSurfaceY, npc.getZ());
        npc.setOnGround(true);
        npc.fallDistance = 0.0F;
        Vec3 velocity = npc.getDeltaMovement();
        npc.setDeltaMovement(velocity.x, 0.0D, velocity.z);
        return true;
    }
}
