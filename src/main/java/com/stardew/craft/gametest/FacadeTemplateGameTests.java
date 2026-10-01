package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.templates.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_roof_templates")
@PrefixGameTestTemplate(false)
public final class FacadeTemplateGameTests {
    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void windowsJoinInTheirOwnPlaneAndRetainPanes(GameTestHelper h) {
        var world=h.getLevel();var p=h.absolutePos(new BlockPos(8,3,8));
        var frame=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.WINDOW_FRAME).get().defaultBlockState();
        var transom=TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.WINDOW_TRANSOM).get().defaultBlockState();
        for(Direction front:Direction.Plane.HORIZONTAL) {
            for(var q:BlockPos.betweenClosed(p.offset(-2,-2,-2),p.offset(2,2,2)))world.setBlock(q,Blocks.AIR.defaultBlockState(),3);
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)world.setBlock(p.relative(front.getClockWise(),x).above(y),
                    (y==0?transom:frame).setValue(MaterialTemplateBlock.FACING,front),3);
            var state=world.getBlockState(p);
            h.assertTrue(ConnectedFacadeTemplateBlock.connections(state)==15,"Interior window retains a border: "+front);
            var entity=(TemplateBlockEntity)world.getBlockEntity(p);
            h.assertTrue(entity.effectiveFillMaterial().is(com.stardew.craft.block.ModBlocks.PALE_BLUE_WINDOW_GLASS.get()),"Missing default pane");
            h.assertTrue(!state.getShape(world,p).isEmpty(),"Window center cannot be selected");
            world.setBlock(p.relative(front.getClockWise()),Blocks.AIR.defaultBlockState(),3);
            state=world.getBlockState(p);
            h.assertTrue(ConnectedFacadeTemplateBlock.connections(state)==13,"Removing a neighbor did not restore the right jamb");
            h.assertTrue(ConnectedFacadeTemplateBlock.connections(state.rotate(Rotation.CLOCKWISE_90))==13,"Rotating window changed local borders");
            h.assertTrue(ConnectedFacadeTemplateBlock.connections(state.mirror(Mirror.LEFT_RIGHT))==7,"Mirroring window kept wrong jamb");
            for(var box:state.getShape(world,p).toAabbs())h.assertTrue(box.minX>=0 && box.minY>=0 && box.minZ>=0 && box.maxX<=1 && box.maxY<=1 && box.maxZ<=1,"Window collision escaped cell");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void facadeSlotsSurviveItemsAndClearIndependently(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(8,3,8));
        for(var shape:TemplateShape.values()) {
            if(!shape.isCompositeWall() && !shape.isWindow())continue;
            var block=TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3);level.setBlock(p,block.defaultBlockState(),3);
            var entity=(TemplateBlockEntity)level.getBlockEntity(p);
            entity.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());entity.setFillMaterial(Blocks.BLUE_STAINED_GLASS.defaultBlockState());
            var copied=new net.minecraft.world.item.ItemStack(block);entity.saveToItem(copied);
            var drops=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(p),level,p,entity);
            h.assertTrue(drops.size()==1 && net.minecraft.world.item.ItemStack.isSameItemSameTags(copied,com.stardew.craft.port.PortJava.getFirst(drops)),"Composite drop lost or duplicated a material: "+shape);
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3);level.setBlock(p,block.defaultBlockState(),3);
            h.assertTrue(net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level,null,p,copied),"Item data rejected");
            entity=(TemplateBlockEntity)level.getBlockEntity(p);
            h.assertTrue(entity.material().is(Blocks.OAK_PLANKS) && entity.fillMaterial().is(Blocks.BLUE_STAINED_GLASS),"Placed template lost a material");
            entity.setFillMaterial(null);
            h.assertTrue(entity.material().is(Blocks.OAK_PLANKS),"Clearing backing also cleared frame");
            h.assertTrue(shape.isWindow()?entity.effectiveFillMaterial().is(com.stardew.craft.block.ModBlocks.PALE_BLUE_WINDOW_GLASS.get()):entity.effectiveFillMaterial()==null,"Wrong empty-slot behavior");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void facadeInteractionFillsPrimaryBeforeBacking(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(8,3,8));
        var player=PortGameTests.makeMockPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        for(var shape:new TemplateShape[]{TemplateShape.WALL_BEAM,TemplateShape.WINDOW_FRAME})for(Direction front:Direction.Plane.HORIZONTAL) {
            var block=TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(p,block.defaultBlockState().setValue(MaterialTemplateBlock.FACING,front),3);
            var entity=(TemplateBlockEntity)level.getBlockEntity(p);
            if (shape.isWindow()) {
                double bx=.05,bz=.5;
                for(int i=0,n=TemplateShapeCache.turnsFrom(Direction.NORTH,front);i<n;i++){double old=bx;bx=1-bz;bz=old;}
                var backHit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(p.getX()+bx,p.getY()+.5,p.getZ()+bz),front.getOpposite(),p,false);
                h.assertTrue(!((WallCompositeTemplateBlock)block).targetsFill(level.getBlockState(p),backHit),"Rear of wooden jamb targeted the glass slot");
            }
            for(boolean fill:new boolean[]{false,true}) {
                // Front frame at z=0; exposed backing at z=3 or window pane at z=5.
                double x=.5,z=(shape.isWindow()?5:3)/16D;
                for(int i=0,n=TemplateShapeCache.turnsFrom(Direction.NORTH,front);i<n;i++){double old=x;x=1-z;z=old;}
                var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(p.getX()+x,p.getY()+.5,p.getZ()+z),front,p,false);
                player.setShiftKeyDown(false);player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(fill?Blocks.BRICKS:Blocks.OAK_PLANKS,3));
                TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,p,hit));
                h.assertTrue(player.getItemInHand(hand).getCount()==2,"Wrong consumption or target "+shape+" / "+front);
                h.assertTrue(fill?entity.fillMaterial().is(Blocks.BRICKS):entity.material().is(Blocks.OAK_PLANKS),"Wrong material slot");
                if(fill){player.setShiftKeyDown(true);player.setItemInHand(hand,net.minecraft.world.item.ItemStack.EMPTY);
                    TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,p,hit));
                    h.assertTrue(entity.fillMaterial()==null && entity.material()==null,"Shift removal must clear both slots");}
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void secondaryOnlyAndHeldItemRemovalWorkForEveryComposite(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(8,3,8));
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        for(var shape:TemplateShape.values()) {
            var block=TemplateContent.TEMPLATE_BLOCKS.get(shape).get();
            if(!(block instanceof CompositeTemplateBlock)) continue;
            var player=PortGameTests.makeMockPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(p,block.defaultBlockState(),3);
            var entity=(TemplateBlockEntity)level.getBlockEntity(p);
            // Always use the same hit: slot selection must not depend on geometry.
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p),Direction.DOWN,p,false);
            player.setShiftKeyDown(true);
            player.setItemInHand(hand,new net.minecraft.world.item.ItemStack(Blocks.BRICKS,3));
            TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,p,hit));
            h.assertTrue(entity.material()==null && entity.fillMaterial().is(Blocks.BRICKS),"Secondary-only application failed: "+shape);
            h.assertTrue(player.getItemInHand(hand).getCount()==2,"Secondary consumed wrong count: "+shape);
            player.setShiftKeyDown(false);
            TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,p,hit));
            h.assertTrue(entity.material().is(Blocks.BRICKS) && entity.fillMaterial().is(Blocks.BRICKS),"Primary could not be added after secondary: "+shape);
            player.setShiftKeyDown(true);
            TemplateInteractionEvents.onRightClick(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,p,hit));
            h.assertTrue(entity.material()==null && entity.fillMaterial()==null,"Held-item removal did not clear both: "+shape);
            h.assertTrue(player.getInventory().countItem(Blocks.BRICKS.asItem())==3,"Identical materials not refunded once per slot: "+shape);
            player.setItemInHand(hand,net.minecraft.world.item.ItemStack.EMPTY);
            var empty=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,hand,p,hit);
            TemplateInteractionEvents.onRightClick(empty);
            h.assertTrue(!empty.isCanceled(),"Empty template consumed empty-hand interaction: "+shape);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_roof_templates", template="roof_templates")
    public static void buildFacadeSampleAndExportActualBlockData(GameTestHelper h) throws Exception {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(3,2,4));
        for(int x=0;x<7;x++)for(int y=0;y<6;y++) {
            var p=origin.offset(x,y,0);
            if(y==0 || y==5){level.setBlock(p,(y==0?Blocks.STONE_BRICKS:Blocks.WHITE_CONCRETE).defaultBlockState(),3);continue;}
            TemplateShape shape;
            if(y==4)shape=x==0||x==4||x==6?TemplateShape.WALL_JUNCTION:TemplateShape.WALL_BEAM;
            else if(x>=1 && x<=3)shape=y==2?TemplateShape.WINDOW_TRANSOM:TemplateShape.WINDOW_FRAME;
            else shape=x==5?TemplateShape.WALL_INFILL:TemplateShape.WALL_POST;
            level.setBlock(p,TemplateContent.TEMPLATE_BLOCKS.get(shape).get().defaultBlockState().setValue(MaterialTemplateBlock.FACING,Direction.NORTH),3);
            var entity=(TemplateBlockEntity)level.getBlockEntity(p);
            entity.setMaterial((shape==TemplateShape.WALL_INFILL?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE).defaultBlockState());
            if(shape.isCompositeWall())entity.setFillMaterial(Blocks.WHITE_CONCRETE.defaultBlockState());
            if(shape.isWindow())entity.setFillMaterial(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
        }
        for(int x=0;x<7;x++) {
            var p=origin.offset(x,5,-1);
            level.setBlock(p,TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_EAVE).get().defaultBlockState().setValue(MaterialTemplateBlock.FACING,Direction.SOUTH),3);
            ((TemplateBlockEntity)level.getBlockEntity(p)).setMaterial(Blocks.GRAY_CONCRETE.defaultBlockState());
        }
        h.assertTrue(ConnectedFacadeTemplateBlock.connections(level.getBlockState(origin.offset(0,4,0)))==6,"Top-left elbow is not connected down/right");
        h.assertTrue(ConnectedFacadeTemplateBlock.connections(level.getBlockState(origin.offset(4,4,0)))==14,"T joint has wrong branches");
        h.assertTrue(ConnectedFacadeTemplateBlock.connections(level.getBlockState(origin.offset(6,4,0)))==12,"Top-right elbow is not connected down/left");
        var dir=java.nio.file.Path.of("build/reports/facade-templates");java.nio.file.Files.createDirectories(dir);
        var template=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
        template.fillFromWorld(level,origin.offset(0,0,-1),new net.minecraft.core.Vec3i(7,6,2),false,Blocks.STRUCTURE_VOID);
        net.minecraft.nbt.NbtIo.writeCompressed(template.save(new net.minecraft.nbt.CompoundTag()),dir.resolve("facade-sample.nbt"));
        var cells=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(var p:BlockPos.betweenClosed(origin.offset(0,0,-1),origin.offset(6,5,0))) {
            var state=level.getBlockState(p);if(state.isAir())continue;
            var row=new java.util.LinkedHashMap<String,Object>();row.put("position",java.util.List.of(p.getX()-origin.getX(),p.getY()-origin.getY(),p.getZ()-origin.getZ()));
            row.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(state).toString());
            if(level.getBlockEntity(p) instanceof TemplateBlockEntity entity){row.put("material",net.minecraft.nbt.NbtUtils.writeBlockState(entity.material()).toString());
                if(entity.effectiveFillMaterial()!=null)row.put("fill",net.minecraft.nbt.NbtUtils.writeBlockState(entity.effectiveFillMaterial()).toString());}
            var parts=new java.util.ArrayList<java.util.Map<String,Object>>();
            if(state.getBlock() instanceof MaterialTemplateBlock block) {
                var shape=block.templateShape();int joins=ConnectedFacadeTemplateBlock.connections(state);
                for(var box:FacadeTemplateGeometry.frame(shape,joins))parts.add(java.util.Map.of("box",java.util.List.of(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()),"slot","primary"));
                if(shape.isComposite())for(var box:FacadeTemplateGeometry.fill(shape,joins))parts.add(java.util.Map.of("box",java.util.List.of(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()),"slot","fill"));
                row.put("facing",state.getValue(MaterialTemplateBlock.FACING).getName());
            } else parts.add(java.util.Map.of("box",java.util.List.of(0,0,0,16,16,16),"slot","primary"));
            row.put("parts",parts);cells.add(row);
        }
        java.nio.file.Files.writeString(dir.resolve("facade-sample.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(cells));
        h.succeed();
    }
}
