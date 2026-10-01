package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineExitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MineExitGameTests {
    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities", timeoutTicks = 100)
    public static void fourHighLadderPlacementProtectionAndRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = (MineExitBlock) ModBlocks.MINE_EXIT.get();
        BlockPos root = helper.absolutePos(new BlockPos(8, 2, 8));
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "Exit ladder test"), ClientInformation.createDefault()) {
            // No connected client: expose the two permission modes directly for this test player.
            @Override public boolean isCreative() { return getAbilities().instabuild; }
        };
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int removedTier = 0; removedTier < 4; removedTier++) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y < 5; y++) {
                level.setBlock(root.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            for (int tier = 0; tier < 4; tier++) level.setBlock(root.above(tier).relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
            player.getAbilities().instabuild = true;
            ItemStack stack = new ItemStack(block);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(root), facing, root, false));
            helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction(), "Four-high placement failed");
            for (int tier = 0; tier < 4; tier++) {
                BlockPos pos = root.above(tier);
                var state = level.getBlockState(pos);
                helper.assertTrue(state.is(block) && state.getValue(MineExitBlock.TIER) == tier
                        && state.getValue(MineExitBlock.FACING) == facing, "Missing or misoriented extension");
                helper.assertTrue(state.is(BlockTags.CLIMBABLE), "Ladder cannot be climbed");
                helper.assertTrue(state.getDestroySpeed(level, pos) < 0 && state.getPistonPushReaction() == PushReaction.BLOCK, "Ladder is destructible/movable");
                helper.assertTrue(state.getLightEmission(level, pos) == 0, "Wood ladder emits light");
                var outline = state.getShape(level, pos).move(0, tier, 0);
                helper.assertTrue(!Shapes.joinIsNotEmpty(outline, level.getBlockState(root).getShape(level, root), BooleanOp.NOT_SAME), "Tier outlines differ");
                var collision = state.getCollisionShape(level, pos);
                helper.assertTrue(!collision.isEmpty() && collision.min(Direction.Axis.Y) >= 0 && collision.max(Direction.Axis.Y) <= 1, "Tier collision is wrong");
                player.getAbilities().instabuild = false;
                helper.assertTrue(!block.onDestroyedByPlayer(state, level, pos, player, false, level.getFluidState(pos))
                        && level.getBlockState(pos).is(block), "Survival player destroyed the exit");
            }
            player.setPos(root.getX() + 0.5, root.getY() + 0.2, root.getZ() + 0.5);
            helper.assertTrue(player.onClimbable(), "Player does not recognize ladder volume");
            // Mining the supporting wall must not destroy this protected exit indirectly.
            level.removeBlock(root.relative(facing.getOpposite()), false);
            helper.assertTrue(level.getBlockState(root).is(block), "Removing wall destroyed the exit");
            player.getAbilities().instabuild = true;
            BlockPos remove = root.above(removedTier);
            helper.assertTrue(block.onDestroyedByPlayer(level.getBlockState(remove), level, remove, player, false,
                    level.getFluidState(remove)), "Creative editing was blocked");
            for (int tier = 0; tier < 4; tier++) helper.assertTrue(level.isEmptyBlock(root.above(tier)), "Orphaned ladder after creative removal");
        }
        for (int tier = 0; tier < 4; tier++) level.setBlock(root.above(tier).north(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root.above(3), Blocks.STONE.defaultBlockState(), 3);
        ItemStack stack = new ItemStack(block);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(root), Direction.SOUTH, root, false));
        helper.assertTrue(block.getStateForPlacement(context) == null && level.isEmptyBlock(root), "Blocked placement created a partial ladder");
        level.removeBlock(root.above(3), false);
        var base = block.defaultBlockState();
        level.setBlock(root, base, 3);block.placeExtensions(level, root, base);
        level.explode(null, root.getX() + 0.5, root.getY() + 1, root.getZ() + 1.5, 4, Level.ExplosionInteraction.TNT);
        for (int tier = 0; tier < 4; tier++) helper.assertTrue(level.getBlockState(root.above(tier)).is(block), "Explosion destroyed exit");
        helper.succeed();
    }
}
