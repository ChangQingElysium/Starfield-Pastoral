package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_pond_prefab")
@PrefixGameTestTemplate(false)
public final class FishPondPrefabGameTests {
    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty",timeoutTicks=100)
    public static void importedPondHasExcavationAndNoAuthoredSoil(GameTestHelper h) {
        PrefabDefinitions.validateAssets(h.getLevel(),FishPondPrefabs.FAMILY);
        var tier=PrefabDefinitions.get(FishPondPrefabs.FAMILY).tier(1);
        var template=PrefabDefinitions.template(h.getLevel(),tier);
        h.assertTrue(template.cells().stream().filter(c->c.state().is(ModBlocks.FISH_POND_WATER.get())).count()==36,"Missing pond water layers");
        h.assertTrue(template.cells().stream().noneMatch(c->c.state().is(Blocks.DIRT)||c.state().is(Blocks.GRASS_BLOCK)||c.state().is(Blocks.PURPLE_WOOL)||c.state().is(Blocks.STRUCTURE_VOID)),"Authored ground/marker leaked into projection");
        h.assertTrue(template.retainedGround().size()==93,"Lost keep-world soil or rear padding");
        for(var facing:Direction.Plane.HORIZONTAL) {
            var anchor=h.absolutePos(new BlockPos(8,5,8));
            for(var cell:template.cells()) if(cell.state().is(ModBlocks.POND_STONE.get()))
                h.assertTrue(PrefabDefinitions.world(cell.pos(),tier.anchor(),anchor,PrefabDefinitions.rotation(facing)).getY()==anchor.getY()+1,"Rotated rim not on ground");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty",timeoutTicks=100)
    public static void allRotationsAcceptSolidGroundAndRejectAnOpenWaterBottom(GameTestHelper h) {
        var level=h.getLevel();var anchor=h.absolutePos(new BlockPos(8,5,8));
        for(var pos:BlockPos.betweenClosed(anchor.offset(-7,-4,-7),anchor.offset(7,4,7)))
            level.setBlock(pos,pos.getY()<=anchor.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2|16);
        var family=PrefabDefinitions.get(FishPondPrefabs.FAMILY);var tier=family.tier(1);
        for(var facing:Direction.Plane.HORIZONTAL) {
            var rotation=PrefabDefinitions.rotation(facing);var bounds=PrefabDefinitions.transform(family.reservation(),anchor,rotation);
            h.assertTrue(FishPondPrefabs.checkSite(level,bounds,anchor,facing,null)==null,"Solid terrain rejected for "+facing);
            var bottom=PrefabDefinitions.world(new BlockPos(2,0,3),tier.anchor(),anchor,rotation);
            level.setBlock(bottom,Blocks.AIR.defaultBlockState(),2|16);
            var issue=FishPondPrefabs.checkSite(level,bounds,anchor,facing,null);
            h.assertTrue(issue!=null && issue.issue().equals("pond_support"),"Water bottom allowed to open into a cave");
            level.setBlock(bottom,Blocks.STONE.defaultBlockState(),2|16);
            var cavity=bottom.above();level.setBlock(cavity,Blocks.AIR.defaultBlockState(),2|16);
            h.assertTrue(FishPondPrefabs.checkSite(level,bounds,anchor,facing,null)==null,"Contained cavity was rejected");
            level.setBlock(cavity,Blocks.STONE.defaultBlockState(),2|16);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty",timeoutTicks=100)
    public static void rotatingAMovePreservesFishRequestsProduceAndSignAnchor(GameTestHelper h) {
        var level=h.getLevel();var dim=level.dimension().location();
        var family=PrefabDefinitions.get(FishPondPrefabs.FAMILY);var tier=family.tier(1);
        var oldAnchor=h.absolutePos(new BlockPos(3,5,3));var newAnchor=oldAnchor.offset(8,0,8);
        var oldManager=PrefabDefinitions.world(tier.manager(),tier.anchor(),oldAnchor,PrefabDefinitions.rotation(Direction.SOUTH));
        var newManager=PrefabDefinitions.world(tier.manager(),tier.anchor(),newAnchor,PrefabDefinitions.rotation(Direction.WEST));
        var id=java.util.UUID.randomUUID();var farm=java.util.UUID.randomUUID();
        var before=new BuildingRecord(id,farm,1,FishPondPrefabs.FAMILY,BuildingRecord.Mode.PREFAB,dim,oldAnchor,oldManager,Direction.SOUTH,
                PrefabDefinitions.transform(family.reservation(),oldAnchor,PrefabDefinitions.rotation(Direction.SOUTH)),BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,0,"Pond");
        var after=new BuildingRecord(id,farm,1,FishPondPrefabs.FAMILY,BuildingRecord.Mode.PREFAB,dim,newAnchor,newManager,Direction.WEST,
                PrefabDefinitions.transform(family.reservation(),newAnchor,PrefabDefinitions.rotation(Direction.WEST)),BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,1,"Pond");
        var water=oldAnchor.offset(2,-2,2);var bucket=oldAnchor.offset(4,2,4);var net=oldAnchor.offset(2,2,0);
        var data=com.stardew.craft.fishpond.data.FishPondWorldData.get(level);
        var pondId=data.createPond(farm,dim.toString(),oldManager,bucket,java.util.Set.of(net),java.util.Set.of(water.asLong()),
                water.getX(),water.getY(),water.getZ(),water.getX(),water.getY(),water.getZ());
        try {
            var pond=data.getPond(pondId).orElseThrow();pond.setFishTypeId("stardewcraft:sturgeon");pond.setCurrentPopulation(7);
            pond.setGoldenAnimalCracker(true);pond.setOutputItemId("stardewcraft:roe");pond.setOutputCount(2);
            pond.setNeededItemId("stardewcraft:stone");pond.setNeededItemCount(3);
            data.movePrefab(before,after);data.movePrefab(before,after);
            var moved=data.getPond(pondId).orElseThrow();
            h.assertTrue(moved.managerPos().equals(newManager) && moved.bucketPos().equals(BuildingTransfer.destination(before,after,bucket)),"Move lost facility positions");
            h.assertTrue(moved.containsWater(BuildingTransfer.destination(before,after,water)),"Move lost the water mask");
            h.assertTrue(moved.currentPopulation()==7 && moved.goldenAnimalCracker() && moved.outputCount()==2 && moved.neededItemCount()==3,"Move reset fish state");
            h.assertTrue(BuildingTransfer.destination(before,after,FishPondPrefabs.signPosition(before)).equals(FishPondPrefabs.signPosition(after)),"Sign marker does not rotate with the pond");
        } finally { data.removePond(pondId); }
        h.succeed();
    }
}
