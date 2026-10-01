package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.ManholeBlock;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
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

@GameTestHolder("stardewcraft_manhole")
@PrefixGameTestTemplate(false)
public final class ManholeGameTests {
    @GameTest(templateNamespace = "stardewcraft_manhole", template = "ring_utilities")
    public static void footprintOwnershipAndSingleDrop(GameTestHelper h) {
        var level = h.getLevel();
        var main = h.absolutePos(new BlockPos(10, 1, 10));
        var block = ModBlocks.MANHOLE.get();
        for (var at : BlockPos.betweenClosed(main.offset(-5, -1, -5), main.offset(5, 3, 5)))
            level.setBlock(at, at.getY() == main.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(main.offset(5, 0, 5)));
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int brokenCell = 0; brokenCell < ManholeBlock.CELL_COUNT; brokenCell++) {
                player.setYRot(facing.getOpposite().toYRot());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block, 3));
                var ctx = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(main), Direction.UP, main.below(), false)));
                var blocked = main.offset(ManholeBlock.offset(brokenCell, facing));
                level.setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
                h.assertTrue(!((BlockItem) block.asItem()).place(ctx).consumesAction(), "Occupied footprint must reject placement");
                h.assertTrue(player.getMainHandItem().getCount() == 3, "Failed placement consumed item");
                level.removeBlock(blocked, false);
                h.assertTrue(((BlockItem) block.asItem()).place(ctx).consumesAction(), "Manhole placement failed");
                for (int cell = 0; cell < ManholeBlock.CELL_COUNT; cell++) {
                    var at = main.offset(ManholeBlock.offset(cell, facing));
                    var state = level.getBlockState(at);
                    h.assertTrue(state.is(block) && state.getValue(ManholeBlock.CELL) == cell, "Missing or wrong footprint cell");
                    h.assertTrue(state.getValue(MapDecorStaticBlock.FACING) == facing, "Wrong facing");
                    h.assertTrue(main.equals(block.findMainPos(level, at, state)), "Incorrect cell owner");
                    h.assertTrue(state.getCollisionShape(level, at).bounds().equals(new AABB(0, 0, 0, 1, 0.125, 1)), "Collision exceeds local thin cell");
                }
                // A touching second cover must retain its own ownership during removal.
                var neighbor = main.offset(ManholeBlock.offset(1, facing).multiply(3));
                var neighborState = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
                level.setBlock(neighbor, neighborState, 3);
                block.setPlacedBy(level, neighbor, neighborState, player, new ItemStack(block));
                for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(main).inflate(6))) item.discard();
                level.destroyBlock(main.offset(ManholeBlock.offset(brokenCell, facing)), true);
                for (int cell = 0; cell < ManholeBlock.CELL_COUNT; cell++) {
                    h.assertTrue(level.getBlockState(main.offset(ManholeBlock.offset(cell, facing))).isAir(), "Removal left a fragment");
                    var at = neighbor.offset(ManholeBlock.offset(cell, facing));
                    h.assertTrue(level.getBlockState(at).is(block) && neighbor.equals(block.findMainPos(level, at, level.getBlockState(at))), "Removal damaged touching cover");
                }
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(main).inflate(6));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(block.asItem()) && drops.getFirst().getItem().getCount() == 1, "Manhole must drop exactly once");
                MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(neighbor, false));
            }
        }
        h.succeed();
    }
}
