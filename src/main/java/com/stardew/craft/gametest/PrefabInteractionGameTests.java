package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class PrefabInteractionGameTests {
    @GameTest(templateNamespace="stardewcraft_buildings", template="construction_site")
    public static void rotatedWorkersFaceTheFenceAndUpgradeWall(GameTestHelper h) {
        var level=h.getLevel();
        var registry=FarmInstanceRegistry.get(level.getServer());
        UUID owner=UUID.randomUUID();
        var farm=registry.createFarm(owner,"Worker","Worker rotation",FarmType.STANDARD);
        var data=BuildingWorldData.get(level.getServer());
        var family=PrefabDefinitions.get(PrefabDefinitions.COOP);
        var tier=family.tier(1);
        try {
            for(var facing:Direction.Plane.HORIZONTAL) {
                var anchor=h.absolutePos(new BlockPos(22,1,22));
                var rotation=PrefabDefinitions.rotation(facing);
                var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),family.id(),BuildingRecord.Mode.PREFAB,
                        level.dimension().location(),anchor,PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,rotation),
                        facing,PrefabDefinitions.transform(family.reservation(),anchor,rotation));
                var permit=UUID.randomUUID();data.recordPurchase(permit,farm.getInstanceId(),true,family.id());
                h.assertTrue(data.beginPrefab(record,permit,10)==BuildingWorldData.Result.SUCCESS,"Start order");
                record=data.find(record.id());
                BuildingPlacementService.scaffold(level,record);
                RisingConstruction.clear(level,record.id());
                BuildingPlacementService.scaffold(level,record);
                var workers=level.getEntitiesOfClass(RobinConstructionEntity.class,BuildingPlacementService.aabb(record.claim()));
                h.assertTrue(workers.size()==1,"One worker for "+facing);
                var worker=workers.getFirst();
                h.assertTrue(worker.getDirection()==facing && !worker.high(),"Actual entity yaw/low clip for "+facing);
                h.assertTrue(level.getBlockState(worker.blockPosition().relative(worker.getDirection())).is(ModBlocks.CONSTRUCTION_FENCE.get()),"Worker must face real fence for "+facing);
                // Finish the actual template, then verify the indoor high-strike target after rotation.
                data.constructionDay(11,true);data.constructionDay(12,true);data.constructionDay(13,true);
                BuildingPlacementService.finish(level,record);
                var indoor=BuildingLifecycleService.indoorWorkPosition(level,record);
                h.assertTrue(indoor!=null,"Indoor work position for "+facing);
                var target=indoor.above().relative(facing);
                h.assertTrue(level.getBlockState(target).isFaceSturdy(level,target,facing.getOpposite()),"High strike must face wall for "+facing);
                data.demolish(record.id());BuildingProtection.clearMasks();
                var claim=record.claim();
                BuildingProtection.transfer(()->{for(var pos:BlockPos.betweenClosed(claim.min(),claim.maxInclusive()))level.removeBlock(pos,false);});
            }
        } finally {registry.deleteFarm(owner);BuildingProtection.clearMasks();}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings", template="construction_site")
    public static void protectedPotsAndTemplatesCannotExtractButPlayerBlocksRemainUsable(GameTestHelper h) {
        var level=h.getLevel();var registry=FarmInstanceRegistry.get(level.getServer());var owner=UUID.randomUUID();
        var farm=registry.createFarm(owner,"Interaction","Interaction test",FarmType.STANDARD);
        var data=BuildingWorldData.get(level.getServer());var family=PrefabDefinitions.get(PrefabDefinitions.COOP);var tier=family.tier(1);
        var anchor=h.absolutePos(new BlockPos(18,1,22));var rotation=PrefabDefinitions.rotation(Direction.SOUTH);
        var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),family.id(),BuildingRecord.Mode.PREFAB,
                level.dimension().location(),anchor,PrefabDefinitions.world(tier.manager(),tier.anchor(),anchor,rotation),Direction.SOUTH,
                PrefabDefinitions.transform(family.reservation(),anchor,rotation));
        try {
            var permit=UUID.randomUUID();data.recordPurchase(permit,farm.getInstanceId(),true,family.id());
            h.assertTrue(data.beginPrefab(record,permit,10)==BuildingWorldData.Result.SUCCESS,"Start interaction fixture order");
            BuildingPlacementService.scaffold(level,data.find(record.id()));
            data.constructionDay(11,true);data.constructionDay(12,true);data.constructionDay(13,true);
            BuildingPlacementService.finish(level,data.find(record.id()));
            var nativeCells=BuildingTransfer.nativeCells(level,record,1);
            var template=nativeCells.keySet().stream().filter(p->level.getBlockState(p).getBlock() instanceof com.stardew.craft.templates.TemplateBlock).findFirst().orElseThrow();
            var pot=nativeCells.keySet().stream().filter(p->!p.equals(template)&&!p.equals(record.manager())).findFirst().orElseThrow();
            // Inject a potted flower into a native component position to exercise the same mask.
            BuildingProtection.internal(()->level.setBlock(pot,Blocks.POTTED_POPPY.defaultBlockState(),3));
            var player=FakePlayerFactory.get(level,new GameProfile(owner,"Interaction"));
            for(boolean creative:new boolean[]{false,true})for(boolean shift:new boolean[]{false,true})for(var hand:InteractionHand.values()) {
                player.getAbilities().instabuild=creative;player.setShiftKeyDown(shift);
                for(var pos:java.util.List.of(pot,template)) {
                    var before=level.getBlockEntity(pos) instanceof com.stardew.craft.templates.TemplateBlockEntity be?be.material():null;
                    int flowers=player.getInventory().countItem(net.minecraft.world.item.Items.POPPY);
                    var event=new PlayerInteractEvent.RightClickBlock(player,hand,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                    MinecraftForge.EVENT_BUS.post(event);
                    h.assertTrue(event.isCanceled(),"Protected extraction must cancel before normal hooks");
                    h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.POPPY)==flowers,"No extracted flower");
                    if(before!=null)h.assertTrue(before.equals(((com.stardew.craft.templates.TemplateBlockEntity)level.getBlockEntity(pos)).material()),"Template material remains");
                }
            }
            h.assertTrue(level.getBlockState(pot).is(Blocks.POTTED_POPPY),"Potted flower remains");
            var free=BlockPos.betweenClosedStream(record.claim().min(),record.claim().maxInclusive())
                    .filter(p->level.getBlockState(p).isAir()&&!BuildingProtection.protects(level,p)).map(BlockPos::immutable).findFirst().orElseThrow();
            level.setBlock(free,Blocks.POTTED_POPPY.defaultBlockState(),3);
            h.assertTrue(!BuildingProtection.deniesInteraction(level,free),"Player pot stays usable");
            level.setBlock(free,level.getBlockState(template),3);
            h.assertTrue(!BuildingProtection.deniesInteraction(level,free),"Player template stays editable");
            h.assertTrue(!BuildingProtection.deniesInteraction(level,record.manager()),"Manager remains usable");
        } finally {data.demolish(record.id());registry.deleteFarm(owner);BuildingProtection.clearMasks();}
        h.succeed();
    }
}
