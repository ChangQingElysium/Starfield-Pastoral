package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.*;
import com.stardew.craft.mining.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_skull_runtime")
@PrefixGameTestTemplate(false)
public final class SkullCavernRuntimeGameTests {
    @GameTest(templateNamespace="stardewcraft_skull_runtime",template="ring_utilities",timeoutTicks=2000)
    public static void approvedSkullFloorsPlacePopulateAndKeepTheirLadders(GameTestHelper h) {
        var level=h.getLevel();
        var floors=new ArrayList<Integer>();floors.add(-1);
        for(int f=121;f<=160;f++)floors.add(f);
        floors.addAll(List.of(220,320,420));
        for(int i=0;i<floors.size();i++) {
            int f=floors.get(i);
            h.runAtTickTime(2+i*3L,()->{
                var manager=MineFloorDataManager.get(level);var old=manager.getFloorData(f);
                if(old!=null)old.setGenerationVersion(0);
                OrdinaryMineRuntime.ensure(level,f);
                var layout=OrdinaryMineLayout.load(level,f);var data=manager.getFloorData(f);
                h.assertTrue(data.getGenerationVersion()==OrdinaryMineRuntime.VERSION,"Not generated "+f);
                var spawn=BlockPos.containing(layout.spawn(f));
                h.assertTrue(OrdinaryMineRuntime.floorAt(spawn)==f,"Wrong slot "+f);
                h.assertTrue(level.getBlockState(spawn.above()).getCollisionShape(level,spawn.above()).isEmpty(),"Blocked head "+f);
                h.assertTrue(level.getBlockState(spawn.below()).isFaceSturdy(level,spawn.below(),net.minecraft.core.Direction.UP),"No arrival support "+f);
                h.assertTrue(level.getBiome(spawn).unwrapKey().orElseThrow().location().getPath().equals("skull_cavern"),"Skull cavern inherited lava fishing biome");
                if(f<0 || data.isTreasureRoom())h.assertTrue(data.generatedStoneCount()==0 && data.getEnemyCount()==0,"Populated lobby/reward "+f);
                else h.assertTrue(data.generatedStoneCount()>0,"Empty mining floor "+f);
                if(data.isTreasureRoom()) {
                    int[] xs=f==320?new int[]{10,8}:f==420?new int[]{9,7,11}:new int[]{9};
                    for(int x:xs) {
                        var p=layout.position(f,x,9);
                        h.assertTrue(level.getBlockEntity(p) instanceof com.stardew.craft.blockentity.MineChestBlockEntity,"Missing chest "+f+"/"+x);
                        h.assertTrue(level.getBlockState(p).getValue(MineChestBlock.SPECIAL)==SkullCavernRuntime.isForcedTreasure(f),"Wrong reward chest variant "+f);
                    }
                    h.assertTrue(data.hasLadderFound(),"Treasure room has no descent "+f);
                }
                OrdinaryMineRuntime.ensure(level,f);
                h.assertTrue(manager.getFloorData(f)==data,"Second arrival reset room "+f);
            });
        }
        h.runAtTickTime(2+floors.size()*3L,h::succeed);
    }
    @GameTest(templateNamespace="stardewcraft_skull_runtime",template="ring_utilities")
    public static void everySelectableLayoutHasApprovedArchitecture(GameTestHelper h) {
        var level=h.getLevel();
        for(int f=121;f<1500;f++) {
            String name=SkullCavernRuntime.chooseLayout(level,f);
            var layout=OrdinaryMineLayout.loadNamed(level,name);
            var template=level.getStructureManager().get(new ResourceLocation("stardewcraft","mine_layouts/"+name)).orElseThrow();
            h.assertTrue(template.getSize().equals(layout.size),"Wrong template dimensions "+name);
        }
        h.assertTrue(SkullCavernRuntime.chooseLayout(level,220).equals("desert_reward_100"),"100 not forced");
        h.assertTrue(SkullCavernRuntime.chooseLayout(level,320).equals("desert_reward_200"),"200 not forced");
        h.assertTrue(SkullCavernRuntime.chooseLayout(level,420).equals("desert_reward_300"),"300 not forced");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_skull_runtime",template="ring_utilities")
    public static void shaftsAreDailyDeterministicAndCannotSkipFirstHundred(GameTestHelper h) {
        boolean longFall=false;
        for(int day=1;day<200;day++)for(int f=121;f<240;f++) {
            int drop=SkullCavernRuntime.shaftLevels(f,98721,day);
            h.assertTrue(drop==SkullCavernRuntime.shaftLevels(f,98721,day),"Time-of-day rerolled shaft");
            h.assertTrue(drop>=1 && drop<=15,"Invalid shaft drop");
            h.assertTrue(f>=220 || f+drop<=220,"Skipped Qi checkpoint");
            if(drop>8)longFall=true;
        }
        h.assertTrue(longFall,"Missing deep shaft branch");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_skull_runtime",template="ring_utilities")
    public static void deepStoneDistributionUsesMappedSkullHealthAndIridium(GameTestHelper h) {
        var r=RandomSource.create(87);int iridium=0,copper=0;
        for(int i=0;i<30000;i++) {
            var n=OrdinaryMinePopulation.chooseStone(520,0,0,true,r);
            h.assertTrue(MineStoneMining.stateForSource(n.source(),n.health()).isPresent(),"Missing node "+n);
            if(n.source().equals("751")){copper++;h.assertTrue(n.health()==2,"Skull copper uses ordinary 3 HP");}
            if(n.source().equals("765")){iridium++;h.assertTrue(n.health()==16,"Wrong iridium HP");}
            if(Set.of("32","38","40","42").contains(n.source()))h.assertTrue(n.health()==5,"Skull stone uses lava HP");
        }
        h.assertTrue(iridium>100 && copper>0,"Missing depth ore branches");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_skull_runtime",template="ring_utilities")
    public static void eachTreasureInstanceHasIndependentPersistentPersonalClaims(GameTestHelper h) {
        var m=new MineRewardClaimManager();var a=UUID.randomUUID();var b=UUID.randomUUID();
        int first=m.allocateTemporaryKey(),second=m.allocateTemporaryKey();
        m.markOpened(a,first);m.markClaimed(a,first);
        var copy=MineRewardClaimManager.load(m.save(new net.minecraft.nbt.CompoundTag()),h.getLevel().registryAccess());
        int next=copy.allocateTemporaryKey();
        h.assertTrue(first<0 && second<0 && next<second,"Claim IDs collided after restart");
        h.assertTrue(copy.hasOpened(a,first) && !copy.hasOpened(b,first) && !copy.hasOpened(a,second),"Treasure lid/claim leaked");
        copy.forgetTemporaryKeys(Set.of(first));
        h.assertTrue(!copy.hasOpened(a,first) && !copy.hasClaimed(a,first),"Retired generation retained claims");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_skull_runtime",template="ring_utilities")
    public static void sourceTreasureSlotsDoNotSubstituteBeerOrGeodes(GameTestHelper h) {
        var r=RandomSource.create(72);
        h.assertTrue(SkullCavernTreasurePool.rollSlot(0,r,null).is(com.stardew.craft.item.ModItems.MEGA_BOMB.get()),"Mega bomb mapped to ale");
        h.assertTrue(SkullCavernTreasurePool.rollSlot(1,r,null).is(com.stardew.craft.item.ModItems.BOMB_ITEM.get()),"Bomb mapped to beer");
        h.assertTrue(SkullCavernTreasurePool.rollSlot(5,r,null).is(com.stardew.craft.item.ModItems.WARP_TOTEM_FARM.get()),"Wrong warp totem");
        h.assertTrue(SkullCavernTreasurePool.rollSlot(19,r,null).is(com.stardew.craft.item.ModItems.SEED_MAKER.get()),"Wrong machine");
        h.assertTrue(SkullCavernTreasurePool.rollSlot(24,r,null).is(com.stardew.craft.item.ModItems.AUTO_PETTER.get()),"Auto petter missing");
        h.assertTrue(SkullCavernTreasurePool.rollSlot(25,r,null).is(com.stardew.craft.item.ModItems.DARK_COWBOY_HAT.get()),"Dark cowboy missing");h.succeed();
    }
}
