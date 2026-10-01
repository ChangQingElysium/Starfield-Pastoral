package com.stardew.craft.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.world.StardewWorldAnchor;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.*;
import com.stardew.craft.world.WorldAnchorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcRuntimeAuditGameTests {
    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
    private static Object call(Class<?> type,String name,Class<?>[] types,Object...args) throws ReflectiveOperationException {
        var m=type.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static Object field(Object value,String name) throws ReflectiveOperationException {
        var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private static Map<ResourceLocation,StardewWorldAnchor> anchors() {
        var result=new LinkedHashMap<ResourceLocation,StardewWorldAnchor>();
        for(var a:WorldAnchorRegistry.all())result.put(a.id(),a);
        return result;
    }
    private static void useTestDimensionAnchors(GameTestHelper h, Map<ResourceLocation,StardewWorldAnchor> original) {
        var local=new LinkedHashMap<ResourceLocation,StardewWorldAnchor>();
        for(var a:original.values()) local.put(a.id(),new StardewWorldAnchor(a.id(),h.getLevel().dimension().location(),
                a.position(),a.yaw(),a.indoor(),a.useGroundHeight(),a.locationId(),a.roles()));
        WorldAnchorRegistry.replaceLegacyNpcAnchors(local);
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void outdoorBuildingStopDoesNotRouteInside(GameTestHelper h) throws ReflectiveOperationException {
        var original=anchors();
        try {
            useTestDimensionAnchors(h,original);
            var state=new NpcRuntimeState("shane");state.setLocationName("animalshop");state.setNamedPointId("shane_animalshop_outside");
            var planner=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner");
            var result=call(planner,"resolveRoute",new Class<?>[]{ServerLevel.class,String.class,NpcRuntimeState.class,BlockPos.class},
                    h.getLevel(),"shane",state,new BlockPos(0,64,0));
            h.assertTrue(field(result,"status").toString().equals("READY"),"Outside stop did not resolve");
            var steps=(List<?>)field(result,"destinationSteps");
            for(var step:steps) h.assertTrue(!field(step,"mode").toString().equals("WARP"),
                    "Building label routed an explicit outdoor stop back indoors");
            var target=NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null);
            h.assertTrue(field(com.stardew.craft.port.PortJava.getLast(steps),"target").equals(target.position()),"Outdoor stop lost its exact destination");
        } finally {WorldAnchorRegistry.replaceLegacyNpcAnchors(original);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void legacyProfileDoesNotTreatEntryAsArrival(GameTestHelper h) throws ReflectiveOperationException {
        var original=anchors();
        try {
            useTestDimensionAnchors(h,original);
            var state=new NpcRuntimeState("abigail");state.setLocationName("seedshop");
            var planner=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner");
            var entry=NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null).position();
            var result=call(planner,"resolveRoute",new Class<?>[]{ServerLevel.class,String.class,NpcRuntimeState.class,BlockPos.class},
                    h.getLevel(),"abigail",state,BlockPos.containing(entry));
            var steps=(List<?>)field(result,"destinationSteps");
            h.assertTrue(!field(com.stardew.craft.port.PortJava.getLast(steps),"target").equals(entry),"Entrance position overrode the profile's actual stay target");
        } finally {WorldAnchorRegistry.replaceLegacyNpcAnchors(original);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void residentChunkSurvivesCorridorRelease(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();String id="residency_audit";
        var here=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,2,2)));var target=here.add(64,0,0);
        try {
            NpcChunkForceManager.ensureResidentChunkForced(level,id,here);
            NpcChunkForceManager.ensureRouteTargetChunkForced(level,id,target);
            NpcChunkForceManager.ensureRouteCorridorChunksForced(level,id,here,target);
            NpcChunkForceManager.releaseRouteCorridor(level,id);
            var levels=(Map<?,?>)field(null,NpcChunkForceManager.class,"LEVELS");
            var lease=((Map<?,?>)levels.get(level)).get(id);
            var held=(Set<?>)field(lease,"held");
            h.assertTrue(held.contains(field(lease,"resident")) && held.contains(field(lease,"target")),
                    "Pausing travel released the actor's own chunk while retaining only its distant target");
            h.assertTrue(!field(lease,"resident").equals(field(lease,"target")),"Fixture must use separate chunks");
        } finally {NpcChunkForceManager.releaseNpcForcedChunks(level,id);}
        h.succeed();
    }

    private static Object field(Object value,Class<?> type,String name) throws ReflectiveOperationException {
        var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void explicitClockChangesReplaceInFlightSchedule(GameTestHelper h) {
        scheduleTransition(h,"clock");
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void sameClockReloadReplacesDestinationAndBehavior(GameTestHelper h) {
        scheduleTransition(h,"reload");
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void nextDayDiscardsPreviousScheduleCursor(GameTestHelper h) {
        scheduleTransition(h,"day");
    }

    private static void scheduleTransition(GameTestHelper h,String change) {
        var clock=com.stardew.craft.time.StardewTimeManager.get();
        int time=clock.getCurrentTime(),day=clock.getCurrentDay(),season=clock.getCurrentSeason();
        var schedules=NpcDataRegistry.schedules();var runtime=NpcRuntimeDataManager.get(h.getLevel());
        var originalStates=new LinkedHashMap<>(runtime.states());
        try {
            // Replace, rather than mutate, existing states so fixtures cannot alter other tests.
            runtime.states().clear();
            clock.setCurrentDay(1);clock.setCurrentSeason(0);clock.setCurrentTime(360);
            var schedule=json("""
                    {"default":{"600":"Town @audit_home 2","900":"Town @audit_work 2 addon:work",
                    "1800":"Town @audit_evening 0 addon:rest"}}
                    """);
            NpcDataRegistry.replaceSchedules(Map.of("robin",schedule));NpcScheduleRuntimeService.clearExecutionState();
            NpcScheduleRuntimeService.tick(h.getLevel());
            h.assertTrue(runtime.states().get("robin").namedPointId().equals("audit_home"),"Morning fixture did not select home");
            clock.setCurrentTime(19*60);NpcScheduleRuntimeService.tick(h.getLevel());
            var state=runtime.states().get("robin");
            h.assertTrue(state.scheduleCheckpoint()==1800 && state.namedPointId().equals("audit_evening"),
                    "Explicit clock jump retained an unfinished morning destination");
            switch(change) {
                case "clock" -> {
                    clock.setCurrentTime(10*60);NpcScheduleRuntimeService.tick(h.getLevel());
                    h.assertTrue(state.scheduleCheckpoint()==900 && state.namedPointId().equals("audit_work")
                            && state.routeBehaviorToken().equals("addon:work"),"Backward clock mixed old action with new target");
                }
                case "reload" -> {
                    var replacement=schedule.deepCopy();
                    replacement.getAsJsonObject("default").addProperty("1800","Town @audit_relocated 1 addon:new_work");
                    NpcDataRegistry.replaceSchedules(Map.of("robin",replacement));
                    NpcScheduleRuntimeService.tick(h.getLevel());
                    h.assertTrue(state.namedPointId().equals("audit_relocated") && state.facing()==1
                            && state.routeBehaviorToken().equals("addon:new_work"),"Same-clock reload left stale destination, facing or behavior");
                }
                case "day" -> {
                    clock.setCurrentDay(2);clock.setCurrentTime(360);NpcScheduleRuntimeService.tick(h.getLevel());
                    h.assertTrue(state.scheduleCheckpoint()==600 && state.namedPointId().equals("audit_home")
                            && state.routeBehaviorToken().isEmpty(),"New day retained yesterday's evening action");
                }
                default -> throw new IllegalArgumentException(change);
            }
        } finally {
            NpcDataRegistry.replaceSchedules(schedules);runtime.states().clear();runtime.states().putAll(originalStates);
            clock.setCurrentDay(day);clock.setCurrentSeason(season);clock.setCurrentTime(time);NpcScheduleRuntimeService.clearExecutionState();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    @SuppressWarnings("unchecked")
    public static void obsoleteEntityUnloadCannotRemoveReplacement(GameTestHelper h) throws ReflectiveOperationException {
        String id="penny";var level=h.getLevel();
        var tracked=(Map<String,UUID>)field(null,NpcSpawnManager.class,"TRACKED_NPC_UUIDS");
        var forced=(Set<String>)field(null,NpcSpawnManager.class,"FORCE_SPAWN_IDS");
        var runtime=NpcRuntimeDataManager.get(level);var original=runtime.states().get(id);
        var previous=tracked.get(id);boolean wasForced=forced.contains(id);
        var oldNpc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);oldNpc.setNpcId(id);oldNpc.setOnGround(true);
        var replacement=UUID.randomUUID();var state=new NpcRuntimeState(id);
        var actual=new NpcRuntimeState.ActualPosition(level.dimension().location().toString(),3,65,7,0,1);
        state.rememberPosition(actual);
        try {
            runtime.states().put(id,state);tracked.put(id,replacement);forced.remove(id);
            oldNpc.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(replacement.equals(tracked.get(id)) && !forced.contains(id),"Old entity unload removed/requeued the replacement actor");
            h.assertTrue(actual.equals(state.actualPosition()),"Old entity overwrote the replacement's saved location");
        } finally {
            if(previous==null)tracked.remove(id);else tracked.put(id,previous);
            if(wasForced)forced.add(id);else forced.remove(id);
            if(original==null)runtime.states().remove(id);else runtime.states().put(id,original);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void suppressedActorUnloadDoesNotResurrectIt(GameTestHelper h) throws ReflectiveOperationException {
        unloadWithoutValleyRecovery(h,"penny",true);
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void miningActorUnloadDoesNotStartValleyRespawnLoop(GameTestHelper h) throws ReflectiveOperationException {
        unloadWithoutValleyRecovery(h,"dwarf",false);
    }

    @SuppressWarnings("unchecked")
    private static void unloadWithoutValleyRecovery(GameTestHelper h,String id,boolean suppress) throws ReflectiveOperationException {
        var tracked=(Map<String,UUID>)field(null,NpcSpawnManager.class,"TRACKED_NPC_UUIDS");
        var forced=(Set<String>)field(null,NpcSpawnManager.class,"FORCE_SPAWN_IDS");
        var suppressed=(Set<String>)field(null,NpcSpawnManager.class,"SUPPRESSED_SPAWN_IDS");
        var last=(Map<String,Long>)field(null,NpcSpawnManager.class,"LAST_SPAWN_GAME_TIME");
        var misses=(Map<String,Integer>)field(null,NpcSpawnManager.class,"TRACKED_MISS_COUNTS");
        var oldUuid=tracked.get(id);var oldLast=last.get(id);var oldMiss=misses.get(id);
        boolean wasForced=forced.contains(id),wasSuppressed=suppressed.contains(id);
        var runtime=NpcRuntimeDataManager.get(h.getLevel());var original=runtime.states().get(id);
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),h.getLevel());npc.setNpcId(id);npc.setOnGround(true);
        try {
            runtime.states().put(id,new NpcRuntimeState(id));tracked.put(id,npc.getUUID());forced.remove(id);
            if(suppress)suppressed.add(id);else suppressed.remove(id);
            npc.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(!tracked.containsKey(id) && !forced.contains(id),"Suppressed/mining actor entered ordinary valley recovery queue");
        } finally {
            if(oldUuid==null)tracked.remove(id);else tracked.put(id,oldUuid);
            if(oldLast==null)last.remove(id);else last.put(id,oldLast);
            if(oldMiss==null)misses.remove(id);else misses.put(id,oldMiss);
            if(wasForced)forced.add(id);else forced.remove(id);
            if(wasSuppressed)suppressed.add(id);else suppressed.remove(id);
            if(original==null)runtime.states().remove(id);else runtime.states().put(id,original);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void staleDayAndForeignDimensionPositionsCannotOverrideSchedule(GameTestHelper h) throws ReflectiveOperationException {
        var originalAnchors=anchors();var runtime=NpcRuntimeDataManager.get(h.getLevel());var original=runtime.states().get("caroline");
        var clock=com.stardew.craft.time.StardewTimeManager.get();
        try {
            useTestDimensionAnchors(h,originalAnchors);
            var state=new NpcRuntimeState("caroline");state.setNamedPointId("caroline_schedule_01");runtime.states().put("caroline",state);
            var target=NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null).position();
            for(var saved:List.of(
                    new NpcRuntimeState.ActualPosition(h.getLevel().dimension().location().toString(),1,65,3,0,clock.getAbsoluteDay()-1),
                    new NpcRuntimeState.ActualPosition("minecraft:the_nether",1,65,3,0,clock.getAbsoluteDay()))) {
                state.rememberPosition(saved);
                var restored=call(NpcSpawnManager.class,"resolveRuntimeScheduleSpawn",new Class<?>[]{ServerLevel.class,String.class},h.getLevel(),"caroline");
                h.assertTrue(target.equals(restored),"Stale day or foreign dimension displaced current schedule spawn");
            }
        } finally {
            WorldAnchorRegistry.replaceLegacyNpcAnchors(originalAnchors);
            if(original==null)runtime.states().remove("caroline");else runtime.states().put("caroline",original);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void movingResidentReleasesOldChunkWithoutTouchingOtherNpc(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();String first="lease_audit_first",second="lease_audit_second";
        var here=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,2,2)));var there=here.add(16,0,0);
        try {
            NpcChunkForceManager.ensureResidentChunkForced(level,first,here);
            NpcChunkForceManager.ensureResidentChunkForced(level,second,here);
            var levels=(Map<?,?>)field(null,NpcChunkForceManager.class,"LEVELS");var leases=(Map<?,?>)levels.get(level);
            var oldChunk=field(leases.get(first),"resident");
            NpcChunkForceManager.ensureResidentChunkForced(level,first,there);
            var firstLease=leases.get(first);var held=(Set<?>)field(firstLease,"held");
            h.assertTrue(held.size()==1 && !held.contains(oldChunk),"Walking across chunk boundary accumulates old resident tickets");
            NpcChunkForceManager.releaseNpcForcedChunks(level,first);
            h.assertTrue(!leases.containsKey(first) && ((Set<?>)field(leases.get(second),"held")).contains(oldChunk),
                    "Releasing one NPC removed another NPC's shared-chunk lease");
        } finally {
            NpcChunkForceManager.releaseNpcForcedChunks(level,first);NpcChunkForceManager.releaseNpcForcedChunks(level,second);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    @SuppressWarnings("unchecked")
    public static void confirmedUnloadSavesPositionAndRequestsImmediateRecovery(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();String id="penny";
        var tracked=(Map<String,UUID>)field(null,NpcSpawnManager.class,"TRACKED_NPC_UUIDS");
        var forced=(Set<String>)field(null,NpcSpawnManager.class,"FORCE_SPAWN_IDS");
        var last=(Map<String,Long>)field(null,NpcSpawnManager.class,"LAST_SPAWN_GAME_TIME");
        UUID oldUuid=tracked.get(id);Long oldSpawn=last.get(id);boolean wasForced=forced.contains(id);
        var runtime=NpcRuntimeDataManager.get(level);var oldState=runtime.states().get(id);
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);npc.setNpcId(id);
        npc.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3,2,3))));npc.setOnGround(true);
        try {
            runtime.states().put(id,new NpcRuntimeState(id));
            tracked.put(id,npc.getUUID());last.put(id,level.getGameTime());
            npc.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(!tracked.containsKey(id) && !last.containsKey(id) && forced.contains(id),
                    "Confirmed nonpersistent unload still waits for stale UUID or respawn cooldown");
            var actual=runtime.states().get(id).actualPosition();
            h.assertTrue(actual!=null && actual.x()==npc.getX() && actual.z()==npc.getZ(),"Unload lost the final physical location");
        } finally {
            if(oldUuid==null)tracked.remove(id);else tracked.put(id,oldUuid);
            if(oldSpawn==null)last.remove(id);else last.put(id,oldSpawn);
            if(!wasForced)forced.remove(id);
            if(oldState==null)runtime.states().remove(id);else runtime.states().put(id,oldState);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void retiredMapCoordinatesCannotOverrideCurrentScheduleSpawn(GameTestHelper h) throws ReflectiveOperationException {
        var original=anchors();var runtime=NpcRuntimeDataManager.get(h.getLevel());var old=runtime.states().get("caroline");
        try {
            useTestDimensionAnchors(h,original);
            var state=new NpcRuntimeState("caroline");state.setLocationName("seedshop");state.setNamedPointId("caroline_schedule_01");
            state.rememberPosition(new NpcRuntimeState.ActualPosition(h.getLevel().dimension().location().toString(),148.5,-13,122.5,0,
                    com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay()));
            runtime.states().put("caroline",state);
            Vec3 spawn=(Vec3)call(NpcSpawnManager.class,"resolveRuntimeScheduleSpawn",new Class<?>[]{ServerLevel.class,String.class},h.getLevel(),"caroline");
            var target=NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null).position();
            h.assertTrue(target.equals(spawn),"Same-day saved retired position overrode the current map");
            state.rememberPosition(new NpcRuntimeState.ActualPosition(h.getLevel().dimension().location().toString(),21.5,36,-22.5,0,
                    com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay()));
            spawn=(Vec3)call(NpcSpawnManager.class,"resolveRuntimeScheduleSpawn",new Class<?>[]{ServerLevel.class,String.class},h.getLevel(),"caroline");
            h.assertTrue(spawn.equals(new Vec3(21.5,36,-22.5)),"Ordinary in-flight recovery was teleported to its destination");
        } finally {
            WorldAnchorRegistry.replaceLegacyNpcAnchors(original);
            if(old==null)runtime.states().remove("caroline");else runtime.states().put("caroline",old);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void authoritativeRelocationPersistsBeforeNextEntityTick(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();var runtime=NpcRuntimeDataManager.get(level);var old=runtime.states().get("robin");
        var base=h.absolutePos(new BlockPos(2,2,2));level.setBlockAndUpdate(base.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);npc.setNpcId("robin");npc.setPos(0,64,0);npc.setOnGround(false);
        var state=new NpcRuntimeState("robin");var target=Vec3.atBottomCenterOf(base);
        try {
            runtime.states().put("robin",state);
            call(NpcSpawnManager.class,"moveNpcToScheduleTarget",new Class<?>[]{ServerLevel.class,StardewNpcEntity.class,Vec3.class,NpcRuntimeState.class},
                    level,npc,target,state);
            var actual=state.actualPosition();
            h.assertTrue(actual!=null && actual.x()==target.x && actual.y()==target.y && actual.z()==target.z,
                    "Immediate unload or shutdown would restore the pre-relocation position");
        } finally {if(old==null)runtime.states().remove("robin");else runtime.states().put("robin",old);}
        h.succeed();
    }

    private static Object route(ServerLevel level,NpcRuntimeState state) throws ReflectiveOperationException {
        return call(Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner"),"resolveRoute",
                new Class<?>[]{ServerLevel.class,String.class,NpcRuntimeState.class,BlockPos.class},level,"robin",state,new BlockPos(0,64,0));
    }
    private static void profileTargetCheck(GameTestHelper h,boolean foreign) throws ReflectiveOperationException {
        var events=NpcDataRegistry.events();var oldAnchors=anchors();
        try {
            var replacement=new LinkedHashMap<>(events);
            replacement.put("npc_route_points",json("{\"points\":{\"audit_profile_end\":{\"x\":0,\"y\":64,\"z\":0}}}"));
            replacement.put("npc_route_profiles",json("{\"profiles\":{\"robin\":{\"town\":[{\"point\":\"audit_profile_end\"}]}}}"));
            NpcDataRegistry.replaceEvents(replacement);
            if(foreign) {
                var a=new LinkedHashMap<>(oldAnchors);var id=new ResourceLocation("stardewcraft:audit_missing");
                a.put(id,new StardewWorldAnchor(id,new ResourceLocation("minecraft:the_nether"),new Vec3(1,64,1),0,false,false,null,Set.of()));
                WorldAnchorRegistry.replaceLegacyNpcAnchors(a);
            }
            var state=new NpcRuntimeState("robin");state.setNamedPointId("audit_missing");
            h.assertTrue(!field(route(h.getLevel(),state),"status").toString().equals("READY"),
                    foreign?"Profile bypasses anchor dimension and walks in the wrong world":"Missing explicit destination silently uses profile endpoint");
        } finally {NpcDataRegistry.replaceEvents(events);WorldAnchorRegistry.replaceLegacyNpcAnchors(oldAnchors);}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void missingNamedPointCannotFallBackToProfile(GameTestHelper h) throws ReflectiveOperationException {profileTargetCheck(h,false);}
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void profileCannotBypassAnchorDimension(GameTestHelper h) throws ReflectiveOperationException {profileTargetCheck(h,true);}

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void relocatedChairUsesRegisteredAnchor(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));var oldPos=pos.offset(4,0,0);
        var events=NpcDataRegistry.events();var oldAnchors=anchors();var block=level.getBlockState(pos);
        try {
            level.setBlockAndUpdate(pos,ModBlocks.STOOL.get().defaultBlockState());
            var point=new JsonObject();point.addProperty("x",oldPos.getX());point.addProperty("y",oldPos.getY());point.addProperty("z",oldPos.getZ());point.addProperty("furniture","chair");
            var points=new JsonObject();points.add("audit_moved_chair",point);var root=new JsonObject();root.add("points",points);
            var replacement=new LinkedHashMap<>(events);replacement.put("npc_route_points",root);NpcDataRegistry.replaceEvents(replacement);
            var a=new LinkedHashMap<>(oldAnchors);var id=new ResourceLocation("stardewcraft:audit_moved_chair");
            a.put(id,new StardewWorldAnchor(id,level.dimension().location(),Vec3.atBottomCenterOf(pos),0,true,false,null,Set.of()));
            WorldAnchorRegistry.replaceLegacyNpcAnchors(a);
            var support=NpcSupportTarget.resolve(level,"audit_moved_chair");
            h.assertTrue(support!=null && support.block().equals(pos),"Support reads obsolete JSON coordinates after anchor relocation");
            var state=new NpcRuntimeState("robin");state.setNamedPointId("audit_moved_chair");
            h.assertTrue(NpcScheduleRuntimeService.resolveWorldTarget(level,state,null).position().equals(support.approach()),"Movement and action disagree on chair approach");
        } finally {NpcDataRegistry.replaceEvents(events);WorldAnchorRegistry.replaceLegacyNpcAnchors(oldAnchors);level.setBlockAndUpdate(pos,block);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void removedScheduleDoesNotKeepOldAction(GameTestHelper h) {
        var schedules=NpcDataRegistry.schedules();var runtime=NpcRuntimeDataManager.get(h.getLevel());var states=new LinkedHashMap<>(runtime.states());
        try {
            var state=runtime.getOrCreate("robin");state.setNamedPointId("audit_stale_job");state.setRouteBehaviorToken("audit:work");
            NpcDataRegistry.replaceSchedules(Map.of());NpcScheduleRuntimeService.tick(h.getLevel());
            h.assertTrue(state.activeScheduleKey().isEmpty() && state.routeBehaviorToken().isEmpty() && state.namedPointId().isEmpty(),"Removed schedule keeps executing its previous job");
            h.assertTrue(NpcRuntimeState.fromNbt(state.toNbt()).activeScheduleKey().isEmpty(),"Save/load resurrects removed schedule");
        } finally {NpcDataRegistry.replaceSchedules(schedules);runtime.states().clear();runtime.states().putAll(states);NpcScheduleRuntimeService.clearExecutionState();}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void nearbyOutdoorDestinationSurvivesExitExpansion(GameTestHelper h) throws ReflectiveOperationException {
        var originalAnchors=anchors();
        try {
            var localAnchors=new LinkedHashMap<ResourceLocation,StardewWorldAnchor>();
            for(var a:originalAnchors.values()) localAnchors.put(a.id(),new StardewWorldAnchor(a.id(),h.getLevel().dimension().location(),a.position(),a.yaw(),a.indoor(),a.useGroundHeight(),a.locationId(),a.roles()));
            WorldAnchorRegistry.replaceLegacyNpcAnchors(localAnchors);
        var planner=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner");
        var indoor=(Vec3)call(planner,"indoorExitForLocation",new Class<?>[]{String.class},"sciencehouse");
        var outdoor=(Vec3)call(planner,"outdoorExitForLocation",new Class<?>[]{String.class},"sciencehouse");
        h.assertTrue(indoor!=null && outdoor!=null,"Shipped sciencehouse portal missing");
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),h.getLevel());npc.setNpcId("robin");npc.setPos(indoor);
        var stepType=Class.forName(planner.getName()+"$NpcRouteStep");var routeType=Class.forName(planner.getName()+"$NpcRouteContext");
        var target=outdoor.add(1,0,0);var step=call(stepType,"walk",new Class<?>[]{String.class,Vec3.class},"audit_doorstep_job",target);
        var route=call(routeType,"ready",new Class<?>[]{String.class,List.class},"town",List.of(step));
        var plan=call(NpcCentralMovementService.class,"buildPlan",new Class<?>[]{ServerLevel.class,StardewNpcEntity.class,routeType,String.class,long.class},h.getLevel(),npc,route,"audit_exit",h.getLevel().getGameTime());
        var steps=(List<?>)field(plan,"steps");
        h.assertTrue(steps.size()==3 && field(com.stardew.craft.port.PortJava.getLast(steps),"target").equals(target),"Exit expansion deletes a real destination within two blocks of door");
        } finally {WorldAnchorRegistry.replaceLegacyNpcAnchors(originalAnchors);}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    @SuppressWarnings("unchecked")
    public static void scheduleUsesConfiguredArrivalRadius(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();var clock=com.stardew.craft.time.StardewTimeManager.get();int time=clock.getCurrentTime();
        var events=NpcDataRegistry.events();var schedules=NpcDataRegistry.schedules();
        var runtime=NpcRuntimeDataManager.get(level);var states=new LinkedHashMap<>(runtime.states());
        var trackedField=NpcSpawnManager.class.getDeclaredField("TRACKED_NPC_UUIDS");trackedField.setAccessible(true);
        var tracked=(Map<String,UUID>)trackedField.get(null);var oldTracked=new HashMap<>(tracked);
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);npc.setNpcId("robin");
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,4))));npc.setOnGround(true);
        try {
            level.addFreshEntity(npc);tracked.put("robin",npc.getUUID());
            var point=new JsonObject();point.addProperty("x",npc.getX()+.8);point.addProperty("y",npc.getY());point.addProperty("z",npc.getZ());
            var points=new JsonObject();points.add("audit_arrival",point);var root=new JsonObject();root.add("points",points);
            var replacement=new LinkedHashMap<>(events);replacement.put("npc_route_points",root);
            replacement.put("npc_runtime",json("{\"navigation\":{\"arrival_radius\":0.9}}"));
            replacement.put("npc_route_profiles",json("{\"profiles\":{}}"));NpcDataRegistry.replaceEvents(replacement);
            var schedule=json("{\"spring\":{\"600\":\"Town @audit_arrival 2\",\"900\":\"Town @audit_next 2\"}}");
            NpcDataRegistry.replaceSchedules(Map.of("robin",schedule));clock.setCurrentTime(360);NpcScheduleRuntimeService.clearExecutionState();
            NpcScheduleRuntimeService.tick(level);
            h.assertTrue(NpcNavigationPolicy.current().arrived(.8,0,0,NpcNavigationPolicy.current().arrivalRadius()),"Fixture must be inside configured movement arrival radius");
            var selected=call(NpcScheduleRuntimeService.class,"resolveActiveNode",new Class<?>[]{ServerLevel.class,String.class,JsonObject.class,int.class,com.stardew.craft.time.StardewTimeManager.class,String.class},level,"robin",schedule,900,clock,"sun");
            h.assertTrue(((Integer)field(selected,"checkpoint"))==900,"Movement has arrived but schedule is stuck on a different hard-coded radius");
        } finally {
            npc.discard();tracked.clear();tracked.putAll(oldTracked);NpcDataRegistry.replaceEvents(events);NpcDataRegistry.replaceSchedules(schedules);
            runtime.states().clear();runtime.states().putAll(states);clock.setCurrentTime(time);NpcScheduleRuntimeService.clearExecutionState();
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void finalApproachDoesNotWalkAcrossHole(GameTestHelper h) throws ReflectiveOperationException {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(2,2,2));
        for(int x=-1;x<=4;x++)for(int z=-1;z<=4;z++) {
            level.setBlockAndUpdate(base.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(int y=0;y<3;y++)level.setBlockAndUpdate(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(1,-1,1),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);npc.setNpcId("robin");
        npc.setPos(base.getX()+.8,base.getY(),base.getZ()+1.5);npc.setOnGround(true);
        var target=npc.position().add(1.4,0,0);
        var stepType=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner$NpcRouteStep");
        var step=call(stepType,"walk",new Class<?>[]{String.class,Vec3.class},"audit_gap",target);
        var planType=Class.forName("com.stardew.craft.npc.runtime.NpcCentralMovementService$NpcRoutePlan");
        var constructor=planType.getDeclaredConstructor(String.class,UUID.class,List.class,long.class);constructor.setAccessible(true);
        var plan=constructor.newInstance("audit_gap",npc.getUUID(),List.of(step),level.getGameTime());
        call(NpcCentralMovementService.class,"executePlanTick",new Class<?>[]{ServerLevel.class,StardewNpcEntity.class,planType},level,npc,plan);
        h.assertTrue(!field(plan,"debugStage").equals("precise_approach"),"Final approach bypasses pathfinding and walks straight into a hole");
        NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
        h.succeed();
    }

}
