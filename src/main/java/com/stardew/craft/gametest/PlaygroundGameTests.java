package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlaygroundBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_playground")
@PrefixGameTestTemplate(false)
public final class PlaygroundGameTests {
    private static PlaygroundBlock[] blocks() {
        return new PlaygroundBlock[]{ModBlocks.PLAYGROUND_SLIDE.get(), ModBlocks.CLIMBING_FRAME.get()};
    }
    private static BlockPos prepare(GameTestHelper h) {
        for (int x=0;x<22;x++) for (int z=0;z<22;z++) for (int y=0;y<8;y++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)), y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        return h.absolutePos(new BlockPos(10,1,10));
    }
    private static BlockPlaceContext context(net.minecraft.world.entity.player.Player player, BlockPos pos, PlaygroundBlock block) {
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block,3));
        return new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false)));
    }
    private static int count(GameTestHelper h, BlockPos main, PlaygroundBlock block) {
        int n=0;for(var p:BlockPos.betweenClosed(main.offset(-5,0,-5),main.offset(5,4,5)))
            if(h.getLevel().getBlockState(p).is(block)&&main.equals(block.findMainPos(h.getLevel(),p,h.getLevel().getBlockState(p))))n++;
        return n;
    }
    private static Vec3 rotate(Vec3 local, Direction facing) {
        double x=local.x-.5,z=local.z-.5;
        return switch(facing){case EAST->new Vec3(.5-z,local.y,.5+x);case SOUTH->new Vec3(.5-x,local.y,.5-z);case WEST->new Vec3(.5+z,local.y,.5-x);default->local;};
    }
    @GameTest(templateNamespace="stardewcraft_playground",template="ring_utilities",timeoutTicks=200)
    public static void rotationCollisionClimbingAndSingleDrop(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var player=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        for(var block:blocks())for(var facing:Direction.Plane.HORIZONTAL){
            player.setPos(Vec3.atBottomCenterOf(main.offset(8,0,8)));player.setYRot(facing.getOpposite().toYRot());
            var ctx=context(player,main,block);
            h.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Cannot place "+block.modelName()+" "+facing);
            int expected=count(h,main,block);h.assertTrue(expected>10,"Missing extension cells");
            for(var p:BlockPos.betweenClosed(main.offset(-5,0,-5),main.offset(5,4,5))){
                var state=level.getBlockState(p);if(!state.is(block))continue;
                h.assertTrue(main.equals(block.findMainPos(level,p,state)),"Wrong cell owner");
                var shape=state.getCollisionShape(level,p);if(!shape.isEmpty()){
                    var b=shape.bounds();h.assertTrue(b.minX>=-1e-7&&b.maxX<=1.0000001&&b.minY>=-1e-7&&b.maxY<=1.0000001&&b.minZ>=-1e-7&&b.maxZ<=1.0000001,"Collision escapes cell");
                }
                h.assertTrue((level.getBlockEntity(p)!=null)==p.equals(main),"Only the main cell may render the whole model");
            }
            Vec3 climb=block.isSlide()?new Vec3(1,1,(-2-32*Math.tan(Math.PI/8))/16):new Vec3(1,1,37.0/16);
            player.setPos(Vec3.atLowerCornerOf(main).add(rotate(climb,facing)));
            var climbPos=player.blockPosition();var climbState=level.getBlockState(climbPos);
            h.assertTrue(climbState.is(block)&&block.isLadder(climbState,level,climbPos,player),"Cannot climb designated surface "+block.modelName()+" "+facing);
            player.setPos(Vec3.atLowerCornerOf(main).add(rotate(new Vec3(1,1,1),facing)));
            h.assertTrue(!block.isLadder(level.getBlockState(main),level,main,player),"Empty center behaves like a ladder");
            var neighbor=main.relative(facing.getCounterClockWise(),3);player.setPos(Vec3.atBottomCenterOf(main.offset(8,0,8)));
            var ctx2=context(player,neighbor,block);h.assertTrue(((BlockItem)ctx2.getItemInHand().getItem()).place(ctx2).consumesAction(),"Adjacent placement failed");
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(10)))item.discard();
            var extension=main.offset(PlaygroundBlock.rotateOffset(new BlockPos(1,2,0),facing));
            h.assertTrue(level.getBlockState(extension).is(block),"Missing test extension");
            level.destroyBlock(extension,true);
            h.assertTrue(count(h,main,block)==0&&count(h,neighbor,block)==expected,"Removal leaked parts or damaged adjacent structure");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(6));
            h.assertTrue(drops.stream().mapToInt(i->i.getItem().getCount()).sum()==1&&com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()),"Expected exactly one whole item: "+block.modelName()+" "+facing+" "+drops.stream().map(i->i.getItem().toString()).toList());
            PlaygroundBlock.runWithDropsSuppressed(()->level.removeBlock(neighbor,false));
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_playground",template="ring_utilities",timeoutTicks=200)
    public static void blockedAndUnsupportedPlacementAreAtomic(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var player=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(main.offset(8,0,8)));
        for(var block:blocks())for(var facing:Direction.Plane.HORIZONTAL){
            player.setYRot(facing.getOpposite().toYRot());
            var obstacle=main.offset(PlaygroundBlock.rotateOffset(new BlockPos(1,2,0),facing));
            level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);var ctx=context(player,main,block);
            h.assertTrue(!((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Placed through obstruction");
            h.assertTrue(count(h,main,block)==0&&ctx.getItemInHand().getCount()==3,"Failed placement left parts or consumed item");
            level.removeBlock(obstacle,false);
            var foot=main.offset(PlaygroundBlock.rotateOffset(new BlockPos(1,-1,0),facing));level.removeBlock(foot,false);
            h.assertTrue(block.getStateForPlacement(context(player,main,block))==null,"Unsupported leg accepted");
            level.setBlock(foot,Blocks.STONE.defaultBlockState(),3);
        }
        h.succeed();
    }
}
