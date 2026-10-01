package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.DoubleSwingBlock;
import com.stardew.craft.block.decor.DoubleSwingMotion;
import com.stardew.craft.entity.seat.DoubleSwingSeatEntity;
import java.nio.charset.StandardCharsets;
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
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_swing")
@PrefixGameTestTemplate(false)
public final class DoubleSwingGameTests {
    private static BlockPlaceContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.DOUBLE_SWING.get(), 3));
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false)));
    }
    private static BlockPos prepare(GameTestHelper h) {
        var level = h.getLevel();
        for (int x=0;x<22;x++) for (int z=0;z<22;z++) for (int y=0;y<8;y++)
            level.setBlock(h.absolutePos(new BlockPos(x,y,z)), y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        return h.absolutePos(new BlockPos(10,1,10));
    }
    private static int count(GameTestHelper h, BlockPos main) {
        int count=0;
        for (var pos:BlockPos.betweenClosed(main.offset(-3,0,-3),main.offset(3,5,3)))
            if(h.getLevel().getBlockState(pos).is(ModBlocks.DOUBLE_SWING.get()))count++;
        return count;
    }
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void placementClearanceAndSingleDrop(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var block=ModBlocks.DOUBLE_SWING.get();var player=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(main.offset(8,0,7)));
        for(var facing:Direction.Plane.HORIZONTAL){
            player.setYRot(facing.getOpposite().toYRot());var ctx=context(player,main);
            h.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Cannot place "+facing);
            h.assertTrue(count(h,main)==82,"Incomplete winter/motion clearance");
            for(var pos:BlockPos.betweenClosed(main.offset(-3,0,-3),main.offset(3,5,3))){
                var state=level.getBlockState(pos);if(!state.is(block))continue;
                h.assertTrue(main.equals(block.findMainPos(level,pos,state)),"Wrong owner "+pos);
                var shape=state.getCollisionShape(level,pos);
                if(!shape.isEmpty())h.assertTrue(shape.bounds().minX>=0&&shape.bounds().maxX<=1&&shape.bounds().minY>=0&&shape.bounds().maxY<=1&&shape.bounds().minZ>=0&&shape.bounds().maxZ<=1,"Collision escapes cell");
            }
            h.assertTrue(level.getBlockState(main.above()).getCollisionShape(level,main.above()).isEmpty(),"Empty center blocks walking");
            var neighbor=main.relative(facing.getClockWise(),7);var ctx2=context(player,neighbor);
            h.assertTrue(((BlockItem)ctx2.getItemInHand().getItem()).place(ctx2).consumesAction(),"Cannot place adjacent swing "+facing+", occupied="+count(h,neighbor)+", feet="+level.getBlockState(neighbor.offset(DoubleSwingBlock.rotateOffset(new BlockPos(-2,-1,0),facing)))+" / "+level.getBlockState(neighbor.offset(DoubleSwingBlock.rotateOffset(new BlockPos(2,-1,0),facing))));
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(10)))item.discard();
            var pillar=main.offset(DoubleSwingBlock.rotateOffset(new BlockPos(-2,3,0),facing));
            level.destroyBlock(pillar,true);
            h.assertTrue(count(h,main)==0,"Broken swing left cells");h.assertTrue(count(h,neighbor)==82,"Damaged adjacent swing");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(4));
            h.assertTrue(drops.stream().mapToInt(i->i.getItem().getCount()).sum()==1&&com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()),"Expected one whole swing drop");
            DoubleSwingBlock.runWithDropsSuppressed(()->level.removeBlock(neighbor,false));
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void obstructedPlacementIsAtomic(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var block=ModBlocks.DOUBLE_SWING.get();var player=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(main.offset(8,0,7)));player.setYRot(Direction.SOUTH.toYRot());
        for(var obstacle:new BlockPos[]{main.offset(1,2,1),main.offset(3,5,0)}){
            level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);var ctx=context(player,main);
            h.assertTrue(!((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Placed inside swing/snow clearance");
            h.assertTrue(count(h,main)==0&&ctx.getItemInHand().getCount()==3,"Failed placement consumed item/left parts");
            level.removeBlock(obstacle,false);
        }
        level.removeBlock(main.offset(-2,-1,0),false);
        h.assertTrue(block.getStateForPlacement(context(player,main))==null,"Unsupported steel foot allowed");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void independentSeatsFollowMotionAndCleanUp(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var block=ModBlocks.DOUBLE_SWING.get();
        for(var facing:Direction.Plane.HORIZONTAL){
            var one=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);var two=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
            one.setPos(Vec3.atBottomCenterOf(main.offset(8,0,7)));one.setYRot(facing.getOpposite().toYRot());
            var ctx=context(one,main);h.assertTrue(((BlockItem)ctx.getItemInHand().getItem()).place(ctx).consumesAction(),"Setup failed");
            for(int slot=0;slot<2;slot++){
                var player=slot==0?one:two;var off=DoubleSwingBlock.rotateOffset(new BlockPos(slot==0?-1:1,0,0),facing);var pos=main.offset(off);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos),facing,pos,false);
                player.setShiftKeyDown(true);PortBlockInteraction.stateUseWithoutItem(level.getBlockState(pos), level,player,hit);h.assertTrue(!player.isPassenger(),"Sneak unexpectedly mounted");
                player.setShiftKeyDown(false);PortBlockInteraction.stateUseWithoutItem(level.getBlockState(pos), level,player,hit);
                h.assertTrue(player.getVehicle() instanceof DoubleSwingSeatEntity,"Could not mount seat");
                var seat=(DoubleSwingSeatEntity)player.getVehicle();h.assertTrue(seat.slot()==slot,"Click selected wrong seat");
                for(int i=0;i<3;i++)level.tickNonPassenger(seat);
                h.assertTrue(seat.isAlive()&&seat.getFirstPassenger()==player,"Mounted seat discarded");
                var surface=DoubleSwingMotion.surface(main,facing,slot,DoubleSwingMotion.seconds(level.getGameTime(),0),com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason()==3);
                h.assertTrue(player.position().add(0,.75-1.0/16,0).distanceTo(surface)<.0001,"Hips detached from moving seat");
            }
            h.assertTrue(one.getVehicle()!=two.getVehicle(),"Two players share same entity");
            var third=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);var firstPos=main.offset(DoubleSwingBlock.rotateOffset(new BlockPos(-1,0,0),facing));
            PortBlockInteraction.stateUseWithoutItem(level.getBlockState(firstPos), level,third,new BlockHitResult(Vec3.atCenterOf(firstPos),facing,firstPos,false));
            h.assertTrue(!third.isPassenger(),"Occupied seat stolen");
            var firstSeat=one.getVehicle();one.stopRiding();level.tickNonPassenger(firstSeat);level.tickNonPassenger(firstSeat);
            h.assertTrue(firstSeat.isRemoved()&&two.isPassenger(),"Dismount removed wrong seat or leaked entity");
            var secondSeat=two.getVehicle();level.destroyBlock(main.above(4),false);
            h.assertTrue(!two.isPassenger()&&secondSeat.isRemoved()&&count(h,main)==0,"Destruction retained rider or parts");
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_swing",template="ring_utilities")
    public static void seasonalResourcesAndLoopAreComplete(GameTestHelper h) throws Exception {
        for(String season:new String[]{"spring","summer","fall","winter"}){
            try(var in=DoubleSwingGameTests.class.getResourceAsStream("/assets/stardewcraft/double_swing/"+season+".json")){
                h.assertTrue(in!=null,"Missing season assembly");var data=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                h.assertTrue(data.getAsJsonArray("parts").size()==(season.equals("winter")?60:53),"Wrong seasonal parts");
                for(var entry:data.getAsJsonArray("parts")){
                    String path=entry.getAsJsonObject().get("model").getAsString().split(":")[1];
                    try(var part=DoubleSwingGameTests.class.getResourceAsStream("/assets/stardewcraft/models/"+path+".json")){h.assertTrue(part!=null,"Missing native part");}
                }
            }
        }
        for(int slot=0;slot<2;slot++)for(int frame=0;frame<80;frame++){
            double t=frame/20.0;h.assertTrue(Math.abs(DoubleSwingMotion.angle(slot,t,false)-DoubleSwingMotion.angle(slot,t+4,false))<1e-9,"Animation loop jumps");
            h.assertTrue(DoubleSwingMotion.angle(slot,t,true)==0,"Winter swings");
        }
        h.succeed();
    }
}
