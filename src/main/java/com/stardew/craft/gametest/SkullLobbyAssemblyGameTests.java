package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.SkullLobbyAssemblyBlock;
import com.stardew.craft.blockentity.SkullLobbyLightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder("stardewcraft_skull_lobby")
@PrefixGameTestTemplate(false)
public final class SkullLobbyAssemblyGameTests {
    private static final int[][] WALL = {{0,0,0},{0,1,0},{0,2,0},{1,0,0},{1,1,0},{1,2,0}};
    private static final int[][] ALTAR = {{0,0,0},{0,1,-1},{0,1,0},{1,0,-1},{1,0,0},{2,0,-1},{2,0,0},{3,0,0},{3,1,-1},{3,1,0}};
    private static BlockPos part(BlockPos root, int[] offset, Direction facing) {
        return root.offset(SkullLobbyAssemblyBlock.rotateOffset(new BlockPos(offset[0],offset[1],offset[2]),facing));
    }
    private static ServerPlayer player(GameTestHelper h) {
        var p = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Shrine test"));
        p.setGameMode(GameType.CREATIVE);
        return p;
    }
    @GameTest(templateNamespace="stardewcraft_skull_lobby", template="ring_utilities", timeoutTicks=40)
    public static void fourDirectionsAutoPlaceSelectAndRemoveEveryPart(GameTestHelper h) {
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(8,3,8));var player=player(h);
        SkullLobbyAssemblyBlock[] blocks={ModBlocks.SKULL_SHRINE_WALL.get(),ModBlocks.SKULL_SHRINE_ALTAR.get(),ModBlocks.SKULL_CAVERN_DOOR.get(),ModBlocks.SKULL_WALL_BRAZIER.get(),ModBlocks.SKULL_STALAGMITE.get()};
        int[][][] offsets={WALL,ALTAR,{{0,0,0},{0,1,0},{0,2,0}},{{0,0,0},{0,1,0}},{{0,0,0},{0,1,0}}};
        for(int b=0;b<blocks.length;b++)for(Direction facing:Direction.Plane.HORIZONTAL){
            var block=blocks[b];var stack=new ItemStack(block);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var ctx=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(root),facing,root,false));
            h.assertTrue(((BlockItem)stack.getItem()).place(ctx).consumesAction(),"Assembly placement failed");
            var shape=level.getBlockState(root).getShape(level,root).bounds();
            for(int i=0;i<offsets[b].length;i++){
                BlockPos q=part(root,offsets[b][i],facing);var state=level.getBlockState(q);
                h.assertTrue(state.is(block)&&state.getValue(SkullLobbyAssemblyBlock.SECTION)==i,"Missing or misoriented part");
                var delta=q.subtract(root);
                h.assertTrue(state.getShape(level,q).bounds().move(delta.getX(),delta.getY(),delta.getZ()).equals(shape),"Part lost whole outline");
                h.assertTrue(state.getDestroySpeed(level,q)<0,"Lobby architecture can be mined");
                h.assertTrue(Block.getDrops(state,level,q,level.getBlockEntity(q)).isEmpty(),"Assembly duplicated drops");
            }
            level.removeBlock(part(root,offsets[b][offsets[b].length-1],facing),false);
            for(int[] o:offsets[b])h.assertTrue(level.isEmptyBlock(part(root,o,facing)),"Removing extension left orphan geometry");
            // No partial placement when one required cell is obstructed.
            BlockPos obstacle=part(root,offsets[b][1],facing);level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(block.getStateForPlacement(ctx)==null,"Placement ignores occupied extension cell");
            level.removeBlock(obstacle,false);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_skull_lobby", template="ring_utilities", timeoutTicks=40)
    public static void reverseImportAndCreativeSwitchKeepBothAssetsTogether(GameTestHelper h) {
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(10,3,8));var wallRoot=root.offset(-1,1,0);var facing=Direction.SOUTH;
        var altar=ModBlocks.SKULL_SHRINE_ALTAR.get();var wall=ModBlocks.SKULL_SHRINE_WALL.get();
        for(int kind=0;kind<2;kind++){
            var block=kind==0?altar:wall;var r=kind==0?root:wallRoot;var offsets=kind==0?ALTAR:WALL;
            for(int i=offsets.length-1;i>=0;i--)level.setBlock(part(r,offsets[i],facing),block.defaultBlockState().setValue(SkullLobbyAssemblyBlock.FACING,facing).setValue(SkullLobbyAssemblyBlock.SECTION,i),3);
        }
        h.runAtTickTime(3,()->{
            var p=player(h);var hit=new BlockHitResult(Vec3.atCenterOf(root),Direction.SOUTH,root,false);
            for(boolean lit:new boolean[]{true,false}){
                PortBlockInteraction.stateUseWithoutItem(level.getBlockState(root), level,p,hit);
                for(int kind=0;kind<2;kind++){
                    var block=kind==0?altar:wall;var r=kind==0?root:wallRoot;var offsets=kind==0?ALTAR:WALL;int emitters=0;
                    for(int[] offset:offsets){
                        var q=part(r,offset,facing);var s=level.getBlockState(q);
                        h.assertTrue(s.is(block)&&s.getValue(SkullLobbyAssemblyBlock.LIT)==lit,"Shrine lost imported part or synchronized state");
                        h.assertTrue(level.getBlockEntity(q) instanceof SkullLobbyLightBlockEntity,"Missing light discovery entity");
                        if(s.getLightEmission(level,q)>0)emitters++;
                    }
                    h.assertTrue(emitters==(lit?2:0),"Stone parts glow, or approved emitters stay dark");
                }
            }
            h.succeed();
        });
    }
}
