package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.templates.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in: -PgameTestNamespaces=stardewcraft_house_details. */
@GameTestHolder("stardewcraft_house_details")
@PrefixGameTestTemplate(false)
public final class HouseDetailsGameTests {
    @GameTest(templateNamespace="stardewcraft_house_details", template="house_details")
    public static void pendantReservesLowerCellAndDropsOnceFromEitherPart(GameTestHelper h) {
        var level=h.getLevel();var player=FakePlayerFactory.getMinecraft(level);
        for(var block:new MapDecorStaticBlock[]{ModBlocks.OWL_PENDANT.get(),ModBlocks.HANGING_BASKET.get()}) {
            var support=h.absolutePos(new BlockPos(block==ModBlocks.OWL_PENDANT.get()?4:10,7,5));
            var main=support.below();level.setBlock(support,Blocks.STONE.defaultBlockState(),3);
            level.setBlock(main,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(main.below(),Blocks.STONE.defaultBlockState(),3);
            var stack=new ItemStack(block,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atBottomCenterOf(support),Direction.DOWN,support,false);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
            stack.useOn(context);
            h.assertTrue(level.getBlockState(main).isAir(),"Pendant overwrote blocked lower cell");
            h.assertTrue(stack.getCount()==2,"Rejected placement consumed an item");
            level.setBlock(main.below(),Blocks.AIR.defaultBlockState(),3);stack.useOn(context);
            h.assertTrue(level.getBlockState(main).is(block),"Ceiling placement failed");
            h.assertTrue(level.getBlockState(main.below()).is(block),"Lower occupied cell was not reserved");
            h.assertTrue(level.getBlockState(main.below()).getValue(MapDecorStaticBlock.PART)==MapDecorStaticBlock.Part.EXTENSION,"Incorrect lower part");
            level.destroyBlock(main.below(),true);
            h.assertTrue(level.getBlockState(main).isAir() && level.getBlockState(main.below()).isAir(),"Dangling decor part");
            int count=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(2)).stream()
                    .filter(e->e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(count==1,"Expected one decor drop, found "+count);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_house_details", template="house_details")
    public static void removingCeilingCleansBothPendantCells(GameTestHelper h) {
        var level=h.getLevel();var player=FakePlayerFactory.getMinecraft(level);var support=h.absolutePos(new BlockPos(7,7,7));
        var block=ModBlocks.OWL_PENDANT.get();var stack=new ItemStack(block);
        level.setBlock(support,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(support.below(),Blocks.AIR.defaultBlockState(),3);level.setBlock(support.below(2),Blocks.AIR.defaultBlockState(),3);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(support),Direction.DOWN,support,false)));
        h.assertTrue(level.getBlockState(support.below()).is(block),"Initial pendant missing");
        level.destroyBlock(support,false);
        h.assertTrue(level.getBlockState(support.below()).isAir() && level.getBlockState(support.below(2)).isAir(),"Unsupported pendant survives");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_house_details", template="house_details")
    public static void paintedDoorsKeepTwoHalvesAndRespondToRedstone(GameTestHelper h) {
        var level=h.getLevel();var player=FakePlayerFactory.getMinecraft(level);var pos=h.absolutePos(new BlockPos(7,2,7));
        for (var block : new DoorBlock[]{ModBlocks.GREEN_PANEL_DOOR.get(), ModBlocks.RED_GLASS_DOOR.get()}) {
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
        var stack=new ItemStack(block);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,-.5,0),Direction.UP,pos.below(),false)));
        h.assertTrue(level.getBlockState(pos).is(block) && level.getBlockState(pos.above()).is(block),"Door placement did not create two halves");
        h.assertTrue(level.getBlockState(pos.above()).getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER,"Wrong upper half");
        block.setOpen(player,level,level.getBlockState(pos),pos,true);
        h.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN) && level.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Manual open desynchronized halves");
        block.setOpen(player,level,level.getBlockState(pos),pos,false);
        level.setBlock(pos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        h.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN) && level.getBlockState(pos).getValue(DoorBlock.POWERED),"Redstone did not open door");
        level.setBlock(pos.east(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(!level.getBlockState(pos).getValue(DoorBlock.OPEN),"Door remained open after power was removed");
        level.destroyBlock(pos.above(),true);
        h.assertTrue(level.getBlockState(pos).isAir(),"Removing upper half left lower door");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_house_details", template="house_details")
    public static void paintedDoorPlacementAndCollisionMatchVanillaInEveryDirection(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var pos = h.absolutePos(new BlockPos(7, 2, 7));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        for (var block : new DoorBlock[]{ModBlocks.GREEN_PANEL_DOOR.get(), ModBlocks.RED_GLASS_DOOR.get()}) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                player.setYRot(facing.toYRot());
                player.setXRot(0);
                player.setPos(Vec3.atCenterOf(pos.relative(facing.getOpposite(), 2)));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
                var context = new net.minecraft.world.item.context.BlockPlaceContext(new UseOnContext(player,
                        InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atBottomCenterOf(pos).add(.2, 0, .2),
                        Direction.UP, pos.below(), false)));
                var actual = block.getStateForPlacement(context);
                var vanilla = Blocks.OAK_DOOR.getStateForPlacement(context);
                h.assertTrue(actual != null && vanilla != null, "Placement unexpectedly rejected");
                h.assertTrue(actual.getValue(DoorBlock.FACING) == facing, "Placement facing differs from player heading");
                h.assertTrue(actual.getValue(DoorBlock.HINGE) == vanilla.getValue(DoorBlock.HINGE), "Hinge selection differs from vanilla");
                for (var hinge : net.minecraft.world.level.block.state.properties.DoorHingeSide.values()) {
                    for (boolean open : new boolean[]{false, true}) {
                        var state = actual.setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, open);
                        var expected = vanilla.setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, open);
                        h.assertTrue(state.getShape(level, pos).bounds().equals(expected.getShape(level, pos).bounds()),
                                "Door shape differs from vanilla: " + facing + " / " + hinge + " / " + open);
                        h.assertTrue(state.getCollisionShape(level, pos).bounds().equals(expected.getCollisionShape(level, pos).bounds()),
                                "Door collision differs from vanilla");
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_house_details", template="house_details")
    public static void refinedWindowsConnectAndRetainIndependentGlassMaterial(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(7,4,7));
        var block=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.WINDOW_FRAME).get();
        for(var front:Direction.Plane.HORIZONTAL) {
            for(var q:BlockPos.betweenClosed(p.offset(-2,-2,-2),p.offset(2,2,2)))level.setBlock(q,Blocks.AIR.defaultBlockState(),3);
            for(int x=0;x<2;x++)for(int y=0;y<2;y++)level.setBlock(p.relative(front.getClockWise(),x).above(y),block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,front),3);
            var state=level.getBlockState(p);h.assertTrue(ConnectedFacadeTemplateBlock.connections(state)==3,"Incorrect corner connection");
            var entity=(TemplateBlockEntity)level.getBlockEntity(p);
            h.assertTrue(entity.effectiveFillMaterial().is(ModBlocks.PALE_BLUE_WINDOW_GLASS.get()),"Default glass was not updated");
            entity.setMaterial(ModBlocks.BLUE_GRAY_TIMBER.get().defaultBlockState());entity.setFillMaterial(Blocks.RED_STAINED_GLASS.defaultBlockState());
            var copied=new ItemStack(block);entity.saveToItem(copied,level.registryAccess());
            var data=TemplateBlockEntity.itemMaterials(copied);
            h.assertTrue(data.get(TemplateBlockEntity.FILL_MATERIAL_PROPERTY).is(Blocks.RED_STAINED_GLASS),"Copied window lost its custom pane");
            entity.setFillMaterial(null);h.assertTrue(entity.effectiveFillMaterial().is(ModBlocks.PALE_BLUE_WINDOW_GLASS.get()),"Clearing pane failed to restore built-in glass");
            level.setBlock(p.relative(front.getClockWise()),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(ConnectedFacadeTemplateBlock.connections(level.getBlockState(p))==1,"Deleted neighbor left an open jamb");
        }
        h.succeed();
    }
}
