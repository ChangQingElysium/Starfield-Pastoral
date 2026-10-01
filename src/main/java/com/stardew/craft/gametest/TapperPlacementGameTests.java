package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.TapperBlock;
import com.stardew.craft.blockentity.NewTreePartBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeManager;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashSet;
import java.util.Set;

@GameTestHolder("stardewcraft_tapper_placement")
@PrefixGameTestTemplate(false)
public final class TapperPlacementGameTests {
    private static void use(GameTestHelper h, ServerPlayer player, BlockPos clicked, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
        player.getMainHandItem().useOn(new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND,
                player.getMainHandItem(), hit));
    }

    @GameTest(templateNamespace="stardewcraft_tapper_placement", template="empty", timeoutTicks=400)
    public static void all25ShapesFromFourSidesAndOldTreeRecovery(GameTestHelper h) {
        var level=h.getLevel(); var registry=PrefabTreeRegistry.get(level);
        var player=FakePlayerFactory.getMinecraft(level); player.setGameMode(GameType.SURVIVAL);
        BlockPos root=h.absolutePos(new BlockPos(8,12,8));
        int checked=0;
        for (var def:WildTrees.ALL) for (int variant=1;variant<=5;variant++) {
            for (Direction side:Direction.Plane.HORIZONTAL) {
                Set<BlockPos> cleanup=new HashSet<>();
                try {
                    h.assertTrue(PrefabTreeManager.place(level,root,def,variant,false),"Missing tree");
                    var tree=registry.getByRoot(root);cleanup.addAll(tree.members());
                    for(BlockPos member:tree.members()) {
                        if(level.getBlockEntity(member) instanceof NewTreePartBlockEntity marker) marker.clearGeneratedTreeMarker();
                        for(Direction d:Direction.Plane.HORIZONTAL)cleanup.add(member.relative(d));
                    }
                    // Alternate registered old trees without BE markers and unregistered map trees.
                    if(side==Direction.NORTH || side==Direction.EAST) registry.unregister(tree);
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.TAPPER.get(),2));
                    // Hanging glass/low branches occupy some centre-line standing positions.
                    // Test real, unobstructed player positions on each side, not an eye inside a pane.
                    for(double lateral:new double[]{0,.9,-.9,1.6,-1.6}) {
                        Direction tangent=side.getClockWise();
                        player.setPos(root.getX()+.5+side.getStepX()*3+tangent.getStepX()*lateral,
                                root.getY(),root.getZ()+.5+side.getStepZ()*3+tangent.getStepZ()*lateral);
                        if(!level.noCollision(player,player.getBoundingBox()))continue;
                        use(h,player,root,side);
                        if(player.getMainHandItem().getCount()==1)break;
                    }
                    h.assertTrue(player.getMainHandItem().getCount()==1,"Failed attachment: "+def.id()+"/"+variant+" "+side);
                    tree=registry.getByRoot(root);h.assertTrue(tree!=null,"Old tree not restored");
                    var tappers=TapperBlock.attachedTappers(level,tree.members());
                    h.assertTrue(tappers.size()==1,"Wrong attachment count");
                    BlockPos tapper=tappers.iterator().next();
                    h.assertTrue(TapperBlock.findValidProductionDef(level,tapper,level.getBlockState(tapper))==def,"No production");
                    use(h,player,root,side);
                    h.assertTrue(player.getMainHandItem().getCount()==1 && TapperBlock.attachedTappers(level,tree.members()).size()==1,"Second tapper accepted");
                    checked++;
                } finally {
                    var tree=registry.getByRoot(root);if(tree!=null)registry.unregister(tree);
                    com.stardew.craft.manager.WildTreeSeedManager.get(level).untrackTree(level,root);
                    for(BlockPos pos:cleanup)level.removeBlock(pos,false);
                }
            }
        }
        h.assertTrue(checked==100,"Not all variants/directions covered");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_tapper_placement", template="empty")
    public static void blockedSitesDoNotReplaceBlocksAndAdjacentTreesDoNotShareLimit(GameTestHelper h) {
        var level=h.getLevel();var def=WildTrees.OAK;var registry=PrefabTreeRegistry.get(level);
        var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);
        BlockPos root=h.absolutePos(new BlockPos(3,3,3)),log=root.above();
        level.setBlock(root,def.modernRoot().get().defaultBlockState(),2);
        level.setBlock(log,def.modernLog().get().defaultBlockState(),2);
        h.assertTrue(PrefabTreeManager.findOrRestoreForTapper(level,log)==null,"Loose placed wood was adopted");
        registry.register(root,def.id(),1,Set.of(root,log));
        try {
            for (Direction d:Direction.Plane.HORIZONTAL) {
                level.setBlock(root.relative(d),Blocks.STONE.defaultBlockState(),2);
                level.setBlock(log.relative(d),Blocks.STONE.defaultBlockState(),2);
            }
            player.setPos(root.getX()+.5,root.getY(),root.getZ()+3.5);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.TAPPER.get(),2));use(h,player,root,Direction.SOUTH);
            h.assertTrue(player.getMainHandItem().getCount()==2,"Blocked placement consumed item");
            for(Direction d:Direction.Plane.HORIZONTAL) h.assertTrue(level.getBlockState(log.relative(d)).is(Blocks.STONE),"Obstruction removed");
            level.setBlock(log.east(),ModBlocks.TAPPER.get().defaultBlockState().setValue(TapperBlock.FACING,Direction.EAST),2|16);
            h.assertTrue(TapperBlock.attachedTappers(level,Set.of(log)).isEmpty(),"Tapper facing another tree counted");
        } finally {registry.unregister(registry.getByRoot(root));}
        h.succeed();
    }
}
