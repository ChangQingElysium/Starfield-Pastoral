package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.mine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("stardewcraft_mine_assets")
@PrefixGameTestTemplate(false)
public final class MineAssetThemeGameTests {
    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void blockedEntryNeverDropsFromAnyCell(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));
        var block=ModBlocks.MINE_BLOCKED_ENTRY.get();var area=new AABB(pos).inflate(4);
        for(var theme:MineBuildingTheme.values()) for(var facing:Direction.Plane.HORIZONTAL) {
            for(int removal=1;removal<=6;removal++) {
                int cellIndex=removal%6;
                var state=block.defaultBlockState().setValue(MapDecorStaticBlock.FACING,facing)
                        .setValue(MineBuildingTheme.PROPERTY,theme);
                level.setBlock(pos,state,3);h.assertTrue(block.placeExtensions(level,pos,state),"Entry placement failed");
                var cells=new java.util.ArrayList<BlockPos>();cells.add(pos);
                for(int x=-2;x<=2;x++) for(int y=0;y<3;y++) for(int z=-2;z<=2;z++) {
                    var cell=pos.offset(x,y,z);
                    if(!cell.equals(pos)&&level.getBlockState(cell).is(block))cells.add(cell);
                }
                h.assertTrue(cells.size()==6,"Entry footprint changed");
                // The normal loot path (MAIN), and the inherited extension removal fallback.
                var target=cells.get(cellIndex);
                if(cellIndex==0) level.destroyBlock(target,true);
                else level.setBlock(target,Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,area).stream()
                        .noneMatch(e->e.getItem().is(block.asItem())),"Unbreakable entry dropped from cell "+cellIndex);
                for(var cell:cells) h.assertTrue(!level.getBlockState(cell).is(block),"Removed entry left an orphan");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void blockedEntrySurvivesNeighborUpdates(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));var block=ModBlocks.MINE_BLOCKED_ENTRY.get();
        var state=block.defaultBlockState();level.setBlock(pos,state,3);
        h.assertTrue(block.placeExtensions(level,pos,state),"Entry placement failed");
        for(int n=0;n<10;n++) {
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
            level.removeBlock(pos.below(),false);
            level.updateNeighborsAt(pos,Blocks.AIR);
        }
        h.runAtTickTime(4,()->{
            int count=0;for(int x=0;x<2;x++)for(int y=0;y<3;y++) {
                var cell=pos.offset(x,y,0);var current=level.getBlockState(cell);
                if(current.is(block)) count++;
                h.assertTrue(current.is(block)&&current.getDestroySpeed(level,cell)<0,"Neighbor update removed or weakened a cell");
            }
            h.assertTrue(count==6,"Incomplete entry");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)).stream()
                    .noneMatch(e->e.getItem().is(block.asItem())),"Neighbor update dropped entry");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void allArchitecturePartsKeepThePickedTheme(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(8,3,8));
        for (Block registered : List.of(ModBlocks.MINE_BLOCKED_ENTRY.get(), ModBlocks.ELEVATOR.get())) {
            var block=(MapDecorStaticBlock)registered;
            for (var theme:MineBuildingTheme.values()) for (var facing:Direction.Plane.HORIZONTAL) {
                for (int x=-2;x<=2;x++) for(int y=0;y<4;y++) for(int z=-2;z<=2;z++) level.removeBlock(pos.offset(x,y,z),false);
                var state=block.defaultBlockState().setValue(MapDecorStaticBlock.FACING,facing).setValue(MineBuildingTheme.PROPERTY,theme);
                level.setBlock(pos,state,3);
                h.assertTrue(block.placeExtensions(level,pos,state),"Could not place themed extensions");
                int count=0;
                for (int x=-2;x<=2;x++) for(int y=0;y<4;y++) for(int z=-2;z<=2;z++) {
                    var cell=pos.offset(x,y,z);var s=level.getBlockState(cell);
                    if(!s.is(block))continue;
                    count++;
                    h.assertTrue(s.getValue(MineBuildingTheme.PROPERTY)==theme,"An extension reverted to earth");
                    h.assertTrue(pos.equals(block.findMainPos(level,cell,s)),"Theme changed the anchor lookup");
                    var item=block.getCloneItemStack(level,cell,s);
                    h.assertTrue(PortItemData.getOrDefault(item, DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(MineBuildingTheme.PROPERTY)==theme,"Pick lost theme");
                    if(block instanceof ElevatorBlock && s.getValue(ElevatorBlock.SECTION)==3) {
                        h.assertTrue(s.getCollisionShape(level,cell).isEmpty(),"Elevator call switch became collidable");
                    }
                }
                h.assertTrue(count==(block instanceof ElevatorBlock?4:6),"Wrong architecture footprint: "+count);
                MapDecorStaticBlock.runWithDropsSuppressed(()->level.removeBlock(pos,false));
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void pavingConnectionsUseTheDonorsActualMaterial(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));
        int[][] offsets={{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.removeBlock(pos.offset(x,1,z),false);
        var soil=ModBlocks.MINE_FROST_SOIL.get().defaultBlockState();level.setBlock(pos,soil,3);
        for(int mask=0;mask<256;mask++) {
            for(int i=0;i<8;i++)level.setBlock(pos.offset(offsets[i][0],0,offsets[i][1]),(mask&(1<<i))!=0
                    ? ModBlocks.MINE_PLANKS.get().defaultBlockState().setValue(MineBuildingTheme.PROPERTY,MineBuildingTheme.values()[i])
                    : Blocks.AIR.defaultBlockState(),3);
            int[] masks=MinePlankConnections.themeMasks(level,pos,soil);int canonical=MinePlankConnections.canonical(mask);
            for(int i=0;i<8;i++)h.assertTrue(masks[i]==(canonical&(1<<i)),"Overlay borrowed another theme or doubled a corner");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void barrelAndCrateBreakAsOneCacheFromEitherCell(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(8,3,8));var area=new AABB(pos).inflate(3);
        for(Block registered:List.of(ModBlocks.MINE_BARREL.get(),ModBlocks.MINE_CRATE.get())) {
            var block=(MineBarrelBlock)registered;List<String> baseline=null;
            for(int part=0;part<2;part++) {
                var state=block.defaultBlockState().setValue(MineBuildingTheme.PROPERTY,MineBuildingTheme.FROST_DARK);
                level.setBlock(pos,state,3);h.assertTrue(block.placeExtensions(level,pos,state),"Container has no upper cell");
                var upper=level.getBlockState(pos.above());
                h.assertTrue(upper.getShape(level,pos.above()).bounds().move(0,1,0).equals(state.getShape(level,pos).bounds()),"Container outline changes across parts");
                level.getRandom().setSeed(112358);
                level.destroyBlock(part==0?pos:pos.above(),false);
                h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir(),"Container left an orphan");
                var drops=level.getEntitiesOfClass(ItemEntity.class,area);
                h.assertTrue(drops.stream().noneMatch(e->e.getItem().is(block.asItem())),"Container incorrectly drops itself");
                var contents=drops.stream().map(e->e.getItem().toString()).sorted().toList();
                if(baseline==null)baseline=contents;else h.assertTrue(baseline.equals(contents),"Upper part produced a second loot roll");
                int count=drops.size();level.destroyBlock(pos,false);level.destroyBlock(pos.above(),false);
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,area).size()==count,"Already broken cache dropped again");
                drops.forEach(ItemEntity::discard);
            }
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void nativeSupportKeepsMaterialAndColumnAfterImport(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(4,3,4));
        var block=ModBlocks.MINE_TIMBER_SUPPORT.get();int index=0;
        for(var theme:List.of(MineBuildingTheme.EARTH_DARK,MineBuildingTheme.LAVA,MineBuildingTheme.LAVA_DARK)) {
            var pos=base.offset(index++*3,0,0);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
            // The upper-first ordering previously deleted extensions during structure placement.
            for(int tier=3;tier>=0;tier--)level.setBlock(pos.above(tier),block.defaultBlockState()
                    .setValue(MineBuildingTheme.PROPERTY,theme).setValue(MineTimberSupportBlock.TIER,tier),3);
        }
        h.runAtTickTime(3,()->{
            for(int i=0;i<3;i++) {
                var pos=base.offset(i*3,0,0);var root=level.getBlockState(pos);
                for(int tier=0;tier<4;tier++) {
                    var p=pos.above(tier);var state=level.getBlockState(p);
                    h.assertTrue(state.is(block),"Support piece vanished after import");
                    h.assertTrue(state.getValue(MineBuildingTheme.PROPERTY)==root.getValue(MineBuildingTheme.PROPERTY),"Support material changed across parts");
                    h.assertTrue(state.getShape(level,p).bounds().move(0,tier,0).equals(root.getShape(level,pos).bounds()),"Support outline differs across parts");
                    var pick=block.getCloneItemStack(level,p,state);
                    h.assertTrue(PortItemData.get(pick, DataComponents.BLOCK_STATE).get(MineBuildingTheme.PROPERTY)==root.getValue(MineBuildingTheme.PROPERTY),"Support pick lost material");
                }
            }
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "stardewcraft_mine_assets", template = "ring_utilities", timeoutTicks = 100)
    public static void ironWindowAttachesInFourDirectionsAndCleansUp(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(4,3,4));var block=ModBlocks.MINE_IRON_WINDOW.get();int index=0;
        for(var facing:Direction.Plane.HORIZONTAL) {
            var pos=base.offset((index%2)*5,0,(index/2)*5);index++;
            for(int tier=0;tier<2;tier++)level.setBlock(pos.relative(facing.getOpposite()).above(tier),Blocks.STONE.defaultBlockState(),3);
            var main=block.defaultBlockState().setValue(MapDecorStaticBlock.FACING,facing);
            level.setBlock(pos.above(),main.setValue(MapDecorStaticBlock.PART,MapDecorStaticBlock.Part.EXTENSION),3);
            level.setBlock(pos,main,3);
        }
        h.runAtTickTime(3,()->{
            int i=0;
            for(var facing:Direction.Plane.HORIZONTAL) {
                var pos=base.offset((i%2)*5,0,(i/2)*5);i++;
                var main=level.getBlockState(pos);var upper=level.getBlockState(pos.above());
                h.assertTrue(main.is(block)&&upper.is(block),"Window lost its upper cell");
                h.assertTrue(pos.equals(block.findMainPos(level,pos.above(),upper)),"Window has the wrong anchor");
                h.assertTrue(main.getShape(level,pos).bounds().equals(upper.getShape(level,pos.above()).bounds().move(0,1,0)),"Window outline changes at upper cell");
                MapDecorStaticBlock.runWithDropsSuppressed(()->level.removeBlock(pos.above(),false));
                h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir(),"Window removal left an orphan");
            }
            h.succeed();
        });
    }
}
