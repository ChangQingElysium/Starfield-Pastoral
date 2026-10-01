package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainShapeBlock;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.templates.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in headless verification: -PgameTestNamespaces=stardewcraft_shaped_terrain. */
@GameTestHolder("stardewcraft_shaped_terrain")
@PrefixGameTestTemplate(false)
public final class ShapedTerrainGameTests {
    @GameTest(templateNamespace="stardewcraft_shaped_terrain",template="house_details")
    public static void placementRetainsVariantsAndDoubleSlabsDropTwo(GameTestHelper h) {
        var level=h.getLevel();var player=FakePlayerFactory.getMinecraft(level);
        var pos=h.absolutePos(new BlockPos(7,2,7));
        Block[] blocks={ModBlocks.GRASS_SLAB.get(),ModBlocks.GRASS_STAIRS.get(),ModBlocks.DARK_GRASS_SLAB.get(),ModBlocks.DARK_GRASS_STAIRS.get(),ModBlocks.DIRT_SLAB.get(),ModBlocks.DIRT_STAIRS.get(),ModBlocks.CLIFF_SLAB.get(),ModBlocks.CLIFF_STAIRS.get()};
        for(Block block:blocks) {
            clear(h,pos);var variant=TerrainVariants.property(block.defaultBlockState());
            var expected=variant==null?block.defaultBlockState():block.defaultBlockState().setValue(variant,variant.getPossibleValues().stream().mapToInt(i->i).max().orElseThrow());
            var copy=TerrainVariants.fixedCopy(new ItemStack(block,8),expected);
            player.setItemInHand(InteractionHand.MAIN_HAND,copy);
            copy.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false)));
            var state=level.getBlockState(pos);
            h.assertTrue(state.is(block),"Placement failed for "+block);
            if(variant!=null)h.assertTrue(state.getValue(variant).equals(expected.getValue(variant)),"Fixed copied variant rerolled");
            h.assertTrue(TerrainShapeBlock.material(state).is(((TerrainShapeBlock)block).terrainKind().block()),"Lost material identity");
            h.assertTrue(!state.isRandomlyTicking(),"Shaped grass must not decay");
            if(block instanceof SlabBlock) {
                copy.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
                state=level.getBlockState(pos);
                h.assertTrue(state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE,"Second slab failed to merge");
                if(variant!=null)h.assertTrue(state.getValue(variant).equals(expected.getValue(variant)),"Merging rerolled existing variant");
                int count=Block.getDrops(state,level,pos,null).stream().filter(s->s.is(block.asItem())).mapToInt(ItemStack::getCount).sum();
                h.assertTrue(count==2,"Double slab must drop two matching slabs; got "+count);
            } else {
                h.assertTrue(state.getShape(level,pos).bounds().maxY==1,"Stair collision changed height");
                var adjacent=pos.relative(state.getValue(StairBlock.FACING));
                level.setBlock(adjacent,block.defaultBlockState().setValue(StairBlock.FACING,state.getValue(StairBlock.FACING).getClockWise()),3);
                h.assertTrue(level.getBlockState(pos).getValue(StairBlock.SHAPE)!=net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT,"Stair corner failed to connect");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_shaped_terrain",template="house_details")
    public static void windowsAndRailingConnectInAllDirections(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(7,2,7));var level=h.getLevel();
        Block window=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.GRID_WINDOW).get();
        Block railing=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.BALCONY_RAILING).get();
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            clear(h,pos);var w=window.defaultBlockState().setValue(MaterialTemplateBlock.FACING,facing);
            level.setBlock(pos,w,3);level.setBlock(pos.above(),w,3);
            h.assertTrue(level.getBlockState(pos).getValue(GridWindowTemplateBlock.CONNECTIONS)==1,"Window lower join failed");
            h.assertTrue(level.getBlockState(pos.above()).getValue(GridWindowTemplateBlock.CONNECTIONS)==4,"Window upper join failed");
            level.destroyBlock(pos.above(),false);
            h.assertTrue(level.getBlockState(pos).getValue(GridWindowTemplateBlock.CONNECTIONS)==0,"Window frame failed to restore");
            clear(h,pos);var r=railing.defaultBlockState().setValue(MaterialTemplateBlock.FACING,facing);
            level.setBlock(pos,r,3);level.setBlock(pos.relative(facing.getClockWise()),r,3);level.setBlock(pos.relative(facing.getCounterClockWise()),r,3);
            h.assertTrue(level.getBlockState(pos).getValue(BalconyRailingTemplateBlock.PROFILE)==2,"Railing middle failed");
            clear(h,pos);level.setBlock(pos,r,3);
            level.setBlock(pos.relative(facing.getOpposite()),r.setValue(MaterialTemplateBlock.FACING,facing.getClockWise()),3);
            h.assertTrue(level.getBlockState(pos).getValue(BalconyRailingTemplateBlock.PROFILE)==6,"Free corner needs its end post");
            var bounds=level.getBlockState(pos).getShape(level,pos).bounds();
            h.assertTrue(bounds.minX>=0&&bounds.minY>=0&&bounds.minZ>=0&&bounds.maxX<=1&&bounds.maxY<=1&&bounds.maxZ<=1,"Railing escapes cell bounds");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_shaped_terrain",template="house_details")
    public static void brownDoorCreatesBothHalvesAndOpens(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(7,2,7));var level=h.getLevel();var player=FakePlayerFactory.getMinecraft(level);
        var door=ModBlocks.BROWN_GLASS_DOOR.get();
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            clear(h,pos);player.setYRot(facing.toYRot());player.setXRot(0);player.setPos(Vec3.atCenterOf(pos.relative(facing.getOpposite(),2)));
            var stack=new ItemStack(door);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false)));
            h.assertTrue(level.getBlockState(pos).is(door)&&level.getBlockState(pos.above()).is(door),"Door halves missing");
            h.assertTrue(level.getBlockState(pos).getValue(DoorBlock.FACING)==facing,"Door faces backwards");
            level.setBlock(pos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN)&&level.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Powered door halves disagree");
            level.destroyBlock(pos.above(),false);h.assertTrue(level.getBlockState(pos).isAir(),"Door lower half left behind");
        }
        h.succeed();
    }

    private static void clear(GameTestHelper h,BlockPos pos) {
        for(var p:BlockPos.betweenClosed(pos.offset(-3,0,-3),pos.offset(3,3,3)))h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3);
        for(var p:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,-1,3)))h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);
    }
}
