package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.templates.*;
import com.stardew.craft.workbench.TemplateWorkbenchRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_roof_templates")
@PrefixGameTestTemplate(false)
public final class RoofTemplateGameTests {
    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void slopeCornersAndRidgeNetworksFollowNeighbors(GameTestHelper h) {
        var world=h.getLevel(); var p=h.absolutePos(new BlockPos(8,2,8));
        var block=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_SLOPE).get();
        var base=block.defaultBlockState();
        for (Direction facing:Direction.Plane.HORIZONTAL) {
            var state=base.setValue(MaterialTemplateBlock.FACING,facing);
            for (boolean inner:new boolean[]{false,true}) for (boolean left:new boolean[]{false,true}) {
                for (var at:BlockPos.betweenClosed(p.offset(-2,0,-2),p.offset(2,0,2))) world.setBlock(at,Blocks.AIR.defaultBlockState(),3);
                world.setBlock(p,state,3);
                var neighbor=p.relative(inner?facing.getOpposite():facing);
                world.setBlock(neighbor,base.setValue(MaterialTemplateBlock.FACING,left?facing.getCounterClockWise():facing.getClockWise()),3);
                var expected=inner?(left?StairsShape.INNER_LEFT:StairsShape.INNER_RIGHT):(left?StairsShape.OUTER_LEFT:StairsShape.OUTER_RIGHT);
                h.assertTrue(world.getBlockState(p).getValue(SmartRoofTemplateBlock.ROOF_SHAPE)==expected,"Wrong corner "+facing+" / "+expected);
                world.setBlock(neighbor,Blocks.AIR.defaultBlockState(),3);
                h.assertTrue(world.getBlockState(p).getValue(SmartRoofTemplateBlock.ROOF_SHAPE)==StairsShape.STRAIGHT,"Corner did not clear");
            }
        }
        var ridge=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_RIDGE).get().defaultBlockState();
        Direction[] directions={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        for(int mask=0;mask<16;mask++) {
            for(Direction d:directions) world.setBlock(p.relative(d),Blocks.AIR.defaultBlockState(),3);
            world.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            world.setBlock(p,ridge,3);
            for(int i=0;i<4;i++) world.setBlock(p.relative(directions[i]),(mask&(1<<i))!=0?ridge:Blocks.AIR.defaultBlockState(),3);
            var state=world.getBlockState(p);
            h.assertTrue(SmartRidgeTemplateBlock.connectionMask(state)==mask,"Ridge network mask "+mask);
            var rotated=state.rotate(Rotation.CLOCKWISE_90);
            h.assertTrue(SmartRidgeTemplateBlock.connectionMask(rotated)==((mask<<1)&15 | mask>>3),"Ridge rotation "+mask);
            for(var box:state.getShape(world,p).toAabbs()) h.assertTrue(box.minX>=0 && box.minY>=0 && box.minZ>=0
                    && box.maxX<=1 && box.maxY<=0.5+RoofTemplateForm.SHELL_THICKNESS+1E-5 && box.maxZ<=1,"Ridge collision escaped its visible height");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void newTemplatesRetainMaterialsAndLocalCollision(GameTestHelper h) {
        var world=h.getLevel(); var p=h.absolutePos(new BlockPos(8,2,8));
        h.assertTrue(TemplateWorkbenchRecipes.build().size()==java.util.Arrays.stream(TemplateShape.values()).filter(TemplateShape::visibleInCreativeTab).count(),"Missing template workbench recipe");
        for(var shape:new TemplateShape[]{TemplateShape.WALL_BEAM,TemplateShape.WALL_POST,TemplateShape.WALL_BRACE_LEFT,
                TemplateShape.WALL_BRACE_RIGHT,TemplateShape.ROOF_EAVE,TemplateShape.GABLE_PANEL}) {
            var block=TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            h.assertTrue(block.asItem()!=net.minecraft.world.item.Items.AIR,"Missing item: "+shape);
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                world.setBlock(p,block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,direction),3);
                var entity=(TemplateBlockEntity)world.getBlockEntity(p);
                entity.setMaterial(Blocks.BRICKS.defaultBlockState());
                h.assertTrue(entity.material().is(Blocks.BRICKS) && world.getBlockState(p).is(block),"Material replaced template identity");
                for(var box:world.getBlockState(p).getShape(world,p).toAabbs()) h.assertTrue(box.minX>=0 && box.minY>=0 && box.minZ>=0
                        && box.maxX<=1 && box.maxY<=1 && box.maxZ<=1,"Collision outside occupied cell: "+shape);
                entity.setMaterial(Blocks.GLASS.defaultBlockState());
                h.assertTrue(!world.getBlockState(p).getValue(MaterialTemplateBlock.SOLID),"Transparent material lost: "+shape);
                world.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            }
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void compositeRoofRetainsSlotsThroughItemsAndStructureData(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        for (var shape : TemplateShape.values()) {
            if (shape.meshKind() != TemplateShape.MeshKind.ROOF) continue;
            var block = TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, block.defaultBlockState(), 3);
            var entity = (TemplateBlockEntity) level.getBlockEntity(pos);
            double hollowVolume = volume(level.getBlockState(pos).getCollisionShape(level, pos));
            entity.setMaterial(Blocks.GLASS.defaultBlockState());
            entity.setFillMaterial(Blocks.BRICKS.defaultBlockState());
            var state = level.getBlockState(pos);
            h.assertTrue(state.getValue(RoofTemplateBlock.FILLED), "Fill collision state missing: " + shape);
            // Thick lower/ridge pieces leave a smaller but still usable infill cavity.
            h.assertTrue(volume(state.getCollisionShape(level, pos)) > hollowVolume + 1E-6, "Hollow eave still solid: " + shape);
            var saved = entity.saveWithFullMetadata();
            // Both Ctrl+pick and survival drops use the standard block-entity item component.
            var copied = new net.minecraft.world.item.ItemStack(block);
            entity.saveToItem(copied);
            var drops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, entity);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()), "Roof drop duplicated or missing: " + shape);
            h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameTags(copied, drops.getFirst()), "Dropped roof lost a slot: " + shape);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, block.defaultBlockState(), 3);
            h.assertTrue(net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level, null, pos, copied), "Item placement rejected data");
            entity = (TemplateBlockEntity) level.getBlockEntity(pos);
            h.assertTrue(entity.material().is(Blocks.GLASS) && entity.fillMaterial().is(Blocks.BRICKS)
                    && level.getBlockState(pos).getValue(RoofTemplateBlock.FILLED), "Placed item did not restore both slots");
            entity.setFillMaterial(null);
            h.assertTrue(!level.getBlockState(pos).getValue(RoofTemplateBlock.FILLED)
                    && Math.abs(volume(level.getBlockState(pos).getCollisionShape(level,pos))-hollowVolume) < 1E-6, "Removing fill left solid collision");
            entity.load(saved);
            h.assertTrue(entity.fillMaterial().is(Blocks.BRICKS) && level.getBlockState(pos).getValue(RoofTemplateBlock.FILLED), "Structure restoration lost infill");
            entity.setFillMaterial(null);
            entity.setFillMaterial(Blocks.CHEST.defaultBlockState());
            h.assertTrue(entity.fillMaterial() == null, "Functional block accepted as a static infill");
            for (var box : level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())
                h.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0 && box.maxX <= 1 && box.maxY <= 1.5 && box.maxZ <= 1, "Roof escaped its footprint or structural height");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void roofInteractionsConsumeAndRemoveOnlyTheSelectedSlot(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8,3,8));
        var block = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_SLOPE).get();
        level.setBlock(pos, block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,Direction.NORTH), 3);
        var entity = (TemplateBlockEntity) level.getBlockEntity(pos);
        var player = PortGameTests.makeMockPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var top = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5),Direction.UP,pos,false);
        var bottom = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(pos.getX()+0.5,pos.getY()+0.375,pos.getZ()+0.5),Direction.DOWN,pos,false);
        player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(Blocks.GLASS,3));
        TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,pos,top));
        h.assertTrue(entity.material().is(Blocks.GLASS) && entity.fillMaterial()==null && player.getItemInHand(hand).getCount()==2,"Roof application consumed wrong slot/count");
        player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(Blocks.BRICKS,3));
        TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,pos,bottom));
        h.assertTrue(entity.fillMaterial().is(Blocks.BRICKS) && entity.material().is(Blocks.GLASS) && player.getItemInHand(hand).getCount()==2,"Infill application changed roof");
        var ordinary = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,pos,bottom);
        TemplateInteractionEvents.onRightClick(ordinary);
        h.assertTrue(!ordinary.isCanceled() && player.getItemInHand(hand).getCount()==2,"Filled slot blocked normal placement");
        player.setShiftKeyDown(true);
        player.setItemInHand(hand,net.minecraft.world.item.ItemStack.EMPTY);
        TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,pos,bottom));
        h.assertTrue(entity.fillMaterial()==null && entity.material()==null,"Shift removal must clear both slots");
        h.assertTrue(player.getInventory().countItem(Blocks.BRICKS.asItem())==1,"Infill refund duplicated or missing");
        h.assertTrue(player.getInventory().countItem(Blocks.GLASS.asItem())==1,"Primary refund duplicated or missing");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void refinedTrimOnlyAppearsAtTheWholeRoofPerimeter(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(8,4,8));
        var block=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_SLOPE).get();
        Direction[] sides={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            for(var q:BlockPos.betweenClosed(p.offset(-2,-2,-2),p.offset(2,2,2))) level.setBlock(q,Blocks.AIR.defaultBlockState(),3);
            var state=block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,facing);
            level.setBlock(p,state,3);
            h.assertTrue(RoofTemplateEdges.exposed(level,p,state)==15,"Isolated roof lost an edge");
            level.setBlock(p.relative(facing).above(),state,3);
            level.setBlock(p.relative(facing.getOpposite()).below(),state,3);
            level.setBlock(p.relative(facing.getClockWise()),state,3);
            int expected=0;
            for(int j=0;j<4;j++) if(sides[j]==facing.getCounterClockWise())expected=1<<j;
            h.assertTrue(RoofTemplateEdges.exposed(level,p,level.getBlockState(p))==expected,"Internal trim remains across a height step: "+facing);
            level.setBlock(p.relative(facing).above(),Blocks.AIR.defaultBlockState(),3);
            for(int j=0;j<4;j++) if(sides[j]==facing)expected|=1<<j;
            h.assertTrue(RoofTemplateEdges.exposed(level,p,level.getBlockState(p))==expected,"Exposed edge did not return after removal");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void wholeHipRoofConnectsAcrossRisingDiagonalCourses(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(5,3,5));
        var block=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_SLOPE).get();
        for(int x=0;x<4;x++) for(int z=0;z<4;z++) {
            int y=Math.min(Math.min(x,3-x),Math.min(z,3-z));
            Direction facing=z==y?Direction.SOUTH:3-z==y?Direction.NORTH:x==y?Direction.EAST:Direction.WEST;
            level.setBlock(origin.offset(x,y,z),block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,facing),3);
        }
        for(int x=0;x<4;x++) for(int z=0;z<4;z++) {
            int y=Math.min(Math.min(x,3-x),Math.min(z,3-z));
            var pos=origin.offset(x,y,z);var state=level.getBlockState(pos);
            int expected=(z==0?1:0)|(x==3?2:0)|(z==3?4:0)|(x==0?8:0);
            h.assertTrue(RoofTemplateEdges.exposed(level,pos,state)==expected,
                    "Hip roof has an internal break at "+x+","+y+","+z+": "+state+" mask="+RoofTemplateEdges.exposed(level,pos,state)+" N="+level.getBlockState(pos.north())+" S="+level.getBlockState(pos.south())+" E+="+level.getBlockState(pos.east().above()));
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void gableCoursesAndMaterialChangesKeepTheCorrectSections(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(5,3,5));
        var block=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_SLOPE).get();
        var state=block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,Direction.NORTH);
        var next=p.north().above();
        level.setBlock(p,state,3);level.setBlock(next,state,3);
        var a=(TemplateBlockEntity)level.getBlockEntity(p);
        var b=(TemplateBlockEntity)level.getBlockEntity(next);
        a.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
        b.setMaterial(Blocks.STONE_BRICKS.defaultBlockState());
        h.assertTrue((RoofTemplateEdges.hiddenSections(level,p,level.getBlockState(p))&1)!=0,"Opaque rising join retains its internal cap");
        b.setMaterial(Blocks.GLASS.defaultBlockState());
        h.assertTrue((RoofTemplateEdges.exposed(level,p,level.getBlockState(p))&1)==0,"Changing material broke the geometric join");
        h.assertTrue((RoofTemplateEdges.hiddenSections(level,p,level.getBlockState(p))&1)==0,"Glass removed the visible wood section");
        a.setMaterial(Blocks.GLASS.defaultBlockState());
        h.assertTrue((RoofTemplateEdges.hiddenSections(level,p,level.getBlockState(p))&1)!=0,"Same glass retains doubled internal faces");
        b.setMaterial(Blocks.STONE_BRICKS.defaultBlockState());
        h.assertTrue((RoofTemplateEdges.hiddenSections(level,p,level.getBlockState(p))&1)!=0,"Opaque neighbor fails to hide the glass section");
        level.setBlock(next,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue((RoofTemplateEdges.exposed(level,p,level.getBlockState(p))&1)!=0
                && (RoofTemplateEdges.hiddenSections(level,p,level.getBlockState(p))&1)==0,"Removing a course left an open end");
        h.succeed();
    }

    private static double volume(net.minecraft.world.phys.shapes.VoxelShape shape) {
        return shape.toAabbs().stream().mapToDouble(b -> b.getXsize()*b.getYsize()*b.getZsize()).sum();
    }

}
