package com.stardew.craft.gametest;

import com.google.gson.JsonParser;
import com.stardew.craft.aquarium.AquariumMotion;
import com.stardew.craft.aquarium.AquariumRules;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.AquariumBlockEntity;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.cosmetic.StardewHatItem;
import com.stardew.craft.menu.AquariumMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@GameTestHolder("stardewcraft_aquarium")
@PrefixGameTestTemplate(false)
public final class AquariumGameTests {
    private static BlockPos prepare(GameTestHelper h) {
        for (int x=1;x<15;x++) for (int z=1;z<15;z++) for (int y=0;y<5;y++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)), y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        return h.absolutePos(new BlockPos(7,1,7));
    }
    private static Player player(GameTestHelper h, BlockPos pos, Direction facing, ItemStack stack) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(Vec3.atBottomCenterOf(pos.offset(5,0,5)));
        p.setYRot(facing.getOpposite().toYRot());p.setItemInHand(InteractionHand.MAIN_HAND,stack);return p;
    }
    private static BlockPlaceContext context(Player p,BlockPos pos) {
        return new BlockPlaceContext(new UseOnContext(p,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos.below(),false)));
    }
    private static AquariumBlockEntity place(GameTestHelper h,BlockPos pos,Direction facing,ItemStack stack) {
        var p=player(h,pos,facing,stack);h.assertTrue(((BlockItem)stack.getItem()).place(context(p,pos)).consumesAction(),"Aquarium placement failed "+facing);
        return (AquariumBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static ItemStack object(String id) { return FishPondQualifiedItemService.createItemStack("(O)"+id,1); }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void everySourceSpeciesAndDecorationResolves(GameTestHelper h) throws Exception {
        try(var stream=AquariumRules.class.getResourceAsStream("/data/stardewcraft/aquarium/fish.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            h.assertTrue(data.size()==72,"Wrong source species count");
            for(var entry:data.entrySet()) {
                var fish=object(entry.getKey());h.assertTrue(!fish.isEmpty(),"Missing species "+entry.getKey());
                h.assertTrue(AquariumRules.movement(fish).equals(entry.getValue().getAsString().split("/")[1]),"Wrong movement "+entry.getKey());
                if (!entry.getKey().equals("397")) {
                    String path="/assets/stardewcraft/pond_fish/"+BuiltInRegistries.ITEM.getKey(fish.getItem()).getPath()+".json";
                    try(var model=AquariumRules.class.getResourceAsStream(path)) { h.assertTrue(model!=null,"Species has no model: "+path); }
                }
            }
        }
        var tank=new AquariumBlockEntity(BlockPos.ZERO,ModBlocks.LARGE_FISH_TANK.get().defaultBlockState());
        for(String id:new String[]{"152","393","390","117","166","832","109","709","392","394","167","789","330","797"}) {
            h.assertTrue(!object(id).isEmpty(),"Missing decoration "+id);
            h.assertTrue(tank.insert(object(id))>=9,"Decoration rejected "+id);
            h.assertTrue(tank.insert(object(id))<0,"Duplicate decoration accepted "+id);
        }
        h.assertTrue(tank.insert(new ItemStack(ModItems.FROG_EGG.get()))==3,"Frog needs a ground slot");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void capacityHatAndLastItemPackets(GameTestHelper h) {
        var pos=prepare(h);var tank=place(h,pos,Direction.NORTH,new ItemStack(ModItems.LARGE_FISH_TANK.get()));
        for(int i=0;i<3;i++)h.assertTrue(tank.insert(object("145"))==i,"Wrong swim slot");
        h.assertTrue(tank.insert(object("145"))<0,"Fourth swimmer accepted");
        var hatItem=BuiltInRegistries.ITEM.stream().filter(i->i instanceof StardewHatItem).findFirst().orElseThrow();
        h.assertTrue(tank.insert(new ItemStack(hatItem))<0,"Hat accepted without sea urchin");
        for(int i=0;i<3;i++)h.assertTrue(tank.insert(object("397"))==3+i,"Sea urchin needs ground slot");
        for(int i=0;i<3;i++)h.assertTrue(tank.insert(new ItemStack(hatItem))==6+i,"Hat rejected despite wearer");
        h.assertTrue(tank.insert(object("715"))<0,"Fourth ground creature accepted");
        var mirror=new AquariumBlockEntity(pos,tank.getBlockState());
        mirror.onDataPacket(null,tank.getUpdatePacket(),h.getLevel().registryAccess());
        for(int i=0;i<9;i++) {
            var removed=tank.removeItem(i,1);h.assertTrue(!removed.isEmpty(),"Lost stored item");
            mirror.onDataPacket(null,tank.getUpdatePacket(),h.getLevel().registryAccess());
            for(int j=0;j<AquariumRules.SIZE;j++)h.assertTrue(ItemStack.matches(tank.getItem(j),mirror.getItem(j)),"Ghost fish/hat in live snapshot");
        }
        h.assertTrue(tank.isEmpty()&&mirror.isEmpty(),"Last removal did not clear tank");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void menuTransfersOnePerSlotAndSharesAuthoritativeContents(GameTestHelper h) {
        var pos=prepare(h);var tank=place(h,pos,Direction.NORTH,new ItemStack(ModItems.LARGE_FISH_TANK.get()));
        var p=player(h,pos,Direction.NORTH,ItemStack.EMPTY);
        var fish=object("145").copyWithCount(20);fish.set(DataComponents.CUSTOM_NAME,Component.literal("Named catch"));
        p.getInventory().setItem(9,fish);
        var a=new AquariumMenu(1,p.getInventory(),tank);var b=new AquariumMenu(2,p.getInventory(),tank);
        // The native quick-move loop repeats once per empty slot; two viewers share the same cap.
        a.quickMoveStack(p,AquariumRules.SIZE);b.quickMoveStack(p,AquariumRules.SIZE);
        a.quickMoveStack(p,AquariumRules.SIZE);b.quickMoveStack(p,AquariumRules.SIZE);
        h.assertTrue(p.getInventory().getItem(9).getCount()==17,"Menu overfilled or consumed extra fish: "+p.getInventory().getItem(9).getCount());
        for(int i=0;i<3;i++)h.assertTrue(tank.getItem(i).getCount()==1&&tank.getItem(i).getHoverName().getString().equals("Named catch"),"Components/count changed");
        for(int i=0;i<3;i++)a.quickMoveStack(p,i);
        h.assertTrue(tank.isEmpty(),"Quick withdrawal left contents");
        int total=0;for(var stack:p.getInventory().items)if(stack.is(fish.getItem()))total+=stack.getCount();
        h.assertTrue(total==20,"Menu duplicated or lost fish");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void packedContentsSurviveMainAndExtensionBreakAtEveryFacing(GameTestHelper h) {
        for(Direction direction:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST})for(boolean extension:new boolean[]{false,true}) {
            var pos=prepare(h);var tank=place(h,pos,direction,new ItemStack(ModItems.LARGE_FISH_TANK.get()));
            var fish=object("145");fish.set(DataComponents.CUSTOM_NAME,Component.literal("Keep this fish"));
            com.stardew.craft.item.quality.QualityHelper.setQuality(fish,3);
            tank.insert(fish);tank.insert(object("152"));long seed=tank.layoutSeed();
            BlockPos broken=extension?pos.above():pos;
            h.getLevel().destroyBlock(broken,true);
            var bounds=new AABB(pos).inflate(7);
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,bounds,e->e.getItem().is(ModItems.LARGE_FISH_TANK.get()));
            h.assertTrue(drops.size()==1&&drops.getFirst().getItem().getCount()==1,"Furniture duplicated/lost after break "+direction+"/"+extension);
            var packed=drops.getFirst().getItem().copy();drops.getFirst().discard();
            for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-4,0,-4),pos.offset(4,3,4)))
                h.assertTrue(!h.getLevel().getBlockState(cell).is(ModBlocks.LARGE_FISH_TANK.get()),"Orphan aquarium extension");
            var restored=place(h,pos,direction,packed);
            h.assertTrue(ItemStack.matches(restored.getItem(0),fish)&&restored.getItem(9).is(object("152").getItem()),"Packed contents changed");
            h.assertTrue(restored.layoutSeed()==seed,"Moving shuffled the decoration seed");
            restored.clearContent();restored.discardEmptyOnRemoval=true;h.getLevel().removeBlock(pos,false);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void placementChecksHeightSupportAndAllRotations(GameTestHelper h) {
        for(Direction direction:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var pos=prepare(h);var block=ModBlocks.LARGE_FISH_TANK.get();
            var p=player(h,pos,direction,new ItemStack(ModItems.LARGE_FISH_TANK.get()));
            h.getLevel().setBlock(pos.above(2),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(block.getStateForPlacement(context(p,pos))==null,"Ignored overhead obstacle");
            h.getLevel().removeBlock(pos.above(2),false);
            var tank=place(h,pos,direction,new ItemStack(ModItems.LARGE_FISH_TANK.get()));
            int occupied=0;for(BlockPos cell:BlockPos.betweenClosed(pos.offset(-4,0,-4),pos.offset(4,3,4)))
                if(h.getLevel().getBlockState(cell).is(block))occupied++;
            h.assertTrue(occupied==24,"Wrong occupied volume "+direction+": "+occupied);
            tank.discardEmptyOnRemoval=true;h.getLevel().removeBlock(pos,false);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void playerBreakPreservesFilledTanksAndCreativeEmptyTanksDoNotDrop(GameTestHelper h) {
        for(GameType mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE})for(boolean filled:new boolean[]{false,true})for(boolean extension:new boolean[]{false,true}) {
            var pos=prepare(h);var tank=place(h,pos,Direction.NORTH,new ItemStack(ModItems.LARGE_FISH_TANK.get()));
            if(filled)tank.insert(object("145"));
            var p=h.makeMockPlayer(mode);mode.updatePlayerAbilities(p.getAbilities());
            var broken=extension?pos.above():pos;var state=h.getLevel().getBlockState(broken);
            state.getBlock().playerWillDestroy(h.getLevel(),broken,state,p);
            h.getLevel().destroyBlock(broken,mode!=GameType.CREATIVE);
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(6),e->e.getItem().is(ModItems.LARGE_FISH_TANK.get()));
            int expected=mode==GameType.CREATIVE&&!filled?0:1;
            h.assertTrue(drops.size()==expected,"Wrong packed drop count: "+mode+" / "+filled+" / "+extension);
            drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_aquarium",template="empty")
    public static void fullRotatedFishEnvelopeStaysInsideWater(GameTestHelper h) {
        for(String kind:new String[]{"fish","eel","cephalopod","float","ground","crawl","front_crawl","static","frog"})
            for(int slot=0;slot<6;slot++)for(int tick=0;tick<12000;tick+=7) {
                double w=12,ht=7,d=5;var p=AquariumMotion.sample(kind,slot,17389,tick/20.0,w,ht,d);
                double a=Math.toRadians(p.yaw()),r=Math.toRadians(p.roll());
                double rw=Math.abs(Math.cos(r))*w+Math.abs(Math.sin(r))*ht;
                double hx=(Math.abs(Math.cos(a))*rw+Math.abs(Math.sin(a))*d)/2;
                double hz=(Math.abs(Math.sin(a))*rw+Math.abs(Math.cos(a))*d)/2;
                double hy=(Math.abs(Math.sin(r))*w+Math.abs(Math.cos(r))*ht)/2;
                h.assertTrue(p.x()-hx>=-29&&p.x()+hx<=29&&p.z()-hz>=-13&&p.z()+hz<=13&&p.y()-hy>=7&&p.y()+hy<=31,"Fish leaves water "+kind);
            }
        h.succeed();
    }
}
