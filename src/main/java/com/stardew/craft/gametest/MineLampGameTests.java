package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineLampBlock;
import com.stardew.craft.blockentity.MineLampBlockEntity;
import com.stardew.craft.item.MineLampItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("stardewcraft_lamps")
@PrefixGameTestTemplate(false)
public final class MineLampGameTests {
    @GameTest(templateNamespace = "stardewcraft_lamps", template = "ring_utilities")
    public static void themesSurvivePlacementPickingDropsAndSaving(GameTestHelper helper) {
        var level=helper.getLevel();var block=(MineLampBlock)ModBlocks.MINE_LAMP.get();
        BlockPos pos=helper.absolutePos(new BlockPos(8,3,8));
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"Lamp test"));
        player.getAbilities().instabuild=true;
        // All four supports are present: clicked face must win even when the player looks elsewhere.
        for(Direction face:Direction.Plane.HORIZONTAL) level.setBlock(pos.relative(face),Blocks.STONE.defaultBlockState(),3);
        for(var theme:MineLampBlock.Theme.values()) for(Direction face:Direction.Plane.HORIZONTAL) {
            level.removeBlock(pos,false);
            var stack=new ItemStack(block);
            PortItemData.set(stack, DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY.with(MineLampBlock.THEME,theme));
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false));
            helper.assertTrue(((BlockItem)stack.getItem()).place(context).consumesAction(),"Lamp placement failed");
            var state=level.getBlockState(pos);
            helper.assertTrue(state.is(block) && state.getValue(MineLampBlock.FACING)==face && state.getValue(MineLampBlock.THEME)==theme,"Placement changed theme/facing");
            helper.assertTrue(state.getLightEmission(level,pos)==15 && state.getCollisionShape(level,pos).isEmpty(),"Wrong brightness or obstructive collision");
            helper.assertTrue(!state.getShape(level,pos).isEmpty(),"Missing selection shape");
            helper.assertTrue(MineLampItem.theme(block.getCloneItemStack(level,pos,state))==theme,"Picking lost theme");
            var entity=level.getBlockEntity(pos);
            helper.assertTrue(entity instanceof MineLampBlockEntity,"Lamp not available for source discovery");
            var saved=entity.saveWithFullMetadata();
            helper.assertTrue(BlockEntity.loadStatic(pos,state,saved) instanceof MineLampBlockEntity,"Light source lost after loading");
            var drops=Block.getDrops(state,level,pos,entity);
            helper.assertTrue(drops.size()==1 && MineLampItem.theme(com.stardew.craft.port.PortJava.getFirst(drops))==theme,"Loot lost theme");
            level.setBlock(pos,state.setValue(MineLampBlock.LIT,false),3);
            helper.assertTrue(level.getBlockState(pos).getLightEmission(level,pos)==0 && level.getBlockEntity(pos)==entity,"Toggle left light on or recreated source identity");
        }
        var state=level.getBlockState(pos);
        level.removeBlock(pos.relative(state.getValue(MineLampBlock.FACING).getOpposite()),false);
        helper.assertTrue(level.isEmptyBlock(pos),"Unsupported lamp remained floating");
        helper.succeed();
    }
}
