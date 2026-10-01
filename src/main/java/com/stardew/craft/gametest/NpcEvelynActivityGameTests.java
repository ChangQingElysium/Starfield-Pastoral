package com.stardew.craft.gametest;

import com.google.gson.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.animation.NpcScheduleActivity;
import com.stardew.craft.npc.data.*;
import com.stardew.craft.npc.runtime.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.LinkedHashMap;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcEvelynActivityGameTests {
    @GameTest(batch="evelyn_home",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=480)
    public static void stoolChatExitAndResume(GameTestHelper h){run(h,"home");}
    @GameTest(batch="evelyn_clinic",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=480)
    public static void birchChairChatExitAndResume(GameTestHelper h){run(h,"clinic");}
    @GameTest(batch="evelyn_bed",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=480)
    public static void selectedDoubleBedSideChatExitAndResume(GameTestHelper h){run(h,"sleep");}
    @GameTest(batch="evelyn_garden",templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=480)
    public static void gardenTargetChatAndRemovedSoil(GameTestHelper h){run(h,"garden");}

    private static void run(GameTestHelper h,String kind) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(4,2,4));boolean bed=kind.equals("sleep"),garden=kind.equals("garden");
        String action=garden?"garden":bed?"sleep":"sit",behavior=garden?"evelyn_garden":bed?"evelyn_sleep":"evelyn_sit_left";
        var definition=NpcActivityCatalog.find("evelyn",behavior);h.assertTrue(definition!=null&&definition.asset().equals("evelyn_"+action),"Missing registered daily activity");
        var events=NpcDataRegistry.events();var runtime=NpcRuntimeDataManager.get(level);var old=runtime.states().get("evelyn");
        var point=new JsonObject();BlockPos selected=bed?base.offset(1,0,-1):base;
        point.addProperty("x",selected.getX());point.addProperty("y",selected.getY());point.addProperty("z",selected.getZ());
        if(!garden)point.addProperty("furniture",bed?"bed":"chair");
        if(bed){point.addProperty("preserve_bed_side",true);point.add("approach_offset",JsonParser.parseString("[-0.875,0,-0.375]"));}
        var registry=new LinkedHashMap<>(events);var points=registry.get("npc_route_points").deepCopy();points.getAsJsonObject("points").add("evelyn_activity_fixture",point);registry.put("npc_route_points",points);
        if(garden){var target=new JsonObject();target.addProperty("x",base.getX()-1);target.addProperty("y",base.getY());target.addProperty("z",base.getZ());target.addProperty("surface_y",1);point.add("activity_target",target);level.setBlockAndUpdate(base.west(),Blocks.COARSE_DIRT.defaultBlockState());}
        else if(bed) {
            var state=ModBlocks.BED_2.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
            level.setBlockAndUpdate(base,state);
            for(var offset:new BlockPos[]{new BlockPos(1,0,0),new BlockPos(0,0,-1),new BlockPos(1,0,-1)})level.setBlockAndUpdate(base.offset(offset),state.setValue(MapDecorStaticBlock.PART,MapDecorStaticBlock.Part.EXTENSION));
        } else level.setBlockAndUpdate(base,(kind.equals("clinic")?ModBlocks.CHAIR_3:ModBlocks.STOOL).get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.WEST));
        NpcDataRegistry.replaceEvents(registry);
        var activityNpc=new StardewNpcEntity(ModEntities.STARDEW_NPC.get(),level);activityNpc.setNpcId("evelyn");
        var support=NpcSupportTarget.resolve(level,"evelyn_activity_fixture");
        if(!garden)h.assertTrue(support!=null,"Shipped chair or double bed was rejected");
        if(bed)h.assertTrue(support.block().equals(base)&&Math.abs(support.origin().x-(base.getX()+1.5))<1e-6,"Selected right bed half collapsed onto main half");
        activityNpc.setPos(garden?Vec3.atBottomCenterOf(base):support.approach());activityNpc.setYRot(garden?90:support.approachYaw());activityNpc.setOnGround(true);
        level.setBlockAndUpdate(BlockPos.containing(activityNpc.position()).below(),Blocks.STONE.defaultBlockState());
        var state=new NpcRuntimeState("evelyn");state.setNamedPointId("evelyn_activity_fixture");state.setRouteBehaviorToken(behavior);state.setFacing(3);runtime.states().put("evelyn",state);
        var activity=new NpcScheduleActivity(activityNpc);long[] start={-1},exit={-1};int[] callbacks={0};boolean[] requested={false},restored={false},removed={false};
        Runnable restore=()->{if(restored[0])return;restored[0]=true;activity.cancel();NpcDataRegistry.replaceEvents(events);if(old==null)runtime.states().remove("evelyn");else runtime.states().put("evelyn",old);NpcChunkForceManager.releaseNpcForcedChunks(level,"evelyn");};
        h.onEachTick(()->{
            if(restored[0])return;
            try {
                long now=level.getGameTime();activity.tick();var event=activityNpc.getScheduleActivityEvent();
                if(start[0]<0&&!event.isEmpty()){start[0]=event.getLong("start");h.assertTrue(event.getString("action").equals("evelyn_"+action),"Wrong actor asset");if(garden)h.assertTrue(event.contains("activityTarget")&&event.getDouble("targetY")==base.getY()+1,"Garden lost its real soil surface");}
                if(start[0]>=0&&!requested[0]&&now>=start[0]+definition.enterTicks()+8){requested[0]=true;activity.interrupt(()->callbacks[0]++);}
                if(event.contains("exit")){exit[0]=event.getLong("exit");if(!removed[0])h.assertTrue(callbacks[0]==0,"Chat started before the exit finished");}
                if(exit[0]>=0&&event.isEmpty())h.assertTrue(now>=exit[0]+definition.exitTicks(),"Activity released before authored exit completed");
                if(callbacks[0]==1&&!event.isEmpty()&&event.getLong("start")>start[0]){
                    if(!garden){restore.run();h.succeed();return;}
                    if(!removed[0]){removed[0]=true;level.setBlockAndUpdate(base.west(),Blocks.AIR.defaultBlockState());}
                }
                if(removed[0]&&event.isEmpty()){h.assertTrue(NpcActivityTarget.resolve(level,"evelyn_activity_fixture")==null,"Missing soil still accepts watering");restore.run();h.succeed();return;}
                if(h.getTick()>465){restore.run();h.fail("Evelyn "+kind+" lifecycle timed out");}
            }catch(RuntimeException|Error failure){restore.run();throw failure;}
        });
    }
}
