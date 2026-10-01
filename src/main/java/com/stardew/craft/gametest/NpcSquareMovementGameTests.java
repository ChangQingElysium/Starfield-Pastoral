package com.stardew.craft.gametest;

import com.google.gson.*;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.*;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcSquareMovementGameTests {
    @GameTest(batch="square_narrow",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void fourCellStripNeverEscapesDuringRecalculation(GameTestHelper h) {
        var base=floor(h);var npc=npc(h,base);var nav=(NpcPathNavigation)npc.getNavigation();
        var area=new NpcSquareArea(base,base.offset(3,0,0));var target=Vec3.atBottomCenterOf(base.offset(3,0,0));
        h.assertTrue(nav.moveWithin(target,1,area),"Four inclusive cells were mistaken for the token's three source tiles");
        h.getLevel().setBlockAndUpdate(base.offset(1,0,0),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(base.offset(1,1,0),Blocks.STONE.defaultBlockState());
        h.runAfterDelay(21,()->{
            // createPath alone may legally return its old cached route. Exercise the real invalidation path.
            nav.recomputePath();var recalculated=nav.getPath();
            h.assertTrue(recalculated==null||!recalculated.canReach(),"Automatic recalculation escaped the one-cell strip");
            nav.stop();h.assertTrue(nav.moveTo(target.x,target.y,target.z,1),"Later ordinary path retained old square bounds");
            nav.stop();h.succeed();
        });
    }
    @GameTest(batch="square_bounds",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void boundedSearchAndOrdinarySearchIsolation(GameTestHelper h) {
        var base=floor(h);var npc=npc(h,base.offset(1,0,3));var nav=(NpcPathNavigation)npc.getNavigation();
        var area=new NpcSquareArea(base,base.offset(6,0,6));
        for(int z=0;z<=6;z++)for(int y=0;y<3;y++)h.getLevel().setBlockAndUpdate(base.offset(3,y,z),Blocks.STONE.defaultBlockState());
        var target=Vec3.atBottomCenterOf(base.offset(5,0,3));
        h.assertTrue(!nav.moveWithin(target,1,area),"Bounded search escaped around the wall through unapproved cells");
        h.assertTrue(nav.moveTo(target.x,target.y,target.z,1),"Range constraint leaked into ordinary schedule navigation");
        var path=nav.getPath();h.assertTrue(path!=null&&path.canReach(),"Outside detour fixture must have a full ordinary path");
        h.getLevel().setBlockAndUpdate(base.offset(3,0,2),Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(base.offset(3,1,2),Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(base.offset(3,2,2),Blocks.AIR.defaultBlockState());
        h.assertTrue(nav.moveWithin(target,1,area),"Search failed to use the longer permitted internal detour");
        for(int i=0;i<nav.getPath().getNodeCount();i++)h.assertTrue(area.contains(nav.getPath().getEntityPosAtNode(npc,i),npc.getBbWidth()/2.),"Path node escaped bounds");
        nav.stop();h.succeed();
    }

    @GameTest(batch="square_tokens",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void tokenBoundsAndPausedClock(GameTestHelper h) {
        var b=NpcSquareMovement.parse("square_3_1_0");h.assertTrue(b!=null&&b.width()==3&&b.height()==1&&b.facing()==0,"Original square suffix meaning lost");
        h.assertTrue(NpcSquareMovement.parse("square_2_3").facing()==-1,"No facing preference must preserve movement facing");
        for(String bad:List.of("square_0_1","square_1_1_4","square_bad_2","animation.square_3_1_0"))h.assertTrue(NpcSquareMovement.parse(bad)==null,"Malformed token accepted");
        try{NpcSquareArea.decode(JsonParser.parseString("{\"min\":[1,2,3],\"max\":[0,2,3]}").getAsJsonObject());h.fail("Inverted range accepted");}catch(IllegalArgumentException expected){}
        var base=floor(h);var npc=npc(h,base);var s=new NpcSquareMovement(new NpcSquareArea(base,base.offset(5,0,5)),b,base);
        s.resume(0);h.assertTrue(s.choose(h.getLevel(),npc,0)!=null,"No valid square point");
        s.resume(1000);h.assertTrue(!s.timedOut(1000)&&!s.arrived(1000),"Dialogue/unload time consumed movement/hold duration");
        s.resume(1001);s.reject(1001);h.assertTrue(s.target()==null&&s.waiting(1010),"Failed endpoint immediately retried every tick");h.succeed();
    }

    @GameTest(batch="square_execution",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=850)
    public static void actualSquareLifecycleAndScheduleArrival(GameTestHelper h)throws ReflectiveOperationException {
        var base=floor(h);var level=h.getLevel();var npc=npc(h,base.offset(2,0,2));var events=NpcDataRegistry.events();
        var point=new JsonObject();point.addProperty("x",npc.getX());point.addProperty("y",npc.getY());point.addProperty("z",npc.getZ());
        point.add("square_area",JsonParser.parseString("{\"min\":["+base.getX()+","+base.getY()+","+base.getZ()+"],\"max\":["+(base.getX()+6)+","+base.getY()+","+(base.getZ()+6)+"]}"));
        var points=new JsonObject();points.add("square_fixture",point);var root=new JsonObject();root.add("points",points);
        var replacement=new LinkedHashMap<>(events);replacement.put("npc_route_points",root);NpcDataRegistry.replaceEvents(replacement);
        var stepType=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner$NpcRouteStep");
        var walk=stepType.getDeclaredMethod("walk",String.class,Vec3.class);walk.setAccessible(true);var step=walk.invoke(null,"square_fixture",npc.position());
        var type=Class.forName(NpcCentralMovementService.class.getName()+"$NpcRoutePlan");var ctor=type.getDeclaredConstructor(String.class,UUID.class,List.class,long.class);ctor.setAccessible(true);
        var plan=ctor.newInstance("square_fixture",npc.getUUID(),List.of(step),level.getGameTime());set(plan,"currentStepIndex",1);
        var method=NpcCentralMovementService.class.getDeclaredMethod("tickSquare",ServerLevel.class,StardewNpcEntity.class,NpcRuntimeState.class,type);method.setAccessible(true);
        var reached=NpcCentralMovementService.class.getDeclaredMethod("hasReachedScheduleTarget",ServerLevel.class,StardewNpcEntity.class,NpcRuntimeState.class);reached.setAccessible(true);
        var plansField=NpcCentralMovementService.class.getDeclaredField("ACTIVE_PLANS");plansField.setAccessible(true);
        @SuppressWarnings("unchecked") var plans=(Map<String,Object>)plansField.get(null);var previous=plans.put("evelyn",plan);
        var state=new NpcRuntimeState("evelyn");state.setNamedPointId("square_fixture");state.setRouteBehaviorToken("square_3_1_0");
        var area=NpcSquareArea.decode(point.getAsJsonObject("square_area"));var start=npc.position();int[] pauses={0};boolean[] moved={false},done={false};String[] last={""};
        Runnable restore=()->{if(done[0])return;done[0]=true;NpcDataRegistry.replaceEvents(events);if(previous==null)plans.remove("evelyn");else plans.put("evelyn",previous);npc.getNavigation().stop();NpcChunkForceManager.releaseNpcForcedChunks(level,"evelyn");};
        h.onEachTick(()->{
            if(done[0])return;
            try {
                var before=npc.position();level.tickNonPassenger(npc);method.invoke(null,level,npc,state,plan);
                h.assertTrue(area.contains(npc.position(),npc.getBbWidth()/2.),"Actor left confirmed range");
                h.assertTrue(before.distanceToSqr(npc.position())<1,"Square recovery teleported actor");
                moved[0]|=start.distanceToSqr(npc.position())>.5;
                String stage=(String)get(plan,"debugStage");
                if(stage.equals("square_pause")&&!last[0].equals(stage))pauses[0]++;
                last[0]=stage;
                h.assertTrue((boolean)reached.invoke(null,level,npc,state),"Wandering invalidated completed arrival and would stall the next checkpoint");
                if(pauses[0]>=2&&moved[0]){restore.run();h.succeed();}
                if(h.getTick()>820){restore.run();h.fail("Square did not walk/pause twice: "+stage);}
            }catch(ReflectiveOperationException|RuntimeException|Error e){restore.run();throw new RuntimeException(e);}
        });
    }
    private static Object get(Object o,String name)throws ReflectiveOperationException {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String name,Object value)throws ReflectiveOperationException {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private static StardewNpcEntity npc(GameTestHelper h,BlockPos at) {
        var npc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),h.getLevel());npc.setNpcId("evelyn");npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(at));npc.setOnGround(true);return npc;
    }
    private static BlockPos floor(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(2,2,2));
        for(int x=-1;x<=8;x++)for(int z=-1;z<=8;z++) {
            h.getLevel().setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<4;y++)h.getLevel().setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        return base;
    }
}
