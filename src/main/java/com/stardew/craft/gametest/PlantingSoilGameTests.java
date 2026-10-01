package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.block.terrain.TerrainSoils;
import com.stardew.craft.block.tree.WildTreeSaplingBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.MixedSeedsItem;
import com.stardew.craft.item.WildSeedsItem;
import com.stardew.craft.item.TeaSaplingItem;
import com.stardew.craft.item.tree.TreeSeedItem;
import com.stardew.craft.item.tree.fruit.FruitTreeSaplingItem;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

@GameTestHolder("stardewcraft_planting_soil")
@PrefixGameTestTemplate(false)
public final class PlantingSoilGameTests {
    private PlantingSoilGameTests() {}
    private static final Block[] FOREIGN = {Blocks.FARMLAND, Blocks.DIRT, Blocks.GRASS_BLOCK,
            Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD, Blocks.MOSS_BLOCK};

    private static boolean seed(Item item) {
        return item.getClass().getPackageName().startsWith("com.stardew.craft.item.crop")
                || item instanceof MixedSeedsItem || item instanceof WildSeedsItem
                || item instanceof TreeSeedItem || item instanceof FruitTreeSaplingItem || item instanceof TeaSaplingItem;
    }

    @GameTest(templateNamespace="stardewcraft_planting_soil",template="ring_utilities")
    public static void everySeedRejectsImportedSoilWithoutConsumingItems(GameTestHelper h) {
        var level=h.getLevel();var soil=h.absolutePos(new BlockPos(5,2,5));
        var player=FakePlayerFactory.getMinecraft(level);var old=player.getMainHandItem();int checked=0;
        try {
            for (Block ground:FOREIGN) for(Item item:BuiltInRegistries.ITEM) {
                if(!seed(item)) continue;
                level.setBlock(soil.above(),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(soil.above(2),Blocks.AIR.defaultBlockState(),3);
                level.setBlock(soil,ground.defaultBlockState(),3);
                var stack=new ItemStack(item,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(soil),Direction.UP,soil,false));
                var result=item.useOn(context);
                h.assertTrue(!result.consumesAction() && stack.getCount()==2 && level.getBlockState(soil.above()).isAir(),
                        "Foreign soil accepted " + BuiltInRegistries.ITEM.getKey(item)+" on "+BuiltInRegistries.BLOCK.getKey(ground));
                checked++;
            }
            h.assertTrue(checked>=450,"Seed coverage unexpectedly small: "+checked);
        } finally {player.setItemInHand(InteractionHand.MAIN_HAND,old);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_planting_soil",template="ring_utilities")
    public static void allCropAndSaplingRootsRejectForeignGround(GameTestHelper h) {
        var level=h.getLevel();var soil=h.absolutePos(new BlockPos(5,2,5));int checked=0;
        level.setBlock(soil.above(),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(soil.above(2),Blocks.AIR.defaultBlockState(),3);
        for(Block ground:FOREIGN) {
            level.setBlock(soil,ground.defaultBlockState(),3);
            for(Block plant:BuiltInRegistries.BLOCK) {
                if(!(plant instanceof StardewCropBlock || plant instanceof WildTreeSaplingBlock
                        || plant instanceof FruitTreeSaplingBlock || plant instanceof TeaBushBlock)) continue;
                h.assertTrue(!plant.defaultBlockState().canSurvive(level,soil.above()),"Plant accepted foreign ground: "+plant+" on "+ground);
                checked++;
            }
        }
        h.assertTrue(checked>=450,"Root coverage unexpectedly small: "+checked);
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_planting_soil",template="ring_utilities")
    public static void ownSoilsVariantsAndPotRemainPlantable(GameTestHelper h) {
        var level=h.getLevel();var soil=h.absolutePos(new BlockPos(5,2,5));
        var player=FakePlayerFactory.getMinecraft(level);var old=player.getMainHandItem();
        var clock=StardewTimeManager.get();int season=clock.getCurrentSeason();
        try {
            clock.setCurrentSeason(0);
            for(Block ground:new Block[]{ModBlocks.FARMLAND.get(),ModBlocks.SANDY_FARMLAND.get(),ModBlocks.INFERTILE_FARMLAND.get(),ModBlocks.GARDEN_POT.get()}) {
                for(int moisture:new int[]{0,7}) {
                    level.setBlock(soil.above(),Blocks.AIR.defaultBlockState(),3);
                    level.setBlock(soil.above(2),Blocks.AIR.defaultBlockState(),3);
                    level.setBlock(soil,ground.defaultBlockState().setValue(FarmBlock.MOISTURE,moisture),3);
                    var stack=new ItemStack(ModItems.PARSNIP_SEEDS.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(soil),Direction.UP,soil,false));
                    h.assertTrue(stack.getItem().useOn(context).consumesAction() && stack.getCount()==1
                            && level.getBlockState(soil.above()).is(ModBlocks.PARSNIP_CROP.get()),"Own farmland/pot planting failed "+ground);
                }
            }
            for(Block ground:new Block[]{ModBlocks.DIRT.get(),ModBlocks.HARD_SOIL.get(),ModBlocks.YELLOW_DIRT.get(),ModBlocks.GRASS_BLOCK.get(),ModBlocks.DARK_GRASS_BLOCK.get()})
                for(var state:ground.getStateDefinition().getPossibleStates()) {
                    level.setBlock(soil.above(),Blocks.AIR.defaultBlockState(),3);
                    level.setBlock(soil.above(2),Blocks.AIR.defaultBlockState(),3);
                    level.setBlock(soil,state,3);
                    h.assertTrue(TerrainSoils.treeSeedGround(state) && ModBlocks.APPLE_SAPLING.get().defaultBlockState().canSurvive(level,soil.above()),"Own terrain variant rejects trees "+state);
                }
            h.assertTrue(!TerrainSoils.treeSeedGround(ModBlocks.FARMLAND.get().defaultBlockState()),"Wild-tree seed lost its no-hoed-soil rule");
        } finally {clock.setCurrentSeason(season);player.setItemInHand(InteractionHand.MAIN_HAND,old);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_planting_soil",template="ring_utilities")
    public static void importedSoilCannotBecomeOwnSoilByTillingAndDecay(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,2,5));
        var player=FakePlayerFactory.getMinecraft(level);var old=player.getMainHandItem();
        try {
            level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_HOE));
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            for(Block dirt:new Block[]{Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.DIRT_PATH}) {
                level.setBlock(pos,dirt.defaultBlockState(),3);
                var tilled=dirt.defaultBlockState().getToolModifiedState(context,ItemAbilities.HOE_TILL,true);
                h.assertTrue(tilled!=null && tilled.is(Blocks.FARMLAND) && !TerrainSoils.cropSupport(tilled),"Imported soil became a planting substrate");
                h.assertTrue(TerrainSoils.restored(tilled)==Blocks.DIRT,"Imported farmland decayed to own dirt");
            }
            // Exercise the actual nightly cleanup, with a tracked empty field.
            level.setBlock(pos,Blocks.FARMLAND.defaultBlockState(),3);
            var crops=new CropGrowthManager();crops.addCrop(level,pos.above());
            var dry=CropGrowthManager.class.getDeclaredMethod("dryAllFarmland",net.minecraft.server.level.ServerLevel.class);
            dry.setAccessible(true);
            var dimension=Level.class.getDeclaredField("dimension");dimension.setAccessible(true);var oldDimension=dimension.get(level);
            try {
                dimension.set(level,com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
                crops.trackPublicTilledChunk(level,pos);
                dry.invoke(crops,level);
                h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT),"Nightly public-field decay failed or laundered imported soil: "+level.getBlockState(pos));
            } finally {dimension.set(level,oldDimension);}
            for(Block dirt:new Block[]{ModBlocks.DIRT.get(),ModBlocks.HARD_SOIL.get(),ModBlocks.SAND.get(),ModBlocks.YELLOW_DIRT.get()}) {
                level.setBlock(pos,dirt.defaultBlockState(),3);
                var tilled=dirt.defaultBlockState().getToolModifiedState(context,ItemAbilities.HOE_TILL,true);
                h.assertTrue(tilled!=null && TerrainSoils.cropSupport(tilled),"Own soil did not till to own farmland "+dirt);
            }
        } finally {player.setItemInHand(InteractionHand.MAIN_HAND,old);}
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_planting_soil",template="ring_utilities")
    public static void cropCannotKeepGrowingAfterSilentSubstrateReplacement(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,5));
        var dimension=Level.class.getDeclaredField("dimension");dimension.setAccessible(true);var oldDimension=dimension.get(level);
        var clock=StardewTimeManager.get();int season=clock.getCurrentSeason();
        try {
            dimension.set(level,com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);clock.setCurrentSeason(0);
            level.setBlock(pos.below(),ModBlocks.FARMLAND.get().defaultBlockState(),2);
            level.setBlock(pos,ModBlocks.PARSNIP_CROP.get().defaultBlockState(),2);
            level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),2);
            var state=new CropGrowthManager.CropGrowthState();
            var crop=(StardewCropBlock)ModBlocks.PARSNIP_CROP.get();
            for(int day=0;day<10;day++) crop.growCropOneDay(level,pos,level.getBlockState(pos),true,state);
            h.assertTrue(state.phase==0 && state.dayInPhase==0,"Crop grew on foreign soil without neighbor updates");
        } finally {dimension.set(level,oldDimension);clock.setCurrentSeason(season);}
        h.succeed();
    }
}
