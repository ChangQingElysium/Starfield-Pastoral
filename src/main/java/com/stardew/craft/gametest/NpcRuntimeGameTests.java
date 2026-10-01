package com.stardew.craft.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.npc.StardewNpcExecution;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcRuntimeGameTests {
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void explicitTimeEditReselectsScheduleWithoutArrival(GameTestHelper helper) {
        var level = helper.getLevel();
        var clock = com.stardew.craft.time.StardewTimeManager.get();
        int oldTime = clock.getCurrentTime();
        var oldSchedules = NpcDataRegistry.schedules();
        var runtime = NpcRuntimeDataManager.get(level);
        var oldStates = new LinkedHashMap<>(runtime.states());
        try {
            NpcDataRegistry.replaceSchedules(Map.of("robin", JsonParser.parseString("""
                {"spring":{"600":"Town @test_morning 2","900":"Town @test_work 2","1200":"Town @test_lunch 2"},
                 "summer":{"_goto":"spring"},"fall":{"_goto":"spring"},"winter":{"_goto":"spring"}}
                """).getAsJsonObject()));
            NpcScheduleRuntimeService.clearExecutionState();
            clock.setCurrentTime(360);
            NpcScheduleRuntimeService.tick(level);
            var state = runtime.states().get("robin");
            helper.assertTrue(state.scheduleCheckpoint()==600, "Morning checkpoint missing");
            // Explicit ten-minute edit: catches jumps too small for a gap heuristic.
            clock.setCurrentTime(530);
            NpcScheduleRuntimeService.tick(level);
            helper.assertTrue(state.scheduleCheckpoint()==600, "Premature work checkpoint");
            clock.setCurrentTime(540);
            NpcScheduleRuntimeService.tick(level);
            helper.assertTrue(state.scheduleCheckpoint()==900, "Time edit is blocked by an unarrived morning route");
            clock.setCurrentTime(780);
            NpcScheduleRuntimeService.tick(level);
            helper.assertTrue(state.scheduleCheckpoint()==1200, "Forward time edit replays obsolete work stops");
            clock.setCurrentTime(360);
            NpcScheduleRuntimeService.tick(level);
            helper.assertTrue(state.scheduleCheckpoint()==600, "Backward edit kept a future destination");
            clock.setCurrentTimeFromMC(361);
            long revision = clock.getClockRevision();
            clock.setCurrentTimeFromMC(362);
            helper.assertTrue(clock.getClockRevision()==revision, "Ordinary clock progress resets in-flight tasks");
        } finally {
            NpcDataRegistry.replaceSchedules(oldSchedules);
            runtime.states().clear(); runtime.states().putAll(oldStates);
            clock.setCurrentTime(oldTime);
            NpcScheduleRuntimeService.clearExecutionState();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void serverRejectsForgedAndReplayedAnswers(GameTestHelper helper) {
        var level=helper.getLevel();
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NpcQuestionTest"),
                com.stardew.craft.port.net.minecraft.server.level.ClientInformation.createDefault());
        NpcQuestionAuthority.open(player,"abigail","stardewcraft.npc.abigail.fall_sun");
        helper.assertTrue(NpcQuestionAuthority.consume(player,"abigail","27",99999,"Sun_27")==null,"Forged friendship delta accepted");
        helper.assertTrue(NpcQuestionAuthority.consume(player,"wizard","27",10,"Sun_27")==null,"Cross-NPC answer accepted");
        helper.assertTrue(NpcQuestionAuthority.consume(player,"abigail","27",10,"teleport_home")==null,"Forged next node accepted");
        helper.assertTrue(NpcQuestionAuthority.consume(player,"abigail","27",10,"Sun_27")!=null,"Legitimate translated choice rejected");
        helper.assertTrue(NpcQuestionAuthority.consume(player,"abigail","27",10,"Sun_27")==null,"Answer replay accepted");
        NpcQuestionAuthority.open(player,"abigail","$q 1 pick#Pick#$r 1 5 next#Yes");
        NpcQuestionAuthority.close(player.getUUID());
        helper.assertTrue(NpcQuestionAuthority.consume(player,"abigail","1",5,"next")==null,"Closed session remains valid");
        helper.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void registeredFurnitureAnchorEndsAtApproach(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(4,2,4));
        var oldEvents=NpcDataRegistry.events();
        var oldAnchors=new LinkedHashMap<ResourceLocation,StardewWorldAnchor>();
        for(var anchor:WorldAnchorRegistry.all()) oldAnchors.put(anchor.id(),anchor);
        var oldBlock=level.getBlockState(pos);
        String id="npc_test_chair";
        try {
            level.setBlockAndUpdate(pos,ModBlocks.STOOL.get().defaultBlockState());
            JsonObject points=new JsonObject(); var point=new JsonObject();
            point.addProperty("x",pos.getX());point.addProperty("y",pos.getY());point.addProperty("z",pos.getZ());point.addProperty("furniture","chair");
            points.add(id,point); var root=new JsonObject();root.add("points",points);
            var events=new LinkedHashMap<>(oldEvents);events.put("npc_route_points",root);NpcDataRegistry.replaceEvents(events);
            var anchorId=new ResourceLocation("stardewcraft:"+id);
            var anchors=new LinkedHashMap<>(oldAnchors);
            anchors.put(anchorId,new StardewWorldAnchor(anchorId,level.dimension().location(),Vec3.atBottomCenterOf(pos),0,true,false,null,Set.of()));
            WorldAnchorRegistry.replaceLegacyNpcAnchors(anchors);
            var state=new NpcRuntimeState("sam");state.setNamedPointId(id);
            var target=NpcScheduleRuntimeService.resolveWorldTarget(level,state,null);
            var support=NpcSupportTarget.resolve(level,id);
            helper.assertTrue(support!=null && target.position().distanceToSqr(support.approach())<1e-9,"Registered anchor bypassed the furniture approach");
            helper.assertTrue(target.position().distanceToSqr(support.origin())>.6,"Navigation still ends inside furniture");
            var contract=new StardewNpcExecution.Support(support.block(),support.origin(),support.approach(),support.yaw(),support.approachYaw());
            helper.assertTrue(contract.approach().equals(target.position()),"Addon support uses a different coordinate contract");
            level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.assertTrue(NpcSupportTarget.resolve(level,id)==null,"Removed furniture still resolves");
        } finally {
            NpcDataRegistry.replaceEvents(oldEvents);WorldAnchorRegistry.replaceLegacyNpcAnchors(oldAnchors);level.setBlockAndUpdate(pos,oldBlock);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void reloadSyncsActorPhysicsAndControlRevokesActivity(GameTestHelper helper) {
        var events=NpcDataRegistry.events();
        try {
            var replacement=new LinkedHashMap<>(events);
            replacement.put("npc_runtime",JsonParser.parseString("{\"actors\":{\"sam\":{\"width\":0.45,\"height\":1.3,\"eye_height\":1.1,\"speed\":0.18,\"attention\":true}}}").getAsJsonObject());
            NpcDataRegistry.replaceEvents(replacement);
            var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),helper.getLevel()); npc.setNoAi(true); npc.setNpcId("sam");
            helper.assertTrue(Math.abs(npc.getBbHeight()-1.3)<.001,"Authored height not applied to collision box");
            helper.assertTrue(Math.abs(npc.getEyeHeight()-1.1)<.001,"Authored eye height not applied");
            var observer=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),helper.getLevel());
            observer.getEntityData().assignValues(npc.getEntityData().getNonDefaultValues());
            helper.assertTrue(Math.abs(observer.getBbWidth()-.45)<.001 && observer.usesNativeAttention(),"Remote observer did not receive motion metadata");
            var action=new net.minecraft.nbt.CompoundTag();action.putString("action","sam_sweep");action.putLong("start",helper.getLevel().getGameTime());
            npc.setScheduleActivityEvent(action);
            long lease=NpcExecutionCoordinator.claim(npc,"example:scene",90,20);
            npc.tick();
            helper.assertTrue(npc.getScheduleActivityEvent().isEmpty(),"Schedule activity survived external control");
            helper.assertTrue(NpcExecutionCoordinator.owns(npc,"example:scene",lease),"Activity cleanup revoked another controller's lease");
            npc.prepareForNpcRelocation();
            helper.assertTrue(!NpcExecutionCoordinator.owns(npc,"example:scene",lease),"Relocation did not revoke stale work");
            var replacementNpc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),helper.getLevel());replacementNpc.setNpcId("sam");
            long replacementLease=NpcExecutionCoordinator.claim(replacementNpc,"example:scene",90,20);
            npc.prepareForNpcRelocation();
            helper.assertTrue(NpcExecutionCoordinator.owns(replacementNpc,"example:scene",replacementLease),"Old entity cleanup revoked its replacement's work");
            replacementNpc.prepareForNpcRelocation();
        } finally { NpcDataRegistry.replaceEvents(events); }
        helper.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void npcTicketsDoNotClearVanillaForceload(GameTestHelper helper) {
        var level=helper.getLevel();var position=Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4,1,4)));
        var chunk=new net.minecraft.world.level.ChunkPos(BlockPos.containing(position));
        boolean originallyForced=level.getForcedChunks().contains(chunk.toLong());
        try {
            level.setChunkForced(chunk.x,chunk.z,true);
            NpcChunkForceManager.ensureRouteTargetChunkForced(level,"test_ticket_a",position);
            NpcChunkForceManager.ensureRouteTargetChunkForced(level,"test_ticket_b",position);
            NpcChunkForceManager.releaseNpcForcedChunks(level,"test_ticket_a");
            helper.assertTrue(!NpcChunkForceManager.currentForcedTargetChunk(level,"test_ticket_b").equals("<none>"),"One NPC released another's ticket");
            NpcChunkForceManager.releaseNpcForcedChunks(level,"test_ticket_b");
            helper.assertTrue(level.getForcedChunks().contains(chunk.toLong()),"NPC cleanup removed vanilla forceload");
        } finally {
            NpcChunkForceManager.releaseNpcForcedChunks(level,"test_ticket_a");NpcChunkForceManager.releaseNpcForcedChunks(level,"test_ticket_b");
            if (!originallyForced) level.setChunkForced(chunk.x,chunk.z,false);
        }
        helper.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=300)
    public static void authoredRouteWalksThroughDoorAndUpStepWithoutTeleport(GameTestHelper helper) {
        var level=helper.getLevel();
        var base=helper.absolutePos(new BlockPos(1,2,1));
        for(int x=0;x<12;x++) for(int z=0;z<9;z++) {
            level.setBlockAndUpdate(base.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(int y=0;y<4;y++) level.setBlockAndUpdate(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            if(x>=8) level.setBlockAndUpdate(base.offset(x,0,z),net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState());
        }
        for(int z=0;z<9;z++) if(z!=4) for(int y=0;y<3;y++)
            level.setBlockAndUpdate(base.offset(6,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var doorPos=base.offset(6,0,4);
        var door=net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING,net.minecraft.core.Direction.EAST);
        level.setBlockAndUpdate(doorPos,door);
        level.setBlockAndUpdate(doorPos.above(),door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);
        npc.setNpcId("sam"); npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(2,0,4))); npc.setOnGround(true);
        var target=Vec3.atBottomCenterOf(base.offset(10,0,4)).add(0,.5,0);
        boolean[] opened={false};
        boolean[] closedAfterPass={false};
        helper.onEachTick(()->{
            var before=npc.position(); level.tickNonPassenger(npc);
            if(npc.tickCount>=290) helper.fail("Route stalled: pos="+npc.position()+" ground="+npc.onGround()
                    +" navigation="+npc.getNavigation().getPath()+" done="+npc.getNavigation().isDone()
                    +" owner="+NpcExecutionCoordinator.owner(npc)+" removed="+npc.isRemoved()+" gameTime="+level.getGameTime());
            boolean done=NpcCentralMovementService.tickAuthoredWalkTarget(level,npc,"npc_regression","door_step",target);
            boolean doorOpen=level.getBlockState(doorPos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
            if(opened[0] && !doorOpen) {
                helper.assertTrue(npc.getX()>doorPos.getX()+1,"Door closed before the NPC passed through it");
                closedAfterPass[0]=true;
            }
            helper.assertTrue(!closedAfterPass[0] || !doorOpen,"Passed door reopened repeatedly");
            opened[0]|=doorOpen;
            helper.assertTrue(npc.position().distanceToSqr(before)<1,"Walking route teleported instead of navigating");
            if(done) {
                helper.assertTrue(opened[0],"Route did not pass through the authored doorway");
                helper.assertTrue(Math.abs(npc.getY()-target.y)<.1,"Navigation completed on the wrong height");
                NpcCentralMovementService.resetAuthoredMovementPlan("sam","npc_regression");
                helper.assertTrue(!level.getBlockState(doorPos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                        "Door remained open after finishing the route");
                NpcChunkForceManager.releaseNpcForcedChunks(level,"sam");
                NpcExecutionCoordinator.cancel(npc);
                helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void dataDeclaredActivityAndAddonLeaseWorkForAnotherActor(GameTestHelper helper) {
        var level=helper.getLevel(); var events=NpcDataRegistry.events();
        var runtime=NpcRuntimeDataManager.get(level); var previous=runtime.states().get("sebastian");
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level); npc.setNpcId("sebastian"); npc.setNoAi(true);
        var point=helper.absolutePos(new BlockPos(4,2,4)); npc.setPos(Vec3.atBottomCenterOf(point)); npc.setOnGround(true);
        npc.setYRot(0);npc.setYBodyRot(0);npc.setYHeadRot(0);
        var controller=new com.stardew.craft.npc.animation.NpcScheduleActivity(npc);
        try {
            var replacement=new LinkedHashMap<>(events);
            replacement.put("npc_route_points",JsonParser.parseString("{\"points\":{\"npc_test_read\":{\"x\":"+point.getX()+",\"y\":"+point.getY()+",\"z\":"+point.getZ()+"}}}").getAsJsonObject());
            replacement.put("example:npc_activities",JsonParser.parseString("""
                {"activities":{"read":{"actors":["sebastian"],"aliases":["example:read"],"asset":"sebastian",
                "play_clip":"animation.sebastian.idle","enter_ticks":1,"exit_ticks":1}}}
                """).getAsJsonObject());
            NpcDataRegistry.replaceEvents(replacement);
            var state=new NpcRuntimeState("sebastian");state.setNamedPointId("npc_test_read");state.setRouteBehaviorToken("example:read");
            runtime.states().put("sebastian",state);
            for(int i=0;i<10;i++) controller.tick();
            helper.assertTrue(npc.isPlayingNativeActivity(),"Data-defined activity did not start: yaw="+npc.getYRot()+" target="+NpcScheduleRuntimeService.resolveWorldTarget(level,state,null)+" pos="+npc.position()+" owner="+NpcExecutionCoordinator.owner(npc));
            helper.assertTrue(npc.getScheduleActivityEvent().getString("playClip").equals("animation.sebastian.idle"),"Server did not publish the authored clip");
            var active=npc.getScheduleActivityEvent().copy();active.putLong("start",level.getGameTime()-10);
            npc.setScheduleActivityEvent(active);
            replacement.remove("example:npc_activities");NpcDataRegistry.replaceEvents(replacement);
            controller.tick();
            helper.assertTrue(!npc.getScheduleActivityEvent().isEmpty() && npc.getScheduleActivityEvent().contains("exit"),
                    "Removing an activity definition skipped its recorded outro");
            helper.assertTrue(npc.getScheduleActivityEvent().getInt("exitTicks")==1,"Reload changed the active event's timing");
            var actorId=new ResourceLocation("stardewcraft:sebastian");
            com.stardew.craft.api.v1.npc.StardewNpcEntities.register(new ResourceLocation("stardewcraft_npc_runtime:resolver_"+UUID.randomUUID()),100,
                    context->context.npcId().equals(actorId) && context.level()==level ? npc : null);
            var lease=StardewNpcExecution.claim(level,actorId,new ResourceLocation("example:scene"),60,20).orElseThrow();
            helper.assertTrue(StardewNpcExecution.isCurrent(level,lease),"Addon lease is not current");
            StardewNpcExecution.release(level,lease);
            helper.assertTrue(!StardewNpcExecution.isCurrent(level,lease),"Released addon lease remains current");
        } finally {
            controller.cancel();npc.discard();NpcDataRegistry.replaceEvents(events);
            if(previous==null) runtime.states().remove("sebastian"); else runtime.states().put("sebastian",previous);
        }
        helper.succeed();
    }

}
