package com.stardew.craft.gametest;

import com.stardew.craft.fishpond.service.*;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_pond_prefab")
@PrefixGameTestTemplate(false)
public final class FishPondHusbandryGameTests {
    private static ItemStack item(String name){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft:"+name)));}
    private static FishPondRecord pond(GameTestHelper h) {
        var data=FishPondWorldData.get(h.getLevel());var p=h.absolutePos(new BlockPos(2,2,2));
        return data.getPond(data.createPond(UUID.randomUUID(),h.getLevel().dimension().location().toString(),p,p.offset(3,0,0),Set.of(),Set.of(p.asLong()),p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ())).orElseThrow();
    }
    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty")
    public static void fishingToZeroKeepsSpeciesAndClearResetsCracker(GameTestHelper h) {
        var p=pond(h);var l=h.getLevel();var data=FishPondWorldData.get(l);
        try {
            h.assertTrue(FishPondHusbandry.offer(l,p,item("sturgeon"),null,1)==FishPondHusbandry.ItemAbsorbResult.FISH_ACCEPTED,"Initial fish rejected");
            p.setGoldenAnimalCracker(true);p.setLastUnlockedPopulationGate(7);p.setMaxPopulation(7);
            p.setCurrentPopulation(0); // CatchFish retains fishType even after the last occupant.
            var other=item("carp");
            h.assertTrue(FishPondHusbandry.offer(l,p,other,null,1)==FishPondHusbandry.ItemAbsorbResult.WRONG_FISH && other.getCount()==1,"Empty population changed species without clearing");
            p.setOutputItemId("stardewcraft:roe");p.setOutputCount(2);p.setOutputFishType("stardewcraft:sturgeon");
            var output=FishPondHusbandry.createOutputStack(p);
            FishPondHusbandry.clear(l,p);
            h.assertTrue(!p.goldenAnimalCracker() && p.lastUnlockedPopulationGate()==0 && p.fishTypeId().isBlank(),"Clear retained upgrades/cracker/species");
            h.assertTrue(ItemStack.matches(output,FishPondHusbandry.createOutputStack(p)),"Clear lost output or its roe source components");
            h.assertTrue(FishPondHusbandry.offer(l,p,item("carp"),null,1)==FishPondHusbandry.ItemAbsorbResult.FISH_ACCEPTED,"Cleared pond cannot change species");
        } finally {data.removePond(p.pondId());}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty")
    public static void requestUnlockAndDailyClockSurviveReload(GameTestHelper h) {
        var p=pond(h);var l=h.getLevel();var data=FishPondWorldData.get(l);
        try {
            FishPondHusbandry.offer(l,p,item("sturgeon"),null,1);
            h.assertTrue(p.maxPopulation()==1,"Sturgeon initial capacity must be one");
            int days=FishPondDataService.get().resolveSpawnTime(p);
            for(int day=1;day<=days;day++)FishPondHusbandry.applySingleDay(l,data,p,day);
            h.assertTrue(p.neededItemCount()>0 && p.currentPopulation()==1,"Gate request was not created");
            var needed=FishPondQualifiedItemService.createItemStack(p.neededItemId(),p.neededItemCount());
            h.assertTrue(!needed.isEmpty(),"Gate item missing from registry");
            FishPondHusbandry.offer(l,p,needed,null,needed.getCount());
            h.assertTrue(p.hasCompletedRequest()&&p.maxPopulation()==3&&p.daysSinceSpawn()==0,"Request did not unlock source capacity");
            var loaded=FishPondRecord.load(p.save());
            h.assertTrue(loaded.lastUpdateDay()==days && loaded.maxPopulation()==3,"Saved lifecycle lost state");
            FishPondHusbandry.applySingleDay(l,data,loaded,days);
            h.assertTrue(loaded.daysSinceSpawn()==0,"Duplicate day ran again after reload");
            FishPondHusbandry.applySingleDay(l,data,loaded,days+1);
            h.assertTrue(loaded.daysSinceSpawn()==1&&!loaded.hasCompletedRequest(),"Next day failed to persist spawning clock/clear request");
        } finally {data.removePond(p.pondId());}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty")
    public static void clearReleasesEveryFish(GameTestHelper h) {
        var p=pond(h);var l=h.getLevel();var data=FishPondWorldData.get(l);
        try {
            p.setFishTypeId("stardewcraft:carp");p.setCurrentPopulation(3);
            var bounds=new net.minecraft.world.phys.AABB(p.managerPos()).inflate(1,2,1);
            int before=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds,e->e.getItem().is(item("carp").getItem())).stream().mapToInt(e->e.getItem().getCount()).sum();
            FishPondHusbandry.clear(l,p);
            int after=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,bounds,e->e.getItem().is(item("carp").getItem())).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(after-before==3 && p.currentPopulation()==0,"Clear deleted fish instead of releasing them");
        }finally{data.removePond(p.pondId());}
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty")
    public static void navigationUsesDepthAndCannotCrossAir(GameTestHelper h) {
        Set<Long> water=new HashSet<>();
        for(var p:BlockPos.betweenClosed(new BlockPos(0,0,0),new BlockPos(4,3,4)))water.add(p.asLong());
        // Wall open only at the top: a valid path must climb and descend.
        for(var p:BlockPos.betweenClosed(new BlockPos(2,0,0),new BlockPos(2,2,4)))water.remove(p.asLong());
        var space=new PondSwimSpace(water,p->true);var a=new Vec3(.5,.5,2.5);var b=new Vec3(4.5,.5,2.5);
        h.assertTrue(!space.clear(a,b),"Fish swam through a dry wall");
        var route=space.route(a,b);h.assertTrue(!route.isEmpty() && route.stream().anyMatch(p->p.y>3),"Navigation stayed in 2D");
        Vec3 previous=a;for(var p:route){h.assertTrue(space.clear(previous,p),"Route clips water boundary");previous=p;}
        h.assertTrue(!space.fits(new Vec3(.15,.5,.5)),"Only fish center was checked");
        for(int z=0;z<5;z++)water.remove(new BlockPos(2,3,z).asLong());
        h.assertTrue(new PondSwimSpace(water,p->true).route(a,b).isEmpty(),"Fish crossed disconnected water volumes");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_pond_prefab",template="empty")
    public static void bundledRewardsAndRequestsResolveToRegisteredItems(GameTestHelper h) {
        var missing=new java.util.TreeSet<String>();
        for(var entry:FishPondDataService.get().snapshot()) {
            for(var output:entry.producedItems())if(FishPondQualifiedItemService.createItemStack(output.itemId(),1).isEmpty())missing.add(output.itemId());
            for(var choices:entry.populationGates().values())for(var choice:choices) {
                String id=choice.trim().split("\\s+")[0];
                if(FishPondQualifiedItemService.createItemStack(id,1).isEmpty())missing.add(id);
            }
        }
        h.assertTrue(missing.isEmpty(),"Unregistered pond items: "+missing);h.succeed();
    }
}
