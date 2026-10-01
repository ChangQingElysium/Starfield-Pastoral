package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.FishMarketCrateBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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

@GameTestHolder("stardewcraft_fish_market")
@PrefixGameTestTemplate(false)
public final class FishMarketCrateGameTests {
    private static BlockPos prepare(GameTestHelper h) {
        for(int x=2;x<13;x++)for(int z=2;z<13;z++)for(int y=0;y<3;y++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        return h.absolutePos(new BlockPos(7,1,7));
    }
    private static BlockPlaceContext context(Player player, BlockPos main) {
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.FISH_MARKET_CRATE.get(),4));
        return new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(main),Direction.UP,main.below(),false)));
    }
    private static Player place(GameTestHelper h, BlockPos main, Direction facing, GameType mode) {
        var player=h.makeMockPlayer(mode);mode.updatePlayerAbilities(player.getAbilities());
        player.setPos(Vec3.atBottomCenterOf(main.offset(4,0,4)));
        player.setYRot(facing.getOpposite().toYRot());var context=context(player,main);
        h.assertTrue(((BlockItem)context.getItemInHand().getItem()).place(context).consumesAction(),"Placement failed "+facing);
        h.assertTrue(context.getItemInHand().getCount()==(mode==GameType.CREATIVE?4:3),"Wrong furniture consumption");
        return player;
    }
    private static BlockHitResult hit(BlockPos main, Direction facing, int slot) {
        double x=(slot+.5)*2/3-.5,z=0;
        int turns=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        for(int i=0;i<turns;i++){double previous=x;x=-z;z=previous;}
        var point=Vec3.atLowerCornerOf(main).add(x+.5,.6,z+.5);
        return new BlockHitResult(point,Direction.UP,BlockPos.containing(point),false);
    }
    private static ItemStack fish() {
        var fish=new ItemStack(ModItems.SALMON.get(),8);QualityHelper.setQuality(fish,3);
        fish.set(DataComponents.CUSTOM_NAME,Component.literal("Trophy salmon"));return fish;
    }
    @GameTest(templateNamespace="stardewcraft_fish_market",template="empty")
    public static void livePacketsClearRemovedFishAndReplaceEverySlot(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();
        place(h,main,Direction.NORTH,GameType.SURVIVAL);
        var server=(FishMarketCrateBlockEntity)level.getBlockEntity(main);
        var client=new FishMarketCrateBlockEntity(main,server.getBlockState());
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        for(int cycle=0;cycle<orders.length;cycle++) {
            for(int slot=0;slot<3;slot++) {
                var stack=new ItemStack((cycle+slot)%2==0?ModItems.SALMON.get():ModItems.TUNA.get());
                QualityHelper.setQuality(stack,(cycle+slot)%4);
                stack.set(DataComponents.CUSTOM_NAME,Component.literal("Catch "+cycle+" / "+slot));
                server.insert(slot,stack);
                assertPacketMatches(h,server,client,"insert "+cycle+" / "+slot);
            }
            for(int slot:orders[cycle]) {
                server.remove(slot);
                assertPacketMatches(h,server,client,"remove "+cycle+" / "+slot);
            }
            // Chunk entry uses a different callback; verify both full snapshot paths.
            var reloaded=new FishMarketCrateBlockEntity(main,server.getBlockState());
            reloaded.handleUpdateTag(server.getUpdateTag(level.registryAccess()),level.registryAccess());
            for(int slot=0;slot<3;slot++) h.assertTrue(reloaded.fish(slot).isEmpty(),"Chunk snapshot retained a fish");
        }
        h.succeed();
    }
    private static void assertPacketMatches(GameTestHelper h, FishMarketCrateBlockEntity server,
                                           FishMarketCrateBlockEntity client, String step) {
        // Match ClientPacketListener's live update entry point, including an empty tag.
        client.onDataPacket(null,server.getUpdatePacket(),h.getLevel().registryAccess());
        for(int slot=0;slot<3;slot++) h.assertTrue(ItemStack.matches(server.fish(slot),client.fish(slot)),
                "Client display differs from stored fish after "+step+", slot "+slot);
    }
    @GameTest(templateNamespace="stardewcraft_fish_market",template="empty")
    public static void emptySlotsInsertAndOccupiedSlotsReturnWithAnyHeldItem(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();
        for(var facing:Direction.Plane.HORIZONTAL){
            var player=place(h,main,facing,GameType.SURVIVAL);var crate=(FishMarketCrateBlockEntity)level.getBlockEntity(main);
            var held=fish();var expected=held.copyWithCount(1);player.setItemInHand(InteractionHand.MAIN_HAND,held);
            for(int slot=0;slot<3;slot++) {
                var hit=hit(main,facing,slot);
                level.getBlockState(hit.getBlockPos()).useItemOn(held,level,player,InteractionHand.MAIN_HAND,hit);
            }
            h.assertTrue(held.getCount()==5,"Wrong insertion count");
            for(int slot=0;slot<3;slot++)h.assertTrue(ItemStack.matches(expected,crate.fish(slot)),"Wrong fish in slot");
            for(int slot=0;slot<3;slot++) {
                var hand=slot==0?ItemStack.EMPTY:new ItemStack(slot==1?net.minecraft.world.item.Items.STONE:ModItems.TUNA.get(),5);
                player.setItemInHand(InteractionHand.MAIN_HAND,hand);
                var hit=hit(main,facing,slot);
                if(hand.isEmpty())level.getBlockState(hit.getBlockPos()).useWithoutItem(level,player,hit);
                else level.getBlockState(hit.getBlockPos()).useItemOn(hand,level,player,InteractionHand.MAIN_HAND,hit);
                h.assertTrue(crate.fish(slot).isEmpty(),"Occupied slot must take even while holding an item");
                if(slot==0)h.assertTrue(ItemStack.matches(expected,player.getMainHandItem()),"Empty hand did not receive fish");
                else h.assertTrue(player.getMainHandItem()==hand&&hand.getCount()==5,"Held item was overwritten or consumed");
            }
            h.assertTrue(player.getInventory().items.stream().filter(v->ItemStack.isSameItemSameComponents(v,expected))
                    .mapToInt(ItemStack::getCount).sum()==2,"Held-item pickups did not preserve both catches");
            // An empty region neither searches other regions nor triggers the held item's action.
            crate.insert(2,expected);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.STONE));
            var emptyHit=hit(main,facing,0);
            level.getBlockState(emptyHit.getBlockPos()).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,emptyHit);
            h.assertTrue(ItemStack.matches(expected,crate.fish(2)),"Clicking empty slot took a different fish");
            level.destroyBlock(main,true);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_fish_market",template="empty")
    public static void fullInventoryReturnsTheCatchAsOneDrop(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var player=place(h,main,Direction.NORTH,GameType.SURVIVAL);
        var crate=(FishMarketCrateBlockEntity)level.getBlockEntity(main);var expected=fish().copyWithCount(1);
        for(var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
        mode.updatePlayerAbilities(player.getAbilities());crate.insert(1,expected);
        for(int i=0;i<player.getInventory().items.size();i++)player.getInventory().items.set(i,new ItemStack(net.minecraft.world.item.Items.STONE,64));
        var hit=hit(main,Direction.NORTH,1);
        level.getBlockState(hit.getBlockPos()).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(crate.fish(1).isEmpty()&&player.getMainHandItem().is(net.minecraft.world.item.Items.STONE),"Full inventory did not take safely");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(8));
        h.assertTrue(drops.size()==1&&ItemStack.matches(expected,drops.getFirst().getItem()),"Full inventory lost or duplicated catch");
        // A second empty-handed player cannot obtain the removed catch.
        var other=h.makeMockPlayer(GameType.SURVIVAL);other.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        level.getBlockState(hit.getBlockPos()).useWithoutItem(level,other,hit);
        h.assertTrue(other.getMainHandItem().isEmpty(),"Second pickup duplicated catch");
        drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_fish_market",template="empty")
    public static void eitherHalfDropsOneFurnitureAndItsFishExactlyOnce(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();
        for(var facing:Direction.Plane.HORIZONTAL)for(boolean extension:new boolean[]{false,true})for(boolean creative:new boolean[]{false,true}){
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(4)))item.discard();
            var player=place(h,main,facing,creative?GameType.CREATIVE:GameType.SURVIVAL);
            var crate=(FishMarketCrateBlockEntity)level.getBlockEntity(main);for(int slot=0;slot<3;slot++)crate.insert(slot,fish());
            BlockPos other=main.relative(facing.getClockWise()),target=extension?other:main;
            if(creative){level.getBlockState(target).getBlock().playerWillDestroy(level,target,level.getBlockState(target),player);level.removeBlock(target,false);}
            else level.destroyBlock(target,true);
            h.assertTrue(level.isEmptyBlock(main)&&level.isEmptyBlock(other),"Orphaned half");
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(main).inflate(4));
            int furniture=drops.stream().filter(e->e.getItem().is(ModItems.FISH_MARKET_CRATE.get())).mapToInt(e->e.getItem().getCount()).sum();
            int fish=drops.stream().filter(e->ItemStack.isSameItemSameComponents(fish(),e.getItem())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(furniture==(creative?0:1)&&fish==3,"Incorrect or duplicate drops: furniture="+furniture+", fish="+fish);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_fish_market",template="empty")
    public static void blockedOrUnsupportedSecondCellDoesNotConsumeFurniture(GameTestHelper h) {
        var main=prepare(h);var level=h.getLevel();var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(main.offset(4,0,4)));
        for(var facing:Direction.Plane.HORIZONTAL){
            player.setYRot(facing.getOpposite().toYRot());var other=main.relative(facing.getClockWise());
            for(boolean obstacle:new boolean[]{true,false}){
                level.setBlock(obstacle?other:other.below(),obstacle?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                var context=context(player,main);
                h.assertTrue(!((BlockItem)context.getItemInHand().getItem()).place(context).consumesAction(),"Invalid footprint accepted");
                h.assertTrue(context.getItemInHand().getCount()==4&&level.isEmptyBlock(main),"Failed placement left furniture or consumed item");
                level.setBlock(other,Blocks.AIR.defaultBlockState(),3);level.setBlock(other.below(),Blocks.STONE.defaultBlockState(),3);
            }
        }
        h.succeed();
    }
}
