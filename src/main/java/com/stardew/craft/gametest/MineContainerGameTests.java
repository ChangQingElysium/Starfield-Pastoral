package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineBarrelBlock;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.event.MineBarrelBreakHandler;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.MineContainerRewards;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_mine_assets")
@PrefixGameTestTemplate(false)
public final class MineContainerGameTests {
    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void emptyBranchCanDropSeasonSeedsAndStopsOtherRewards(GameTestHelper h) {
        try (var ignored=miningDataLevel(h)) {
        var time=StardewTimeManager.get();int season=time.getCurrentSeason(),day=time.getCurrentDay();
        var seeds=List.of(ModItems.CARROT_SEEDS.get(),ModItems.SUMMER_SQUASH_SEEDS.get(),ModItems.BROCCOLI_SEEDS.get(),ModItems.POWDER_MELON_SEEDS.get());
        try {
            for(int s=0;s<4;s++) {
                time.setCurrentSeason(s);time.setCurrentDay(10);
                var r=new Draws(.1,.05, .01,.9); // empty branch, seeds, +1 lucky seed, stop
                var roll=MineContainerRewards.roll(null,r);
                h.assertTrue(!roll.continueToContents()&&roll.items().size()==1,"Season seeds also rolled the theme table");
                h.assertTrue(roll.items().getFirst().is(seeds.get(s))&&roll.items().getFirst().getCount()==3,"Wrong seasonal seeds or quantity");
                r.exhausted(h);
                time.setCurrentDay(s==0?24:21);
                roll=MineContainerRewards.roll(null,new Draws(.1,.05,.9));
                h.assertTrue(roll.items().getFirst().is(seeds.get((s+1)%4)),"Late season did not advance the seed type");
            }
            var empty=MineContainerRewards.roll(null,new Draws(.1,.9));
            h.assertTrue(!empty.continueToContents()&&empty.items().isEmpty(),"Empty cache manufactured a fallback reward");
        } finally {time.setCurrentSeason(season);time.setCurrentDay(day);}
        h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void mysteryBoxRequiresUnlockAndUsesBreakersMasteryAndLuck(GameTestHelper h) {
        try (var ignored=miningDataLevel(h)) {
        var time=StardewTimeManager.get();int year=time.getCurrentYear(),season=time.getCurrentSeason(),day=time.getCurrentDay();
        try {
            time.setCurrentYear(1);time.setCurrentSeason(0);time.setCurrentDay(1);
            var a=player(h,"box novice");var data=PlayerDataManager.getPlayerData(a);
            var locked=new Draws(.5,.01); // .01 is the inactive Qi-bean check, not a mystery-box roll.
            h.assertTrue(MineContainerRewards.roll(a,locked).items().isEmpty(),"Mystery boxes dropped before unlock");locked.exhausted(h);
            data.addMailFlag("sawQiPlane");data.setDailyLuckForDate(0,112);
            h.assertTrue(MineContainerRewards.roll(a,new Draws(.5,.9,.001)).items().getFirst().is(ModItems.MYSTERY_BOX.get()),"Unlocked ordinary box absent");
            h.assertTrue(MineContainerRewards.roll(a,new Draws(.5,.9,.006)).items().isEmpty(),"Base mystery chance lost the .66 multiplier");
            data.setDailyLuckForDate(.1,112);
            h.assertTrue(!MineContainerRewards.roll(a,new Draws(.5,.9,.006)).items().isEmpty(),"Daily luck no longer affects mystery boxes");
            data.addMasteryExp(1000000);h.assertTrue(data.claimMasteryReward(SkillType.FORAGING),"Test could not grant foraging mastery");
            h.assertTrue(MineContainerRewards.roll(a,new Draws(.5,.9,.001)).items().getFirst().is(ModItems.GOLDEN_MYSTERY_BOX.get()),"Foraging mastery still gives an ordinary box");
            var b=player(h,"other novice");PlayerDataManager.getPlayerData(b).addMailFlag("sawQiPlane");
            h.assertTrue(MineContainerRewards.roll(b,new Draws(.5,.9,.001)).items().getFirst().is(ModItems.MYSTERY_BOX.get()),"Another player's mastery leaked into this reward");
        } finally {time.setCurrentYear(year);time.setCurrentSeason(season);time.setCurrentDay(day);}
        h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void rareRewardsKeepMasteryAndDayGatesAndSkipUnmappedFurniture(GameTestHelper h) {
        try (var ignored=miningDataLevel(h)) {
        var time=StardewTimeManager.get();int year=time.getCurrentYear(),season=time.getCurrentSeason(),day=time.getCurrentDay();
        try {
            time.setCurrentYear(1);time.setCurrentSeason(0);time.setCurrentDay(3);
            var p=player(h,"rare cache");var data=PlayerDataManager.getPlayerData(p);
            data.addMasteryExp(1000000);data.claimMasteryReward(SkillType.FARMING);
            // Nonempty, inactive Qi, cracker, cosmetic -> furniture -> rare furniture (unmapped), skill book.
            var r=new Draws(.5,.9,.001,.001,.1,.01,.0001);
            var drops=MineContainerRewards.roll(p,r).items();r.exhausted(h);
            h.assertTrue(drops.size()==2&&drops.get(0).is(ModItems.GOLDEN_ANIMAL_CRACKER.get())
                    &&drops.get(1).is(ModItems.BOOKS.get("skill_book_0").get()),"Rare rewards were substituted, omitted, or reordered");
            time.setCurrentDay(2);
            r=new Draws(.5,.9,.9);
            h.assertTrue(MineContainerRewards.roll(p,r).items().isEmpty(),"Day-two cache dropped a cosmetic/book");r.exhausted(h);
        } finally {time.setCurrentYear(year);time.setCurrentSeason(season);time.setCurrentDay(day);}
        h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void containerSeedIgnoresGlobalDrawsButChangesWithDateAndTile(GameTestHelper h) {
        try (var ignored=miningDataLevel(h)) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,5));
        var time=StardewTimeManager.get();int day=time.getCurrentDay();
        var baseline=MineContainerRewards.random(level,pos,1);
        double expected=baseline.nextDouble();
        for(int i=0;i<100;i++)level.random.nextDouble();
        h.assertTrue(MineContainerRewards.random(level,pos,1).nextDouble()==expected,"Unrelated world RNG changed the cache");
        h.assertTrue(MineContainerRewards.random(level,pos.east(),1).nextDouble()!=expected,"Different tiles share one cache roll");
        try {time.setCurrentDay(day+1);h.assertTrue(MineContainerRewards.random(level,pos,1).nextDouble()!=expected,"Next-day cache retained the same seed");}
        finally {time.setCurrentDay(day);}
        h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="construction_site")
    public static void mainExtensionAndBombUseActualBreakerInsteadOfNearbyPlayer(GameTestHelper h) throws Exception {
        try (var ignored=miningDataLevel(h)) {
        var level=h.getLevel();var a=player(h,"cache veteran");var b=player(h,"cache novice");
        MiningDataManager.getPlayerData(a).setCurrentFloor(120);
        var pos=findGemSlot(h,a);var area=new AABB(pos).inflate(2);
        b.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);level.players().add(b);
        try {
            place(h,pos,false);MineBarrelBlock.breakBy(level,pos.above(),a);
            h.assertTrue(items(h,area).stream().anyMatch(s->s.is(ModItems.QUARTZ.get())),"Upper-cell weapon break borrowed the nearby novice's loot state");
            h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir(),"Upper hit left half a cache");
            clearItems(h,area);place(h,pos,true);
            var state=level.getBlockState(pos);state.getBlock().playerWillDestroy(level,pos,state,b);level.destroyBlock(pos,false);
            h.assertTrue(items(h,area).stream().noneMatch(s->s.is(ModItems.QUARTZ.get())),"Normal block mining inherited the previous breaker");
            clearItems(h,area);place(h,pos,false);
            var bomb=com.stardew.craft.entity.ModEntities.STARDEW_BOMB.get().create(level);
            var destroy=bomb.getClass().getDeclaredMethod("destroyBlocksInCircle",net.minecraft.server.level.ServerLevel.class,
                    BlockPos.class,float.class,net.minecraft.world.entity.LivingEntity.class);destroy.setAccessible(true);
            // Radius zero intersects only the extension: one entire cache, with the bomb owner.
            destroy.invoke(bomb,level,pos.above(),0f,a);
            h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir(),"Bomb could not hit the upper cell");
            h.assertTrue(items(h,area).stream().anyMatch(s->s.is(ModItems.QUARTZ.get())),"Bomb loot lost its owner");
            int count=items(h,area).size();MineBarrelBlock.breakBy(level,pos,a);
            h.assertTrue(items(h,area).size()==count,"Already broken cache paid twice");
            clearItems(h,area);place(h,pos,false);a.setGameMode(GameType.CREATIVE);MineBarrelBlock.breakBy(level,pos,a);
            h.assertTrue(items(h,area).isEmpty(),"Creative break farmed cache loot");
        } finally {level.players().remove(b);clearItems(h,area);}
        h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void airSwingBreaksBothPartsButCannotBreakThroughWallsOrDuringSkillLock(GameTestHelper h) {
        try (var ignored=miningDataLevel(h)) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));
        var p=player(h,"cache swing");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);p.setYRot(0);p.setXRot(0);
        place(h,pos,false);p.swinging=true;p.swingingArm=InteractionHand.MAIN_HAND;p.swingTime=1;
        com.stardew.craft.combat.skill.WeaponSkillAnimationLock.setLock(p,level.getGameTime(),5);
        MineBarrelBreakHandler.onPlayerTick(new PlayerTickEvent.Post(p));
        h.assertTrue(!level.getBlockState(pos).isAir(),"Animation lock still allows ordinary swings");
        com.stardew.craft.combat.skill.WeaponSkillAnimationLock.clear(p);
        MineBarrelBreakHandler.onPlayerTick(new PlayerTickEvent.Post(p));
        h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir(),"Left-click miss did not hit a nearby cache");
        place(h,pos,false);MineBarrelBreakHandler.onPlayerTick(new PlayerTickEvent.Post(p));
        h.assertTrue(!level.getBlockState(pos).isAir(),"Same-tick animation packet bypassed swing recovery");
        level.setBlock(pos.north().above(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.north(),Blocks.STONE.defaultBlockState(),3);
        MineBarrelBreakHandler.breakNearbyBarrels(level,p,pos);
        h.assertTrue(!level.getBlockState(pos).isAir(),"Forced direct target bypassed the wall");
        level.removeBlock(pos.north(),false);level.removeBlock(pos.north().above(),false);
        MineBarrelBreakHandler.breakNearbyBarrels(level,p,pos);
        h.assertTrue(level.getBlockState(pos).isAir(),"Clear attack path was rejected");
        h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void authoredStrikeUsesItsActualArcEvenWithoutMonsterTargets(GameTestHelper h) {
        try (var ignored=miningDataLevel(h)) {
        var pos=h.absolutePos(new BlockPos(8,3,8));var p=player(h,"cache skill");
        p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);p.setYRot(0);p.setXRot(0);
        var behind=pos.north(3);place(h,pos,false);place(h,behind,true);
        MineBarrelBreakHandler.breakInArc(p,3,.3);
        h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Skill missed the forward cache without a monster");
        h.assertTrue(!h.getLevel().getBlockState(behind).isAir(),"Skill hit a cache behind its arc");
        MineBarrelBlock.breakBy(h.getLevel(),behind,p);h.succeed();
    }
    }

    @GameTest(templateNamespace="stardewcraft_mine_assets",template="ring_utilities")
    public static void nearbyCombatMasterCannotGrantTrinketsToAnotherBreaker(GameTestHelper h) {
        try(var ignored=miningDataLevel(h)) {
            var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));var area=new AABB(pos).inflate(2);
            var novice=player(h,"trinket novice");var master=player(h,"trinket master");
            var data=PlayerDataManager.getPlayerData(master);data.addMasteryExp(1000000);data.claimMasteryReward(SkillType.COMBAT);
            master.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);level.players().add(master);
            try {
                com.stardew.craft.item.trinket.TrinketDropService.trySpawnContainerDrop(level,pos,10000,novice);
                h.assertTrue(items(h,area).isEmpty(),"Nearby mastery granted a trinket to the novice's cache");
                com.stardew.craft.item.trinket.TrinketDropService.trySpawnContainerDrop(level,pos,10000,master);
                h.assertTrue(items(h,area).size()==1&&items(h,area).getFirst().getItem() instanceof
                        com.stardew.craft.item.trinket.StardewTrinketItem,"Combat master could not receive their trinket");
                clearItems(h,area);
                com.stardew.craft.item.trinket.TrinketDropService.trySpawnContainerDrop(level,pos,10000,null);
                h.assertTrue(items(h,area).isEmpty(),"Unowned removal borrowed a nearby player's mastery");
            } finally {level.players().remove(master);clearItems(h,area);}
        }
        h.succeed();
    }

    private interface LevelScope extends AutoCloseable { @Override void close(); }
    /** Same isolated dimension alias used by the node tests; no real mine world is loaded or changed. */
    @SuppressWarnings("unchecked")
    private static LevelScope miningDataLevel(GameTestHelper h) {
        try {
            var field=net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");field.setAccessible(true);
            var levels=(java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                    net.minecraft.server.level.ServerLevel>)field.get(h.getLevel().getServer());
            var key=com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;var previous=levels.put(key,h.getLevel());
            return () -> {if(previous==null)levels.remove(key);else levels.put(key,previous);};
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static ServerPlayer player(GameTestHelper h,String name) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);
        var time=StardewTimeManager.get();PlayerDataManager.getPlayerData(p).setDailyLuckForDate(0,
                ((time.getCurrentYear()*4)+time.getCurrentSeason())*28+time.getCurrentDay()-1);
        return p;
    }
    private static void place(GameTestHelper h,BlockPos pos,boolean crate) {
        var block=(MineBarrelBlock)(crate?ModBlocks.MINE_CRATE.get():ModBlocks.MINE_BARREL.get());
        h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var state=block.defaultBlockState().setValue(MineBuildingTheme.PROPERTY,MineBuildingTheme.EARTH);
        h.getLevel().setBlock(pos,state,3);h.assertTrue(block.placeExtensions(h.getLevel(),pos,state),"Test cache did not place");
    }
    private static BlockPos findGemSlot(GameTestHelper h,ServerPlayer p) {
        // getBounds is world-space; keep both cache parts and the loot query inside the fixture.
        var bounds=h.getBounds();
        int minX=(int)Math.floor(bounds.minX)+4,maxX=(int)Math.ceil(bounds.maxX)-5;
        int minZ=(int)Math.floor(bounds.minZ)+4,maxZ=(int)Math.ceil(bounds.maxZ)-5;
        int y=h.absolutePos(new BlockPos(0,3,0)).getY();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            var pos=new BlockPos(x,y,z);
            if(!bounds.contains(x+.5,y+.5,z+.5)||!bounds.contains(x+.5,y+1.5,z+.5)
                    ||!h.getLevel().getBlockState(pos).canBeReplaced()
                    ||!h.getLevel().getBlockState(pos.above()).canBeReplaced())continue;
            var r=MineContainerRewards.random(h.getLevel(),pos,1);
            if(MineContainerRewards.roll(p,r).continueToContents()&&r.nextDouble()<.65&&r.nextDouble()<.8&&r.nextInt(9)==5)return pos;
        }
        throw new AssertionError("Could not find original gem branch");
    }
    private static List<ItemStack> items(GameTestHelper h,AABB box) {return h.getLevel().getEntitiesOfClass(ItemEntity.class,box).stream().map(ItemEntity::getItem).toList();}
    private static void clearItems(GameTestHelper h,AABB box) {h.getLevel().getEntitiesOfClass(ItemEntity.class,box).forEach(ItemEntity::discard);}
    private static final class Draws extends LegacyRandomSource {
        private final double[] values;private int index;
        Draws(double...values){super(0);this.values=values;}
        @Override public double nextDouble(){if(index>=values.length)throw new AssertionError("Unexpected probability draw "+index);return values[index++];}
        @Override public int nextInt(int bound){return 0;}
        void exhausted(GameTestHelper h){h.assertTrue(index==values.length,"Missing probability draw: "+index+"/"+values.length);}
    }
}
