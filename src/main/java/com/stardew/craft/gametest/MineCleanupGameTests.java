package com.stardew.craft.gametest;

import com.google.gson.JsonParser;
import com.stardew.craft.mining.*;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.UUID;

@GameTestHolder("stardewcraft_mine_cleanup")
@PrefixGameTestTemplate(false)
public final class MineCleanupGameTests {
    @GameTest(templateNamespace="stardewcraft_mine_cleanup",template="ring_utilities")
    public static void existingPlayerDepthSurvivesFloorReplacement(GameTestHelper h) {
        var id=UUID.randomUUID();var other=UUID.randomUUID();
        var player=new CompoundTag();player.putInt("currentFloor",80);player.putInt("maxFloorReached",115);player.putBoolean("receivedMineTotem",true);
        var second=new CompoundTag();second.putInt("currentFloor",121);second.putInt("maxFloorReached",187);
        var players=new CompoundTag();players.put(id.toString(),player);players.put(other.toString(),second);
        var original=new CompoundTag();original.put("players",players);
        var manager=MiningDataManager.load(original,h.getLevel().registryAccess());
        var saved=manager.save(new CompoundTag(),h.getLevel().registryAccess()).getCompound("players");
        var progress=MiningPlayerData.fromNBT(saved.getCompound(id.toString()));
        progress.setCurrentFloor(0);
        h.assertTrue(progress.getMaxFloorReached()==115 && progress.hasReceivedMineTotem(),"Returning to lobby reset depth/totem");
        progress=MiningPlayerData.fromNBT(progress.save());
        h.assertTrue(progress.getMaxFloorReached()==115 && progress.getCurrentFloor()==0,"Depth lost after save/reload");
        h.assertTrue(saved.getCompound(other.toString()).getInt("maxFloorReached")==187,"Another player's Skull progress was reset");
        for(int f=5;f<=115;f+=5)h.assertTrue(f<=progress.getMaxFloorReached(),"Previously reached elevator stop lost");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_mine_cleanup",template="ring_utilities",timeoutTicks=300)
    public static void lobbyIntroRouteAndCamerasFitApprovedArchitecture(GameTestHelper h) throws Exception {
        var level=h.getLevel();var data=MineFloorDataManager.get(level).getFloorData(0);
        if(data!=null)data.setGenerationVersion(0);
        OrdinaryMineRuntime.ensure(level,0);
        var layout=OrdinaryMineLayout.load(level,0);
        var bounds=new AABB(Vec3.atLowerCornerOf(layout.origin(0)),Vec3.atLowerCornerOf(layout.origin(0).offset(layout.size)));
        var carts=level.getEntitiesOfClass(MinecartStationEntity.class,bounds);
        h.assertTrue(carts.size()==1,"Lobby must contain exactly its authored station");
        h.assertTrue(com.stardew.craft.port.PortJava.getFirst(carts).position().distanceTo(new Vec3(-6.5,66.25,-1.5))<.1,"Station spawned at obsolete coordinates");
        var resource=level.getServer().getResourceManager().getResource(new ResourceLocation("stardewcraft","cutscene_events/marlon_mine_intro.json")).orElseThrow();
        try(var reader=resource.openAsReader()) {
            var event=JsonParser.parseReader(reader).getAsJsonObject();
            var trigger=event.getAsJsonObject("trigger");
            h.assertTrue(trigger.has("area_min") && trigger.has("area_max"),"Intro still triggers across the whole dimension");
            var actors=new HashMap<String,Vec3>();int moves=0,cameras=0,swords=0,quests=0;
            for(var raw:event.getAsJsonArray("commands")) {
                var c=raw.getAsJsonObject();var name=c.get("cmd").getAsString();
                if(name.equals("spawn_actor") || name.equals("move_actor")) {
                    var pos=new Vec3(c.get("x").getAsDouble(),c.get("y").getAsDouble(),c.get("z").getAsDouble());
                    var actor=c.get("actor").getAsString();
                    if(name.equals("move_actor")) {
                        var from=actors.get(actor);int steps=(int)Math.ceil(from.distanceTo(pos)*10);
                        for(int i=0;i<=steps;i++)assertStanding(h,from.lerp(pos,i/(double)steps),false);
                        moves++;
                    }
                    assertStanding(h,pos,true);actors.put(actor,pos);
                }
                if(name.equals("camera")) {
                    var eye=new Vec3(c.get("x").getAsDouble(),c.get("y").getAsDouble()+1.62,c.get("z").getAsDouble());
                    h.assertTrue(level.noCollision(null,new AABB(eye,eye).inflate(.15)),"Camera clips architecture: "+eye);
                    for(var target:actors.values()) {
                        var face=target.add(0,1.2,0);
                        var hit=level.clip(new ClipContext(eye,face,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
                        h.assertTrue(hit.getType()==HitResult.Type.MISS,"Camera cannot see actor: "+eye+" -> "+face+" hits "+hit.getBlockPos());
                    }
                    cameras++;
                }
                if(name.equals("add_item") && c.get("item").getAsString().equals("stardewcraft:rusty_sword"))swords+=c.get("count").getAsInt();
                if(name.equals("add_quest") && c.get("quest_id").getAsString().equals("14"))quests++;
                if(name.equals("place_player"))assertStanding(h,new Vec3(c.get("x").getAsDouble(),c.get("y").getAsDouble(),c.get("z").getAsDouble()),true);
            }
            h.assertTrue(moves==3 && cameras>=3 && swords==1 && quests==1,"Original route/reward sequence incomplete");
            h.assertTrue(actors.get("marlon").equals(new Vec3(5.5,66,-3.5)),"Marlon did not end at source tile 23,8");
            h.assertTrue(actors.get("fake_player").equals(new Vec3(5.5,66.125,-1.5)),"Player did not end at source tile 23,10");
        }
        h.succeed();
    }

    private static void assertStanding(GameTestHelper h,Vec3 pos,boolean endpoint) {
        var level=h.getLevel();double top=Double.NEGATIVE_INFINITY;
        for(var shape:level.getBlockCollisions(null,new AABB(pos.x-.28,65,pos.z-.28,pos.x+.28,66.9,pos.z+.28)))
            if(!shape.isEmpty())top=Math.max(top,shape.max(Direction.Axis.Y));
        h.assertTrue(Double.isFinite(top) && top<=66.25,"Missing floor/blocked route at "+pos+" top="+top);
        if(endpoint)h.assertTrue(Math.abs(pos.y-top)<.01,"Actor floats or intersects paving at "+pos+" top="+top);
        h.assertTrue(level.noCollision(null,new AABB(pos.x-.28,top+.001,pos.z-.28,pos.x+.28,top+1.8,pos.z+.28)),"Actor route intersects wall at "+pos);
    }
}
