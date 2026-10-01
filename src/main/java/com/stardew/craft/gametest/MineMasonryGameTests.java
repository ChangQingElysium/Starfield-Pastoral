package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.mine.ElevatorBlock;
import com.stardew.craft.block.mine.MineMasonryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineMasonryGameTests {
    private MineMasonryGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void pavingUpdatesDiagonalCornersAndPreservesSoil(GameTestHelper helper) {
        var level = helper.getLevel();
        var paving = ModBlocks.MINE_MASONRY.get();
        BlockPos center = helper.absolutePos(new BlockPos(8, 3, 8));
        int[][] neighbors = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(center.offset(x,-1,z), ModBlocks.MINE_EARTH_SOIL.get().defaultBlockState(), 3);
            level.setBlock(center.offset(x,0,z), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(center, paving.defaultBlockState().setValue(MineMasonryBlock.VARIANT, 1), 3);
        // Traverse all neighborhoods. Diagonals must update on both placement and removal.
        for (int mask = 0; mask < 256; mask++) {
            for (int i = 0; i < neighbors.length; i++) level.setBlock(center.offset(neighbors[i][0],0,neighbors[i][1]),
                    (mask & (1 << i)) == 0 ? Blocks.AIR.defaultBlockState() : paving.defaultBlockState(), 3);
            var state = level.getBlockState(center);
            helper.assertTrue(state.getValue(MineMasonryBlock.CONNECTIONS) == MineMasonryBlock.canonicalMask(mask), "Stale edge or diagonal at " + mask);
            helper.assertTrue(state.getValue(MineMasonryBlock.VARIANT) == 1, "Connection update erased the surface variant");
        }
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Paving test"), ClientInformation.createDefault());
        player.getAbilities().instabuild = true;
        var stack = paving.getCloneItemStack(level, center, level.getBlockState(center));
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(center), Direction.UP, center, false));
        helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Layer item failed placement");
        var placed = level.getBlockState(center);
        helper.assertTrue(placed.getValue(MineMasonryBlock.VARIANT) == 1, "Picked variant was not preserved");
        helper.assertTrue(placed.getCollisionShape(level, center).max(Direction.Axis.Y) == 1.0 / 16, "Paving became a full block");
        helper.assertTrue(level.getBlockState(center.below()).is(ModBlocks.MINE_EARTH_SOIL.get()), "Paving replaced its soil substrate");
        level.removeBlock(center.below(), false);
        helper.assertTrue(level.getBlockState(center).isAir(), "Unsupported paving remained floating");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void elevatorPlacesAllPartsAndButtonNeverCollides(GameTestHelper helper) {
        var level = helper.getLevel();
        var elevator = (ElevatorBlock) ModBlocks.ELEVATOR.get();
        BlockPos main = helper.absolutePos(new BlockPos(8, 3, 8));
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "Elevator test"), ClientInformation.createDefault());
        player.getAbilities().instabuild = true;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -1; y < 5; y++) {
            level.setBlock(main.offset(x,y,z), y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            player.setYRot(facing.getOpposite().toYRot());
            ItemStack stack = new ItemStack(elevator);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(main), Direction.UP, main, false));
            helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Elevator item failed placement");
            BlockPos button = main.above().relative(facing.getClockWise()).relative(facing);
            for (BlockPos part : new BlockPos[]{main, main.above(), main.above(2), button}) {
                var state = level.getBlockState(part);
                helper.assertTrue(state.is(elevator) && state.getValue(MapDecorStaticBlock.FACING) == facing, "Missing or wrongly oriented extension");
                helper.assertTrue(main.equals(elevator.findMainPos(level, part, state)), "Extension points to the wrong main");
            }
            helper.assertTrue(level.getBlockState(button).getCollisionShape(level, button).isEmpty(), "Call button blocks players");
            helper.assertTrue(!level.getBlockState(button).getShape(level, button).isEmpty(), "Call button cannot be selected");
            helper.assertTrue(level.getBlockState(main.above(2)).getLightEmission() == 12, "Elevator top lamp is dark");
            elevator.playerWillDestroy(level, button, level.getBlockState(button), player);
            for (BlockPos part : new BlockPos[]{main, main.above(), main.above(2), button}) {
                helper.assertTrue(level.getBlockState(part).isAir(), "Creative removal left an orphaned elevator part");
            }
        }
        helper.succeed();
    }
}
