package com.stardew.craft.gametest;

import com.stardew.craft.templates.*;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.PortBlockInteraction;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ChimneyTemplateGameTests {
    @GameTest(templateNamespace = "stardewcraft", template = "ring_utilities")
    public static void placementStackingAndRemovalPreserveMaterials(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var block = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.CHIMNEY).get();
        var item = TemplateContent.TEMPLATE_ITEMS.get(TemplateShape.CHIMNEY).get();
        var base = h.absolutePos(new BlockPos(7, 2, 7));
        player.setPos(Vec3.atCenterOf(base.offset(4, 0, 4)));
        level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var stack = new ItemStack(item, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        TemplateBlockEntity first = null;
        for (int i = 0; i < 3; i++) {
            var pos = base.above(i);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            var clicked = pos.below();
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(clicked).add(0, 1, 0), Direction.UP, clicked, false);
            if (i > 0) h.assertTrue(PortBlockInteraction.stateUseItemOn(level.getBlockState(clicked), stack, level, player,
                    InteractionHand.MAIN_HAND, hit) == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "Template swallowed stacking");
            h.assertTrue(item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction(), "Stack placement failed");
            h.assertTrue(level.getBlockState(pos).getValue(ChimneyTemplateBlock.CAPPED), "Top cap missing");
            if (i > 0) h.assertTrue(!level.getBlockState(clicked).getValue(ChimneyTemplateBlock.CAPPED), "Buried cap remains");
            if (i == 0) {
                first = (TemplateBlockEntity) level.getBlockEntity(pos);
                first.setMaterial(Blocks.IRON_BLOCK.defaultBlockState());
            }
            h.assertTrue(level.getBlockEntity(base) == first && first.material().is(Blocks.IRON_BLOCK), "Material lost during update");
        }
        level.destroyBlock(base.above(2), false);
        h.assertTrue(level.getBlockState(base.above()).getValue(ChimneyTemplateBlock.CAPPED), "Cap did not return");
        h.assertTrue(!level.getBlockState(base).getValue(ChimneyTemplateBlock.CAPPED), "Bottom section gained a cap");
        level.destroyBlock(base.above(), false);
        h.assertTrue(level.getBlockState(base).getValue(ChimneyTemplateBlock.CAPPED), "Last cap did not return");
        h.assertTrue(first.material().is(Blocks.IRON_BLOCK), "Removing upper sections changed lower material");
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft", template = "ring_utilities")
    public static void downwardPlacementAndRoofSupport(GameTestHelper h) {
        var level = h.getLevel();
        var block = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.CHIMNEY).get();
        var pos = h.absolutePos(new BlockPos(7, 3, 7));
        var roof = TemplateContent.TEMPLATE_BLOCKS.get(TemplateShape.ROOF_UPPER_STEEP).get();
        level.setBlock(pos.below(), roof.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos.above(), block.defaultBlockState(), Block.UPDATE_ALL);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setPos(Vec3.atCenterOf(pos.offset(4, 0, 4)));
        var item = TemplateContent.TEMPLATE_ITEMS.get(TemplateShape.CHIMNEY).get();
        var stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos.above()), Direction.DOWN, pos.above(), false);
        h.assertTrue(item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction(), "Placement below existing section failed");
        var state = level.getBlockState(pos);
        h.assertTrue(!state.getValue(ChimneyTemplateBlock.CAPPED), "Downward placement kept an internal cap");
        h.assertTrue(level.getBlockState(pos.below()).is(roof), "Placement replaced the roof");
        for (boolean capped : new boolean[]{false, true}) {
            var shape = state.setValue(ChimneyTemplateBlock.CAPPED, capped).getShape(level, pos);
            for (Direction.Axis axis : Direction.Axis.values()) {
                h.assertTrue(shape.min(axis) >= 0 && shape.max(axis) <= 1, "Shape extends outside its cell");
            }
        }
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(level.getBlockState(pos).getValue(ChimneyTemplateBlock.CAPPED), "Unrelated block connected as chimney");
        h.succeed();
    }
}
