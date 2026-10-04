package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainShapeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The building sand identity and vanilla shape mechanics stay coupled. */
@GameTestHolder("stardewcraft_sand_building")
@PrefixGameTestTemplate(false)
public final class SandBuildingGameTests {
    private SandBuildingGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_sand_building", template = "empty")
    public static void sandSlabsHandPlaceWaterlogMergeAndDropTheirOwnItems(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(pos.north(4)));
        var slab = ModBlocks.SAND_SLAB.get();
        h.assertTrue(slab instanceof SlabBlock && slab.asItem() != Blocks.SAND.asItem()
                        && TerrainShapeBlock.material(slab.defaultBlockState()).is(ModBlocks.SAND.get()),
                "Sand slab lost its own item or mod sand material");
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        var stack = new ItemStack(slab, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false)));
        var bottom = level.getBlockState(pos);
        h.assertTrue(bottom.is(slab) && bottom.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                        && bottom.getValue(SlabBlock.WATERLOGGED) && bottom.getFluidState().is(Fluids.WATER)
                        && stack.getCount() == 3,
                "Bottom sand slab must hand place into water and consume one item");
        h.assertTrue(bottom.getCollisionShape(level, pos).bounds().maxY == .5,
                "Bottom slab collision must be half a block");
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        var doubled = level.getBlockState(pos);
        h.assertTrue(doubled.is(slab) && doubled.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                        && !doubled.getValue(SlabBlock.WATERLOGGED) && doubled.getFluidState().isEmpty()
                        && stack.getCount() == 2,
                "Second sand slab must merge into a dry double slab");
        int count = Block.getDrops(doubled, level, pos, null).stream()
                .filter(drop -> drop.is(slab.asItem())).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(count == 2, "A double sand slab must drop two sand slabs");
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.above()).add(0, -.5, 0), Direction.DOWN, pos.above(), false)));
        var top = level.getBlockState(pos);
        h.assertTrue(top.is(slab) && top.getValue(SlabBlock.TYPE) == SlabType.TOP
                        && top.getValue(SlabBlock.WATERLOGGED)
                        && top.getCollisionShape(level, pos).bounds().minY == .5,
                "Underside placement must create a waterlogged top sand slab");
        player.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_sand_building", template = "empty")
    public static void sandStairsHandPlaceRotateAndFormBothCornerFamilies(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(pos.north(4)));
        var stairs = ModBlocks.SAND_STAIRS.get();
        h.assertTrue(stairs instanceof StairBlock && stairs.asItem() != Blocks.SAND.asItem()
                        && TerrainShapeBlock.material(stairs.defaultBlockState()).is(ModBlocks.SAND.get()),
                "Sand stairs lost their own item or mod sand material");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Half half : Half.values()) {
                for (var cell : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 2, 2)))
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                player.setYRot(facing.toYRot());
                BlockPos support = half == Half.TOP ? pos.above() : pos.below();
                Direction clicked = half == Half.TOP ? Direction.DOWN : Direction.UP;
                if (half == Half.TOP) level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
                var stack = new ItemStack(stairs, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(support).add(0, half == Half.TOP ? -.5 : .5, 0),
                                clicked, support, false)));
                var state = level.getBlockState(pos);
                h.assertTrue(state.is(stairs) && state.getValue(StairBlock.FACING) == facing
                                && state.getValue(StairBlock.HALF) == half
                                && state.getValue(StairBlock.WATERLOGGED) && state.getFluidState().is(Fluids.WATER)
                                && stack.getCount() == 1,
                        "Sand stair hand placement lost facing, half, water or item consumption");
                var rotated = state.rotate(Rotation.CLOCKWISE_90);
                h.assertTrue(rotated.getValue(StairBlock.FACING) == facing.getClockWise()
                                && rotated.getValue(StairBlock.HALF) == half && rotated.getValue(StairBlock.WATERLOGGED),
                        "Rotating sand stairs changed their half or water state");
                for (boolean front : new boolean[]{true, false}) {
                    BlockPos adjacent = pos.relative(front ? facing : facing.getOpposite());
                    for (Direction turn : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
                        level.setBlock(adjacent, stairs.defaultBlockState().setValue(StairBlock.FACING, turn)
                                .setValue(StairBlock.HALF, half), 3);
                        var corner = level.getBlockState(pos);
                        h.assertTrue(corner.getValue(StairBlock.SHAPE).getSerializedName()
                                        .startsWith(front ? "outer_" : "inner_"),
                                "Sand stairs did not form the expected inner/outer corner family");
                        level.setBlock(adjacent, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                var drops = Block.getDrops(level.getBlockState(pos), level, pos, null);
                h.assertTrue(drops.size() == 1 && drops.getFirst().is(stairs.asItem())
                                && drops.getFirst().getCount() == 1,
                        "Sand stairs must drop their own matching item");
            }
        }
        player.getInventory().clearContent();
        h.succeed();
    }
}
