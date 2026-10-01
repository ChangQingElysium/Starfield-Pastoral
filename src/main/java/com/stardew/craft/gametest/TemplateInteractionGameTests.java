package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.templates.SnowLayerTemplateBlock;
import com.stardew.craft.templates.TemplateBlockEntity;
import com.stardew.craft.templates.TemplateContent;
import com.stardew.craft.templates.TemplateShape;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TemplateInteractionGameTests {
    private TemplateInteractionGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void camouflagedTemplatesAllowAdjacentPlacementWithEitherHand(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var player = player(helper, pos);
        for (var entry : TemplateContent.TEMPLATE_BLOCKS.entrySet()) {
            for (InteractionHand hand : InteractionHand.values()) {
                for (Block placed : new Block[]{Blocks.STONE, Blocks.GLASS,
                        TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.SLAB).get()}) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    var state = entry.getValue().get().defaultBlockState();
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                        level.setBlock(pos.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                                Block.UPDATE_CLIENTS);
                    }
                    var template = (TemplateBlockEntity) level.getBlockEntity(pos);
                    template.setMaterial(Blocks.GLASS.defaultBlockState());
                    // Composite templates accept their second material before adjacent placement.
                    if (state.getBlock() instanceof com.stardew.craft.templates.CompositeTemplateBlock) {
                        template.setFillMaterial(Blocks.GLASS.defaultBlockState());
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    var stack = new ItemStack(placed, 3);
                    player.setItemInHand(hand, stack);
                    var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0.5, 0.0625, 0), Direction.EAST, pos, false);
                    helper.assertTrue(player.gameMode.useItemOn(player, level, stack, hand, hit).consumesAction(),
                            "Normal placement was swallowed: " + entry.getKey() + " / " + hand);
                    helper.assertTrue(level.getBlockState(pos.east()).is(placed), "Block did not place next to " + entry.getKey());
                    helper.assertTrue(stack.getCount() == 2 && level.getBlockEntity(pos) == template
                                    && Blocks.GLASS.defaultBlockState().equals(template.material()),
                            "Placement replaced camouflage or consumed the wrong count: " + entry.getKey());
                    if (state.hasProperty(BlockStateProperties.OPEN)) {
                        helper.assertTrue(!level.getBlockState(pos).getValue(BlockStateProperties.OPEN), "Placement opened a template");
                    }
                    if (state.hasProperty(BlockStateProperties.POWERED)) {
                        helper.assertTrue(!level.getBlockState(pos).getValue(BlockStateProperties.POWERED), "Placement activated a template");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void initialCamouflageSneakReplacementRemovalAndSnowStackingStillWork(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var player = player(helper, pos);
        var block = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.SNOW_LAYER).get();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        var template = (TemplateBlockEntity) level.getBlockEntity(pos);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0, 0.125, 0), Direction.UP, pos, false);
        var glass = new ItemStack(Blocks.GLASS, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, glass);
        player.gameMode.useItemOn(player, level, glass, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(Blocks.GLASS.defaultBlockState().equals(template.material()) && glass.getCount() == 2
                && level.isEmptyBlock(pos.above()), "Initial click no longer applies camouflage");

        player.setShiftKeyDown(true);
        var stone = new ItemStack(Blocks.STONE, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        player.gameMode.useItemOn(player, level, stone, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(template.material() == null && stone.getCount() == 3
                && player.getInventory().countItem(Blocks.GLASS.asItem()) == 1, "Sneak removal did not return the old material");

        player.setShiftKeyDown(false);
        player.gameMode.useItemOn(player, level, stone, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(Blocks.STONE.defaultBlockState().equals(template.material()) && stone.getCount() == 2,
                "Applying a new material after removal failed");
        var layers = new ItemStack(block, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, layers);
        player.gameMode.useItemOn(player, level, layers, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(level.getBlockState(pos).getValue(SnowLayerTemplateBlock.LAYERS) == 2 && layers.getCount() == 2
                && Blocks.STONE.defaultBlockState().equals(template.material()), "Normal click broke snow-layer stacking");

        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(template.material() == null && player.getInventory().countItem(Blocks.STONE.asItem()) == 1,
                "Empty-handed sneak removal no longer returns material");
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper, BlockPos pos) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Template interaction"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.setPos(Vec3.atCenterOf(pos.offset(4, 0, 4)));
        return player;
    }
}
