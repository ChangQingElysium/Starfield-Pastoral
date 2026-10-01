package com.stardew.craft.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.animation.NpcScheduleActivity;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcExecutionRepairGameTests {
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void activityExitRetainsBothInteractionRequests(GameTestHelper h) {
        var npc=actor(h,h.absolutePos(new BlockPos(3,2,3)));
        var activity=new NpcScheduleActivity(npc);var calls=new java.util.ArrayList<Integer>();
        activity.interrupt(()->calls.add(1));activity.interrupt(()->calls.add(2));activity.tick();
        h.assertTrue(calls.equals(List.of(1,2)),"Second player's request was lost while waiting for activity exit");
        activity.cancel();h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void turningRetainsSecondPlayerWhenFirstLeaves(GameTestHelper h) throws ReflectiveOperationException {
        var npc=actor(h,h.absolutePos(new BlockPos(3,2,3)));var level=h.getLevel();
        var first=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FirstObserver"));
        var second=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SecondObserver"));
        first.setPos(npc.position().add(1,0,0));second.setPos(npc.position().add(0,0,1));
        var calls=new java.util.ArrayList<Integer>();
        npc.facePlayerTemporarily(first,20,()->calls.add(1));npc.facePlayerTemporarily(second,20,()->calls.add(2));
        first.setPos(npc.position().add(100,0,0));
        var field=StardewNpcEntity.class.getDeclaredField("facingOnComplete");field.setAccessible(true);
        ((Runnable)field.get(npc)).run();
        h.assertTrue(calls.equals(List.of(2)),"Leaving initiator drops or incorrectly dispatches queued interactions");
        npc.prepareForNpcRelocation();h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void malformedReloadKeepsPublishedRevision(GameTestHelper h) throws ReflectiveOperationException {
        var listener=new com.stardew.craft.npc.data.NpcDataManager.ReloadListener();
        var apply=listener.getClass().getDeclaredMethod("apply",Map.class,net.minecraft.server.packs.resources.ResourceManager.class,net.minecraft.util.profiling.ProfilerFiller.class);
        apply.setAccessible(true);
        long revision=NpcDataRegistry.revision();
        var anchors=List.copyOf(com.stardew.craft.world.WorldAnchorRegistry.all());
        var invalid=Map.of(
                "events/location_graph",json("{\"event_id\":\"location_graph\",\"edges\":[{\"from\":{},\"to\":\"town\"}]}"),
                "events/npc_route_points",json("{\"event_id\":\"npc_route_points\",\"points\":{\"broken\":{\"origin_offset\":[0,0]}}}"),
                "location_mappings/broken",json("{\"anchors\":{\"town\":{\"x\":\"NaN\"}}}"));
        for(var entry:invalid.entrySet()) {
            apply.invoke(listener,Map.of(new net.minecraft.resources.ResourceLocation("stardewcraft",entry.getKey()),entry.getValue()),h.getLevel().getServer().getResourceManager(),net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            h.assertTrue(NpcDataRegistry.revision()==revision,"Rejected data changed the published NPC revision");
            h.assertTrue(List.copyOf(com.stardew.craft.world.WorldAnchorRegistry.all()).equals(anchors),"Rejected data changed world anchors");
        }
        apply.invoke(listener,Map.of(new net.minecraft.resources.ResourceLocation("stardewcraft:events/broken_root"),new com.google.gson.JsonArray()),h.getLevel().getServer().getResourceManager(),net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        h.assertTrue(NpcDataRegistry.revision()==revision,"Invalid root silently publishes an empty definition set");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void missingFurnitureWaitsWithoutInventingApproach(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(3,2,3));var npc=actor(h,base);var events=NpcDataRegistry.events();
        try {
            var changed=pointEvents("repair_missing_chair",npc.position(),events);
            changed.get("npc_route_points").getAsJsonObject("points").getAsJsonObject("repair_missing_chair").addProperty("furniture","chair");
            NpcDataRegistry.replaceEvents(changed);
            h.assertTrue(NpcSupportTarget.routePosition(h.getLevel(),"repair_missing_chair",npc.position())==null,"Missing furniture invents a world-direction approach");
            h.assertTrue(NpcSupportTarget.pendingFurniturePosition(h.getLevel(),"repair_missing_chair").equals(npc.position()),"Waiting cannot retain the chunk containing the missing furniture");
        } finally {NpcDataRegistry.replaceEvents(events);}
        h.succeed();
    }
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static StardewNpcEntity actor(GameTestHelper h,BlockPos base) {
        for(int x=-2;x<=4;x++)for(int z=-2;z<=4;z++) {
            h.getLevel().setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),h.getLevel());
        npc.setNpcId("sam");npc.setPos(Vec3.atBottomCenterOf(base));npc.setOnGround(true);return npc;
    }
    private static Map<String,JsonObject> pointEvents(String id,Vec3 target,Map<String,JsonObject> original) {
        var point=new JsonObject();point.addProperty("x",target.x);point.addProperty("y",target.y);point.addProperty("z",target.z);
        var points=new JsonObject();points.add(id,point);var root=new JsonObject();root.add("points",points);
        var events=new LinkedHashMap<>(original);events.put("npc_route_points",root);
        events.put("npc_runtime",json("{\"navigation\":{\"arrival_radius\":1.0}}"));return events;
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void activityStartsAfterArrivalWithConfiguredLargeRadius(GameTestHelper h) {
        var npc=actor(h,h.absolutePos(new BlockPos(3,2,3)));var events=NpcDataRegistry.events();
        var runtime=NpcRuntimeDataManager.get(h.getLevel());var old=runtime.states().get("sam");
        try {
            Vec3 target=npc.position().add(.8,0,0);
            NpcDataRegistry.replaceEvents(pointEvents("repair_activity",target,events));
            var state=new NpcRuntimeState("sam");state.setNamedPointId("repair_activity");state.setRouteBehaviorToken("sam_guitar");state.setFacing(2);
            runtime.states().put("sam",state);
            var activity=new NpcScheduleActivity(npc);
            for(int i=0;i<60 && npc.getScheduleActivityEvent().isEmpty();i++)activity.tick();
            h.assertTrue(!npc.getScheduleActivityEvent().isEmpty(),"Movement arrived at 0.8 but activity never aligned/started");
            h.assertTrue(npc.position().distanceToSqr(target)<.0001,"Action starts away from its target");
            activity.cancel();
        } finally {NpcDataRegistry.replaceEvents(events);if(old==null)runtime.states().remove("sam");else runtime.states().put("sam",old);NpcExecutionCoordinator.cancel(npc);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void activityAlignmentCannotCrossUnsupportedFloor(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(3,2,3));var npc=actor(h,base);var events=NpcDataRegistry.events();
        try {
            NpcDataRegistry.replaceEvents(pointEvents("repair_gap",npc.position(),events));
            h.getLevel().setBlockAndUpdate(base.offset(1,-1,0),Blocks.AIR.defaultBlockState());
            var before=npc.position();var target=before.add(.8,0,0);
            h.assertTrue(!NpcCentralMovementService.alignActivity(h.getLevel(),npc,target),"Activity alignment ignores missing floor");
            h.assertTrue(npc.position().equals(before),"Unsafe alignment moved the actor");
        } finally {NpcDataRegistry.replaceEvents(events);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void externalTakeoverStopsPreviousNavigation(GameTestHelper h) throws ReflectiveOperationException {
        var base=h.absolutePos(new BlockPos(3,2,3));var npc=actor(h,base);
        var node=new net.minecraft.world.level.pathfinder.Node(base.getX()+3,base.getY(),base.getZ());
        var path=new net.minecraft.world.level.pathfinder.Path(List.of(node),base.offset(3,0,0),true);
        NpcExecutionCoordinator.claim(npc,NpcExecutionCoordinator.SCHEDULE,0,20);
        npc.getNavigation().moveTo(path,1);npc.getMoveControl().setWantedPosition(base.getX()+3,base.getY(),base.getZ(),1);
        npc.setDeltaMovement(.2,0,0);
        h.assertTrue(!npc.getNavigation().isDone(),"Fixture requires an active old navigation path");
        var reader=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DialogueReader"));
        var begin=NpcInteractionService.class.getDeclaredMethod("beginDialogueSession",net.minecraft.server.level.ServerPlayer.class,String.class);begin.setAccessible(true);
        begin.invoke(null,reader,npc.getNpcId());
        h.assertTrue(NpcInteractionService.isDialogueMovementLocked(npc.getNpcId()),"Fixture requires an old dialogue lock");
        long lease=NpcExecutionCoordinator.claim(npc,"example:scene",90,20);
        h.assertTrue(!NpcInteractionService.isDialogueMovementLocked(npc.getNpcId()),"Takeover leaves an orphan dialogue lock");
        h.assertTrue(lease>=0 && npc.getNavigation().isDone() && npc.getDeltaMovement().horizontalDistanceSqr()==0,"Takeover leaves the old movement running");
        h.assertTrue(NpcExecutionCoordinator.owns(npc,"example:scene",lease),"Cancellation revokes the new lease");
        NpcExecutionCoordinator.cancel(npc);h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void groundHeightUsesSlabSurfaceAndPreservesExactFloor(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(3,2,3));actor(h,base);var events=NpcDataRegistry.events();
        try {
            // Remove the test template roof: this case represents an outdoor column.
            int roof=h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,base.getX(),base.getZ());
            for(int y=base.getY()+1;y<=roof;y++) h.getLevel().setBlockAndUpdate(new BlockPos(base.getX(),y,base.getZ()),Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(base,Blocks.STONE_SLAB.defaultBlockState());
            Vec3 raw=Vec3.atBottomCenterOf(base).add(0,5,0);
            var changed=pointEvents("repair_ground",raw,events);
            var point=changed.get("npc_route_points").getAsJsonObject("points").getAsJsonObject("repair_ground");point.addProperty("use_ground_height",true);
            NpcDataRegistry.replaceEvents(changed);
            var state=new NpcRuntimeState("sam");state.setNamedPointId("repair_ground");
            var resolved=NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null);
            h.assertTrue(resolved!=null && Math.abs(resolved.position().y-(base.getY()+.5))<.00001,"Ground target ignores slab: resolved="+resolved+" expected="+(base.getY()+.5)+" columnTop="+h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,base.getX(),base.getZ()));
            point.addProperty("indoor",true);NpcDataRegistry.replaceEvents(changed);
            h.assertTrue(NpcScheduleRuntimeService.resolveWorldTarget(h.getLevel(),state,null).position().y==raw.y,"Indoor exact floor was replaced by outdoor heightmap");
        } finally {NpcDataRegistry.replaceEvents(events);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void profileIntermediatePointCannotCrossDimension(GameTestHelper h) throws ReflectiveOperationException {
        var events=NpcDataRegistry.events();
        try {
            var changed=pointEvents("repair_final",Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3,2,3))),events);
            changed.get("npc_route_points").getAsJsonObject("points").add("repair_foreign",json("{\"x\":0,\"y\":64,\"z\":0,\"dimension\":\"minecraft:the_nether\"}"));
            changed.put("npc_route_profiles",json("{\"profiles\":{\"sam\":{\"town\":[{\"point\":\"repair_foreign\"},{\"point\":\"repair_final\"}]}}}"));
            NpcDataRegistry.replaceEvents(changed);
            var state=new NpcRuntimeState("sam");state.setNamedPointId("repair_final");
            var planner=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner");
            var method=planner.getDeclaredMethod("resolveRoute",net.minecraft.server.level.ServerLevel.class,String.class,NpcRuntimeState.class,BlockPos.class);method.setAccessible(true);
            var route=method.invoke(null,h.getLevel(),"sam",state,h.absolutePos(BlockPos.ZERO));
            var ready=route.getClass().getDeclaredMethod("ready");ready.setAccessible(true);
            h.assertTrue(!((Boolean)ready.invoke(route)),"Foreign intermediate point became current-world coordinates");
        } finally {NpcDataRegistry.replaceEvents(events);}
        h.succeed();
    }
}
