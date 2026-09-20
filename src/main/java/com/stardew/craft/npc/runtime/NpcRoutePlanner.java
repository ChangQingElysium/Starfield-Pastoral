package com.stardew.craft.npc.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.api.v1.world.StardewMapSlots;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.interior.InteriorPortalRegistry;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcLocationAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Route resolution: converts schedule targets and route profiles into step sequences.
 */
@SuppressWarnings("null")
final class NpcRoutePlanner {
    private static final Set<String> UNKNOWN_LOCATION_LOGGED = new HashSet<>();
    private static final Set<String> MISSING_CONFIG_POINT_LOGGED = new HashSet<>();
    private static final Set<String> HARD_ENDPOINT_WARNED = new HashSet<>();

    // ── Hot-path caches ──
    private static final Map<String, String> CANONICAL_NPC_ID_CACHE = new HashMap<>();
    private static long dataRevision=-1;
    private static long profileRevision=-1;
    private static JsonObject routeProfiles;
    private static boolean portalTargetsInitialized = false;

    private NpcRoutePlanner() {
    }

    static void resetState() {
        UNKNOWN_LOCATION_LOGGED.clear();
        MISSING_CONFIG_POINT_LOGGED.clear();
        HARD_ENDPOINT_WARNED.clear();
        CANONICAL_NPC_ID_CACHE.clear();
        portalTargetsInitialized = false;
        profileRevision=-1;
        routeProfiles=null;
        NpcLocationGraph.reload();
    }

    // ---- public route resolution entry point ----

    static NpcRouteContext resolveRoute(ServerLevel level, String npcId, NpcRuntimeState state) {
        return resolveRoute(level, npcId, state, null);
    }

    static NpcRouteContext resolveRoute(ServerLevel level, String npcId, NpcRuntimeState state, BlockPos npcPos) {
        if (dataRevision!=NpcDataRegistry.revision()) { resetState(); dataRevision=NpcDataRegistry.revision(); }
        String canonicalNpcId = canonicalNpcId(npcId);
        if (state == null) {
            return NpcRouteContext.invalid("", "missing_runtime_state", "", "");
        }
        if (state.activeScheduleKey().isBlank()) return NpcRouteContext.invalid("", "no_schedule", "", "");
        // A profile supplies the route, never permission to replace an explicit destination.
        var target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, null);
        if (!state.namedPointId().isBlank() && target == null) {
            return NpcRouteContext.waitingForCoordinates(state.locationName(),"target_unavailable",state.namedPointId(),"");
        }
        // Already at the destination: arrival must not depend on a building's
        // entrance route. This also covers resident actors in unmapped interiors.
        if (!state.namedPointId().isBlank() && npcPos != null && target != null && target.position() != null
                && npcPos.equals(BlockPos.containing(target.position()))) {
            return NpcRouteContext.ready(canonicalLocation(state.locationName()),
                    List.of(NpcRouteStep.walk(state.namedPointId(), target.position())));
        }
        if (shouldPreferRemoteOutdoorGraphRoute(state, npcPos)) {
            NpcRouteContext graphRoute = resolveGraphRoute(level, canonicalNpcId, state, npcPos);
            if (graphRoute != null) {
                return graphRoute;
            }
        }
        NpcRouteContext profileRoute = resolveProfileRoute(level, canonicalNpcId, state);
        if (profileRoute != null) {
            return profileRoute;
        }
        NpcRouteContext graphRoute = resolveGraphRoute(level, canonicalNpcId, state, npcPos);
        if (graphRoute != null) {
            return graphRoute;
        }
        NpcRouteContext genericRoute = resolveGenericScheduleRoute(level, canonicalNpcId, state);
        return genericRoute == null
            ? NpcRouteContext.waitingForCoordinates("", "target_missing", state.namedPointId(), "")
            : genericRoute;
    }

    static String canonicalNpcId(String npcId) {
        if (npcId == null || npcId.isBlank()) {
            return "";
        }
        return CANONICAL_NPC_ID_CACHE.computeIfAbsent(npcId, k -> k.trim().toLowerCase(Locale.ROOT));
    }

    static String fixedInteriorLocationAt(BlockPos pos) {
        if (pos == null) {
            return "";
        }
        return InteriorRegionRegistry.fixedInteriorAt(pos)
            .map(region -> locationForFixedInteriorRegion(region.id(), region.aliases()))
            .orElse("");
    }

    static Vec3 indoorExitForLocation(ServerLevel level,String location) {
        var anchor=anchorForLocation(location);
        return anchor==null || anchor.indoorExitPoint().isBlank()?null:pointFromConfig(level,anchor.indoorExitPoint(),null);
    }

    static Vec3 indoorExitForLocation(String canonicalLocation) {
        NpcLocationAnchor anchor = anchorForLocation(canonicalLocation);
        if (anchor == null || anchor.indoorExitPoint().isBlank()) {
            return null;
        }
        return pointFromConfigStrict(anchor.indoorExitPoint(), null, canonicalLocation);
    }

    static Vec3 outdoorExitForLocation(ServerLevel level,String location) {
        var anchor=anchorForLocation(location);
        if(anchor==null) return null;
        if(!anchor.outdoorDoorPoint().isBlank() && pointFromConfig(level,anchor.outdoorDoorPoint(),null)==null) return null;
        return outdoorExitForLocation(location);
    }

    static Vec3 outdoorExitForLocation(String canonicalLocation) {
        NpcLocationAnchor anchor = anchorForLocation(canonicalLocation);
        if (anchor == null) {
            return null;
        }
        if (!anchor.portalTarget().isBlank()) {
            Vec3 portalExit = portalTargetPosition(exitPortalTargetId(anchor.portalTarget()));
            if (portalExit != null) {
                return portalExit;
            }
        }
        if (!anchor.outdoorDoorPoint().isBlank()) {
            return pointFromConfigStrict(anchor.outdoorDoorPoint(), null, canonicalLocation);
        }
        return null;
    }

    static Vec3 outdoorDoorForLocation(ServerLevel level,String location) {
        var anchor=anchorForLocation(location);
        return anchor==null || anchor.outdoorDoorPoint().isBlank()?null:pointFromConfig(level,anchor.outdoorDoorPoint(),null);
    }

    static Vec3 outdoorDoorForLocation(String canonicalLocation) {
        NpcLocationAnchor anchor = anchorForLocation(canonicalLocation);
        if (anchor == null || anchor.outdoorDoorPoint().isBlank()) {
            return null;
        }
        return pointFromConfigStrict(anchor.outdoorDoorPoint(), null, canonicalLocation);
    }

    static Vec3 indoorEntryForLocation(ServerLevel level,String location) {
        var anchor=anchorForLocation(location);
        return anchor==null || anchor.indoorEntryPoint().isBlank()?null:pointFromConfig(level,anchor.indoorEntryPoint(),null);
    }

    static Vec3 indoorEntryForLocation(String canonicalLocation) {
        NpcLocationAnchor anchor = anchorForLocation(canonicalLocation);
        if (anchor == null || anchor.indoorEntryPoint().isBlank()) {
            return null;
        }
        return pointFromConfigStrict(anchor.indoorEntryPoint(), null, canonicalLocation);
    }

    private static NpcLocationAnchor anchorForLocation(String location) {
        String canonical = canonicalLocation(location);
        return canonical.isBlank() ? null : NpcDataRegistry.locationAnchors().get(canonical);
    }

    private static String locationForFixedInteriorRegion(String regionId, List<String> aliases) {
        List<String> candidates = new ArrayList<>();
        if (regionId != null && !regionId.isBlank()) {
            candidates.add(regionId);
        }
        if (aliases != null) {
            candidates.addAll(aliases);
        }
        for (String candidate : candidates) {
            String canonical = canonicalLocation(candidate);
            NpcLocationAnchor anchor = NpcDataRegistry.locationAnchors().get(canonical);
            if (anchor != null && anchor.indoor()) {
                return canonical;
            }
        }
        return "";
    }

    private static String canonicalLocation(String location) {
        if (location == null || location.isBlank()) {
            return "";
        }
        String raw = location.trim().toLowerCase(Locale.ROOT);
        return NpcDataRegistry.locationAliases().getOrDefault(raw, raw);
    }

    static Vec3 anchorPosition(String canonicalLocation, NpcLocationAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        if (anchor.indoor() && !anchor.indoorEntryPoint().isBlank()) {
            Vec3 point = pointFromConfig(anchor.indoorEntryPoint(), null);
            return point;
        }
        if (!anchor.indoor() && !anchor.outdoorDoorPoint().isBlank()) {
            Vec3 point = pointFromConfig(anchor.outdoorDoorPoint(), null);
            return point;
        }
        if (InteriorRegionRegistry.hasFixedInteriorAlias(canonicalLocation)) {
            return null;
        }
        if (!anchor.portalTarget().isBlank()) {
            String targetId = anchor.indoor() ? anchor.portalTarget() : exitPortalTargetId(anchor.portalTarget());
            return portalTargetPosition(targetId);
        }
        return new Vec3(anchor.x(), anchor.y(), anchor.z());
    }

    static Vec3 pointFromConfig(String pointId, Vec3 defaultPoint) {
        return pointFromConfig(null,pointId,defaultPoint);
    }

    static Vec3 pointFromConfig(ServerLevel level,String pointId,Vec3 defaultPoint) {
        var registered=StardewMapSlots.resolveWorldAnchor(pointId);
        if(registered.isPresent()) {
            var anchor=registered.get();
            if(level!=null && !anchor.dimension().equals(level.dimension().location())) return null;
            return NpcTargetSurface.resolve(level,anchor.position(),anchor.useGroundHeight(),anchor.indoor());
        }
        var point=com.stardew.craft.npc.data.NpcRoutePoints.get(pointId);
        if(point==null || !point.has("x") || !point.has("y") || !point.has("z")) return defaultPoint;
        if(level!=null && point.has("dimension") && !point.get("dimension").getAsString().equals(level.dimension().location().toString())) return null;
        return NpcTargetSurface.resolve(level,routePointPosition(point,defaultPoint),
                point.has("use_ground_height") && point.get("use_ground_height").getAsBoolean(),
                point.has("indoor") && point.get("indoor").getAsBoolean());
    }

    static Vec3 routePointPosition(JsonObject obj, Vec3 defaultPoint) {
        double x = obj.has("x") ? obj.get("x").getAsDouble() : defaultPoint.x;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : defaultPoint.y;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : defaultPoint.z;
        return new Vec3(centerRouteAxis(x), y, centerRouteAxis(z));
    }

    private static double centerRouteAxis(double value) {
        return Math.rint(value) == value ? value + 0.5D : value;
    }

    // ---- inner types ----

    enum RouteStepMode {
        WALK,
        WARP
    }

    enum RouteStatus {
        READY,
        WAITING_FOR_COORDINATES,
        INVALID_DATA,
        UNSUPPORTED_CONTEXT
    }

    static final class NpcRouteStep {
        final RouteStepMode mode;
        final String pointId;
        final Vec3 target;

        private NpcRouteStep(RouteStepMode mode, String pointId, Vec3 target) {
            this.mode = mode;
            this.pointId = pointId;
            this.target = target;
        }

        static NpcRouteStep walk(String pointId, Vec3 target) {
            return new NpcRouteStep(RouteStepMode.WALK, pointId, target);
        }

        static NpcRouteStep warp(String pointId, Vec3 target) {
            return new NpcRouteStep(RouteStepMode.WARP, pointId, target);
        }
    }

    static final class NpcRouteContext {
        final String canonicalLocation;
        final List<NpcRouteStep> destinationSteps;
        final RouteStatus status;
        final String diagnosticReason;
        final String missingPointId;
        final String missingPortalLinkId;

        NpcRouteContext(String canonicalLocation, List<NpcRouteStep> destinationSteps) {
            this(canonicalLocation, destinationSteps, RouteStatus.READY, "none", "", "");
        }

        private NpcRouteContext(String canonicalLocation,
                                List<NpcRouteStep> destinationSteps,
                                RouteStatus status,
                                String diagnosticReason,
                                String missingPointId,
                                String missingPortalLinkId) {
            this.canonicalLocation = canonicalLocation;
            this.destinationSteps = destinationSteps;
            this.status = status;
            this.diagnosticReason = diagnosticReason == null || diagnosticReason.isBlank() ? "none" : diagnosticReason;
            this.missingPointId = missingPointId == null ? "" : missingPointId;
            this.missingPortalLinkId = missingPortalLinkId == null ? "" : missingPortalLinkId;
        }

        static NpcRouteContext ready(String canonicalLocation, List<NpcRouteStep> destinationSteps) {
            return new NpcRouteContext(canonicalLocation, destinationSteps);
        }

        static NpcRouteContext waitingForCoordinates(String canonicalLocation,
                                                     String diagnosticReason,
                                                     String missingPointId,
                                                     String missingPortalLinkId) {
            return new NpcRouteContext(canonicalLocation, List.of(), RouteStatus.WAITING_FOR_COORDINATES, diagnosticReason, missingPointId, missingPortalLinkId);
        }

        static NpcRouteContext invalid(String canonicalLocation,
                                       String diagnosticReason,
                                       String missingPointId,
                                       String missingPortalLinkId) {
            return new NpcRouteContext(canonicalLocation, List.of(), RouteStatus.INVALID_DATA, diagnosticReason, missingPointId, missingPortalLinkId);
        }

        boolean ready() {
            return status == RouteStatus.READY;
        }
    }

    // ---- private helpers ----

    private static NpcRouteContext resolveProfileRoute(ServerLevel level, String canonicalNpcId, NpcRuntimeState state) {
        if (canonicalNpcId == null || canonicalNpcId.isBlank() || state == null) {
            return null;
        }

        // Registry getters return defensive copies. Take one private copy per revision,
        // not the entire profile catalog for every actor on every movement tick.
        if(profileRevision!=NpcDataRegistry.revision()) {
            routeProfiles=NpcDataRegistry.events().get("npc_route_profiles");
            profileRevision=NpcDataRegistry.revision();
        }
        JsonObject root = routeProfiles;
        if (root == null || !root.has("profiles") || !root.get("profiles").isJsonObject()) {
            return null;
        }

        JsonObject profiles = root.getAsJsonObject("profiles");
        JsonObject npcProfile = getObjectCaseInsensitive(profiles, canonicalNpcId);
        if (npcProfile == null) {
            return null;
        }

        String location = state.locationName() == null ? "" : state.locationName().trim().toLowerCase(Locale.ROOT);
        String canonicalLocation = NpcDataRegistry.locationAliases().getOrDefault(location, location);
        JsonElement routeEl = npcProfile.get(canonicalLocation);
        if (routeEl == null && "town".equals(canonicalLocation)) {
            routeEl = npcProfile.get("towngarden");
        }
        if (routeEl == null || !routeEl.isJsonArray()) {
            return null;
        }

        List<NpcRouteStep> destination = new ArrayList<>();
        for (JsonElement stepEl : routeEl.getAsJsonArray()) {
            if (!stepEl.isJsonObject()) {
                continue;
            }
            JsonObject stepObj = stepEl.getAsJsonObject();
            if (!stepObj.has("point") || !stepObj.get("point").isJsonPrimitive()) {
                continue;
            }
            String pointId = stepObj.get("point").getAsString().trim();
            if (pointId.isBlank()) {
                continue;
            }

            String mode = "walk";
            if (stepObj.has("mode") && stepObj.get("mode").isJsonPrimitive()) {
                mode = stepObj.get("mode").getAsString().trim().toLowerCase(Locale.ROOT);
            }

            Vec3 point = pointFromConfig(level,pointId,null);
            if (point == null) {
                return NpcRouteContext.waitingForCoordinates(canonicalLocation, "missing_route_point", pointId, "");
            }

            if ("warp".equals(mode)) {
                destination.add(NpcRouteStep.warp(pointId, point));
            } else {
                destination.add(NpcRouteStep.walk(pointId, point));
            }
        }

        if (destination.isEmpty()) {
            return null;
        }

        // If the active schedule provides a named point ("@point_id"), always prefer it
        // as the final destination — both for indoor routes (after warp) and outdoor-only
        // routes. This prevents hard-coded profile endpoints from overriding per-time
        // schedule targets such as town hangout vs football, or indoor sleep vs gaming points.
        String namedPointId = state.namedPointId();
        if (namedPointId != null && !namedPointId.isBlank()) {
            var resolvedTarget=NpcScheduleRuntimeService.resolveWorldTarget(level,state,null);
            Vec3 scheduleNamedTarget=resolvedTarget==null?null:resolvedTarget.position();
            if (scheduleNamedTarget != null) {
                // If the named point is outdoor but the profile route contains a WARP step,
                // abandon the profile route entirely — the NPC would warp indoor and then
                // try to A* walk to unreachable outdoor coordinates, causing infinite
                // plan rebuilds.  Fall through to resolveGenericScheduleRoute() instead.
                boolean routeHasWarp = destination.stream()
                        .anyMatch(s -> s.mode == RouteStepMode.WARP);
                if (routeHasWarp && !isPointIndoor(namedPointId)) {
                    return null;
                }

                int last = destination.size() - 1;
                NpcRouteStep lastStep = destination.get(last);
                if (lastStep.mode == RouteStepMode.WALK) {
                    destination.set(last, NpcRouteStep.walk(namedPointId, scheduleNamedTarget));
                } else {
                    destination.add(NpcRouteStep.walk(namedPointId, scheduleNamedTarget));
                }
            }
        }

        return new NpcRouteContext(canonicalLocation, destination);
    }

    private static JsonObject getObjectCaseInsensitive(JsonObject root, String key) {
        if (root == null || key == null || key.isBlank()) {
            return null;
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(key)) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                return null;
            }
            return entry.getValue().getAsJsonObject();
        }
        return null;
    }

    private static NpcRouteContext resolveGenericScheduleRoute(ServerLevel level,
                                                               String canonicalNpcId,
                                                               NpcRuntimeState state) {
        if (state == null) {
            return null;
        }

        String location = state.locationName() == null ? "" : state.locationName().trim().toLowerCase(Locale.ROOT);
        String canonicalLocation = NpcDataRegistry.locationAliases().getOrDefault(location, location);
        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, null);
        if (target == null || target.position() == null) {
            String missingSig = "target_missing|" + canonicalNpcId + "|" + canonicalLocation;
            if (UNKNOWN_LOCATION_LOGGED.add(missingSig)) {
            }
            return NpcRouteContext.waitingForCoordinates(canonicalLocation, "target_missing", state.namedPointId(), "");
        }

        List<NpcRouteStep> destination = new ArrayList<>();
        if (!target.indoorTarget()) {
            destination.add(NpcRouteStep.walk(state.namedPointId().isBlank() ? "schedule_anchor" : state.namedPointId(), target.position()));
            return NpcRouteContext.ready(canonicalLocation, destination);
        }

        NpcLocationAnchor anchor = NpcDataRegistry.locationAnchors().get(canonicalLocation);
        if (anchor == null) {
            String sig = "missing_anchor|" + canonicalNpcId + "|" + canonicalLocation;
            if (UNKNOWN_LOCATION_LOGGED.add(sig)) {
            }
            return NpcRouteContext.waitingForCoordinates(canonicalLocation, "missing_location_anchor", "", canonicalLocation);
        }
        if (anchor.outdoorDoorPoint().isBlank()) {
            return NpcRouteContext.waitingForCoordinates(canonicalLocation, "missing_outdoor_entry_walk_target", "", canonicalLocation);
        }
        Vec3 outdoorDoor = outdoorDoorForLocation(level,canonicalLocation);
        if (outdoorDoor == null) {
            return NpcRouteContext.waitingForCoordinates(canonicalLocation, "missing_outdoor_entry_walk_target", anchor.outdoorDoorPoint(), canonicalLocation);
        }
        destination.add(NpcRouteStep.walk("outdoor_door", outdoorDoor));

        if (anchor.indoorEntryPoint().isBlank()) {
            return NpcRouteContext.waitingForCoordinates(canonicalLocation, "missing_indoor_entry_landing", "", canonicalLocation);
        }
        Vec3 indoorEntry = pointFromConfig(level,anchor.indoorEntryPoint(),null);
        if (indoorEntry == null) {
            return NpcRouteContext.waitingForCoordinates(canonicalLocation, "missing_indoor_entry_landing", anchor.indoorEntryPoint(), canonicalLocation);
        }
        destination.add(NpcRouteStep.warp("indoor_entry", indoorEntry));
        destination.add(NpcRouteStep.walk(state.namedPointId().isBlank() ? "indoor_target" : state.namedPointId(), target.position()));
        return NpcRouteContext.ready(canonicalLocation, destination);
    }

    private static NpcRouteContext resolveGraphRoute(ServerLevel level,
                                                     String canonicalNpcId,
                                                     NpcRuntimeState state,
                                                     BlockPos npcPos) {
        if (state == null || !NpcLocationGraph.isAvailable()) {
            return null;
        }
        String destinationLocation = canonicalLocation(state.locationName());
        if (destinationLocation.isBlank()) {
            return null;
        }

        NpcScheduleRuntimeService.TargetPoint target = NpcScheduleRuntimeService.resolveWorldTarget(level, state, null);
        if (target == null || target.position() == null) {
            return null;
        }

        // A schedule's location label can name the building while its explicit point
        // is outside (e.g. Shane outside Marnie's ranch). Do not route back inside it.
        var destinationAnchor = anchorForLocation(destinationLocation);
        if (!target.indoorTarget() && destinationAnchor != null && destinationAnchor.indoor()) {
            destinationLocation = NpcLocationGraph.outdoorNeighborFor(destinationLocation);
            if (destinationLocation.isBlank()) return null;
        }

        String currentInteriorLocation = fixedInteriorLocationAt(npcPos);
        String sourceLocation = currentInteriorLocation.isBlank()
            ? outdoorSourceLocation(npcPos)
            : NpcLocationGraph.outdoorNeighborFor(currentInteriorLocation);
        if (sourceLocation.isBlank()) {
            sourceLocation = "town";
        }
        if (sourceLocation.equals(destinationLocation)) {
            return null;
        }

        NpcLocationGraph.GraphRoute graphRoute = NpcLocationGraph.findRoute(sourceLocation, destinationLocation);
        if (graphRoute == null || graphRoute.edges().isEmpty()) {
            return null;
        }
        List<NpcRouteStep> graphSteps = NpcLocationGraph.toRouteSteps(level,graphRoute, target.position());
        if (graphSteps.isEmpty()) {
            return NpcRouteContext.waitingForCoordinates(destinationLocation,"incomplete_location_connection","",sourceLocation+"->"+destinationLocation);
        }
        if (!state.namedPointId().isBlank()) {
            graphSteps = new ArrayList<>(graphSteps);
            graphSteps.set(graphSteps.size() - 1, NpcRouteStep.walk(state.namedPointId(), target.position()));
        }
        return NpcRouteContext.ready(destinationLocation, graphSteps);
    }

    private static boolean shouldPreferRemoteOutdoorGraphRoute(NpcRuntimeState state, BlockPos npcPos) {
        if (npcPos == null || !DesertConstants.isInDesertRegion(npcPos)) {
            return false;
        }
        String destinationLocation = canonicalLocation(state.locationName());
        return !destinationLocation.isBlank()
            && !"desert".equals(destinationLocation)
            && !"oasis".equals(destinationLocation);
    }

    private static String outdoorSourceLocation(BlockPos npcPos) {
        if (npcPos != null && DesertConstants.isInDesertRegion(npcPos)) {
            return "desert";
        }
        return "town";
    }

    private static Vec3 resolveOutdoorDoorForLocation(String canonicalLocation) {
        NpcLocationAnchor anchor = NpcDataRegistry.locationAnchors().get(canonicalLocation);
        if (anchor != null && !anchor.outdoorDoorPoint().isEmpty()) {
            return pointFromConfigStrict(anchor.outdoorDoorPoint(), null, canonicalLocation);
        }
        return null;
    }

    private static Vec3 pointFromConfigStrict(String pointId, NpcRuntimeState state, String canonicalLocation) {
        var registered = StardewMapSlots.resolveWorldAnchor(pointId);
        if (registered.isPresent()) {
            return registered.get().position();
        }
        JsonObject obj = com.stardew.craft.npc.data.NpcRoutePoints.get(pointId);
        if (obj == null) {
            emitHardEndpointWarning("missing_point_id", pointId, canonicalLocation, state);
            return null;
        }

        if (!obj.has("x") || !obj.has("y") || !obj.has("z")) {
            emitHardEndpointWarning("point_missing_xyz", pointId, canonicalLocation, state);
            return null;
        }

        return routePointPosition(obj, Vec3.ZERO);
    }

    private static Vec3 portalTargetPosition(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        ensurePortalTargetsInitialized();
        return InteriorPortalRegistry.resolve(targetId)
            .map(target -> new Vec3(target.x(), target.y(), target.z()))
            .orElse(null);
    }

    private static String exitPortalTargetId(String enterTargetId) {
        String trimmed = enterTargetId == null ? "" : enterTargetId.trim().toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("_enter")) {
            return trimmed.substring(0, trimmed.length() - "_enter".length()) + "_exit";
        }
        return "";
    }

    private static void ensurePortalTargetsInitialized() {
        if (portalTargetsInitialized) {
            return;
        }
        InteriorSubspaceManager.allStructures();
        portalTargetsInitialized = true;
    }

    /**
     * Checks if the given route point has {@code "indoor": true} in npc_route_points.json.
     */
    private static boolean isPointIndoor(String pointId) {
        var registered = StardewMapSlots.resolveWorldAnchor(pointId);
        if (registered.isPresent()) {
            return registered.get().indoor();
        }
        if (pointId != null) {
            String normalizedPointId = pointId.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, NpcLocationAnchor> entry : NpcDataRegistry.locationAnchors().entrySet()) {
                NpcLocationAnchor anchor = entry.getValue();
                if (pointId.equalsIgnoreCase(anchor.indoorEntryPoint())) {
                    return true;
                }
                String location = entry.getKey().trim().toLowerCase(Locale.ROOT);
                if (normalizedPointId.equals(location + "_indoor_entry")
                    || normalizedPointId.equals(location + "_inner_entry")) {
                    return true;
                }
            }
        }

        JsonObject obj=com.stardew.craft.npc.data.NpcRoutePoints.get(pointId);
        return obj!=null && obj.has("indoor") && obj.get("indoor").getAsBoolean();
    }

    private static void emitHardEndpointWarning(String reason, String pointId, String canonicalLocation, NpcRuntimeState state) {
        String sig = String.format(
            Locale.ROOT,
            "%s|%s|%s|%s|%d|%d",
            canonicalLocation,
            pointId,
            reason,
            safe(state == null ? "" : state.activeScheduleKey()),
            state == null ? -1 : state.scheduleCheckpoint(),
            state == null ? -1 : state.scheduleNodeIndex()
        );
        if (!HARD_ENDPOINT_WARNED.add(sig)) {
            return;
        }

    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }
}
