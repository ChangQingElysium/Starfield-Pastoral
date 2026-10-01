package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.block.mine.MineBarrelBlock;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.block.mine.MineRockClumpBlock;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.bomb.StardewBombEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.bomb.BombType;
import com.stardew.craft.mining.MineRockClumpMining;
import com.stardew.craft.network.payload.HudHintPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_mine_break_feedback")
@PrefixGameTestTemplate(false)
public final class MineBreakFeedbackGameTests {
    private static Map<String,Integer> collect(ServerLevel level, BlockPos pos) {
        Map<String,Integer> result = new TreeMap<>();
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
            var stack = entity.getItem();
            result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            entity.discard();
        }
        return result;
    }

    @GameTest(templateNamespace="stardewcraft_bombs",template="ring_utilities",timeoutTicks=200)
    public static void actualBombsKeepCacheContentsForCreativeAndSurvivalOwners(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimensionsField=net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
        dimensionsField.setAccessible(true);
        @SuppressWarnings("unchecked") var levels=(Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,ServerLevel>)dimensionsField.get(level.getServer());
        var mineKey=com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
        var previous=levels.put(mineKey,level);
        // GameTest has no mine dimension. Temporarily supply its saved-data owner, as in MineContainerGameTests.
        try {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Cache blast"));
        player.setPos(h.absoluteVec(new Vec3(1,12,1)));
        var explode = StardewBombEntity.class.getDeclaredMethod("explode"); explode.setAccessible(true);
        for (Block type : new Block[]{ModBlocks.MINE_CRATE.get(), ModBlocks.MINE_BARREL.get()}) {
            var cache = (MineBarrelBlock)type;
            for (var theme : MineBuildingTheme.values()) {
                BlockPos main = null; Map<String,Integer> expected = Map.of();
                // Find a nonempty source roll, so the regression cannot pass with two empty results.
                for (int i=0;i<64 && expected.isEmpty();i++) {
                    main = h.absolutePos(new BlockPos(4+i%4,4,4+i/4));
                    MineBarrelBlock.dropBarrelLoot(level, main, theme, player);
                    expected = collect(level, main);
                }
                h.assertTrue(!expected.isEmpty(), "Could not find a nonempty cache roll");
                for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                    player.setGameMode(mode);
                    for (boolean hitExtension : new boolean[]{false,true}) {
                        var state = cache.defaultBlockState().setValue(MineBuildingTheme.PROPERTY,theme);
                        level.setBlock(main.below(), Blocks.BEDROCK.defaultBlockState(), 2);
                        level.setBlock(main,state,2); cache.placeExtensions(level,main,state);
                        var bomb = new StardewBombEntity(ModEntities.STARDEW_BOMB.get(),level);
                        bomb.setOwner(player); bomb.setBombType(BombType.CHERRY_BOMB);
                        bomb.setPos(main.getX()+.5,main.getY()+(hitExtension?1:0),main.getZ()+.5);
                        explode.invoke(bomb);
                        h.assertTrue(level.isEmptyBlock(main) && level.isEmptyBlock(main.above()),"Bomb did not remove both cache cells");
                        h.assertTrue(expected.equals(collect(level,main)),"Bomb changed/omitted cache contents: "+mode+" "+theme+" extension="+hitExtension);
                        h.assertTrue(!MineBarrelBlock.breakByExplosion(level,main.above(),player) && collect(level,main).isEmpty(),"Duplicate blast settled an extension twice");
                    }
                }
                var state = cache.defaultBlockState().setValue(MineBuildingTheme.PROPERTY,theme);
                level.setBlock(main,state,2); cache.placeExtensions(level,main,state);
                MineBarrelBlock.breakBy(level,main,player);
                h.assertTrue(collect(level,main).isEmpty(),"Creative direct dismantling started dropping cache loot");
            }
        }
        } finally { if(previous==null)levels.remove(mineKey);else levels.put(mineKey,previous); }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_bombs",template="ring_utilities",timeoutTicks=200)
    public static void zeroProgressToolRejectionProducesHintOnFirstHit(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,4,5));
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Clump hint"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(pos).add(0,0,2));
        var hintField=HudHintPayload.class.getDeclaredField("LAST_SENT");hintField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Long> sent=(Map<String,Long>)hintField.get(null);
        Item[] picks={Items.NETHERITE_PICKAXE,ModItems.PICKAXE.get(),ModItems.COPPER_PICKAXE.get(),
                ModItems.STEEL_PICKAXE.get(),ModItems.GOLD_PICKAXE.get(),ModItems.IRIDIUM_PICKAXE.get()};
        for (int source : new int[]{752,754,756,758,672,622,148}) {
            var state=MineRockClumpMining.stateForSource("C"+source).orElseThrow();
            var block=(MineRockClumpBlock)state.getBlock();
            for (var part:MapDecorStaticBlock.Part.values()) for(int tier=0;tier<picks.length;tier++) {
                var current=state.setValue(MapDecorStaticBlock.PART,part);level.setBlock(pos,current,2);
                var tool=new ItemStack(picks[tier]);player.setItemInHand(InteractionHand.MAIN_HAND,tool);
                boolean allowed=Math.max(0,tier-1)>=MineRockClumpMining.minimumTier(source);
                h.assertTrue(MineRockClumpMining.canMine(source,tool)==allowed,"Source tier gate differs");
                var event=new PlayerInteractEvent.LeftClickBlock(player,pos,Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);
                MinecraftForge.EVENT_BUS.post(event);
                h.assertTrue(event.isCanceled()!=allowed,"Initial hit did not enforce gate on "+source+" "+part);
                if(!allowed) {
                    String hint=MineRockClumpMining.failureHint(source,tool);
                    h.assertTrue(sent.containsKey(player.getUUID()+"|"+hint),"Zero-progress rock failed to emit its HUD hint");
                    h.assertTrue(current.getDestroyProgress(player,level,pos)==0,"Rejected tool still advanced mining");
                }
                h.assertTrue(level.getBlockState(pos).is(block),"Invalid attempt destroyed the clump");
            }
        }
        for (Block type:new Block[]{ModBlocks.LARGE_STUMP.get(),ModBlocks.HOLLOW_LOG.get(),ModBlocks.LARGE_BOULDER.get()}) {
            var clump=(ResourceClumpBlock)type;level.setBlock(pos,type.defaultBlockState(),2);
            var tool=new ItemStack(clump.getRequiredTool()==ResourceClumpBlock.RequiredTool.AXE ? ModItems.AXE.get():ModItems.PICKAXE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND,tool);
            var event=new PlayerInteractEvent.LeftClickBlock(player,pos,Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);
            MinecraftForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled() && sent.containsKey(player.getUUID()+"|"+clump.failureHint(tool)),"Stump/log/boulder omitted its initial rejection hint");
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_bombs",template="ring_utilities")
    public static void sourceRightClickDescriptionsCoverBoulderMeteoriteAndLog(GameTestHelper h) throws Exception {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,4,4));
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Clump inspect"));
        var field=HudHintPayload.class.getDeclaredField("LAST_SENT");field.setAccessible(true);
        Map<?,?> sent=(Map<?,?>)field.get(null);
        for(Block block:new Block[]{ModBlocks.MINE_ROCK_CLUMP_672.get(),ModBlocks.MINE_ROCK_CLUMP_622.get(),ModBlocks.HOLLOW_LOG.get(),ModBlocks.LARGE_BOULDER.get()}) {
            level.setBlock(pos,block.defaultBlockState(),2);
            var event=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            MinecraftForge.EVENT_BUS.post(event);
            String hint=block instanceof MineRockClumpBlock rock ? MineRockClumpMining.inspectionHint(rock.sourceId()):((ResourceClumpBlock)block).inspectionHint();
            h.assertTrue(event.isCanceled() && sent.containsKey(player.getUUID()+"|"+hint),"Missing original inspection description");
        }
        h.succeed();
    }
}
