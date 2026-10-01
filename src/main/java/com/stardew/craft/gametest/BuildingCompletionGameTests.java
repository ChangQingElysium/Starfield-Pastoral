package com.stardew.craft.gametest;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.network.payload.BuildingManagerReadyPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class BuildingCompletionGameTests {
    @GameTest(templateNamespace="stardewcraft_buildings", template="construction_site", timeoutTicks=200)
    public static void managersExistOnCompletionInEveryOrientation(GameTestHelper h) {
        var level=h.getLevel();
        var data=BuildingWorldData.get(level.getServer());
        var anchor=h.absolutePos(new BlockPos(24,5,24));
        var farms=com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer());
        var owner=UUID.randomUUID();
        var farmEntry=farms.createFarm(owner,"Completion","Completion",com.stardew.craft.farm.FarmType.STANDARD);
        try {
        for(var family:List.of(PrefabDefinitions.COOP,PrefabDefinitions.BARN,UtilityBuildings.SILO,FishPondPrefabs.FAMILY)) {
            var definition=PrefabDefinitions.get(family);var tier=definition.tier(1);
            for(var facing:Direction.Plane.HORIZONTAL) {
                var rotation=PrefabDefinitions.rotation(facing);
                var manager=PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,rotation);
                var claim=PrefabDefinitions.transform(definition.reservation(),anchor,rotation);
                var farm=farmEntry.getInstanceId();var permit=UUID.randomUUID();
                var record=BuildingRecord.waiting(farm,0,family,BuildingRecord.Mode.PREFAB,level.dimension().location(),anchor,manager,facing,claim);
                try {
                    BuildingProtection.transfer(()-> {
                        for(var pos:BlockPos.betweenClosed(claim.min(),claim.maxInclusive()))
                            level.setBlock(pos,pos.getY()<=anchor.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
                    });
                    data.recordPurchase(permit,farm,true,family);
                    h.assertTrue(data.beginPrefab(record,permit,10)==BuildingWorldData.Result.SUCCESS,"Could not start "+family);
                    BuildingPlacementService.scaffold(level,data.find(record.id()));
                    h.assertTrue(level.getBlockState(manager).is(PrefabDefinitions.managerBlock(family)),"Missing construction manager");
                    for(int day=11;day<=13;day++)data.constructionDay(day,true);
                    BuildingPlacementService.finish(level,data.find(record.id()));
                    var ready=data.find(record.id());
                    h.assertTrue(ready.phase()==BuildingRecord.Phase.READY && data.order(record.id())==null,"Completion still needs a polling tick");
                    h.assertTrue(level.getBlockState(ready.manager()).is(PrefabDefinitions.managerBlock(family)),"Manager is not available immediately: "+family+" "+facing);
                    h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,BuildingPlacementService.aabb(claim)).isEmpty(),"Rising construction can overwrite the finished building");
                    // Bundled upgrades keep the manager at the same world location despite changing template origins.
                    for(var next:definition.tiers())h.assertTrue(PrefabDefinitions.world(next.manager(),next.anchor(),anchor,rotation).equals(manager),"Tier anchor changed the manager location");
                } finally {
                    RisingConstruction.clear(level,record.id());
                    com.stardew.craft.fishpond.data.FishPondWorldData.get(level)
                            .findPondByManagerAnyOwner(level.dimension().location().toString(),manager)
                            .ifPresent(pond->com.stardew.craft.fishpond.data.FishPondWorldData.get(level).removePond(pond.pondId()));
                    data.removeFarm(farm);
                    BuildingProtection.transfer(()-> {
                        for(var pos:BlockPos.betweenClosed(claim.min(),claim.maxInclusive())) {
                            level.removeBlockEntity(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),18|32);
                        }
                    });
                }
            }
        }
        } finally {farms.deleteFarm(owner);}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings", template="empty")
    public static void completionPacketPreservesDimensionPositionAndFacing(GameTestHelper h) {
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            for(var facing:Direction.Plane.HORIZONTAL) {
                var packet=new BuildingManagerReadyPayload(h.getLevel().dimension().location(),new BlockPos(-31,73,18),PrefabDefinitions.managerState(PrefabDefinitions.COOP,facing));
                BuildingManagerReadyPayload.STREAM_CODEC.encode(buffer,packet);
                h.assertTrue(BuildingManagerReadyPayload.STREAM_CODEC.decode(buffer).equals(packet),"Manager completion update lost its position or facing");
            }
        } finally {buffer.release();}
        h.succeed();
    }
}
