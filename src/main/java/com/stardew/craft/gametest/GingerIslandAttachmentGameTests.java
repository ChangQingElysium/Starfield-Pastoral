package com.stardew.craft.gametest;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_ginger_placement")
@PrefixGameTestTemplate(false)
public final class GingerIslandAttachmentGameTests {
    private static final String NS = "stardewcraft_ginger_placement";
    private GingerIslandAttachmentGameTests() {}

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void fractionalRockFeetActuallyTouchTheClickedFoundation(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        level.setBlock(bottom.below(), Blocks.STONE.defaultBlockState(), 2);
        for (String name : List.of("mermaid_performance_rock", "volcano_bone_spike", "volcano_dragon_skull",
                "volcano_rib_arch", "caldera_monument", "professor_boulder", "field_office_radio")) {
            var block = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_" + name);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setPos(Vec3.atBottomCenterOf(bottom.relative(facing, 20)));
                player.setYRot(facing.getOpposite().toYRot());
                var hand = new ItemStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                var hit = new BlockHitResult(Vec3.atCenterOf(bottom.below()).add(0, .5, 0),
                        Direction.UP, bottom.below(), false);
                h.assertTrue(hand.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                        "Grounded hand placement rejected " + name + "/" + facing);
                BlockPos main = bottom.above(block.placementAnchorYOffset());
                var state = level.getBlockState(main);
                h.assertTrue(state.is(block) && state.getValue(MapDecorStaticBlock.FACING) == facing && hand.getCount() == 1,
                        "Grounded item lost anchor/facing/count: " + name);
                double foot = state.getShape(level, main).min(Direction.Axis.Y) + main.getY();
                h.assertTrue(Math.abs(foot - bottom.getY()) < 1e-4,
                        "Fractional authored foot floats above the foundation: " + name + " at " + (foot - bottom.getY()));
                level.removeBlock(main, false);
                h.assertTrue(level.getBlockState(bottom.below()).is(Blocks.STONE), "Grounding consumed foundation");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 150)
    public static void everyWallPropUsesTheClickedFaceAndKeepsTheSupportingWall(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        int owners = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!"wall".equals(asset.placement_mode())) continue;
            owners++;
            var block = (MapDecorStaticBlock) GingerIslandBlocks.get(asset.id());
            for (Direction face : Direction.Plane.HORIZONTAL) {
                BlockPos support = bottom.relative(face.getOpposite());
                List<BlockPos> wall = supportingWall(h, support, face);
                player.setPos(Vec3.atBottomCenterOf(bottom.relative(face, 5)));
                player.setYRot(face.getClockWise().toYRot()); // Deliberately disagree with the clicked wall.
                var hand = new ItemStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                var hit = new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),
                        face, support, false);
                h.assertTrue(hand.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                        "Wall hand placement rejected " + asset.id() + "/" + face);
                BlockPos main = bottom.above(block.placementAnchorYOffset());
                var state = level.getBlockState(main);
                h.assertTrue(state.is(block) && state.getValue(MapDecorStaticBlock.FACING) == face && hand.getCount() == 1,
                        "Player yaw overrides clicked support face: " + asset.id());
                for (BlockPos cell : wall) h.assertTrue(level.getBlockState(cell).is(Blocks.STONE),
                        "Wall prop overwrote its support: " + asset.id());
                level.removeBlock(main, false);
                for (BlockPos cell : wall) level.removeBlock(cell, false);
            }
        }
        h.assertTrue(owners == 15, "Wall policy audit skipped an owner: " + owners);
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void retiredPalmCannotBePlacedAgainstAnyCliffFace(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        var block = GingerIslandBlocks.get("ginger_south_palm");
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos support = bottom.relative(face.getOpposite());
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            var hand = new net.minecraft.world.item.ItemStack(block, 2);
            player.setPos(Vec3.atBottomCenterOf(bottom.relative(face, 6)));
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            var hit = new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),
                    face, support, false);
            h.assertTrue(!hand.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                    "Withdrawn palm still accepts hand placement: " + face);
            h.assertTrue(level.getBlockState(bottom).isAir() && hand.getCount() == 2, "Rejected placement changed the world or item count");
            h.assertTrue(level.getBlockState(support).is(Blocks.STONE), "Retired tree changed the cliff");
            level.removeBlock(support, false);
        }
        h.succeed();
    }

    private static List<BlockPos> supportingWall(GameTestHelper h, BlockPos support, Direction face) {
        var result = new ArrayList<BlockPos>();
        for (int across = -3; across <= 3; across++) for (int up = -1; up <= 6; up++) {
            BlockPos cell = support.relative(face.getClockWise(), across).above(up);
            h.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 2);
            result.add(cell);
        }
        return result;
    }
}
