package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.*;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_ordinary_mine")
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OrdinaryMineRuntimeGameTests {
    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities",timeoutTicks=3000)
    public static void everyApprovedOrdinaryFloorPlacesAndPopulates(GameTestHelper h) {
        var level=h.getLevel(); // GameTestServer intentionally creates only its overworld.
        for(int floor=0;floor<=120;floor++) {
            final int f=floor;
            h.runAtTickTime(2L+floor*3L,()->{
                var previous=MineFloorDataManager.get(level).getFloorData(f);
                if(previous!=null) previous.setGenerationVersion(0); // Exercise placement on every run, including saved test worlds.
                OrdinaryMineRuntime.ensure(level,f);
                var layout=OrdinaryMineLayout.load(level,f);
                var data=MineFloorDataManager.get(level).getFloorData(f);
                h.assertTrue(data!=null && data.getGenerationVersion()==OrdinaryMineRuntime.VERSION,"Floor not generated: "+f);
                int count=0;
                for(var cell:layout.cells) {
                    BlockPos pos=layout.position(f,cell.x(),cell.z());
                    if(level.getBlockState(pos).getBlock() instanceof MineStoneBlock) {
                        count++;h.assertTrue(data.isGeneratedStone(pos),"Untracked stone on "+f+" "+cell);
                        h.assertTrue(cell.candidate() || cell.solid(),"Node outside original solid floor: "+f);
                    }
                }
                h.assertTrue(count==data.generatedStoneCount() && data.getStonesLeft()<=count,"Actual stone count differs on floor "+f+": "+count+" vs "+data.getStonesLeft());
                if(f%10==0) h.assertTrue(count==0 && data.getEnemyCount()==0,"Reward/lobby populated: "+f);
                else h.assertTrue(count>0,"Empty mining floor "+f);
                var spawn=BlockPos.containing(layout.spawn(f));
                h.assertTrue(level.getBlockState(spawn.above()).getCollisionShape(level,spawn.above()).isEmpty(),"Blocked arrival head: "+f);
                h.assertTrue(level.getBlockState(spawn.below()).isFaceSturdy(level,spawn.below(),net.minecraft.core.Direction.UP),"Arrival has no floor: "+f);
                if(MineChestLootTable.isChestFloor(f) && f>0) {
                    var chest=layout.position(f,9,f%20==0 && f%40!=0?13:9);
                    h.assertTrue(level.getBlockEntity(chest) instanceof com.stardew.craft.blockentity.MineChestBlockEntity,"Missing original chest coordinate on "+f);
                }
                if(f==30) {
                    h.assertTrue(!level.getBlockState(layout.position(f,9,9)).is(ModBlocks.MINE_CHEST.get()),"Floor 30 has a reward chest");
                    h.assertTrue(level.getBlockState(spawn.below()).is(ModBlocks.MINE_EARTH_DARK_SOIL.get()),"Floor 30 reused normal earth soil");
                }
                if(f==0) {
                    var bounds=new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(layout.origin(0)),net.minecraft.world.phys.Vec3.atLowerCornerOf(layout.origin(0).offset(layout.size)));
                    h.assertTrue(level.getEntitiesOfClass(com.stardew.craft.entity.minecart.MinecartStationEntity.class,bounds).size()==1,"Lobby station missing or duplicated");
                    var arrival=layout.position(0,13,11);
                    h.assertTrue(level.getBlockState(arrival.above()).getCollisionShape(level,arrival.above()).isEmpty(),"Minecart arrival obstructed");
                }
                // Idempotent ensure must preserve both the population and a player's edit.
                var retained=data;OrdinaryMineRuntime.ensure(level,f);
                h.assertTrue(MineFloorDataManager.get(level).getFloorData(f)==retained,"Repeated entry rebuilt floor "+f);
                if(f==20 || f==60 || f==100) {
                    var water=layout.metadata.getAsJsonArray("water_tiles");h.assertTrue(water!=null && !water.isEmpty(),"Missing fishing pool: "+f);
                    var t=water.get(0).getAsJsonArray();var p=layout.position(f,t.get(0).getAsInt(),t.get(1).getAsInt()).below();
                    h.assertTrue(!level.getFluidState(p).isEmpty(),"Fishing pool is dry: "+f);
                    h.assertTrue(level.getBiome(p).unwrapKey().orElseThrow().location().getPath().equals("mines_"+f),"Wrong fishing biome: "+f);
                }
            });
        }
        h.runAtTickTime(367,()->{
            var layout=OrdinaryMineLayout.load(level,12);var origin=layout.origin(12);
            for(var cell:layout.cells) {
                var p=layout.position(12,cell.x(),cell.z());
                if(level.getBlockState(p.below()).is(ModBlocks.MINE_EARTH_SOIL.get())) {
                    int x=p.getX()-origin.getX(),y=p.getY()-1-origin.getY(),z=p.getZ()-origin.getZ();
                    var data=MineFloorDataManager.get(level).getFloorData(12);
                    h.assertTrue(data.isArchitecture(x+layout.size.getX()*(z+layout.size.getZ()*y)),"Authored floor not protected");break;
                }
            }
            h.succeed();
        });
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void rewardHistoryIsPerPlayerAndSurvivesReload(GameTestHelper h) {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var manager=new MineRewardClaimManager();
        manager.markOpened(a,20);
        h.assertTrue(manager.hasOpened(a,20) && !manager.hasOpened(b,20),"Lid leaks between players");
        h.assertTrue(!manager.hasClaimed(a,20),"Opening consumed reward");
        manager.markClaimed(a,20);
        var reloaded=MineRewardClaimManager.load(manager.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(reloaded.hasOpened(a,20) && reloaded.hasClaimed(a,20) && !reloaded.hasClaimed(b,20),"Persistent claims leak/lost");
        var motion=new MineChestLidMotion();motion.snap(true);
        h.assertTrue(motion.angle(0)==90,"Re-entering an opened chest replays opening");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void sourceNodeSelectionHasNoSpecialAreaOrOldWallNodes(GameTestHelper h) {
        var random=RandomSource.create(91377);
        for(int floor=1;floor<120;floor++) for(int i=0;i<300;i++) {
            var node=OrdinaryMinePopulation.chooseStone(floor,0,0,false,random);
            h.assertTrue(MineStoneMining.stateForSource(node.source(),node.health()).isPresent(),"Unmapped source "+node);
            h.assertTrue(!Set.of("95","845","846","847","849","850","765").contains(node.source()),"Special node in ordinary mine: "+node);
            if(floor==1 || floor%5==0) h.assertTrue(!Set.of("751","290","764").contains(node.source()),"Metal roll on excluded elevator floor");
            if(node.source().equals("290")) h.assertTrue(node.health()==4,"Iron wrong HP");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void floorLadderReplacesSoilOnlyAndTracksSuccessfulPlacement(GameTestHelper h) {
        var level=h.getLevel();var data=new MineFloorData();
        BlockPos p=h.absolutePos(new BlockPos(3,3,3));
        level.setBlock(p.below(),ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(),3);
        level.setBlock(p,Blocks.AIR.defaultBlockState(),3);level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(OrdinaryMineRuntime.placeLadder(level,1,p,data),"Valid soil refused ladder");
        h.assertTrue(level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(ModBlocks.MINE_LADDER.get()),"Ladder placed on top of soil");
        h.assertTrue(data.getLadderPos().equals(p.below()),"Ladder position is not soil layer");
        var rejected=new MineFloorData();level.setBlock(p.below(),ModBlocks.MINE_EARTH_WALL.get().defaultBlockState(),3);
        h.assertTrue(!OrdinaryMineRuntime.placeLadder(level,1,p,rejected) && !rejected.hasLadderFound(),"Wall failure consumed ladder flag");
        h.assertTrue(!OrdinaryMineRuntime.placeLadder(level,120,p,rejected),"Exit below floor 120");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void persistentCachesAndDisconnectLeases(GameTestHelper h) {
        var p=new OrdinaryMineProgress();var player=UUID.randomUUID();
        p.initializePlatforms(45,5);p.breakPlatform(45);p.initializePlatforms(45,99);
        p.emptyCache(OrdinaryMineRuntime.cacheKey(42,new BlockPos(1,66,8400)));
        p.disconnect(player,43,5);
        var copy=OrdinaryMineProgress.load(p.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.platformLimit(45)==4,"Regeneration reset platform container budget");
        h.assertTrue(copy.cacheEmpty(OrdinaryMineRuntime.cacheKey(42,new BlockPos(1,66,8400))),"Coal cache refilled after reload");
        h.assertTrue(copy.deepestDisconnected(5)==43,"Disconnected floor not retained");
        copy.reconnect(player);h.assertTrue(copy.deepestDisconnected(5)==0,"Reconnected player still holds floor");
        copy.disconnect(player,70,5);h.assertTrue(copy.deepestDisconnected(6)==0,"Yesterday's floor retained indefinitely");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void populationCountersSurviveReloadAndRemoveOnce(GameTestHelper h) {
        var d=new MineFloorData();var initial=new BlockPos(1,66,214);var cluster=initial.east();var mob=UUID.randomUUID();
        d.addGeneratedStone(initial);d.addGeneratedStone(cluster,false);d.addGeneratedMonster(mob);d.markArchitecture(721);
        var copy=MineFloorData.fromNBT(d.toNBT());
        h.assertTrue(copy.generatedStoneCount()==2 && copy.getStonesLeft()==1,"Cluster inflated source stone budget");
        h.assertTrue(copy.removeGeneratedStone(initial) && !copy.removeGeneratedStone(initial),"Stone consumed twice");
        h.assertTrue(copy.removeGeneratedMonster(mob) && !copy.removeGeneratedMonster(mob) && copy.getEnemyCount()==0,"Monster death double-counted");
        h.assertTrue(copy.isArchitecture(721) && !copy.isArchitecture(-1),"Architecture mask lost");
        copy.setLadderFound(true);h.assertTrue(!copy.hasStoneLadderSpawned(),"Kill ladder suppressed stone discovery");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void approvedAlternateLayoutsAndPlaceholderRewards(GameTestHelper h) {
        var level=h.getLevel();int count=0;
        for(var id:level.getServer().getResourceManager().listResources("mine_layouts",r->r.getPath().endsWith(".json")).keySet()) {
            String name=id.getPath().substring("mine_layouts/".length(),id.getPath().length()-5);
            if(!name.startsWith("extra_") || name.contains("desert"))continue;
            var layout=OrdinaryMineLayout.loadNamed(level,name);
            var template=level.getStructureManager().get(new net.minecraft.resources.ResourceLocation("stardewcraft","mine_layouts/"+name)).orElseThrow();
            h.assertTrue(template.getSize().equals(layout.size),"Alternate missing/mismatched: "+name);count++;
        }
        h.assertTrue(count>0,"No approved alternate layouts packaged");
        var a=MineChestLootTable.getRewardForFloor(level,null,40,new Random(1));
        var b=MineChestLootTable.getRewardForFloor(level,null,70,new Random(1));
        h.assertTrue(a!=null && a.is(com.stardew.craft.item.ModItems.SLINGSHOT.get()),"40F slingshot reward missing");
        h.assertTrue(b!=null && b.is(com.stardew.craft.item.ModItems.MASTER_SLINGSHOT.get()),"70F master slingshot reward missing");
        h.assertTrue(OrdinaryMineEncounters.flyingMonster(10).isEmpty() && OrdinaryMineEncounters.flyingMonster(21).equals("fly")
                && OrdinaryMineEncounters.flyingMonster(38).equals("bat") && OrdinaryMineEncounters.flyingMonster(63).equals("frost_bat")
                && OrdinaryMineEncounters.flyingMonster(98).equals("lava_bat"),"Flying encounter theme mismatch");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities")
    public static void realChestInventoriesRemainIndependentAfterReplacement(GameTestHelper h) {
        var level=h.getLevel();var pos=new BlockPos(256,70,4020);
        var a=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Mine reward A"));
        var b=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Mine reward B"));
        level.setBlock(pos,ModBlocks.MINE_CHEST.get().defaultBlockState(),3);
        var chest=(com.stardew.craft.blockentity.MineChestBlockEntity)level.getBlockEntity(pos);
        h.assertTrue(chest!=null,"Chest block entity missing");
        var menuA=chest.createMenu(1,a.getInventory(),a);var menuB=chest.createMenu(2,b.getInventory(),b);
        h.assertTrue(menuA.getSlot(13).hasItem() && menuB.getSlot(13).hasItem(),"Each player did not receive a reward");
        menuA.getSlot(13).remove(1);menuA.getSlot(13).setChanged();
        h.assertTrue(MineRewardClaimManager.get(level).hasClaimed(a.getUUID(),20),"Taking reward did not record claim");
        h.assertTrue(menuB.getSlot(13).hasItem() && !MineRewardClaimManager.get(level).hasClaimed(b.getUUID(),20),"A consumed B's reward");
        h.assertTrue(!chest.getUpdateTag(level.registryAccess()).contains("PlayerInventories"),"Private inventories sent in public update tag");
        menuA.removed(a);menuB.removed(b);
        level.removeBlock(pos,false);level.setBlock(pos,ModBlocks.MINE_CHEST.get().defaultBlockState(),3);
        var fresh=(com.stardew.craft.blockentity.MineChestBlockEntity)level.getBlockEntity(pos);
        h.assertTrue(fresh.getOrCreatePlayerInventory(a.getUUID()).get(13).isEmpty(),"Regeneration duplicated claimed reward");
        h.assertTrue(!fresh.getOrCreatePlayerInventory(b.getUUID()).get(13).isEmpty(),"Regeneration consumed unclaimed reward");
        level.removeBlock(pos,false);h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities",timeoutTicks=300)
    public static void completedLightQueueIncludesRemovedEmitters(GameTestHelper h) {
        var level=h.getLevel();
        // Use the real layout bounds and asynchronous light barrier without generating another map.
        var layout=OrdinaryMineLayout.load(level,10);var source=layout.position(10,9,9).above(5);
        level.setBlock(source,Blocks.GLOWSTONE.defaultBlockState(),3);
        OrdinaryMineRuntime.refreshLights(level,layout,10).thenRun(()->{
            h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,source)==15,"New emitter light never propagated");
            level.setBlock(source,Blocks.AIR.defaultBlockState(),3);
            OrdinaryMineRuntime.refreshLights(level,layout,10).thenRun(()->{
                h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,source)<15,"Removed emitter retained maximum light");h.succeed();
            }).exceptionally(error->{h.fail(error.toString());return null;});
        }).exceptionally(error->{h.fail(error.toString());return null;});
    }

    @GameTest(templateNamespace="stardewcraft_ordinary_mine",template="ring_utilities",timeoutTicks=700)
    public static void replacingDifferentLayoutClearsOldFootprint(GameTestHelper h) {
        h.runAtTickTime(390,()->{
            var level=h.getLevel();int floor=12;
            var normal=OrdinaryMineLayout.loadNamed(level,OrdinaryMineLayout.nameForFloor(floor));
            var old=OrdinaryMineLayout.loadNamed(level,"extra_earth_41");
            var normalBounds=new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(normal.origin(floor)),net.minecraft.world.phys.Vec3.atLowerCornerOf(normal.origin(floor).offset(normal.size)));
            BlockPos outside=null;
            for(var p:BlockPos.betweenClosed(old.origin(floor),old.origin(floor).offset(old.size).offset(-1,-1,-1)))
                if(!normalBounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(p))) {outside=p.immutable();break;}
            h.assertTrue(outside!=null,"Fixture layouts unexpectedly have identical footprints");
            level.setBlock(outside,Blocks.GOLD_BLOCK.defaultBlockState(),3);
            var previous=new MineFloorData();previous.setLayoutName(old.name);
            MineFloorDataManager.get(level).setFloorData(floor,previous);
            OrdinaryMineRuntime.ensure(level,floor);
            h.assertTrue(level.getBlockState(outside).isAir(),"Previous layout left blocks outside new footprint");h.succeed();
        });
    }

}
