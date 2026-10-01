package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.animation.NpcScheduleActivity;
import com.stardew.craft.npc.data.*;
import com.stardew.craft.npc.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.LinkedHashMap;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcGusActivityGameTests {
    @GameTest(batch="gus_sofa",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=400)
    public static void sofaMidpointChatExitAndResume(GameTestHelper h) { supported(h,false); }

    @GameTest(batch="gus_bed",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=400)
    public static void bedChatExitAndResume(GameTestHelper h) { supported(h,true); }

    private static void supported(GameTestHelper h,boolean bed) {
        var level=h.getLevel();var block=h.absolutePos(new BlockPos(4,2,4));
        var events=NpcDataRegistry.events();var runtime=NpcRuntimeDataManager.get(level);var old=runtime.states().get("gus");
        var registry=new LinkedHashMap<>(events);var points=registry.get("npc_route_points").deepCopy();
        var point=new com.google.gson.JsonObject();point.addProperty("x",block.getX());point.addProperty("y",block.getY());point.addProperty("z",block.getZ());point.addProperty("furniture",bed?"bed":"chair");
        if(!bed)point.add("seat_span",com.google.gson.JsonParser.parseString("[1,0,0]"));
        points.getAsJsonObject("points").add("gus_support_fixture",point);registry.put("npc_route_points",points);NpcDataRegistry.replaceEvents(registry);
        var sofa=com.stardew.craft.block.ModBlocks.SOFA.get().defaultBlockState();
        level.setBlockAndUpdate(block,bed?com.stardew.craft.block.ModBlocks.BED_1.get().defaultBlockState():sofa);
        if(!bed)level.setBlockAndUpdate(block.east(),sofa);
        var support=NpcSupportTarget.resolve(level,"gus_support_fixture");
        h.assertTrue(support!=null,"Formal furniture failed support resolution");
        if(!bed) {
            h.assertTrue(support.origin().distanceToSqr(Vec3.atBottomCenterOf(block).add(.5,0,0))<1e-9,"Sofa pose did not use the two-seat midpoint");
            level.setBlockAndUpdate(block.east(),Blocks.AIR.defaultBlockState());
            h.assertTrue(NpcSupportTarget.resolve(level,"gus_support_fixture")==null,"Missing second sofa silently degrades to a single seat");
            level.setBlockAndUpdate(block.east(),sofa);
        }
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);npc.setNpcId("gus");npc.setPos(support.approach());npc.setYRot(support.approachYaw());npc.setOnGround(true);
        level.setBlockAndUpdate(BlockPos.containing(support.approach()).below(),Blocks.STONE.defaultBlockState());
        var state=new NpcRuntimeState("gus");state.setNamedPointId("gus_support_fixture");state.setRouteBehaviorToken(bed?"gus_sleep":"gus_sit_down");runtime.states().put("gus",state);
        var activity=new NpcScheduleActivity(npc);long[] start={-1},exit={-1};int[] callbacks={0};boolean[] requested={false},restored={false};
        Runnable restore=()->{if(restored[0])return;restored[0]=true;activity.cancel();NpcDataRegistry.replaceEvents(events);if(old==null)runtime.states().remove("gus");else runtime.states().put("gus",old);NpcChunkForceManager.releaseNpcForcedChunks(level,"gus");};
        h.onEachTick(()->{
            if(restored[0])return;
            try {
                long now=level.getGameTime();activity.tick();var event=npc.getScheduleActivityEvent();
                if(start[0]<0&&!event.isEmpty()){start[0]=event.getLong("start");h.assertTrue(event.getString("action").equals(bed?"gus_sleep":"gus_sit")&&event.getLong("block")==block.asLong(),"Wrong supported activity event");}
                if(start[0]>=0&&!requested[0]&&now>=start[0]+(bed?120:56)){requested[0]=true;activity.interrupt(()->callbacks[0]++);}
                if(event.contains("exit")){exit[0]=event.getLong("exit");h.assertTrue(callbacks[0]==0,"Chat dispatched while Gus was still supported");}
                if(exit[0]>=0&&event.isEmpty())h.assertTrue(now>=exit[0]+(bed?112:48),"Support released before standing exit finished");
                if(callbacks[0]==1&&!event.isEmpty()&&event.getLong("start")>start[0]){restore.run();h.succeed();return;}
                if(h.getTick()>385){restore.run();h.fail("Supported Gus activity did not exit and resume");}
            }catch(RuntimeException|Error failure){restore.run();throw failure;}
        });
    }

    @GameTest(batch="gus_chat",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=240)
    public static void cleaningFinishesExitBeforeInteractionAndCanResume(GameTestHelper h) { run(h,true); }

    @GameTest(batch="gus_schedule",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=240)
    public static void nextScheduleDuringEntryExitsAndReleasesMovement(GameTestHelper h) { run(h,false); }

    private static void run(GameTestHelper h,boolean interrupt) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(3,2,3));
        level.setBlockAndUpdate(base.below(),Blocks.STONE.defaultBlockState());
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);npc.setNpcId("gus");
        npc.setPos(Vec3.atBottomCenterOf(base));npc.setOnGround(true);npc.setYRot(0);
        var definition=NpcActivityCatalog.find("gus","gus_clean");
        h.assertTrue(definition!=null && definition.asset().equals("gus_clean") && !definition.supported(),"Shipped cleaning action is not registered as a free-standing activity");
        h.assertTrue(NpcActivityCatalog.find("gus","gus_sit_down").support().equals("chair") && NpcActivityCatalog.find("gus","gus_sleep").support().equals("bed"),
                "Confirmed sofa/bed actions must resolve through the common activity catalog");
        var originalEvents=NpcDataRegistry.events();var runtime=NpcRuntimeDataManager.get(level);var originalState=runtime.states().get("gus");
        var events=new LinkedHashMap<>(originalEvents);var points=events.get("npc_route_points").deepCopy();
        var point=new com.google.gson.JsonObject();point.addProperty("x",npc.getX());point.addProperty("y",npc.getY());point.addProperty("z",npc.getZ());
        points.getAsJsonObject("points").add("gus_activity_fixture",point);events.put("npc_route_points",points);NpcDataRegistry.replaceEvents(events);
        var state=new NpcRuntimeState("gus");state.setNamedPointId("gus_activity_fixture");state.setRouteBehaviorToken("gus_clean");state.setFacing(2);runtime.states().put("gus",state);
        var activity=new NpcScheduleActivity(npc);long[] start={-1},exit={-1};int[] callbacks={0};boolean[] requested={false},restored={false};
        Runnable restore=()->{
            if(restored[0])return;restored[0]=true;activity.cancel();NpcDataRegistry.replaceEvents(originalEvents);
            if(originalState==null)runtime.states().remove("gus");else runtime.states().put("gus",originalState);
            NpcChunkForceManager.releaseNpcForcedChunks(level,"gus");
        };
        h.onEachTick(()->{
            if(restored[0])return;
            try {
                long now=level.getGameTime();activity.tick();var event=npc.getScheduleActivityEvent();
                if(start[0]<0 && !event.isEmpty()) {
                    start[0]=event.getLong("start");
                    h.assertTrue(event.getString("enterClip").equals("animation.gus.clean_enter") && event.getInt("enterTicks")==32
                            && event.getString("playClip").equals("animation.gus.clean_play") && event.getString("exitClip").equals("animation.gus.clean_exit"),"Activity event lost registered clip/timing data");
                }
                if(start[0]>=0 && !requested[0] && now>=start[0]+(interrupt?40:8)) {
                    requested[0]=true;
                    if(interrupt)activity.interrupt(()->callbacks[0]++);else state.setRouteBehaviorToken("");
                }
                if(event.contains("exit")) {
                    exit[0]=event.getLong("exit");
                    h.assertTrue(exit[0]>=start[0]+32 && callbacks[0]==0,"Interrupted entry or dispatched chat before exiting");
                }
                if(exit[0]>=0 && event.isEmpty()) {
                    h.assertTrue(now>=exit[0]+32,"Activity released before its authored exit finished");
                    h.assertTrue(!npc.isPlayingNativeActivity(),"Activity remained locked after exit");
                    if(!interrupt) {h.assertTrue(callbacks[0]==0,"Schedule change dispatched an interaction");restore.run();h.succeed();return;}
                }
                if(interrupt && callbacks[0]==1 && !event.isEmpty() && event.getLong("start")>start[0]) {
                    h.assertTrue(!event.contains("exit"),"Resumed activity inherited its old exit timestamp");restore.run();h.succeed();return;
                }
                if(h.getTick()>=230) {restore.run();h.fail("Gus activity failed to complete its interrupt/resume lifecycle");}
            } catch(RuntimeException|Error failure) {restore.run();throw failure;}
        });
    }
}
