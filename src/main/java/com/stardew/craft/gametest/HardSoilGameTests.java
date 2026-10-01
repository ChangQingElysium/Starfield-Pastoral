package com.stardew.craft.gametest;

import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainFaceConnections;
import com.stardew.craft.block.utility.SprinklerBlock;
import com.stardew.craft.block.utility.SprinklerTier;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.WateringCanItem;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.common.ItemAbilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_hard_soil")
@PrefixGameTestTemplate(false)
public final class HardSoilGameTests {
    private HardSoilGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void hardSoilTillsSupportsCropsAndDropsItsOwnSubstrate(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(6, 2, 6));
        var player = FakePlayerFactory.getMinecraft(level);
        var old = player.getMainHandItem();
        try {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.HARD_SOIL.get().defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            var tilled = level.getBlockState(pos).getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
            h.assertTrue(tilled != null && tilled.is(ModBlocks.INFERTILE_FARMLAND.get()), "HardSoil did not till to infertile farmland");
            level.setBlock(pos, tilled, 3);
            for (Block crop : new Block[]{Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.PUMPKIN_STEM, ModBlocks.PARSNIP_CROP.get()})
                h.assertTrue(crop.defaultBlockState().canSurvive(level, pos.above()), "Unsupported crop " + crop);
            for (int moisture = 0; moisture < 8; moisture++) {
                var state = tilled.setValue(FarmBlock.MOISTURE, moisture);
                h.assertTrue(state.getCollisionShape(level, pos).max(Direction.Axis.Y) == 15/16d, "Wrong soil height");
                var drops = Block.getDrops(state, level, pos, null);
                h.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.HARD_SOIL.get()), "Farmland dropped another substrate");
            }
            h.assertTrue(Blocks.SAND.defaultBlockState().getToolModifiedState(context, ItemAbilities.HOE_TILL, true) == null,
                    "Vanilla sand identity changed");
            for (Block grass : new Block[]{ModBlocks.GRASS_BLOCK.get(), ModBlocks.DARK_GRASS_BLOCK.get()})
                h.assertTrue(grass.defaultBlockState().getToolModifiedState(context, ItemAbilities.HOE_TILL, true) == null, "Grass became tillable");
            level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(ModBlocks.HARD_SOIL.get().defaultBlockState().getToolModifiedState(context, ItemAbilities.HOE_TILL, true) == null,
                    "Covered sand allowed tilling");
        } finally { player.setItemInHand(InteractionHand.MAIN_HAND, old); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void sprinklerSkipsHardSoilButWateringAndAllFertilizersWork(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var center = h.absolutePos(new BlockPos(6, 3, 6));
        var infertile = center.below().north(); var earth = center.below().south();
        level.setBlock(infertile.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(earth.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(infertile, ModBlocks.INFERTILE_FARMLAND.get().defaultBlockState(), 3);
        level.setBlock(earth, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
        for (SprinklerTier tier : SprinklerTier.values()) for (boolean nozzle : new boolean[]{false, true}) {
            SprinklerBlock.waterNow(level, center, tier, nozzle);
            h.assertTrue(level.getBlockState(infertile).getValue(FarmBlock.MOISTURE) == 0, "Sprinkler tier watered infertile soil: " + tier);
        }
        h.assertTrue(level.getBlockState(infertile).getValue(FarmBlock.MOISTURE) == 0, "Sprinkler watered infertile soil");
        h.assertTrue(level.getBlockState(earth).getValue(FarmBlock.MOISTURE) == 7, "Sprinkler failed on ordinary farmland");
        var water = WateringCanItem.class.getDeclaredMethod("waterTile", Level.class, BlockPos.class);
        water.setAccessible(true);
        h.assertTrue((boolean) water.invoke(ModItems.WATERING_CAN.get(), level, infertile), "Watering can rejected sand");
        h.assertTrue(level.getBlockState(infertile).getValue(FarmBlock.MOISTURE) == 7, "Manual watering failed");
        var manager = new FertilizerManager();
        for (FertilizerType type : FertilizerType.values()) {
            h.assertTrue(manager.tryApplyFertilizer(level, infertile, type), "Fertilizer rejected " + type);
            var loaded = FertilizerManager.load(manager.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            h.assertTrue(loaded.getFertilizer(level, infertile) == type, "Saved fertilizer changed");
            h.assertTrue(level.getBlockState(infertile).is(ModBlocks.INFERTILE_FARMLAND.get()), "Fertilizer changed block identity");
            manager.removeFertilizer(level, infertile);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void overnightRetainingSoilAndReversionKeepHardSoilyIdentity(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(4, 2, 5)); var b = a.east(3);
        var manager = new CropGrowthManager(); var fertilizer = FertilizerManager.get(level);
        for (var pos : new BlockPos[]{a,b}) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.INFERTILE_FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE,7),3);
            level.setBlock(pos.above(), Blocks.WHEAT.defaultBlockState(),3);
            manager.addCrop(level,pos.above());
        }
        try {
            h.assertTrue(fertilizer.tryApplyFertilizer(level,b,FertilizerType.DELUXE_RETAINING_SOIL), "Cannot apply retaining soil");
            var settle = CropGrowthManager.class.getDeclaredMethod("dryAllFarmland", net.minecraft.server.level.ServerLevel.class);
            settle.setAccessible(true); settle.invoke(manager,level);
            h.assertTrue(level.getBlockState(a).is(ModBlocks.INFERTILE_FARMLAND.get()) && level.getBlockState(a).getValue(FarmBlock.MOISTURE)==0,
                    "Overnight drying lost soil identity or crop-protected farmland");
            h.assertTrue(level.getBlockState(b).is(ModBlocks.INFERTILE_FARMLAND.get()) && level.getBlockState(b).getValue(FarmBlock.MOISTURE)==7,
                    "Deluxe retaining soil lost water overnight");
        } finally { fertilizer.removeFertilizer(level,b); }
        level.setBlock(a.above(),Blocks.AIR.defaultBlockState(),3);
        FarmBlock.turnToDirt(null,level.getBlockState(a),level,a);
        h.assertTrue(level.getBlockState(a).is(ModBlocks.HARD_SOIL.get()), "Reversion returned dirt instead of sand");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void terrainPrioritiesConnectExposedFacesWithoutBridgingInsetGaps(GameTestHelper h) {
        var dry=ModBlocks.FARMLAND.get().defaultBlockState();var infertile=ModBlocks.INFERTILE_FARMLAND.get().defaultBlockState();
        h.assertTrue(TerrainFaceConnections.rank(dry)==TerrainFaceConnections.rank(infertile), "Soil families have different dry ranks");
        h.assertTrue(TerrainFaceConnections.rank(dry.setValue(FarmBlock.MOISTURE,7))==TerrainFaceConnections.rank(infertile.setValue(FarmBlock.MOISTURE,7)), "Wet ranks differ");
        var order=java.util.List.of(ModBlocks.CLIFF.get().defaultBlockState(),ModBlocks.HARD_SOIL.get().defaultBlockState(),
                ModBlocks.DIRT.get().defaultBlockState(),infertile,infertile.setValue(FarmBlock.MOISTURE,7),
                ModBlocks.GRASS_BLOCK.get().defaultBlockState(),ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState());
        for(int i=1;i<order.size();i++) h.assertTrue(TerrainFaceConnections.rank(order.get(i))>TerrainFaceConnections.rank(order.get(i-1)),"Wrong priority chain");
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,3,6));
        for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=-1;z<=1;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(pos,order.get(1),3);level.setBlock(pos.north(),order.get(2),3);
        h.assertTrue(TerrainFaceConnections.collect(level,pos,order.get(1),Direction.UP).stream().anyMatch(c->c.state().is(ModBlocks.DIRT.get())&&!c.folded()),"No dirt-to-sand top edge");
        level.setBlock(pos.north(),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.north().above(),order.get(1),3);level.setBlock(pos,order.getFirst(),3);
        h.assertTrue(TerrainFaceConnections.collect(level,pos,order.getFirst(),Direction.UP).stream().anyMatch(c->c.folded()&&c.face()==Direction.SOUTH),"No sand-to-cliff floor/wall fold");
        level.setBlock(pos,infertile,3);level.setBlock(pos.north().above(),order.getLast(),3);
        h.assertTrue(TerrainFaceConnections.collect(level,pos,infertile,Direction.UP).isEmpty(),"Connection bridged the recessed top air gap");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void subsoilKeepsSurfaceAndOnlyReplacesBuriedDirt(GameTestHelper h) {
        var level = h.getLevel(); var min = h.absolutePos(new BlockPos(3, 1, 3));
        for (int x=0;x<3;x++) for (int y=0;y<3;y++)
            level.setBlock(min.offset(x,y,0), ModBlocks.DIRT.get().defaultBlockState(), 2);
        level.setBlock(min.offset(0,2,0),ModBlocks.GRASS_BLOCK.get().defaultBlockState(),2);
        level.setBlock(min.offset(1,1,0),Blocks.STONE.defaultBlockState(),2);
        int count=com.stardew.craft.farm.FarmSubsoil.replaceBuriedDirt(level,min,min.offset(2,2,0));
        h.assertTrue(count==5,"Unexpected subsoil count: "+count);
        h.assertTrue(level.getBlockState(min.above(2)).is(ModBlocks.GRASS_BLOCK.get()),"Grass surface replaced");
        h.assertTrue(level.getBlockState(min.offset(2,2,0)).is(ModBlocks.DIRT.get()),"Fertile surface replaced");
        h.assertTrue(level.getBlockState(min.offset(1,1,0)).is(Blocks.STONE),"Stone replaced");
        h.assertTrue(level.getBlockState(min).is(ModBlocks.HARD_SOIL.get()),"Buried soil remains fertile");
        h.assertTrue(com.stardew.craft.farm.FarmSubsoil.replaceBuriedDirt(level,min,min.offset(2,2,0))==0,"Conversion not idempotent");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void fractionalGrowthSurvivesSaveAndRestoresOnFertileSoil(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(6,3,6));
        var manager=new CropGrowthManager();
        manager.addCrop(level,pos);
        var state=manager.getOrCreateState(level,pos);
        h.assertTrue(!state.advanceOnSoil(true),"First poor-soil day should accumulate progress");
        var loaded=CropGrowthManager.load(manager.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        state=loaded.getState(level,pos);
        h.assertTrue(state!=null && state.infertileProgress==2,"Fractional progress lost during save");
        int advances=0;
        for(int day=2;day<=6;day++) if(state.advanceOnSoil(true)) advances++;
        h.assertTrue(advances==4,"Four growth days must require six watered days");
        loaded.setRegrowing(level,pos,true,3,4);
        state=loaded.getState(level,pos);
        advances=0;
        for(int day=1;day<=5;day++) if(state.advanceOnSoil(true)) advances++;
        h.assertTrue(advances==3,"Three regrowth days must require five watered days");
        h.assertTrue(state.advanceOnSoil(false) && state.infertileProgress==0,"Fertile soil retained growth penalty");
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void wateredCropMaturityAndRegrowthTakeOneAndAHalfTimes(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(5,3,5));
        var dimension=Level.class.getDeclaredField("dimension"); dimension.setAccessible(true);
        var oldDimension=dimension.get(level);
        var clock=com.stardew.craft.time.StardewTimeManager.get(); int oldSeason=clock.getCurrentSeason();
        try {
            dimension.set(level,com.stardew.craft.core.ModDimensions.STARDEW_VALLEY); clock.setCurrentSeason(0);
            level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
            var crop=(com.stardew.craft.block.crop.StardewCropBlock)ModBlocks.PARSNIP_CROP.get();
            for(boolean poor:new boolean[]{false,true}) {
                level.setBlock(pos.below(),(poor?ModBlocks.INFERTILE_FARMLAND:ModBlocks.FARMLAND).get().defaultBlockState(),2);
                level.setBlock(pos,crop.defaultBlockState(),2);
                var growth=new CropGrowthManager.CropGrowthState();
                crop.growCropOneDay(level,pos,level.getBlockState(pos),false,growth);
                h.assertTrue(growth.infertileProgress==0 && growth.dayInPhase==0,"Dry day advanced growth");
                int expected=poor?6:4;
                for(int day=1;day<=expected;day++) {
                    crop.growCropOneDay(level,pos,level.getBlockState(pos),true,growth);
                    h.assertTrue((level.getBlockState(pos).getValue(com.stardew.craft.block.crop.StardewCropBlock.AGE)==3)==(day==expected),
                            "Unexpected parsnip maturity poor="+poor+" day="+day);
                }
            }
            var berry=(com.stardew.craft.block.crop.StardewCropBlock)ModBlocks.STRAWBERRY_CROP.get();
            level.setBlock(pos,berry.defaultBlockState(),2);
            var growth=new CropGrowthManager.CropGrowthState(); growth.regrowing=true;growth.dayInPhase=4;
            for(int day=1;day<=6;day++) {
                berry.growCropOneDay(level,pos,level.getBlockState(pos),true,growth);
                h.assertTrue(growth.regrowing==(day<6),"Unexpected strawberry regrowth day="+day);
            }
        } finally { dimension.set(level,oldDimension);clock.setCurrentSeason(oldSeason); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_hard_soil", template = "ring_utilities")
    public static void fruitTreeCarriesFractionAcrossSaveAndSoilReplacement(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(6,3,6));
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) for(int y=0;y<2;y++)
            level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);
        level.setBlock(pos.below(),ModBlocks.HARD_SOIL.get().defaultBlockState(),2);
        var sapling=ModBlocks.APPLE_SAPLING.get().defaultBlockState();
        h.assertTrue(sapling.canSurvive(level,pos),"Fruit tree cannot root in hard soil");
        level.setBlock(pos,sapling,2);
        var manager=new com.stardew.craft.manager.FruitTreeGrowthManager();
        manager.growOneDay(level,pos);
        h.assertTrue(manager.getDaysGrown(level,pos)==0,"First tree day should retain 4/5 progress");
        manager=com.stardew.craft.manager.FruitTreeGrowthManager.load(manager.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        for(int day=2;day<=5;day++) manager.growOneDay(level,pos);
        h.assertTrue(manager.getDaysGrown(level,pos)==4,"Tree failed to retain progress across save: grown="+manager.getDaysGrown(level,pos)+" state="+level.getBlockState(pos)+" blocked="+manager.isBlockedNow(level,pos)+" data="+manager.save(new CompoundTag(),level.registryAccess()));
        for(int day=6;day<=34;day++) manager.growOneDay(level,pos);
        h.assertTrue(manager.getDaysGrown(level,pos)==27 && level.getBlockState(pos).is(ModBlocks.APPLE_SAPLING.get()),"Fruit tree matured before 35 days");
        // Replacing only the planted soil restores ordinary growth, independent of deeper hard soil.
        level.setBlock(pos.below(2),ModBlocks.HARD_SOIL.get().defaultBlockState(),2);
        level.setBlock(pos.below(),ModBlocks.DIRT.get().defaultBlockState(),2);
        manager.growOneDay(level,pos);
        h.assertTrue(level.getBlockState(pos).is(ModBlocks.APPLE_TREE.get()),"Fertile replacement did not finish growth");
        h.succeed();
    }

}
