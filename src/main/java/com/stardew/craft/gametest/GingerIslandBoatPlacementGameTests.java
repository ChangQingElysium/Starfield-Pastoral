package com.stardew.craft.gametest;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.WillyBoatBlockEntity;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Water retention alone does not prove that a boat's visible waterline is above the sea. */
@GameTestHolder("stardewcraft_ginger_boats")
@PrefixGameTestTemplate(false)
public final class GingerIslandBoatPlacementGameTests {
    private static final String TEMPLATE_NS = "stardewcraft_ginger_placement";
    private static final List<String> BOATS = List.of("ginger_willy_boat", "ginger_island_shipwreck");

    private GingerIslandBoatPlacementGameTests() {}

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void deepWaterAndSeabedHandClicksUseTheActualSurfaceAllFacings(GameTestHelper h) {
        basin(h, 5);
        var level = h.getLevel();
        BlockPos topWater = h.absolutePos(new BlockPos(40, 8, 40));
        double surface = topWater.getY() + level.getFluidState(topWater).getHeight(level, topWater);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(level.addFreshEntity(player), "Placing player must take part in collision queries");
        try {
            for (String id : BOATS) {
                var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    aim(player, topWater, facing);
                    BlockPos main = expectedMain(topWater, block, facing);
                    Set<BlockPos> cells = block.placementPositions(main, facing);
                    assertInside(h, cells);
                    // Ordinary client block rays ignore water and hit the seabed.
                    // Explicit deep-water contacts must use that same surface too.
                    for (BlockPos contact : List.of(topWater.below(5), topWater.below(3))) {
                        Map<BlockPos, BlockState> before = snapshot(h, cells);
                        ItemStack hand = new ItemStack(block, 2);
                        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                        h.assertTrue(hand.useOn(contact(player, contact)).consumesAction(),
                                "Water-column hand placement failed: " + id + " / " + facing + " / " + contact);
                        assertCompleteAndWet(h, block, main, facing, before);
                        h.assertTrue(hand.getCount() == 1, "Boat placement must consume one item");
                        // The authoring contracts put the waterline at model Y=0.
                        // A source water block ends at 8/9, not at its integer base.
                        h.assertTrue(main.getY() > surface && main.getY() - surface < .126,
                                "The authored waterline is submerged or lifted by the keel");
                        if (id.equals("ginger_willy_boat")) {
                            assertWillyDeckAndMotion(h, main, facing, surface);
                            var boat = (WillyBoatBlockEntity) level.getBlockEntity(main);
                            var motion = boat.motion(.375F);
                            level.setBlock(main, level.getBlockState(main)
                                    .setValue(GingerIslandStateDecorBlock.REPAIRED, true), 2 | 16);
                            h.assertTrue(level.getBlockEntity(main) == boat && boat.motion(.375F).equals(motion),
                                    "Repair changed the render owner or restarted its float phase");
                            assertWillyDeckAndMotion(h, main, facing, surface);
                        } else {
                            // Authored floating boards reach down to -0.04724 blocks.
                            h.assertTrue(main.getY() - .05 > surface,
                                    "The shipwreck's floating debris is below the sea surface");
                        }
                        BlockPos broken = facing == Direction.NORTH || facing == Direction.EAST ? main
                                : cells.stream().filter(p -> !p.equals(main)
                                        && before.get(p).getFluidState().is(Fluids.WATER)).findFirst().orElseThrow();
                        restore(h, block, broken, player, before);
                    }
                }
            }
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void oneLayerWaterRejectsTheKeelAndTwoLayersAcceptAllFacings(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos topWater = h.absolutePos(new BlockPos(40, 8, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(level.addFreshEntity(player), "Placing player must take part in collision queries");
        try {
            for (int depth : List.of(1, 2)) {
                basin(h, depth);
                for (String id : BOATS) {
                    var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        aim(player, topWater, facing);
                        BlockPos main = expectedMain(topWater, block, facing);
                        Set<BlockPos> cells = block.placementPositions(main, facing);
                        assertInside(h, cells);
                        Map<BlockPos, BlockState> before = snapshot(h, cells);
                        ItemStack hand = new ItemStack(block, 2);
                        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                        boolean placed = hand.useOn(contact(player, topWater.below(depth))).consumesAction();
                        h.assertTrue(placed == (depth == 2),
                                "Keel clearance ignored the actual seabed depth: " + id + " / " + depth + " / " + facing);
                        if (placed) {
                            assertCompleteAndWet(h, block, main, facing, before);
                            h.assertTrue(hand.getCount() == 1, "Two-layer placement did not consume one item");
                            restore(h, block, main, player, before);
                        } else {
                            h.assertTrue(hand.getCount() == 2, "Shallow-water refusal consumed an item");
                            assertRestored(h, before);
                        }
                    }
                }
            }
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void sourceWaterPovUsesTheSurfaceAndLandBuiltWillyDoesNotFloat(GameTestHelper h) {
        basin(h, 5);
        var level = h.getLevel();
        BlockPos topWater = h.absolutePos(new BlockPos(40, 8, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(level.addFreshEntity(player), "POV player must take part in collision queries");
        try {
            for (String id : BOATS) {
                var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    aim(player, topWater, facing);
                    double surface = topWater.getY() + level.getFluidState(topWater).getHeight(level, topWater);
                    player.setXRot((float) Math.toDegrees(Math.atan2(player.getEyeY() - surface, 4)));
                    ItemStack hand = new ItemStack(block, 2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    BlockPos main = expectedMain(topWater, block, facing);
                    Map<BlockPos, BlockState> before = snapshot(h, block.placementPositions(main, facing));
                    h.assertTrue(hand.getItem().use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                            "Source-water POV failed: " + id + " / " + facing);
                    assertCompleteAndWet(h, block, main, facing, before);
                    h.assertTrue(main.getY() > surface && hand.getCount() == 1, "POV put the boat below water");
                    restore(h, block, main, player, before);
                }
            }
            basin(h, 0);
            BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
            var block = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_willy_boat");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                aim(player, bottom, facing);
                BlockPos main = bottom.above(block.placementAnchorYOffset());
                Map<BlockPos, BlockState> before = snapshot(h, block.placementPositions(main, facing));
                ItemStack hand = new ItemStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                h.assertTrue(hand.useOn(contact(player, bottom.below())).consumesAction(), "Land boat refused valid ground");
                var boat = (WillyBoatBlockEntity) level.getBlockEntity(main);
                h.assertTrue(boat != null && boat.motion(.125F).equals(new WillyBoatBlockEntity.Motion(0, 0))
                                && boat.motion(.875F).equals(new WillyBoatBlockEntity.Motion(0, 0)),
                        "A land-built boat bobs above its static collision");
                restore(h, block, main, player, before);
            }
        } finally {
            player.getInventory().clearContent();
            player.discard();
        }
        h.succeed();
    }

    private static void assertWillyDeckAndMotion(GameTestHelper h, BlockPos main, Direction facing, double surface) {
        var state = h.getLevel().getBlockState(main);
        var collision = state.getCollisionShape(h.getLevel(), main);
        Vec3 deck = switch (facing) {
            case EAST -> new Vec3(-2, 1, .5);
            case SOUTH -> new Vec3(.5, 1, -2);
            case WEST -> new Vec3(3, 1, .5);
            default -> new Vec3(.5, 1, 3);
        };
        AABB player = new AABB(deck.x - .3, 1.04, deck.z - .3, deck.x + .3, 2.84, deck.z + .3);
        h.assertTrue(Math.abs(collision.collide(Direction.Axis.Y, player, -.1) + .04) < 1e-6,
                "The actual coarse deck cannot support a standard player at authored Y=16");
        double lowestAnimatedDeck = main.getY() + 1 - WillyBoatBlockEntity.HEAVE_AMPLITUDE
                - 5 * Math.sin(Math.toRadians(WillyBoatBlockEntity.ROLL_AMPLITUDE_DEGREES));
        h.assertTrue(lowestAnimatedDeck - surface > 1,
                "Even the minimum animated deck height is too close to/submerged in water");
        var boat = (WillyBoatBlockEntity) h.getLevel().getBlockEntity(main);
        h.assertTrue(boat != null && !state.getValue(BlockStateProperties.WATERLOGGED), "Waterline MAIN must be dry");
        boolean moving = false;
        for (float partial : List.of(0F, .25F, .5F, .75F)) {
            var motion = boat.motion(partial);
            moving |= Math.abs(motion.heave()) > 1e-7 || Math.abs(motion.rollDegrees()) > 1e-7;
            h.assertTrue(collision == state.getCollisionShape(h.getLevel(), main), "Visual motion altered static collision");
        }
        h.assertTrue(moving, "Submerged source cells must enable float even though MAIN is dry");
    }

    private static void aim(Player player, BlockPos water, Direction facing) {
        player.setYRot(facing.getOpposite().toYRot());
        player.setXRot(0);
        player.setPos(Vec3.atBottomCenterOf(water.relative(facing, 4).above()));
    }

    private static BlockPos expectedMain(BlockPos topWater, MapDecorStaticBlock block, Direction facing) {
        return topWater.above().relative(facing.getOpposite(), block.waterPlacementForwardOffset());
    }

    private static UseOnContext contact(Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false));
    }

    private static Map<BlockPos, BlockState> snapshot(GameTestHelper h, Set<BlockPos> cells) {
        assertInside(h, cells);
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (BlockPos cell : cells) result.put(cell, h.getLevel().getBlockState(cell));
        return result;
    }

    private static void assertCompleteAndWet(GameTestHelper h, MapDecorStaticBlock block, BlockPos main,
                                             Direction facing, Map<BlockPos, BlockState> before) {
        int owners = 0;
        int wet = 0;
        for (var entry : before.entrySet()) {
            BlockPos cell = entry.getKey();
            var state = h.getLevel().getBlockState(cell);
            h.assertTrue(state.is(block) && state.getValue(MapDecorStaticBlock.FACING) == facing
                            && state.getValue(MapDecorStaticBlock.PART) == (cell.equals(main)
                                ? MapDecorStaticBlock.Part.MAIN : MapDecorStaticBlock.Part.EXTENSION)
                            && main.equals(block.findMainPos(h.getLevel(), cell, state)), "Boat left an orphan/wrong-facing cell");
            boolean water = entry.getValue().getFluidState().is(Fluids.WATER);
            h.assertTrue(state.getValue(BlockStateProperties.WATERLOGGED) == water, "Displaced source water was lost");
            if (water) wet++;
            if (h.getLevel().getBlockEntity(cell) != null) {
                owners++;
                h.assertTrue(cell.equals(main), "Boat extension owns a renderer");
            }
        }
        h.assertTrue(wet > 0 && owners == (block == GingerIslandBlocks.get("ginger_willy_boat") ? 1 : 0),
                "Boat is dry or owns duplicate renderers");
    }

    private static void restore(GameTestHelper h, MapDecorStaticBlock block, BlockPos broken,
                                Player player, Map<BlockPos, BlockState> before) {
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            block.playerWillDestroy(h.getLevel(), broken, h.getLevel().getBlockState(broken), player);
            h.getLevel().removeBlock(broken, false);
        });
        assertRestored(h, before);
    }

    private static void assertRestored(GameTestHelper h, Map<BlockPos, BlockState> before) {
        for (var entry : before.entrySet()) h.assertTrue(h.getLevel().getBlockState(entry.getKey()).equals(entry.getValue())
                && h.getLevel().getBlockEntity(entry.getKey()) == null, "Boat removal/refusal changed source water or seabed");
    }

    private static void assertInside(GameTestHelper h, Set<BlockPos> cells) {
        for (BlockPos cell : cells) h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(cell)), "Boat escaped its actual test fixture");
    }

    private static void basin(GameTestHelper h, int depth) {
        int floor = depth == 0 ? 3 : 8 - depth;
        for (BlockPos relative : BlockPos.betweenClosed(10, 3, 10, 69, 23, 69)) {
            BlockPos pos = h.absolutePos(relative);
            h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "Basin escaped its test fixture");
            h.getLevel().setBlock(pos, relative.getY() <= floor ? Blocks.STONE.defaultBlockState()
                    : depth > 0 && relative.getY() <= 8 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
    }
}
