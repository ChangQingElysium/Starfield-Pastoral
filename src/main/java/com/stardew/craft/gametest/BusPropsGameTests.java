package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.desert.DesertBusService;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class BusPropsGameTests {
    private BusPropsGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void reusablePropsPlaceRotateAndRemoveBothCells(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Bus props"));
        player.setGameMode(GameType.CREATIVE);
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
        for (MapDecorStaticBlock block : List.of(ModBlocks.ROAD_SIGN.get(),ModBlocks.TICKET_MACHINE.get())) {
            helper.assertTrue(StardewItemCatalog.tabForItem(block.asItem()) == StardewCatalogTab.BUILDING,"Missing building catalog entry");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.getOpposite().toYRot());
                var stack = new ItemStack(block);
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                level.setBlock(pos.above(),Blocks.STONE.defaultBlockState(),2);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
                var blocked = new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                helper.assertTrue(!((BlockItem)stack.getItem()).place(blocked).consumesAction(),"Placement overwrote obstruction above");
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).is(Blocks.STONE),"Blocked placement left a partial prop");
                level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
                var context = new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                helper.assertTrue(((BlockItem)stack.getItem()).place(context).consumesAction(),"Prop placement failed");
                var state = level.getBlockState(pos);
                var upper = level.getBlockState(pos.above());
                helper.assertTrue(state.is(block) && state.getValue(MapDecorStaticBlock.FACING) == facing,"Wrong facing");
                helper.assertTrue(upper.is(block) && upper.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION,"Missing upper selection cell");
                helper.assertTrue(pos.equals(block.findMainPos(level,pos.above(),upper)),"Upper cell lost owner");
                var shape = state.getShape(level,pos);
                helper.assertTrue(!shape.isEmpty() && shape.bounds().maxY > 1 && shape.bounds().maxY < 2,"Model collision did not load");
                helper.assertTrue(!Shapes.joinIsNotEmpty(shape,upper.getShape(level,pos.above()).move(0,1,0),BooleanOp.NOT_SAME),"Upper and lower outlines diverge");
                level.destroyBlock(pos.above(),true);
                helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(),"Breaking the upper half orphaned a cell");
                var drops = level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3),e -> e.getItem().is(block.asItem()));
                helper.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,"Upper half dropped zero or duplicate items");
                drops.forEach(Entity::discard);
            }
            // Creative removal must clean up both halves without emitting an item.
            var state = block.defaultBlockState();
            level.setBlock(pos,state,2);block.placeExtensions(level,pos,state);
            block.playerWillDestroy(level,pos.above(),level.getBlockState(pos.above()),player);
            level.removeBlock(pos.above(),false);
            helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(),"Creative removal orphaned the base");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3),e -> e.getItem().is(block.asItem())).isEmpty(),"Creative removal dropped an item");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void bothTicketMachineCellsUseTheBusGateWithoutCharging(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        var player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Ticket test"));
        player.setPos(Vec3.atCenterOf(pos.north(2)));
        var data = PlayerStardewDataAPI.getData(player);
        data.removeMailFlag(CCStoryFlags.CC_VAULT);
        int money = PlayerStardewDataAPI.getMoney(player);
        var machine = ModBlocks.TICKET_MACHINE.get();
        var state = machine.defaultBlockState();
        level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),2);
        level.setBlock(pos,state,2);machine.placeExtensions(level,pos,state);
        for (BlockPos cell : List.of(pos,pos.above())) {
            var hit = new BlockHitResult(Vec3.atCenterOf(cell),Direction.NORTH,cell,false);
            helper.assertTrue(level.getBlockState(cell).useWithoutItem(level,player,hit).consumesAction(),"Machine half failed to handle interaction");
            helper.assertTrue(PlayerStardewDataAPI.getMoney(player) == money && !DesertBusService.isRiding(player),"Locked machine charged or started a ride");
        }
        // The reusable road sign has no bus behavior.
        var sign = ModBlocks.ROAD_SIGN.get();
        var signPos = pos.east(3);
        level.setBlock(signPos.above(),Blocks.AIR.defaultBlockState(),2);
        level.setBlock(signPos,sign.defaultBlockState(),2);sign.placeExtensions(level,signPos,sign.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(signPos.above()),Direction.NORTH,signPos.above(),false);
        helper.assertTrue(!level.getBlockState(signPos.above()).useWithoutItem(level,player,hit).consumesAction(),"Reusable road sign opens a purchase flow");
        helper.succeed();
    }
}
