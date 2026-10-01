package com.stardew.craft.gametest;

import com.stardew.craft.port.PortGameTests;
import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlaygroundBlock;
import com.stardew.craft.block.terrain.PlaygroundSandConnections;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_sand_bird")
@PrefixGameTestTemplate(false)
public final class SandBirdGameTests {
    private static BlockPos prepare(GameTestHelper h) {
        for (int x=0;x<22;x++) for (int z=0;z<22;z++) for (int y=0;y<8;y++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)), y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        return h.absolutePos(new BlockPos(10,1,10));
    }
    private static BlockPlaceContext context(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        return new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false)));
    }
    @GameTest(templateNamespace="stardewcraft_sand_bird",template="ring_utilities",timeoutTicks=200)
    public static void sandConnectsAcrossVariantsAndFixedCopiesPersist(GameTestHelper h) {
        var pos=prepare(h);var level=h.getLevel();var block=ModBlocks.PLAYGROUND_SAND.get();var base=block.defaultBlockState();
        int[][] offsets={{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        var rows=new HashSet<Integer>();level.setBlock(pos,base,3);
        for(int mask=0;mask<256;mask++) {
            int expected=mask;
            for(int i=0;i<8;i++)level.setBlock(pos.offset(offsets[i][0],0,offsets[i][1]),
                    (mask&(1<<i))==0?Blocks.AIR.defaultBlockState():base.setValue(TerrainVariants.SAND,i%4),3);
            for(int i=0;i<4;i++)if((mask&(1<<i))==0||(mask&(1<<((i+1)%4)))==0)expected&=~(16<<i);
            h.assertTrue(PlaygroundSandConnections.mask(level,pos)==expected,"Incorrect sand rim topology: "+mask);
            rows.add(PlaygroundSandConnections.row(expected));
        }
        h.assertTrue(rows.size()==47,"Missing connected rim corners");
        level.setBlock(pos.east(),ModBlocks.ASPHALT_ROAD.get().defaultBlockState(),3);
        level.setBlock(pos.east().above(),base,3);
        h.assertTrue((PlaygroundSandConnections.mask(level,pos)&2)==0,"Sand joins road or another elevation");
        var player=PortGameTests.makeMockPlayer(h, GameType.CREATIVE);player.setPos(Vec3.atBottomCenterOf(pos.offset(7,0,7)));
        for(int v=0;v<4;v++) {
            var source=base.setValue(TerrainVariants.SAND,v);var plain=new ItemStack(block,16);
            var fixed=TerrainVariants.fixedCopy(plain,source);h.assertTrue(!PortItemData.has(plain, DataComponents.BLOCK_STATE),"Copy mutates normal inventory");
            player.setItemInHand(InteractionHand.MAIN_HAND,fixed);
            for(int repeat=0;repeat<5;repeat++) {
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                h.assertTrue(((BlockItem)fixed.getItem()).place(context(player,pos)).consumesAction(),"Sand placement failed");
                var placed=level.getBlockState(pos);h.assertTrue(placed.equals(source),"Fixed sand rerolled");
                var saved=BlockState.CODEC.encodeStart(NbtOps.INSTANCE,placed).getOrThrow();
                h.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE,saved).getOrThrow().equals(source),"Variant lost on save");
                h.assertTrue(TerrainWorldUpgrade.varied(source,33,pos).equals(source),"Terrain migration changes sand");
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block));var seen=new HashSet<Integer>();
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);level.getRandom().setSeed(418);
        for(int i=0;i<128;i++)seen.add(block.getStateForPlacement(context(player,pos)).getValue(TerrainVariants.SAND));
        h.assertTrue(seen.size()==4,"Normal placement omits a sand variant");
        h.assertTrue(base.isSolidRender(level,pos)&&base.isCollisionShapeFullBlock(level,pos),"Sand is not an opaque solid surface");
        h.assertTrue(base.is(BlockTags.MINEABLE_WITH_SHOVEL),"Sand is not shovel-mineable");
        h.assertTrue(StardewItemCatalog.tabForItem(block.asItem())==StardewCatalogTab.BUILDING,"Sand catalog classification");
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(5)))item.discard();
        level.setBlock(pos,base,3);level.destroyBlock(pos,true);
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
        h.assertTrue(drops.size()==1&&com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()),"Sand drops the wrong item");h.succeed();
    }
    private static int count(GameTestHelper h, BlockPos main, PlaygroundBlock block) {
        int n=0;for(var p:BlockPos.betweenClosed(main.offset(-3,0,-3),main.offset(3,3,3)))
            if(h.getLevel().getBlockState(p).is(block)&&main.equals(block.findMainPos(h.getLevel(),p,h.getLevel().getBlockState(p))))n++;
        return n;
    }
    @GameTest(templateNamespace="stardewcraft_sand_bird",template="ring_utilities",timeoutTicks=200)
    public static void birdRotatesReservesSpaceAndDropsOnce(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var block=ModBlocks.BIRD_SPRING_RIDER.get();var player=PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        h.assertTrue(StardewItemCatalog.tabForItem(block.asItem())==StardewCatalogTab.BUILDING,"Bird catalog classification");
        for(var facing:Direction.Plane.HORIZONTAL) {
            player.setPos(Vec3.atBottomCenterOf(main.offset(7,0,7)));player.setYRot(facing.getOpposite().toYRot());
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block,3));
            var head=main.above();level.setBlock(head,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(!((BlockItem)block.asItem()).place(context(player,main)).consumesAction(),"Bird placed through its head obstruction");
            h.assertTrue(count(h,main,block)==0&&player.getMainHandItem().getCount()==3,"Failed placement is not atomic");
            level.removeBlock(head,false);level.removeBlock(main.below(),false);
            h.assertTrue(block.getStateForPlacement(context(player,main))==null,"Floating anchor accepted");
            level.setBlock(main.below(),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(((BlockItem)block.asItem()).place(context(player,main)).consumesAction(),"Bird placement failed");
            int expected=count(h,main,block);h.assertTrue(expected>=4,"Missing bird extension cells");
            h.assertTrue(level.getBlockState(main).getValue(PlaygroundBlock.FACING)==facing,"Bird orientation incorrect");
            for(var p:BlockPos.betweenClosed(main.offset(-3,0,-3),main.offset(3,3,3))) {
                var state=level.getBlockState(p);if(!state.is(block))continue;
                h.assertTrue(main.equals(block.findMainPos(level,p,state)),"Wrong bird owner");
                h.assertTrue((level.getBlockEntity(p)!=null)==p.equals(main),"Extension duplicates bird renderer");
                h.assertTrue(!block.isLadder(state,level,p,player),"Bird unexpectedly behaves like climbing frame");
                var shape=state.getCollisionShape(level,p);if(!shape.isEmpty()) {
                    var b=shape.bounds();h.assertTrue(b.minX>=-1e-7&&b.maxX<=1.0000001&&b.minY>=-1e-7&&b.maxY<=1.0000001&&b.minZ>=-1e-7&&b.maxZ<=1.0000001,"Bird collision escapes cell");
                }
            }
            var neighbor=main.relative(facing.getCounterClockWise(),2);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block,3));
            h.assertTrue(((BlockItem)block.asItem()).place(context(player,neighbor)).consumesAction(),"Adjacent bird placement failed");
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(6)))item.discard();
            level.destroyBlock(main.above(),true);
            h.assertTrue(count(h,main,block)==0&&count(h,neighbor,block)==expected,"Removal leaked parts or damaged adjacent bird");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(4));
            h.assertTrue(drops.size()==1&&com.stardew.craft.port.PortJava.getFirst(drops).getItem().getCount()==1&&com.stardew.craft.port.PortJava.getFirst(drops).getItem().is(block.asItem()),"Bird must drop once");
            PlaygroundBlock.runWithDropsSuppressed(()->level.removeBlock(neighbor,false));
        }
        h.succeed();
    }
}
