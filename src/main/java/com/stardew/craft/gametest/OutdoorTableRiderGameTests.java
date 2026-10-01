package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlaygroundBlock;
import com.stardew.craft.block.utility.OutdoorTableBlock;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity;
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

@GameTestHolder("stardewcraft_table_riders")
@PrefixGameTestTemplate(false)
public final class OutdoorTableRiderGameTests {
    private static BlockPos prepare(GameTestHelper h) {
        for(int x=0;x<22;x++)for(int z=0;z<22;z++)for(int y=0;y<8;y++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        return h.absolutePos(new BlockPos(10,1,10));
    }
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void tableConnectionsKeepItemsAndDrops(GameTestHelper h) {
        var pos=prepare(h);var level=h.getLevel();var table=ModBlocks.OUTDOOR_TABLE.get();
        var base=table.defaultBlockState().setValue(OutdoorTableBlock.VARIANT,2);
        for(var p:new BlockPos[]{pos,pos.east(),pos.south(),pos.east().south()})level.setBlock(p,base,3);
        h.assertTrue(level.getBlockState(pos).getValue(OutdoorTableBlock.CONNECTIONS)==38,"2x2 corner must include diagonal");
        var be=(TableDisplayBlockEntity)level.getBlockEntity(pos);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(pos.north(2)));
        var stack=new ItemStack(Items.DIAMOND,3);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false);
        level.getBlockState(pos).useItemOn(stack,level,player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(stack.getCount()==2&&be.getDisplayItem().is(Items.DIAMOND)&&be.getDisplayItem().getCount()==1,"Display must consume exactly one item");
        var tag=be.saveWithFullMetadata(level.registryAccess());
        var restored=new TableDisplayBlockEntity(pos,level.getBlockState(pos));restored.loadWithComponents(tag,level.registryAccess());
        h.assertTrue(restored.getDisplayItem().is(Items.DIAMOND),"Display item lost on reload");
        level.removeBlock(pos.east().south(),false);
        h.assertTrue(level.getBlockState(pos).getValue(OutdoorTableBlock.CONNECTIONS)==6,"Diagonal removal must restore inner leg");
        h.assertTrue(level.getBlockEntity(pos)==be&&be.hasDisplayItem()&&level.getBlockState(pos).getValue(OutdoorTableBlock.VARIANT)==2,"Connection change replaced item or variant");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        level.getBlockState(pos).useWithoutItem(level,player,hit);
        h.assertTrue(!be.hasDisplayItem()&&player.getInventory().countItem(Items.DIAMOND)==1,"Empty hand must retrieve display");
        be.setDisplayItem(new ItemStack(Items.EMERALD));
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)))item.discard();
        level.destroyBlock(pos,true);
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3));
        h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.EMERALD)).mapToInt(e->e.getItem().getCount()).sum()==1,"Stored item must drop exactly once");
        h.assertTrue(drops.stream().filter(e->e.getItem().is(table.asItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"Table must drop itself");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void birdMountsAboveLowCollisionAndCleansUp(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var block=ModBlocks.BIRD_SPRING_RIDER.get();
        for(var facing:Direction.Plane.HORIZONTAL){
            var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(main.offset(6,0,6)));player.setYRot(facing.getOpposite().toYRot());
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block,3));
            var ctx=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(main),Direction.UP,main.below(),false)));
            h.assertTrue(((BlockItem)block.asItem()).place(ctx).consumesAction(),"Bird placement failed");
            var shape=level.getBlockState(main).getCollisionShape(level,main);
            h.assertTrue(shape.toAabbs().size()==1&&shape.bounds().maxY<=1,"Base should be a small AABB");
            h.assertTrue(level.getBlockState(main.above()).getCollisionShape(level,main.above()).isEmpty(),"Head must not collide");
            var hit=new BlockHitResult(Vec3.atCenterOf(main.above()),facing,main.above(),false);
            level.getBlockState(main.above()).useWithoutItem(level,player,hit);
            h.assertTrue(player.getVehicle() instanceof BirdSpringRiderSeatEntity,"Head click should mount bird");
            var seat=(BirdSpringRiderSeatEntity)player.getVehicle();level.tickNonPassenger(seat);
            h.assertTrue(player.position().distanceTo(seat.riderFeet(0))<.00001,"Rider must follow the animated saddle");
            var other=h.makeMockPlayer(GameType.SURVIVAL);level.getBlockState(main.above()).useWithoutItem(level,other,hit);
            h.assertTrue(!other.isPassenger(),"Occupied bird cannot be stolen");
            player.stopRiding();level.tickNonPassenger(seat);level.tickNonPassenger(seat);
            h.assertTrue(seat.isRemoved(),"Dismount leaked seat");
            level.getBlockState(main.above()).useWithoutItem(level,player,hit);var replacement=player.getVehicle();
            h.assertTrue(replacement!=null,"Cannot remount bird");
            level.destroyBlock(main.above(),false);
            h.assertTrue(!player.isPassenger()&&replacement.isRemoved()&&level.getBlockState(main).isAir(),"Breaking upper part must dismount and clean up");
        }
        h.succeed();
    }
}
