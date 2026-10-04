package com.stardew.craft.gametest;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.*;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class SiloRuntimeGameTests {
    @GameTest(template="construction_site")
    public static void siloColumnNeedsSolidBricksAndAcceptsManagerAtEveryHeight(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(12,1,12));
        for(int height=0;height<10;height++) for(int corner=0;corner<4;corner++) {
            var manager=base.offset(corner%2,height,corner/2);
            for(var cell:BlockPos.betweenClosed(base,base.offset(1,9,1))) level.setBlock(cell,Blocks.BRICKS.defaultBlockState(),18);
            level.setBlock(manager,ModBlocks.SILO_MANAGER.get().defaultBlockState(),18);
            var scan=UtilityBuildings.scanSilo(level,manager,UtilityBuildings.bounds(UtilityBuildings.SILO,manager));
            h.assertTrue(scan.valid() && scan.column().min().equals(base),"Manager position rejected: "+height+"/"+corner);
            var hole=base.offset((corner%2)^1,5,(corner/2)^1);
            for(var wrong:List.of(Blocks.AIR,Blocks.RED_NETHER_BRICKS,Blocks.BRICK_SLAB,Blocks.OAK_PLANKS)) {
                level.setBlock(hole,wrong.defaultBlockState(),18);
                h.assertTrue(!UtilityBuildings.scanSilo(level,manager,UtilityBuildings.bounds(UtilityBuildings.SILO,manager)).valid(),"Incomplete/wrong-material column accepted: "+wrong);
            }
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void siloTemplateMarkerAndTerrainAreNormalizedForAllRotations(GameTestHelper h) {
        PrefabDefinitions.validateAssets(h.getLevel(),UtilityBuildings.SILO);
        var family=PrefabDefinitions.get(UtilityBuildings.SILO);var tier=family.tier(1);var template=PrefabDefinitions.template(h.getLevel(),tier);
        h.assertTrue(family.tiers().size()==1 && tier.size().equals(new BlockPos(7,15,7)) && tier.manager().equals(new BlockPos(3,1,5)),"Wrong silo template geometry");
        h.assertTrue(template.retainedGround().size()==49 && template.cells().stream().noneMatch(c->c.state().is(Blocks.PURPLE_WOOL)),"Marker or synthetic terrain escaped import");
        h.assertTrue(template.cells().stream().filter(c->c.state().is(ModBlocks.SILO_MANAGER.get())).count()==1,"Expected one manager");
        h.assertTrue(PrefabDefinitions.blueprintItem(UtilityBuildings.SILO)==ModItems.SILO_BLUEPRINT.get(),"Silo route delivers a manager instead of a blueprint");
        for(var facing:Direction.Plane.HORIZONTAL) {
            var rotation=PrefabDefinitions.rotation(facing);var origin=new BlockPos(20,30,40);
            var claim=PrefabDefinitions.transform(family.reservation(),origin,rotation);
            var manager=PrefabDefinitions.world(tier.manager(),tier.anchor(),origin,rotation);
            h.assertTrue(claim.contains(manager) && manager.getY()==31,"Buried/outside manager after rotation");
        }
        h.succeed();
    }
    @GameTest(template="construction_site")
    public static void siloRequiresConfirmationAndDamagedColumnsStopCapacityWithoutDeletingHay(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"Silo storage","Silo storage",FarmType.STANDARD);var data=BuildingWorldData.get(server);
        try {
            var base=h.absolutePos(new BlockPos(10,1,10));var manager=base.above(5);
            for(var cell:BlockPos.betweenClosed(base,base.offset(1,9,1))) level.setBlock(cell,Blocks.BRICKS.defaultBlockState(),18);
            level.setBlock(manager,ModBlocks.SILO_MANAGER.get().defaultBlockState(),18);
            var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),UtilityBuildings.SILO,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),manager,manager,Direction.SOUTH,UtilityBuildings.bounds(UtilityBuildings.SILO,manager));
            h.assertTrue(data.register(record)==BuildingWorldData.Result.SUCCESS,"Register silo");
            UtilityBuildings.refresh(level,record);
            h.assertTrue(data.find(record.id()).phase()==BuildingRecord.Phase.WAITING && FarmFeed.capacity(server,farm.getInstanceId())==0,"Column auto-built without confirmation");
            h.assertTrue(UtilityBuildings.acceptSilo(level,data.find(record.id())),"Accept column");
            record=data.find(record.id());
            h.assertTrue(record.claim().min().equals(base) && FarmFeed.store(server,farm.getInstanceId(),300)==240,"Wrong claim or capacity");
            h.assertTrue(!LivestockHomes.accepts(record),"Silo offered as an animal home");
            level.removeBlock(base.south(),false);
            h.assertTrue(FarmFeed.capacity(server,farm.getInstanceId())==0 && FarmFeed.amount(server,farm.getInstanceId())==240,"Damage retained capacity or erased stock");
            level.setBlock(base.south(),Blocks.BRICKS.defaultBlockState(),18);
            h.assertTrue(FarmFeed.capacity(server,farm.getInstanceId())==240,"Repair failed to reactivate silo");
            data.rename(record.id(),data.find(record.id()).revision(),"North grain");level.removeBlock(manager,false);
            var missing=data.find(record.id());
            h.assertTrue(missing.phase()==BuildingRecord.Phase.MISSING && FarmFeed.capacity(server,farm.getInstanceId())==0,"Manager removal did not detach");
            h.assertTrue(data.restoreSelf(missing,manager,Direction.SOUTH,UtilityBuildings.bounds(UtilityBuildings.SILO,manager))==BuildingWorldData.Result.SUCCESS,"Restore manager identity");
            level.setBlock(manager,ModBlocks.SILO_MANAGER.get().defaultBlockState(),18);
            h.assertTrue(UtilityBuildings.acceptSilo(level,data.find(record.id())) && data.find(record.id()).displayName().equals("North grain"),"Rebuild lost name or claim");
            data.demolish(record.id());
            h.assertTrue(FarmFeed.take(server,farm.getInstanceId(),300)==240,"Removing silo deleted stored hay");
        } finally { farms.deleteFarm(owner); }
        h.succeed();
    }
    @GameTest(template="construction_site")
    public static void siloMoveRotatesAnOffCenterManagerAndKeepsColumnOnGround(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(6,1,6));var manager=base.offset(1,7,0);
        for(var pos:BlockPos.betweenClosed(base,base.offset(1,9,1)))level.setBlock(pos,Blocks.BRICKS.defaultBlockState(),18);
        level.setBlock(manager,ModBlocks.SILO_MANAGER.get().defaultBlockState(),18);
        var record=new BuildingRecord(UUID.randomUUID(),UUID.randomUUID(),0,UtilityBuildings.SILO,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),manager,manager,Direction.SOUTH,
                new BuildingBounds(base,base.offset(2,10,2)),BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,1,"High manager");
        var document=new ItemStack(ModItems.SILO_BLUEPRINT.get());BuildingBlueprintItem.bindMove(document,record);
        var ground=h.absolutePos(new BlockPos(25,0,25));
        for(var facing:Direction.Plane.HORIZONTAL) {
            var anchor=BuildingBlueprintItem.targetAnchor(document,ground,facing);
            var bounds=UtilityBuildings.moveBounds(record,anchor,facing);
            h.assertTrue(bounds.min().getY()==ground.getY()+1,"Moving high manager makes the column float/sink");
            var transfer=BuildingTransfer.move(level,record,anchor,facing);
            h.assertTrue(transfer.contents().size()==40 && transfer.after().claim().equals(bounds),"Moved column lost cells");
            for(var cell:transfer.contents())h.assertTrue(bounds.contains(cell.pos()),"Rotation escaped new claim");
        }
        var transfer=BuildingTransfer.move(level,record,BuildingBlueprintItem.targetAnchor(document,ground,Direction.WEST),Direction.WEST);
        transfer.project(level);BuildingTransfer.load(transfer.save(),level.registryAccess()).project(level);
        h.assertTrue(level.getBlockState(manager).isAir() && UtilityBuildings.scanSilo(level,transfer.after().manager(),transfer.after().claim()).valid(),"Move replay broke or duplicated column");
        h.succeed();
    }
    @GameTest(template="construction_site",timeoutTicks=200)
    public static void siloConstructionWorkerAndCompletedPrefabSurviveAllRotations(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"Silo prefab","Silo prefab",FarmType.STANDARD);var data=BuildingWorldData.get(server);
        var family=PrefabDefinitions.get(UtilityBuildings.SILO);var tier=family.tier(1);var anchor=h.absolutePos(new BlockPos(20,0,20));
        try {
            for(var facing:Direction.Plane.HORIZONTAL) {
                var rotation=PrefabDefinitions.rotation(facing);var claim=PrefabDefinitions.transform(family.reservation(),anchor,rotation);
                for(var pos:BlockPos.betweenClosed(claim.min(),claim.maxInclusive()))level.setBlock(pos,(pos.getY()==anchor.getY()?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);
                h.assertTrue(BuildingPlacementService.checkSpace(level,claim)==null,"Silo ground incorrectly requires air");
                var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),UtilityBuildings.SILO,BuildingRecord.Mode.PREFAB,level.dimension().location(),anchor,
                        PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,rotation),facing,claim);
                var permit=UUID.randomUUID();data.recordPurchase(permit,farm.getInstanceId(),true,UtilityBuildings.SILO);
                h.assertTrue(data.beginPrefab(record,permit,10)==BuildingWorldData.Result.SUCCESS,"Silo order rejected");record=data.find(record.id());
                h.assertTrue(data.order(record.id()).remainingDays()==2,"Silo build days must match vanilla (2)");
                BuildingPlacementService.scaffold(level,record);RisingConstruction.clear(level,record.id());BuildingPlacementService.scaffold(level,record);
                var workers=level.getEntitiesOfClass(RobinConstructionEntity.class,BuildingPlacementService.aabb(claim));
                h.assertTrue(workers.size()==1 && !com.stardew.craft.port.PortJava.getFirst(workers).blockPosition().equals(record.manager()),"Worker inside manager");
                var worker=com.stardew.craft.port.PortJava.getFirst(workers);
                h.assertTrue(level.getBlockState(worker.blockPosition().relative(worker.getDirection())).is(ModBlocks.CONSTRUCTION_FENCE.get()),"Rotated silo worker hits air");
                h.assertTrue(FarmFeed.capacity(server,farm.getInstanceId())==0,"Construction added hay capacity early");
                for(int day=11;day<=13;day++)data.constructionDay(day,true);
                BuildingPlacementService.finish(level,record);record=data.find(record.id());
                h.assertTrue(FarmFeed.capacity(server,farm.getInstanceId())==240 && record.phase()==BuildingRecord.Phase.READY,"Completed silo has no capacity");
                h.assertTrue(level.getBlockState(record.manager()).is(ModBlocks.SILO_MANAGER.get()),"Lost manager marker");
                h.assertTrue(data.assessResidence(record.id(),record.revision(),0)==BuildingWorldData.Result.SUCCESS,"Could not simulate an interrupted prefab assessment");
                h.assertTrue(data.find(record.id()).residence()==BuildingRecord.Residence.INVALID
                        && FarmFeed.capacity(server,farm.getInstanceId())==240
                        && data.find(record.id()).residence()==BuildingRecord.Residence.VALID,
                        "A purchased silo could not recover its capacity assessment");
                record=data.find(record.id());
                h.assertTrue(PrefabDefinitions.retainedGround(level,record).stream().allMatch(p->level.getBlockState(p).is(Blocks.STONE)),"Completion replaced natural support");
                h.assertTrue(data.beginUpgrade(record.id(),record.revision(),14)!=BuildingWorldData.Result.SUCCESS,"Silo exposes a nonexistent upgrade");
                var nativeCells=BuildingTransfer.nativeCells(level,record,1);
                h.assertTrue(!nativeCells.isEmpty() && nativeCells.keySet().stream().allMatch(p->p.getY()>anchor.getY()),"Ground included in silo's native protection");
                data.demolish(record.id());BuildingProtection.clearMasks();
                BuildingProtection.transfer(()->{for(var pos:BlockPos.betweenClosed(claim.min().above(),claim.maxInclusive()))level.removeBlock(pos,false);});
            }
        } finally { farms.deleteFarm(owner);BuildingProtection.clearMasks(); }
        h.succeed();
    }
    @GameTest(template="construction_site",timeoutTicks=200)
    public static void siloPrefabMoveAndDemolitionPreservePlayerChestAndFarmStock(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"Silo move","Silo move",FarmType.STANDARD);var data=BuildingWorldData.get(server);
        var family=PrefabDefinitions.get(UtilityBuildings.SILO);var tier=family.tier(1);var origin=h.absolutePos(new BlockPos(5,0,5));
        var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),UtilityBuildings.SILO,BuildingRecord.Mode.PREFAB,level.dimension().location(),origin,
                origin.offset(tier.manager()),Direction.SOUTH,PrefabDefinitions.transform(family.reservation(),origin,Rotation.NONE));
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"SiloMove"));
        try {
            for(var pos:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,0,0)),h.absolutePos(new BlockPos(47,0,47))))level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
            var permit=UUID.randomUUID();data.recordPurchase(permit,farm.getInstanceId(),true,UtilityBuildings.SILO);data.beginPrefab(record,permit,10);data.markScaffold(record.id());
            for(int day=11;day<=13;day++)data.constructionDay(day,true);
            BuildingPlacementService.finish(level,data.find(record.id()));record=data.find(record.id());
            FarmFeed.store(server,farm.getInstanceId(),117);
            var chest=origin.above();level.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
            ((net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(chest)).setItem(0,new ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
            h.assertTrue(BuildingProtection.deniesReplacement(level,record.manager(),Blocks.AIR.defaultBlockState()) && !BuildingProtection.deniesReplacement(level,chest,Blocks.AIR.defaultBlockState()),"Native protection blocks player additions");
            var blueprint=new ItemStack(ModItems.SILO_BLUEPRINT.get());BuildingBlueprintItem.bindMove(blueprint,record);
            h.assertTrue(level.getBlockState(record.manager()).is(ModBlocks.SILO_MANAGER.get()),"Creating move blueprint removes source");
            var target=h.absolutePos(new BlockPos(30,0,30));var transfer=BuildingTransfer.move(level,record,target,Direction.WEST);
            h.assertTrue(data.beginTransfer(transfer)==BuildingWorldData.Result.SUCCESS,"Silo transfer rejected");
            transfer.project(level);transfer.project(level);data.finishTransfer(record.id());
            var moved=data.find(record.id());var movedChest=BuildingTransfer.destination(record,moved,chest);
            h.assertTrue(level.getBlockState(record.manager()).isAir() && ((net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(movedChest)).getItem(0).getCount()==7,"Move lost or duplicated player contents");
            h.assertTrue(FarmFeed.amount(server,farm.getInstanceId())==117 && FarmFeed.capacity(server,farm.getInstanceId())==240,"Move changed shared hay");
            var nativeCells=BuildingTransfer.nativeCells(level,moved,1);
            h.assertTrue(BuildingDemolition.perform(player,moved),"Empty silo cannot be demolished");
            h.assertTrue(data.find(moved.id())==null && nativeCells.keySet().stream().allMatch(p->level.getBlockState(p).isAir()),"Demolition left native blocks");
            h.assertTrue(((net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(movedChest)).getItem(0).getCount()==7 && FarmFeed.amount(server,farm.getInstanceId())==117,"Demolition deleted additions or hay");
        } finally {farms.deleteFarm(owner);BuildingProtection.clearMasks();}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void siloCanvasKeepsPhysicalLayoutAndPointerCoordinatesAcrossGuiScales(GameTestHelper h) {
        for(var size:List.of(new int[]{320,240},new int[]{1067,701},new int[]{1920,1080},new int[]{2561,1441})) {
            double expected=-1;
            for(int scale=1;scale<=8;scale++) {
                var canvas=com.stardew.craft.client.gui.common.GuiLayoutMath.viewport(size[0],size[1],scale,564,360);
                double physical=canvas.scale()*scale;
                if(expected<0)expected=physical;
                h.assertTrue(Math.abs(expected-physical)<1e-8 && canvas.width()>=564 && canvas.height()>=360,"Silo canvas depends on user GUI scale");
                for(var point:List.of(new int[]{18,22},new int[]{436,105},new int[]{436,270},new int[]{548,340})) {
                    double sx=canvas.x()+point[0]*canvas.scale(), sy=canvas.y()+point[1]*canvas.scale();
                    h.assertTrue(Math.abs(canvas.mouseX(sx)-point[0])<1e-8 && Math.abs(canvas.mouseY(sy)-point[1])<1e-8,"Draw/click/drag coordinates disagree");
                }
            }
        }
        h.succeed();
    }

}
