package com.stardew.craft.npc.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcLocationAnchor;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.festival.desert.DesertFestivalNpcVisitService;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.festival.desert.DesertFestivalVendorService;
import com.stardew.craft.festival.nightmarket.NightMarketNpcVisitService;
import com.stardew.craft.festival.squid.SquidFestService;
import com.stardew.craft.festival.trout.TroutDerbyService;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prototype schedule runtime: resolves the active checkpoint from schedule data by Stardew time.
 */
@SuppressWarnings("null")
public final class NpcScheduleRuntimeService {
    private static final String[] WEEKDAY_NAMES = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private static final String[] WEEKDAY_SHORT = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    private static boolean warnedMissingTownAnchor;
    private static final Map<String, ScheduleKeyTrace> LAST_KEY_TRACE = new LinkedHashMap<>();
    private static final Set<String> MAIL_POLICY_LOGGED = new HashSet<>();
    private static final Set<String> UNKNOWN_CONDITION_LOGGED = new HashSet<>();

    // Schedule resolution cache: only re-resolve when game clock changes.
    private static int cachedScheduleClock = Integer.MIN_VALUE;
    private static String cachedWeather = "";
    private static int cachedAbsoluteDay = Integer.MIN_VALUE;
    private static boolean cachedDesertFestivalDay;
    private static boolean cachedTroutDerbyOpen;
    private static boolean cachedSquidFestOpen;

    private NpcScheduleRuntimeService() {
    }

    public static void tick(ServerLevel level) {
        StardewTimeManager timeManager = StardewTimeManager.get();
        int currentTime = minutesToScheduleClock(timeManager.getCurrentTime());
        int absoluteDay = timeManager.getAbsoluteDay();
        boolean desertFestivalDay = DesertFestivalService.isFestivalDay();
        boolean troutDerbyOpen = TroutDerbyService.isFestivalOpen();
        boolean squidFestOpen = SquidFestService.isFestivalOpen();
        String activeWeather = WeatherManager.getCurrentWeather(level);
        if (activeWeather == null) activeWeather = "";

        // Skip full re-resolution if clock and weather haven't changed since last tick.
        if (currentTime == cachedScheduleClock && activeWeather.equals(cachedWeather)
            && absoluteDay == cachedAbsoluteDay && desertFestivalDay == cachedDesertFestivalDay
            && troutDerbyOpen == cachedTroutDerbyOpen && squidFestOpen == cachedSquidFestOpen) {
            return;
        }
        cachedScheduleClock = currentTime;
        cachedWeather = activeWeather;
        cachedAbsoluteDay = absoluteDay;
        cachedDesertFestivalDay = desertFestivalDay;
        cachedTroutDerbyOpen = troutDerbyOpen;
        cachedSquidFestOpen = squidFestOpen;

        NpcRuntimeDataManager runtimeData = NpcRuntimeDataManager.get(level);
        boolean changed = false;

        for (Map.Entry<String, NpcCapabilityProfile> entry : NpcDataRegistry.capabilities().entrySet()) {
            NpcCapabilityProfile profile = entry.getValue();
            if (!profile.implemented()) {
                continue;
            }

            String npcId = profile.npcId();
            NpcRuntimeState state = runtimeData.getOrCreate(npcId);
            ScheduleNode activeNode = resolveActiveNode(level, npcId, NpcDataRegistry.schedules().get(npcId), currentTime, timeManager, activeWeather);
            if (activeNode == null) {
                continue;
            }

            boolean stateChanged = false;
            if (!activeNode.scheduleKey().equalsIgnoreCase(state.activeScheduleKey())) {
                state.setActiveScheduleKey(activeNode.scheduleKey());
                stateChanged = true;
            }
            if (state.scheduleCheckpoint() != activeNode.checkpoint()) {
                state.setScheduleCheckpoint(activeNode.checkpoint());
                stateChanged = true;
            }
            if (!activeNode.locationName().equalsIgnoreCase(state.locationName())) {
                state.setLocationName(activeNode.locationName());
                stateChanged = true;
            }
            if (state.tileX() != activeNode.tileX()) {
                state.setTileX(activeNode.tileX());
                stateChanged = true;
            }
            if (state.tileY() != activeNode.tileY()) {
                state.setTileY(activeNode.tileY());
                stateChanged = true;
            }
            if (state.facing() != activeNode.facing()) {
                state.setFacing(activeNode.facing());
                stateChanged = true;
            }
            if (state.scheduleNodeIndex() != activeNode.index()) {
                state.setScheduleNodeIndex(activeNode.index());
                stateChanged = true;
            }
            if (!activeNode.routeBehaviorToken().equals(state.routeBehaviorToken())) {
                state.setRouteBehaviorToken(activeNode.routeBehaviorToken());
                stateChanged = true;
            }
            if (!activeNode.namedPointId().equals(state.namedPointId())) {
                state.setNamedPointId(activeNode.namedPointId());
                stateChanged = true;
            }

            if (stateChanged) {
                changed = true;
            }
        }

        if (changed) {
            runtimeData.setDirty();
        }
    }

    /** Force cache invalidation (e.g. after server context reset or data reload). */
    public static void invalidateCache() {
        cachedScheduleClock = Integer.MIN_VALUE;
        cachedWeather = "";
        cachedAbsoluteDay = Integer.MIN_VALUE;
        cachedDesertFestivalDay = false;
        cachedTroutDerbyOpen = false;
        cachedSquidFestOpen = false;
    }

    public static boolean isDesertFestivalMarlonOverride(String npcId) {
        return npcId != null && "marlon".equalsIgnoreCase(npcId) && DesertFestivalService.isFestivalDay();
    }

    public static TargetPoint resolveDesertFestivalMarlonTarget() {
        return new TargetPoint(new Vec3(-228.5D, 64.0D, -209.5D), false, false);
    }

    public static int desertFestivalMarlonFacing() {
        return 2;
    }

    public static boolean isTroutDerbyWillyOverride(String npcId) {
        return TroutDerbyService.isWillyScheduleOverride(npcId);
    }

    public static boolean isSquidFestWillyOverride(String npcId) {
        return SquidFestService.isWillyScheduleOverride(npcId);
    }

    public static TargetPoint resolveWorldTarget(ServerLevel level, NpcRuntimeState state, Vec3 defaultPosition) {
        if (state == null) {
            return defaultPosition == null ? null : new TargetPoint(defaultPosition, true, false);
        }

        // --- Named-point override (project-native "@point_id" schedule format) ---
        // This bypasses anchor lookup and returns the exact world position from route_points.json.
        String pointId = state.namedPointId();
        if (pointId != null && !pointId.isBlank()) {
            com.google.gson.JsonObject routePointsRoot = NpcDataRegistry.events().get("npc_route_points");
            if (routePointsRoot != null && routePointsRoot.has("points")) {
                com.google.gson.JsonObject points = routePointsRoot.getAsJsonObject("points");
                if (points.has(pointId)) {
                    com.google.gson.JsonObject pt = points.getAsJsonObject(pointId);
                    Vec3 position = NpcRoutePlanner.routePointPosition(pt, Vec3.ZERO);
                    boolean indoor = pt.has("indoor") && pt.get("indoor").getAsBoolean();
                    return new TargetPoint(position, false, indoor);
                }
            }
            // Named point defined in schedule but missing from route_points.json — fall through to anchor.
        }

        // --- Anchor lookup (legacy tile-offset or per-location anchor) ---
        String location = canonicalLocation(state.locationName());
        NpcLocationAnchor anchor = NpcDataRegistry.locationAnchors().get(location);
        if (anchor != null) {
            if (anchor.useScheduleTileOffset()) {
                double x = anchor.x() + state.tileX();
                double z = anchor.z() + state.tileY();
                return new TargetPoint(new Vec3(x, anchor.y(), z), anchor.useGroundHeight(), anchor.indoor());
            }

            Vec3 anchorPosition = NpcRoutePlanner.anchorPosition(location, anchor);
            if (anchorPosition == null) {
                return null;
            }
            boolean useGroundHeight = anchor.portalTarget().isBlank() && anchor.useGroundHeight();
            return new TargetPoint(anchorPosition, useGroundHeight, anchor.indoor());
        }

        if ("town".equals(location) && !warnedMissingTownAnchor) {
            warnedMissingTownAnchor = true;
        }
        return null;
    }

    public static ScheduleKeyTrace getLastKeyTrace(String npcId) {
        if (npcId == null) {
            return null;
        }
        return LAST_KEY_TRACE.get(npcId.toLowerCase(Locale.ROOT));
    }

    private static String canonicalLocation(String locationName) {
        if (locationName == null || locationName.isBlank()) {
            return "";
        }
        String raw = locationName.trim().toLowerCase(Locale.ROOT);
        return NpcDataRegistry.locationAliases().getOrDefault(raw, raw);
    }

    private static ScheduleNode resolveActiveNode(ServerLevel level,
                                                  String npcId,
                                                  JsonObject scheduleRoot,
                                                  int currentTime,
                                                  StardewTimeManager timeManager,
                                                  String activeWeather) {
        if (isTroutDerbyWillyOverride(npcId)) {
            return new ScheduleNode(TroutDerbyService.FESTIVAL_ID, 610, "forest", 0, 0,
                TroutDerbyService.WILLY_FACING, "", TroutDerbyService.WILLY_POINT_ID, 0);
        }
        if (isSquidFestWillyOverride(npcId)) {
            return new ScheduleNode(SquidFestService.FESTIVAL_ID, 610, "beach", 0, 0,
                SquidFestService.WILLY_FACING, "", SquidFestService.WILLY_POINT_ID, 0);
        }
        ScheduleNode nightMarketVisitorNode = resolveNightMarketVisitorNode(npcId, currentTime);
        if (nightMarketVisitorNode != null) {
            return nightMarketVisitorNode;
        }
        ScheduleNode desertVendorNode = resolveDesertFestivalVendorNode(npcId, currentTime);
        if (desertVendorNode != null) {
            return desertVendorNode;
        }
        ScheduleNode desertVisitorNode = resolveDesertFestivalVisitorNode(npcId, currentTime);
        if (desertVisitorNode != null) {
            return desertVisitorNode;
        }

        if (scheduleRoot == null) {
            return null;
        }

        String scheduleKey = selectScheduleKey(level, npcId, scheduleRoot, timeManager, activeWeather);
        if (scheduleKey == null) {
            return null;
        }

            JsonObject activeSchedule = resolveScheduleObjectWithGoto(level, npcId, scheduleRoot, scheduleKey);
        if (activeSchedule == null) {
            return null;
        }
        List<ScheduleNode> nodes = new ArrayList<>();
        String lastLocation = "";
        for (Map.Entry<String, JsonElement> entry : activeSchedule.entrySet()) {
            if (entry.getKey().startsWith("_")) {
                continue;
            }
            int checkpoint = parseCheckpoint(entry.getKey());
            if (checkpoint < 0 || !entry.getValue().isJsonPrimitive()) {
                continue;
            }

            ScheduleNode parsed = parseNode(scheduleKey, checkpoint, entry.getValue().getAsString(), lastLocation);
            if (parsed != null) {
                nodes.add(parsed);
                lastLocation = parsed.locationName();
            }
        }

        if (nodes.isEmpty()) {
            return null;
        }

        nodes.sort(Comparator.comparingInt(ScheduleNode::checkpoint));
        for (int i = 0; i < nodes.size(); i++) {
            ScheduleNode node = nodes.get(i);
            nodes.set(i, node.withIndex(i));
        }

        ScheduleNode active = nodes.get(0);
        for (ScheduleNode node : nodes) {
            if (currentTime >= node.checkpoint()) {
                active = node;
            }
        }
        return active;
    }

    private static ScheduleNode resolveDesertFestivalVendorNode(String npcId, int currentTime) {
        if (isDesertFestivalMarlonOverride(npcId)) {
            return new ScheduleNode("desertFestival", 0, "desert", 0, 0, desertFestivalMarlonFacing(), "", "desert_festival_marlon", 0);
        }
        int slot = DesertFestivalVendorService.vendorSlotFor(npcId);
        if (slot < 0) {
            return null;
        }
        int arrivalTime = DesertFestivalVendorService.arrivalTimeForSlot(slot);
        if (arrivalTime < 0 || currentTime < arrivalTime || currentTime >= DesertFestivalVendorService.closeTime()) {
            return null;
        }
        return new ScheduleNode(
            "desertFestival",
            arrivalTime,
            "desert",
            0,
            0,
            2,
            "",
            DesertFestivalVendorService.stallPointForSlot(slot),
            0
        );
    }

    private static ScheduleNode resolveNightMarketVisitorNode(String npcId, int currentTime) {
        NightMarketNpcVisitService.VisitEntry visit = NightMarketNpcVisitService.currentVisit(npcId, currentTime);
        if (visit == null) {
            return null;
        }
        return new ScheduleNode(
            visit.scheduleKey(),
            visit.arrivalTime(),
            "beach",
            0,
            0,
            visit.facing(),
            "",
            visit.pointId(),
            0
        );
    }

    private static ScheduleNode resolveDesertFestivalVisitorNode(String npcId, int currentTime) {
        DesertFestivalNpcVisitService.VisitEntry visit = DesertFestivalNpcVisitService.currentVisit(npcId, currentTime);
        if (visit == null) {
            return null;
        }
        return new ScheduleNode(
            visit.scheduleKey(),
            visit.arrivalTime(),
            "desert",
            0,
            0,
            visit.facing(),
            visit.behavior(),
            visit.pointId(),
            0
        );
    }

    private static String selectScheduleKey(ServerLevel level,
                                            String npcId,
                                            JsonObject scheduleRoot,
                                            StardewTimeManager timeManager,
                                            String activeWeather) {
        NpcFriendshipDataManager friendship = NpcFriendshipDataManager.get(level);
        UUID schedulePlayerId = resolveScheduleContextPlayer(level, npcId);
        Set<String> candidates = new LinkedHashSet<>();
        int day = Math.max(1, timeManager.getCurrentDay());
        String season = timeManager.getSeasonName().toLowerCase(Locale.ROOT);
        int weekdayIndex = (day - 1) % WEEKDAY_NAMES.length;
        String weekday = WEEKDAY_NAMES[weekdayIndex];
        String weekdayShort = WEEKDAY_SHORT[weekdayIndex];
        String weather = activeWeather == null ? "" : activeWeather.toLowerCase(Locale.ROOT);
        int hearts = Math.max(0, friendship.getPointsForNpc(schedulePlayerId, canonicalNpcId(npcId)) / NpcInteractionService.POINTS_PER_HEART);

        // Vanilla checks Green Rain before passive festivals and Pam's repaired-bus schedule.
        if (timeManager.getCurrentYear() == 1 && weather.contains("greenrain")) {
            candidates.add("GreenRain");
            candidates.add("greenrain");
        }

        for (FestivalDefinition festival : FestivalService.getActivePassiveFestivalsToday()) {
            int dayOfFestival = festival.dayOfFestival(timeManager.getCurrentSeason(), day);
            if (dayOfFestival > 0) {
                candidates.add(festival.id() + "_" + dayOfFestival);
            }
            candidates.add(festival.id());
        }

        // 1) <season>_<day>
        candidates.add(season + "_" + day);

        // 2) <day>_<hearts>
        candidates.add(day + "_" + hearts);

        // 3) <day>
        candidates.add(String.valueOf(day));

        // 4) Pam uses the dedicated bus schedule only after the Vault is complete.
        if (shouldUsePamBusSchedule(npcId)) {
            candidates.add("bus");
        }

        // 5/6/7) Weather overrides: rain2 > rain > snow
        if (weather.contains("rain") || weather.contains("storm")) {
            candidates.add("rain2");
            candidates.add("rain");
        }
        if (weather.contains("snow")) {
            candidates.add("snow");
        }

        // 7) <season>_<dayOfWeek>_<hearts>
        candidates.add(season + "_" + weekday + "_" + hearts);
        candidates.add(season + "_" + weekdayShort + "_" + hearts);

        // 8) <season>_<dayOfWeek>
        candidates.add(season + "_" + weekday);
        candidates.add(season + "_" + weekdayShort);

        // 9) <dayOfWeek>_<hearts>
        candidates.add(weekday + "_" + hearts);
        candidates.add(weekdayShort + "_" + hearts);

        // 10) <dayOfWeek>
        candidates.add(weekday);
        candidates.add(weekdayShort);

        // 11) <season>
        candidates.add(season);

        // 12) spring_<dayOfWeek>
        candidates.add("spring_" + weekday);
        candidates.add("spring_" + weekdayShort);

        // 13) spring
        candidates.add("spring");

        // 14) default
        candidates.add("default");

        List<String> traceCandidates = new ArrayList<>();
        List<String> traceRejects = new ArrayList<>();

        for (String candidate : candidates) {
            traceCandidates.add(candidate);
            JsonObject obj = getScheduleObjectCaseInsensitive(scheduleRoot, candidate);
            if (obj == null) {
                traceRejects.add(candidate + " -> missing_key");
                continue;
            }

            if (!scheduleConditionPasses(level, obj, schedulePlayerId)) {
                traceRejects.add(candidate + " -> condition_blocked");
                continue;
            }

            String actual = findActualScheduleKey(scheduleRoot, candidate);
            LAST_KEY_TRACE.put(
                canonicalNpcId(npcId),
                new ScheduleKeyTrace(
                    canonicalNpcId(npcId),
                    day,
                    season,
                    weekday,
                    weather,
                    hearts,
                    actual,
                    Collections.unmodifiableList(traceCandidates),
                    Collections.unmodifiableList(traceRejects)
                )
            );
            return actual;
        }

        LAST_KEY_TRACE.put(
            canonicalNpcId(npcId),
            new ScheduleKeyTrace(
                canonicalNpcId(npcId),
                day,
                season,
                weekday,
                weather,
                hearts,
                "<none>",
                Collections.unmodifiableList(traceCandidates),
                Collections.unmodifiableList(traceRejects)
            )
        );
        return null;
    }

    private static boolean shouldUsePamBusSchedule(String npcId) {
        return "pam".equals(canonicalNpcId(npcId))
            && CCStoryFlags.anyPlayerHasFlag(CCStoryFlags.CC_VAULT);
    }

    private static String canonicalNpcId(String npcId) {
        return NpcRoutePlanner.canonicalNpcId(npcId);
    }

    private static JsonObject resolveScheduleObjectWithGoto(ServerLevel level, String npcId, JsonObject scheduleRoot, String scheduleKey) {
        String current = scheduleKey;
        UUID schedulePlayerId = resolveScheduleContextPlayer(level, npcId);
        Set<String> visited = new HashSet<>();

        while (current != null && !current.isBlank() && !visited.contains(current)) {
            visited.add(current);
            JsonObject obj = getScheduleObjectCaseInsensitive(scheduleRoot, current);
            if (obj == null) {
                return null;
            }

            if (!scheduleConditionPasses(level, obj, schedulePlayerId)) {
                return null;
            }
            if (!obj.has("_goto") || !obj.get("_goto").isJsonPrimitive()) {
                return obj;
            }

            String next = obj.get("_goto").getAsString();
            current = next == null ? null : next.trim();
        }

        // Detect _goto cycle
        if (current != null && visited.contains(current)) {
        }

        return null;
    }

    private static boolean scheduleConditionPasses(ServerLevel level, JsonObject scheduleObj, UUID schedulePlayerId) {
        if (!scheduleObj.has("_condition") || !scheduleObj.get("_condition").isJsonPrimitive()) {
            return true;
        }

        String raw = scheduleObj.get("_condition").getAsString();
        if (raw == null || raw.isBlank()) {
            return true;
        }

        String[] parts = raw.trim().split("\\s+");
        if (parts.length >= 4 && "NOT".equalsIgnoreCase(parts[0]) && "friendship".equalsIgnoreCase(parts[1])) {
            return evaluateFriendshipCondition(level, parts, true, schedulePlayerId);
        }
        if (parts.length >= 3 && "friendship".equalsIgnoreCase(parts[0])) {
            return evaluateFriendshipCondition(level, parts, false, schedulePlayerId);
        }
        if (parts.length >= 2 && "MAIL".equalsIgnoreCase(parts[0])) {
            boolean allowed = mailConditionAllowed(level, false);
            logMailPolicyDecision(raw, allowed);
            return allowed;
        }
        if (parts.length >= 3 && "NOT".equalsIgnoreCase(parts[0]) && "MAIL".equalsIgnoreCase(parts[1])) {
            boolean allowed = mailConditionAllowed(level, true);
            logMailPolicyDecision(raw, allowed);
            return allowed;
        }

        logUnknownCondition(raw);
        // Unknown condition syntax: keep schedule available rather than hard-blocking it.
        return true;
    }

    private static void logMailPolicyDecision(String rawCondition, boolean allowed) {
        String key = rawCondition == null ? "<null>" : rawCondition.trim().toLowerCase(Locale.ROOT);
        if (!MAIL_POLICY_LOGGED.add(key + "|" + allowed)) {
            return;
        }
    }

    private static void logUnknownCondition(String rawCondition) {
        String key = rawCondition == null ? "<null>" : rawCondition.trim().toLowerCase(Locale.ROOT);
        if (!UNKNOWN_CONDITION_LOGGED.add(key)) {
            return;
        }
    }

    private static boolean mailConditionAllowed(ServerLevel level, boolean negated) {
        String policy = resolveMailConditionPolicyRaw(NpcDataRegistry.events().get("npc_schedule_policy"));
        return switch (policy) {
            case "allow_all" -> true;
            case "block_all" -> false;
            case "strict_block" -> false;
            case "block_mail_allow_not_mail" -> negated;
            default -> negated;
        };
    }

    private static String resolveMailConditionPolicyRaw(JsonObject policyRoot) {
        if (policyRoot == null || !policyRoot.has("mail_condition_policy") || !policyRoot.get("mail_condition_policy").isJsonPrimitive()) {
            return "block_mail_allow_not_mail";
        }
        String raw = policyRoot.get("mail_condition_policy").getAsString();
        if (raw == null || raw.isBlank()) {
            return "block_mail_allow_not_mail";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean evaluateFriendshipCondition(ServerLevel level, String[] parts, boolean negate, UUID schedulePlayerId) {
        int startIndex = negate ? 2 : 1;
        if ((parts.length - startIndex) < 2) {
            return true;
        }

        NpcFriendshipDataManager friendship = NpcFriendshipDataManager.get(level);
        boolean allMatched = true;

        for (int i = startIndex; i + 1 < parts.length; i += 2) {
            String targetNpc = parts[i].toLowerCase(Locale.ROOT);
            int hearts;
            try {
                hearts = Integer.parseInt(parts[i + 1]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            int thresholdPoints = hearts * NpcInteractionService.POINTS_PER_HEART;
            int currentPoints = friendship.getPointsForNpc(schedulePlayerId, targetNpc);
            if (currentPoints < thresholdPoints) {
                allMatched = false;
                break;
            }
        }

        return negate ? !allMatched : allMatched;
    }

    private static UUID resolveScheduleContextPlayer(ServerLevel level, String npcId) {
        if (level == null) {
            return null;
        }

        List<ServerPlayer> players = level.players();
        if (players == null || players.isEmpty()) {
            return null;
        }

        // Multiplayer policy: always bind schedule context to host player to keep
        // global NPC schedule deterministic across clients.
        if (level.getServer() != null) {
            for (ServerPlayer player : players) {
                if (player == null) {
                    continue;
                }
                if (level.getServer().isSingleplayerOwner(player.getGameProfile())) {
                    return player.getUUID();
                }
            }
        }

        for (ServerPlayer player : players) {
            if (player != null && !player.isSpectator()) {
                return player.getUUID();
            }
        }
        return players.get(0).getUUID();
    }

    private static JsonObject getScheduleObjectCaseInsensitive(JsonObject root, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(candidate)) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                return null;
            }
            return entry.getValue().getAsJsonObject();
        }
        return null;
    }

    private static String findActualScheduleKey(JsonObject root, String candidate) {
        for (String key : root.keySet()) {
            if (key.equalsIgnoreCase(candidate)) {
                return key;
            }
        }
        return candidate;
    }

    @SuppressWarnings("unused")
    private static int parseCheckpoint(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int minutesToScheduleClock(int totalMinutes) {
        int hour = Math.max(0, totalMinutes / 60);
        int minute = Math.max(0, totalMinutes % 60);
        return hour * 100 + minute;
    }

    @SuppressWarnings("unused")
    private static ScheduleNode parseNode(String scheduleKey, int checkpoint, String raw, String previousLocation) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        try {
            int startIndex = 0;
            String location;
            if (isInteger(parts[0])) {
                if (previousLocation == null || previousLocation.isBlank()) {
                    return null;
                }
                location = previousLocation;
            } else {
                location = parts[0];
                startIndex = 1;
            }

            // --- NEW FORMAT: "<location> @<namedPointId> [<facing>] [<behavior>]" ---
            if (startIndex < parts.length && parts[startIndex].startsWith("@")) {
                String namedPointId = parts[startIndex].substring(1);
                int facing = 2; // default facing south
                String behavior = "";
                if (startIndex + 1 < parts.length && isInteger(parts[startIndex + 1])) {
                    facing = Integer.parseInt(parts[startIndex + 1]);
                    behavior = extractRouteBehaviorToken(parts, startIndex + 2);
                } else if (startIndex + 1 < parts.length) {
                    behavior = extractRouteBehaviorToken(parts, startIndex + 1);
                }
                return new ScheduleNode(scheduleKey, checkpoint, location, 0, 0, facing, behavior, namedPointId, 0);
            }

            // --- SIMPLE FORMAT: "<location> <facing>" (1 int, outdoor shorthand) ---
            if (parts.length == startIndex + 1 && isInteger(parts[startIndex])) {
                int facing = Integer.parseInt(parts[startIndex]);
                return new ScheduleNode(scheduleKey, checkpoint, location, 0, 0, facing, "", "", 0);
            }

            // --- LEGACY SDV FORMAT: "<location> <tileX> <tileY> <facing> [<behavior>]" ---
            if (parts.length < startIndex + 3) {
                return null;
            }

            int tileX = Integer.parseInt(parts[startIndex]);
            int tileY = Integer.parseInt(parts[startIndex + 1]);
            int facing = Integer.parseInt(parts[startIndex + 2]);
            String behavior = extractRouteBehaviorToken(parts, startIndex + 3);
            return new ScheduleNode(scheduleKey, checkpoint, location, tileX, tileY, facing, behavior, "", 0);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isInteger(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String extractRouteBehaviorToken(String[] parts, int tokenStartIndex) {
        if (parts.length <= tokenStartIndex) {
            return "";
        }
        for (int i = tokenStartIndex; i < parts.length; i++) {
            String token = parts[i];
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("\"") || token.contains("\\")) {
                continue;
            }
            return token;
        }
        return "";
    }

    private record ScheduleNode(String scheduleKey,
                                int checkpoint,
                                String locationName,
                                int tileX,
                                int tileY,
                                int facing,
                                String routeBehaviorToken,
                                /**
                                 * Named route-point ID from npc_route_points.json.
                                 * Set when the schedule uses the {@code @point_id} shorthand;
                                 * empty string = use anchor / tile-offset logic.
                                 */
                                String namedPointId,
                                int index) {
        private ScheduleNode withIndex(int nextIndex) {
            return new ScheduleNode(scheduleKey, checkpoint, locationName, tileX, tileY, facing, routeBehaviorToken, namedPointId, nextIndex);
        }
    }

    public record TargetPoint(Vec3 position, boolean useGroundHeight, boolean indoorTarget) {
    }

    public record ScheduleKeyTrace(
        String npcId,
        int day,
        String season,
        String weekday,
        String weather,
        int hearts,
        String selectedKey,
        List<String> candidates,
        List<String> rejections
    ) {
    }
}
