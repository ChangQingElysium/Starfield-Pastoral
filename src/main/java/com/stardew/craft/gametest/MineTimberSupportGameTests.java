package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineTimberSupportBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineTimberSupportGameTests {
    private MineTimberSupportGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void timberColumnsJoinAndBreakAsOneColumnInEveryDirection(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.MINE_TIMBER_SUPPORT.get();
        var origin = helper.absolutePos(new BlockPos(8, 1, 8));
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "Timber test"));
        player.getAbilities().instabuild = true;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction along = facing.getCounterClockWise();
            for (int width : new int[]{2, 4, 6}) {
                for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                    level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 0; y < 5; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                for (int i = 0; i < width; i++) {
                    BlockPos pos = origin.relative(along, i);
                    var sample = block.defaultBlockState().setValue(MineTimberSupportBlock.VARIANT, i == 1 ? 1 : 0);
                    var stack = block.getCloneItemStack(level, pos, sample);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                            new BlockHitResult(Vec3.atCenterOf(pos), facing, pos, false));
                    helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Column placement failed");
                }
                for (int i = 0; i < width; i++) for (int tier = 0; tier < 4; tier++) {
                    BlockPos pos = origin.relative(along, i).above(tier);
                    var state = level.getBlockState(pos);
                    helper.assertTrue(state.is(block) && state.getValue(MineTimberSupportBlock.TIER) == tier
                            && state.getValue(MineTimberSupportBlock.FACING) == facing, "Incomplete or rotated column");
                    helper.assertTrue(state.getValue(MineTimberSupportBlock.LEFT) == (i > 0)
                            && state.getValue(MineTimberSupportBlock.RIGHT) == (i < width - 1), "Join/end-cap states are wrong");
                    helper.assertTrue(state.getValue(MineTimberSupportBlock.VARIANT) == (i == 1 ? 1 : 0), "Damage variant lost");
                    var basePos = pos.below(tier);
                    var outline = state.getShape(level, pos).move(0, tier, 0);
                    var baseOutline = level.getBlockState(basePos).getShape(level, basePos);
                    helper.assertTrue(!Shapes.joinIsNotEmpty(outline, baseOutline, BooleanOp.NOT_SAME), "Selection outline differs by tier");
                    var collision = state.getCollisionShape(level, pos);
                    helper.assertTrue(collision.min(Direction.Axis.Y) >= 0 && collision.max(Direction.Axis.Y) <= 1,
                            "Collision extends outside its own occupied cell");
                }
                // Breaking an upper piece must remove this column and reconnect both neighbors.
                BlockPos middle = origin.relative(along);
                var upper = level.getBlockState(middle.above(2));
                block.playerWillDestroy(level, middle.above(2), upper, player);
                level.destroyBlock(middle.above(2), false);
                for (int tier = 0; tier < 4; tier++) {
                    helper.assertTrue(level.isEmptyBlock(middle.above(tier)), "Orphaned column piece after removal");
                    helper.assertTrue(!level.getBlockState(origin.above(tier)).getValue(MineTimberSupportBlock.RIGHT), "Left neighbor did not recap");
                    if (width > 2) helper.assertTrue(!level.getBlockState(origin.relative(along, 2).above(tier))
                            .getValue(MineTimberSupportBlock.LEFT), "Right neighbor did not recap");
                }
            }
        }
        // A blocked upper cell must reject the entire placement.
        BlockPos test = origin.offset(0, 0, -5);
        level.setBlock(test.above(3), Blocks.STONE.defaultBlockState(), 3);
        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(test), Direction.SOUTH, test, false));
        helper.assertTrue(block.getStateForPlacement(context) == null && level.isEmptyBlock(test), "Blocked placement left partial geometry");
        helper.succeed();
    }
}
