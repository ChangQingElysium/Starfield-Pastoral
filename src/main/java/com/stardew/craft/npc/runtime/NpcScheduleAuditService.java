package com.stardew.craft.npc.runtime;

import com.google.gson.GsonBuilder;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Explicit dedicated-server audit: real map, normal simulation ticks, no client required. */
public final class NpcScheduleAuditService {
    private static final boolean ENABLED = Boolean.getBoolean("stardewcraft.npcScheduleAudit");
    private static final Map<MinecraftServer, Run> RUNS = new java.util.IdentityHashMap<>();
    private static final int DAY_TICKS = 20_000; // Production clock: 6AM to 2AM.
    private static final int WARMUP_TICKS = 200;
    private static final int DAYS = 2;

    private NpcScheduleAuditService() {}

    public static void tick(MinecraftServer server) {
        if (ENABLED) RUNS.computeIfAbsent(server, Run::new).tick();
    }

    public static void onServerStopped(MinecraftServer server) {
        Run run = RUNS.remove(server);
        if (run != null && !run.finished) run.writeReport("interrupted");
    }

    private static final class Run {
        private final MinecraftServer server;
        private final Map<String, Actor> actors = new TreeMap<>();
        private final List<String> fatal = new ArrayList<>();
        private final long startedNanos = System.nanoTime();
        private ServerLevel valley;
        private int ticks;
        private int day = 1;
        private int minute = 360;
        private boolean finished;

        private Run(MinecraftServer server) { this.server = server; }

        private void tick() {
            if (finished) return;
            if (valley == null) {
                valley = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (valley == null) {
                    fatal.add("Stardew Valley dimension not loaded");
                    finish();
                    return;
                }
                StardewTimeManager clock = StardewTimeManager.get();
                clock.setCurrentSeason(3);
                clock.setCurrentDay(day);
                clock.setCurrentTime(minute);
                NpcScheduleRuntimeService.clearExecutionState();
                NpcRuntimeManager.ensureActiveWorld(valley);
                NpcDataRegistry.capabilities().values().stream().filter(profile -> profile.implemented())
                        .forEach(profile -> actors.put(profile.npcId(), new Actor(profile.canRunPathing(),
                                NpcDataRegistry.schedules().containsKey(profile.npcId()))));
                server.tickRateManager().requestGameToSprint(WARMUP_TICKS + DAYS * DAY_TICKS + 100);
                StardewCraft.LOGGER.info("[NPC_SCHEDULE_AUDIT] started actors={} winter days={} normalClockRatio=1000ticks/hour", actors.size(), DAYS);
            }
            ticks++;
            int elapsed = Math.max(0, ticks - WARMUP_TICKS);
            int nextDay = 1 + elapsed / DAY_TICKS;
            if (nextDay > DAYS) { sample(); finish(); return; }
            if (nextDay != day) {
                sample();
                day = nextDay;
                com.stardew.craft.event.DimensionEventHandler.triggerAdvance(valley, 1560, "npc_schedule_audit");
                StardewTimeManager.get().setCurrentDay(day);
                NpcScheduleRuntimeService.clearExecutionState();
            }
            minute = 360 + (elapsed % DAY_TICKS) * 60 / 1000;
            StardewTimeManager.get().setCurrentTimeFromMC(minute);
            if (ticks >= WARMUP_TICKS) sample();
            if (ticks % 2000 == 0) {
                writeReport("running");
                StardewCraft.LOGGER.info("[NPC_SCHEDULE_AUDIT] day={} minute={} observed={}/{} issues={}",
                        day, minute, actors.values().stream().filter(actor -> actor.samples > 0).count(), actors.size(),
                        actors.values().stream().mapToInt(actor -> actor.issues.size()).sum());
            }
        }

        private void sample() {
            Map<String, List<StardewNpcEntity>> live = new TreeMap<>();
            for (ServerLevel level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
                if (entity instanceof StardewNpcEntity npc && npc.isAlive())
                    live.computeIfAbsent(npc.getNpcId(), ignored -> new ArrayList<>()).add(npc);
            }
            var states = NpcRuntimeDataManager.get(valley).states();
            actors.forEach((id, actor) -> {
                var entities = live.getOrDefault(id, List.of());
                actor.maxInstances = Math.max(actor.maxInstances, entities.size());
                NpcRuntimeState state = states.get(id);
                if (entities.isEmpty()) { actor.absentSamples++; return; }
                StardewNpcEntity npc = entities.getFirst();
                actor.samples++;
                actor.dimension = npc.level().dimension().location().toString();
                var snapshot = NpcCentralMovementService.getDebugSnapshot(id);
                String task = day + "/" + (state == null ? "no_state" : state.activeScheduleKey() + "/" + state.scheduleCheckpoint() + "/" + state.namedPointId());
                if (!task.equals(actor.task)) {
                    if (state != null && actor.pathing && actor.hasSchedule) {
                        for (var node : NpcScheduleRuntimeService.auditDayPlan(valley, id, state.activeScheduleKey())) {
                            if (node.time() <= 2600) actor.expected.putIfAbsent(
                                    day + "/" + state.activeScheduleKey() + "/" + node.time(), node.point());
                        }
                    }
                    actor.nodes.add(task);
                    actor.task = task;
                    actor.stillTicks = 0;
                    actor.localOrigin = npc.position();
                    actor.localSince = ticks;
                }
                if (npc.position().distanceToSqr(actor.localOrigin) >= 4) {
                    actor.localOrigin = npc.position(); actor.localSince = ticks;
                }
                if (npc.position().distanceToSqr(actor.lastPosition) < .0025) actor.stillTicks++;
                else actor.stillTicks = 0;
                actor.lastPosition = npc.position();
                actor.position = npc.position().toString();
                actor.stage = snapshot == null ? "no_snapshot" : snapshot.stage;
                if (entities.size() > 1) actor.issues.putIfAbsent(task + "/duplicate", "instances=" + entities.size());
                if (snapshot == null) return;
                boolean active = snapshot.pathIndex < snapshot.pathSize;
                // Standing at a completed stop is expected. Its dwell time must
                // not become "stuck time" when a later push restarts alignment.
                if (!active || !actor.wasActive) {
                    actor.stillTicks = 0;
                    actor.localOrigin = npc.position();
                    actor.localSince = ticks;
                }
                actor.wasActive = active;
                String detail = "day=" + day + " minute=" + minute + " pos=" + npc.position()
                        + " stage=" + snapshot.stage + " point=" + snapshot.pointId
                        + " path=" + snapshot.pathIndex + "/" + snapshot.pathSize + " target=" + snapshot.target
                        + " reason=" + snapshot.repathReason + " route=" + snapshot.routeDiagnosticReason
                        + " ground=" + npc.onGround() + " clear=" + npc.level().noBlockCollision(npc, npc.getBoundingBox())
                        + " navTarget=" + npc.getNavigation().getTargetPos()
                        + " nav=" + (npc.getNavigation().getPath() == null ? "null" : npc.getNavigation().getPath()
                            + " reach=" + npc.getNavigation().getPath().canReach()
                            + " done=" + npc.getNavigation().isDone()
                            + " end=" + npc.getNavigation().getPath().getEndNode())
                        + " next=" + snapshot.nextWaypoint
                        + " recognized=" + (state != null && NpcCentralMovementService.hasReachedScheduleTarget(valley, npc, state))
                        + " tracked=" + (NpcSpawnManager.getTrackedNpc(valley, id) == npc)
                        + " feet=" + npc.level().getBlockState(npc.blockPosition())
                        + " below=" + npc.level().getBlockState(npc.blockPosition().below())
                        + " speed=" + npc.getSpeed() + " input=" + npc.zza
                        + " entityTicks=" + npc.tickCount + " noAi=" + npc.isNoAi()
                        + " movement=" + npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                        + " control=" + npc.getMoveControl().hasWanted()
                        + " activity=" + npc.isNativeActivityMovementLocked()
                        + " waitSince=" + snapshot.destinationWaitSince + " now=" + npc.level().getGameTime()
                        + " policy=" + snapshot.arrivalPolicy + " plan=" + snapshot.planIdentity
                        + " stop=" + (npc.getNavigation() instanceof com.stardew.craft.entity.npc.NpcPathNavigation navigation
                            ? navigation.lastStopDiagnostic() : "");
                actor.lastDetail = detail;
                if (active && ticks - actor.localSince >= 400 && ticks % 20 == 0) {
                    var trace = actor.stallTrace.computeIfAbsent(task, ignored -> new ArrayList<>());
                    if (trace.size() < 12) {
                        var path = npc.getNavigation().getPath();
                        StringBuilder nodes = new StringBuilder();
                        if (path != null) for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 5); i++)
                            nodes.append(path.getEntityPosAtNode(npc, i)).append(" ");
                        trace.add(detail + " nodes=" + nodes);
                    }
                }
                if (active && (actor.stillTicks >= 400 || ticks - actor.localSince >= 600))
                    actor.issues.putIfAbsent(task + "/stalled", detail);
                if (state != null && !state.activeScheduleKey().isBlank() && actor.pathing
                        && ("no_route".equals(snapshot.stage) || "empty_plan".equals(snapshot.stage)
                        || "waiting_for_coordinates".equals(snapshot.stage)))
                    actor.issues.putIfAbsent(task + "/no_route", detail);
                if ("done_nearest_reachable".equals(snapshot.stage)) actor.nearest.putIfAbsent(task, detail);
                if ("done".equals(snapshot.stage) || "done_nearest_reachable".equals(snapshot.stage)
                        || "interaction_pause".equals(snapshot.stage) && state != null
                            && NpcCentralMovementService.hasReachedScheduleTarget(valley, npc, state)
                        || snapshot.stage.startsWith("square_")) {
                    actor.completed.putIfAbsent(task, detail);
                    if (state != null) actor.completedCheckpoints.add(day + "/" + state.activeScheduleKey() + "/" + state.scheduleCheckpoint());
                    if (state != null && npc.level() == valley) {
                        var target = NpcScheduleRuntimeService.resolveWorldTarget(valley, state, null);
                        if (target != null) {
                            var point = NpcSupportTarget.point(state.namedPointId());
                            if (point != null && point.has("arrival") && "exact_work".equals(point.get("arrival").getAsString())
                                    && npc.position().distanceToSqr(target.position()) > 1.0E-6D)
                                actor.issues.putIfAbsent(task + "/inexact_workpoint", detail);
                            var actualRegion = com.stardew.craft.interior.InteriorRegionRegistry.fixedInteriorIdAt(npc.blockPosition());
                            var targetRegion = com.stardew.craft.interior.InteriorRegionRegistry.fixedInteriorIdAt(net.minecraft.core.BlockPos.containing(target.position()));
                            if (!actualRegion.equals(targetRegion)) actor.issues.putIfAbsent(task + "/wrong_interior", detail);
                        }
                    }
                }
            });
        }

        private void finish() {
            finished = true;
            actors.forEach((id, actor) -> {
                actor.expected.forEach((checkpoint, point) -> {
                    if (!actor.completedCheckpoints.contains(checkpoint))
                        actor.issues.putIfAbsent(checkpoint + "/unfinished", "No confirmed arrival at " + point);
                });
                if (actor.samples == 0 && !"governor".equals(id))
                    actor.issues.put("not_observed", "Scheduled actor was absent for the entire audit");
                if (actor.pathing && actor.hasSchedule && actor.samples > 0 && actor.completed.isEmpty())
                    actor.issues.put("no_completed_nodes", actor.lastDetail);
            });
            writeReport("complete");
            StardewCraft.LOGGER.info("[NPC_SCHEDULE_AUDIT] COMPLETE observed={}/{} actorsWithIssues={}",
                    actors.values().stream().filter(actor -> actor.samples > 0).count(), actors.size(),
                    actors.values().stream().filter(actor -> !actor.issues.isEmpty()).count());
            server.halt(false);
        }

        private void writeReport(String status) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", status); report.put("season", "winter"); report.put("days", DAYS);
            report.put("day", day); report.put("minute", minute); report.put("ticks", ticks);
            report.put("elapsedSeconds", (System.nanoTime() - startedNanos) / 1e9);
            report.put("fatal", fatal);
            report.put("excluded", Map.of("governor", "Festival-only actor; winter 1-2 has no governor event"));
            report.put("actors", actors);
            Path path = Path.of(System.getProperty("stardewcraft.npcScheduleAuditReport", "npc-schedule-audit.json"));
            try {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (IOException exception) {
                StardewCraft.LOGGER.error("[NPC_SCHEDULE_AUDIT] Cannot write report", exception);
            }
        }
    }

    private static final class Actor {
        final boolean pathing, hasSchedule;
        int samples, absentSamples, maxInstances;
        transient int stillTicks, localSince;
        transient boolean wasActive;
        transient Vec3 localOrigin = Vec3.ZERO, lastPosition = Vec3.ZERO;
        String dimension = "", position = "", stage = "", task = "", lastDetail = "";
        final List<String> nodes = new ArrayList<>();
        final Map<String, List<String>> stallTrace = new LinkedHashMap<>();
        final Map<String, String> expected = new LinkedHashMap<>();
        final java.util.Set<String> completedCheckpoints = new java.util.LinkedHashSet<>();
        final Map<String, String> completed = new LinkedHashMap<>(), nearest = new LinkedHashMap<>(), issues = new LinkedHashMap<>();
        Actor(boolean pathing, boolean hasSchedule) { this.pathing = pathing; this.hasSchedule = hasSchedule; }
    }
}
