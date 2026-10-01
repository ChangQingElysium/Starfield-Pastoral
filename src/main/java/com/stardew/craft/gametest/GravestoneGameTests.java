package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_gravestones")
@PrefixGameTestTemplate(false)
public final class GravestoneGameTests {
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void bothSizesPlaceRotateAndDropOnce(GameTestHelper h) {
        var level=h.getLevel();var main=h.absolutePos(new BlockPos(10,1,10));
        for(var at:BlockPos.betweenClosed(main.offset(-3,-1,-3),main.offset(3,4,3)))
            level.setBlock(at,at.getY()==main.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(main.offset(3,0,3)));
        for(var block:new MapDecorStaticBlock[]{ModBlocks.TALL_GRAVESTONE.get(),ModBlocks.SHORT_GRAVESTONE.get()})
            for(var facing:Direction.Plane.HORIZONTAL){
                player.setYRot(facing.getOpposite().toYRot());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block,3));
                var ctx=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(main),Direction.UP,main.below(),false)));
                boolean tall=block==ModBlocks.TALL_GRAVESTONE.get();
                if(tall){
                    level.setBlock(main.above(),Blocks.STONE.defaultBlockState(),3);
                    h.assertTrue(block.getStateForPlacement(ctx)==null,"Tall grave must reserve upper cell");
                    level.removeBlock(main.above(),false);
                }
                h.assertTrue(((BlockItem)block.asItem()).place(ctx).consumesAction(),"Grave placement failed");
                h.assertTrue(level.getBlockState(main).getValue(MapDecorStaticBlock.FACING)==facing,"Wrong facing");
                h.assertTrue(level.getBlockState(main.above()).is(block)==tall,"Wrong upper-cell occupancy");
                var hitPos=tall?main.above():main;
                h.assertTrue(main.equals(block.findMainPos(level,hitPos,level.getBlockState(hitPos))),"Upper piece lost owner");
                for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(3)))item.discard();
                level.destroyBlock(hitPos,true);
                var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(3));
                h.assertTrue(drops.size()==1&&drops.getFirst().getItem().is(block.asItem())&&drops.getFirst().getItem().getCount()==1,"Grave must drop once");
                h.assertTrue(level.getBlockState(main).isAir()&&level.getBlockState(main.above()).isAir(),"Grave removal leaked upper cell");
            }
        h.succeed();
    }
}
