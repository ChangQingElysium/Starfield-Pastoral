package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.*;
import com.stardew.craft.floor.*;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class BuildingResetGameTests {
    @GameTest(template="empty")
    public static void renamedDetachedHomeRetainsIdentityButReleasesItsClaim(GameTestHelper h){
        var data=new BuildingWorldData();var pos=new BlockPos(12,30,12);var family=PrefabDefinitions.COOP;
        var record=BuildingRecord.waiting(UUID.randomUUID(),0,family,BuildingRecord.Mode.SELF_BUILT,h.getLevel().dimension().location(),pos,pos,Direction.SOUTH,PrefabDefinitions.get(family).selfBounds(pos));
        data.register(record);data.acceptSelf(record.id(),0,3);data.rename(record.id(),1,"南边的小鸡之家");
        data.detachSelf(record.id());var detached=data.find(record.id());
        h.assertTrue(detached.phase()==BuildingRecord.Phase.MISSING && data.occupying(record.dimension(),pos)==null,"Removed manager erased identity or kept its claim");
        var reloaded=BuildingWorldData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        var target=pos.east(22);h.assertTrue(reloaded.restoreSelf(reloaded.find(record.id()),target,Direction.WEST,PrefabDefinitions.get(family).selfBounds(target))==BuildingWorldData.Result.SUCCESS,"Bound manager failed to restore");
        var restored=reloaded.find(record.id());h.assertTrue(restored.displayName().equals("南边的小鸡之家") && restored.id().equals(record.id()) && restored.tier()==1,"Restore lost name or skipped tiers");
        h.assertTrue(reloaded.restoreSelf(restored,target,Direction.WEST,restored.claim())!=BuildingWorldData.Result.SUCCESS,"Copied manager restored a second house");h.succeed();
    }
    @GameTest(template="empty")
    public static void missingResidenceCanReloadAfterItsOldSpaceIsReused(GameTestHelper h) {
        var data=new BuildingWorldData();var center=new BlockPos(3,4,5);var family=PrefabDefinitions.COOP;
        var old=BuildingRecord.waiting(UUID.randomUUID(),0,family,BuildingRecord.Mode.SELF_BUILT,h.getLevel().dimension().location(),center,center,Direction.SOUTH,PrefabDefinitions.get(family).selfBounds(center));
        data.register(old);data.detachSelf(old.id());
        var next=BuildingRecord.waiting(old.farmId(),0,family,BuildingRecord.Mode.SELF_BUILT,old.dimension(),center,center,Direction.SOUTH,old.claim());
        data.register(next);
        var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());var rows=saved.getList("Buildings",10);
        var first=rows.remove(0);rows.add(first); // Active record before the old missing record.
        var loaded=BuildingWorldData.load(saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.find(old.id()).phase()==BuildingRecord.Phase.MISSING && next.id().equals(loaded.occupying(old.dimension(),center)),"Missing residence incorrectly blocked another house on reload");h.succeed();
    }
    @GameTest(template="empty")
    public static void allCornerOrientationsCanCreateValidBuildingIdentities(GameTestHelper h){
        for(var family:List.of(PrefabDefinitions.COOP,PrefabDefinitions.BARN))for(var facing:Direction.Plane.HORIZONTAL){
            var definition=PrefabDefinitions.get(family);var tier=definition.tier(1);var anchor=new BlockPos(-19,12,-31);var rotation=PrefabDefinitions.rotation(facing);
            var claim=PrefabDefinitions.transform(definition.reservation(),anchor,rotation);var manager=PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,rotation);
            var record=BuildingRecord.waiting(UUID.randomUUID(),0,family,BuildingRecord.Mode.PREFAB,h.getLevel().dimension().location(),anchor,manager,facing,claim);
            h.assertTrue(BuildingRecord.load(record.save()).equals(record),"Rotated corner cannot survive record serialization");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void copiedDocumentSharesDurablePinAndCancellation(GameTestHelper h){
        var data=new BuildingDrafts();var stack=new ItemStack(ModItems.COOP_BLUEPRINT.get());BuildingBlueprintItem.bind(stack,UUID.randomUUID());
        var tag=BuildingBlueprintItem.draft(stack);tag.putLong("DraftAnchor",new BlockPos(4,5,6).asLong());tag.putString("DraftDimension",h.getLevel().dimension().location().toString());tag.putString("DraftFacing","west");
        PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));data.write(stack);var copy=stack.copy();
        data=BuildingDrafts.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        tag=BuildingBlueprintItem.draft(stack);tag.remove("DraftAnchor");PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));data.write(stack);data.apply(copy);
        h.assertTrue(BuildingBlueprintItem.pinned(copy,h.getLevel())==null && BuildingBlueprintItem.facing(copy)==Direction.WEST,"Copied item retained a conflicting pin after cancellation/reload");h.succeed();
    }
    @GameTest(template="construction_site",timeoutTicks=200)
    public static void rotatingSelfHomeCarriesContentsAndOnlyItsIndoorAnimals(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();var farm=farms.createFarm(owner,"MoveContent","MoveContent",FarmType.STANDARD);
        try{
            var center=h.absolutePos(new BlockPos(8,1,8));var bounds=PrefabDefinitions.get(PrefabDefinitions.COOP).selfBounds(center);var buildings=BuildingWorldData.get(server);
            for(var pos:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,0,0)),h.absolutePos(new BlockPos(47,0,47))))level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlock(center.offset(x,7,z),Blocks.OAK_PLANKS.defaultBlockState(),18);
            level.setBlock(center,ModBlocks.COOP_MANAGER.get().defaultBlockState(),18);
            var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),PrefabDefinitions.COOP,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),center,center,Direction.SOUTH,bounds);
            buildings.register(record);buildings.acceptSelf(record.id(),0,1);record=buildings.find(record.id());
            var chest=center.offset(-2,0,1);level.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);((ChestBlockEntity)level.getBlockEntity(chest)).setItem(0,new ItemStack(Items.DIAMOND,7));
            var outside=center.east(7);level.setBlock(outside,Blocks.GOLD_BLOCK.defaultBlockState(),18);
            var floor=center.below().south(2);SurfaceFloorData.get(level).restore(level,floor,new SurfaceFloorData.Cover(SurfaceFloorType.WOOD,3));
            var framePos=center.offset(2,1,1); level.setBlock(framePos.north(),Blocks.OAK_PLANKS.defaultBlockState(),18);
            var frame=new net.minecraft.world.entity.decoration.ItemFrame(level,framePos,Direction.SOUTH);
            frame.setItem(new ItemStack(Items.EMERALD),false);var frameId=frame.getUUID();level.addFreshEntity(frame);
            var data=LivestockWorldData.get(server);var insideId=UUID.randomUUID();var outsideId=UUID.randomUUID();var unrelatedId=UUID.randomUUID();
            var indoor=center.south(3);var outdoor=center.south(10);
            data.put(new LivestockRecord(insideId,owner,farm.getInstanceId(),record.id(),"Inside",1,1,LivestockCare.purchased()).at(new LivestockLocation(indoor,center,false,100,false)));
            data.put(new LivestockRecord(outsideId,owner,farm.getInstanceId(),record.id(),"Outside",2,1,LivestockCare.purchased()).at(new LivestockLocation(outdoor,center,true,100,false)));
            data.put(new LivestockRecord(unrelatedId,owner,farm.getInstanceId(),UUID.randomUUID(),"Unrelated",3,1,LivestockCare.purchased()).at(new LivestockLocation(indoor,center,false,100,false)));
            var transfer=BuildingTransfer.move(level,record,center.east(22),Direction.WEST);var restored=BuildingTransfer.load(transfer.save(),level.registryAccess());
            h.assertTrue(buildings.beginTransfer(restored)==BuildingWorldData.Result.SUCCESS,"Self-built transfer rejected");restored.project(level);restored.project(level);buildings.finishTransfer(record.id());
            var moved=buildings.find(record.id());var toChest=BuildingTransfer.destination(record,moved,chest);
            h.assertTrue(((ChestBlockEntity)level.getBlockEntity(toChest)).getItem(0).getCount()==7 && level.getBlockState(chest).isAir(),"Container lost/duplicated in replay");
            var movedFrame=(net.minecraft.world.entity.decoration.ItemFrame)level.getEntity(frameId);
            h.assertTrue(movedFrame != null && movedFrame.getPos().equals(BuildingTransfer.destination(record,moved,framePos))
                    && movedFrame.getDirection()==Direction.WEST && movedFrame.getItem().is(Items.EMERALD),"Wall decoration lost its identity, item, or rotated attachment");
            h.assertTrue(level.getBlockState(outside).is(Blocks.GOLD_BLOCK),"Moving a home stole its exterior surroundings");
            h.assertTrue(SurfaceFloorData.get(level).at(floor)==null && SurfaceFloorData.get(level).at(BuildingTransfer.destination(record,moved,floor)).variant()==3,"Floor overlay failed replay");
            h.assertTrue(data.find(insideId).location().position().equals(BuildingTransfer.destination(record,moved,indoor)),"Indoor bound animal did not rotate with its home");
            h.assertTrue(data.find(outsideId).location().position().equals(outdoor) && data.find(outsideId).location().homeAnchor().equals(moved.anchor()),"Outdoor animal was recalled or lost its new return address");
            h.assertTrue(data.find(unrelatedId).location().position().equals(indoor),"Unrelated animal was stolen");
        }finally{farms.deleteFarm(owner);}h.succeed();
    }

    @GameTest(template="construction_site",timeoutTicks=200)
    public static void prefabDemolitionPreservesAdditionsAndRejectsAttachments(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var owner=UUID.randomUUID();
        var farms=FarmInstanceRegistry.get(server);var farm=farms.createFarm(owner,"Demolition","Demolition",FarmType.STANDARD);
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"Demolition"));
        try {
            var data=BuildingWorldData.get(server);var definition=PrefabDefinitions.get(PrefabDefinitions.COOP);var tier=definition.tier(1);
            var anchor=h.absolutePos(new BlockPos(3,1,20));
            var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),PrefabDefinitions.COOP,BuildingRecord.Mode.PREFAB,level.dimension().location(),anchor,
                    PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,Rotation.NONE),Direction.SOUTH,PrefabDefinitions.transform(definition.reservation(),anchor,Rotation.NONE));
            var permit=UUID.randomUUID();data.recordPurchase(permit,farm.getInstanceId(),true,record.family());data.beginPrefab(record,permit,10);data.markScaffold(record.id());
            for(int day=11;day<=13;day++)data.constructionDay(day,true);
            BuildingPlacementService.finish(level,data.find(record.id()));record=data.find(record.id());
            var nativeCells=BuildingTransfer.nativeCells(level,record,1);
            BlockPos floor=nativeCells.keySet().stream().filter(pos->level.getBlockState(pos).isSolidRender(level,pos) && level.getBlockState(pos.above()).isAir()).findFirst().orElseThrow();
            SurfaceFloorData.get(level).restore(level,floor,new SurfaceFloorData.Cover(SurfaceFloorType.WOOD,1));
            h.assertTrue(!BuildingDemolition.perform(player,record) && data.find(record.id())!=null,"Demolition erased an attached player floor");
            SurfaceFloorData.get(level).remove(level,floor,false);
            var extra=record.claim().maxInclusive().above();level.setBlock(extra,Blocks.CHEST.defaultBlockState(),18);
            ((ChestBlockEntity)level.getBlockEntity(extra)).setItem(0,new ItemStack(Items.DIAMOND,9));
            h.assertTrue(BuildingDemolition.perform(player,record),"Empty native structure failed demolition");
            h.assertTrue(data.find(record.id())==null && nativeCells.keySet().stream().allMatch(pos->level.getBlockState(pos).isAir()),"Native components remain after demolition");
            h.assertTrue(((ChestBlockEntity)level.getBlockEntity(extra)).getItem(0).getCount()==9,"Demolition touched player additions");
        } finally {farms.deleteFarm(owner);}h.succeed();
    }
    @GameTest(template="empty")
    public static void constructionProtectsGroundSupportAndRisingPropsAreUnique(GameTestHelper h) {
        var level=h.getLevel();var data=BuildingWorldData.get(level.getServer());var definition=PrefabDefinitions.get(PrefabDefinitions.COOP);var tier=definition.tier(1);
        var anchor=h.absolutePos(new BlockPos(2,1,2));var record=BuildingRecord.waiting(UUID.randomUUID(),0,PrefabDefinitions.COOP,BuildingRecord.Mode.PREFAB,level.dimension().location(),anchor,
                PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,Rotation.NONE),Direction.SOUTH,PrefabDefinitions.transform(definition.reservation(),anchor,Rotation.NONE));
        var permit=UUID.randomUUID();data.recordPurchase(permit,record.farmId(),true,record.family());data.beginPrefab(record,permit,10);
        var support=record.claim().min().below();h.assertTrue(BuildingProtection.protects(level,support),"Construction foundation can be undermined");
        var pos=record.claim().min();RisingConstruction.place(level,record,pos,Blocks.OAK_FENCE.defaultBlockState(),true);RisingConstruction.place(level,record,pos,Blocks.OAK_FENCE.defaultBlockState(),true);
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).size()==1,"Repeated recovery duplicates rising fence");
        RisingConstruction.clear(level,record.id());data.demolish(record.id());
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(),"Clearing construction left ghost props");h.succeed();
    }
}
