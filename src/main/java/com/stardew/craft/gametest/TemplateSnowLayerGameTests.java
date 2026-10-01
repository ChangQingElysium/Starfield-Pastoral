package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.templates.MaterialTemplateBlock;
import com.stardew.craft.templates.SnowLayerTemplateBlock;
import com.stardew.craft.templates.TemplateBlockEntity;
import com.stardew.craft.templates.TemplateContent;
import com.stardew.craft.templates.TemplateShape;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TemplateSnowLayerGameTests {
    private TemplateSnowLayerGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void snowTemplateStacksThroughItemPlacementAndKeepsItsMaterial(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var block = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.SNOW_LAYER).get();
        var item = TemplateContent.TEMPLATE_ITEMS.get(TemplateShape.SNOW_LAYER).get();
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "Layer test"), ClientInformation.createDefault());
        player.setPos(Vec3.atCenterOf(pos.offset(3, 0, 3)));
        var stack = new ItemStack(item, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        TemplateBlockEntity original = null;
        for (int layers = 1; layers <= 8; layers++) {
            var clicked = layers == 1 ? pos.below() : pos;
            double height = layers == 1 ? 1 : (layers - 1) / 8D;
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(clicked).add(0, height, 0), Direction.UP, clicked, false);
            if (layers > 1) {
                helper.assertTrue(level.getBlockState(pos).useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit)
                                == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                        "Template material interaction swallowed layer placement");
            }
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
            helper.assertTrue(item.place(context).consumesAction(), "Layer placement failed: " + layers);
            var state = level.getBlockState(pos);
            helper.assertTrue(state.is(block) && state.getValue(SnowLayerTemplateBlock.LAYERS) == layers,
                    "Layer was placed in the wrong cell or did not increase");
            helper.assertTrue(stack.getCount() == 16 - layers, "Incorrect item consumption");
            helper.assertTrue(Math.abs(state.getShape(level, pos).max(Direction.Axis.Y) - layers / 8D) < 1E-6
                            && Math.abs(state.getCollisionShape(level, pos).max(Direction.Axis.Y) - layers / 8D) < 1E-6,
                    "Selection or collision height did not follow the layer count");
            var template = (TemplateBlockEntity) level.getBlockEntity(pos);
            if (layers == 1) {
                original = template;
                template.setMaterial(Blocks.GLASS.defaultBlockState());
            }
            helper.assertTrue(template == original && Blocks.GLASS.defaultBlockState().equals(template.material()),
                    "Stacking replaced the block entity or lost its material");
            helper.assertTrue(!level.getBlockState(pos).getValue(MaterialTemplateBlock.SOLID), "Stacking reset glass occlusion");
            int dropped = Block.getDrops(level.getBlockState(pos), level, pos, template).stream()
                    .filter(drop -> drop.is(item)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(dropped == layers, "Breaking layers returns the wrong template count: " + dropped);
        }
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 1, 0), Direction.UP, pos, false);
        helper.assertTrue(item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction(),
                "Ninth layer did not start the next cell");
        helper.assertTrue(level.getBlockState(pos).getValue(SnowLayerTemplateBlock.LAYERS) == 8
                        && level.getBlockState(pos.above()).is(block)
                        && level.getBlockState(pos.above()).getValue(SnowLayerTemplateBlock.LAYERS) == 1,
                "Full stack overflowed or changed the lower cell");
        original.setMaterial(Blocks.STONE.defaultBlockState());
        helper.assertTrue(Block.isShapeFullBlock(level.getBlockState(pos).getOcclusionShape(level, pos)),
                "Eight opaque layers do not occlude as a full block");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void snowTemplateDoesNotReplaceLayersWithOtherItemsOrMelt(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var block = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.SNOW_LAYER).get();
        var state = block.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "Layer safety"), ClientInformation.createDefault());
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 0.125, 0), Direction.UP, pos, false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE), hit);
        helper.assertTrue(!state.canBeReplaced(context), "Another block can erase the layer and its material");
        var side = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false));
        helper.assertTrue(side.getClickedPos().equals(pos.east()), "Side placement unexpectedly stacked vertically");
        helper.assertTrue(!state.isRandomlyTicking(), "Building layers should not inherit snow melting");
        helper.succeed();
    }
}
