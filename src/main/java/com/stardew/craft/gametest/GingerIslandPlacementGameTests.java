package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered hand items, rather than structure-only setBlock, must install and remove complete props. */
@GameTestHolder("stardewcraft_ginger_placement")
@PrefixGameTestTemplate(false)
public final class GingerIslandPlacementGameTests {
    private static final String NS = "stardewcraft_ginger_placement";
    private static final List<String> BOATS = List.of("ginger_willy_boat", "ginger_island_shipwreck");

    private GingerIslandPlacementGameTests() {}

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 200)
    public static void everyStaticPropHandPlacesAtItsBottomInAllDirections(GameTestHelper h) {
        flatFoundation(h);
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Island prop placement"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 4, 5))));
        int checked = 0;
        try {
            for (var asset : GingerIslandAssets.blocks()) {
                var registered = GingerIslandBlocks.get(asset.id());
                if (!(registered instanceof MapDecorStaticBlock block)
                        || registered instanceof GingerIslandStateDecorBlock || BOATS.contains(asset.id())) continue;
                h.assertTrue(block.asItem() instanceof BlockItem, "Missing registered BlockItem: " + asset.id());
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    face(player, facing);
                    ItemStack stack = new ItemStack(block, 3);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    BlockPos main = bottom.above(block.placementAnchorYOffset());
                    Set<BlockPos> cells = block.placementPositions(main, facing);
                    assertInside(h, cells);
                    h.assertTrue(((BlockItem) stack.getItem()).place(new BlockPlaceContext(groundContext(player, bottom)))
                                    .consumesAction(), "Actual BlockItem rejects " + asset.id() + " / " + facing);
                    assertComplete(h, block, main, facing);
                    h.assertTrue(stack.getCount() == 2, "Placement did not consume exactly one: " + asset.id());
                    for (BlockPos cell : cells) {
                        h.assertTrue(cell.getY() >= bottom.getY(), "Authored footprint cut into foundation: " + asset.id());
                    }
                    h.assertTrue(level.getBlockState(bottom.below()).is(Blocks.STONE), "Foundation was overwritten");
                    MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
                    for (BlockPos cell : cells) h.assertTrue(level.isEmptyBlock(cell), "Removal left a part: " + asset.id());
                    checked++;
                }
            }
        } finally {
            player.getInventory().clearContent();
        }
        h.assertTrue(checked == 424, "Static prop audit accidentally skipped the catalog: " + checked);
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void everyBuildingChoiceHandPlacesAndKeepsItsSelectionInAllDirections(GameTestHelper h) {
        flatFoundation(h);
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(bottom.north(25)));
        int checked = 0;
        int owners = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!(GingerIslandBlocks.get(asset.id()) instanceof GingerIslandStateDecorBlock block)
                    || BOATS.contains(asset.id())) continue;
            owners++;
            for (String value : block.buildingStateValues()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    face(player, facing);
                    ItemStack hand = block.hasBuildingVariants() ? block.stackForVisualState(value) : new ItemStack(block);
                    hand.setCount(2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    BlockPos main = bottom.above(block.placementAnchorYOffset());
                    assertInside(h, block.placementPositions(main, facing));
                    h.assertTrue(hand.getItem().useOn(groundContext(player, bottom)).consumesAction(),
                            "Building choice cannot be placed: " + asset.id() + " / " + value + " / " + facing);
                    assertComplete(h, block, main, facing);
                    String actualModel = block.modelForState(level.getBlockState(main));
                    h.assertTrue(actualModel.equals(asset.state_models().get(value)), "State placement used another model");
                    var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(main), level, main, level.getBlockEntity(main));
                    h.assertTrue(drops.size() == 1, "State owner duplicated or lost its drop");
                    if (block.hasBuildingVariants()) {
                        var dropped = drops.getFirst().get(DataComponents.BLOCK_STATE);
                        h.assertTrue(dropped != null && value.equals(dropped.properties().get(asset.state_property())),
                                "Building drop forgot its visual choice");
                    } else {
                        h.assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(block), drops.getFirst()),
                                "Functional process became a stateful held item");
                    }
                    MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
                    checked++;
                }
            }
        }
        h.assertTrue(owners == 25 && checked == 208, "Building placement audit missed choices: " + owners + " / " + checked);
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void boatMotionIsSubtleAndContinuousAcrossFractionalFramesAndTickWraps(GameTestHelper h) {
        BlockPos anchor = new BlockPos(31, 64, -27);
        var previous = com.stardew.craft.blockentity.WillyBoatBlockEntity.sampleMotion(0, 0, anchor);
        double largestMovement = 0;
        for (int frame = 1; frame <= 2000; frame++) {
            var motion = com.stardew.craft.blockentity.WillyBoatBlockEntity.sampleMotion(frame / 4, (frame % 4) / 4F, anchor);
            h.assertTrue(Double.isFinite(motion.heave()) && Float.isFinite(motion.rollDegrees())
                    && Math.abs(motion.heave()) <= .010001 && Math.abs(motion.rollDegrees()) <= .150001,
                    "Boat motion exceeds the requested slight float");
            double difference = Math.abs(motion.heave() - previous.heave());
            h.assertTrue(difference < .000106 && Math.abs(motion.rollDegrees() - previous.rollDegrees()) < .00108,
                    "Boat animation jumps at a frame or periodic tick boundary");
            largestMovement = Math.max(largestMovement, difference);
            previous = motion;
        }
        h.assertTrue(largestMovement > .00005, "Boat animation is frozen");
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void raisedAnchorAndExtensionsRejectSolidsWithoutSidewaysFallback(GameTestHelper h) {
        flatFoundation(h);
        var level = h.getLevel();
        var block = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_resort_umbrella_coral");
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        BlockPos main = bottom.above(block.placementAnchorYOffset());
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        face(player, Direction.NORTH);
        player.setPos(Vec3.atBottomCenterOf(bottom.north(10)));
        ItemStack stack = new ItemStack(block, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Set<BlockPos> cells = block.placementPositions(main, Direction.NORTH);
        h.assertTrue(block.placementAnchorYOffset() > 0, "Umbrella no longer exercises a negative modeling pivot");
        level.setBlock(main, Blocks.STONE.defaultBlockState(), 18);
        h.assertTrue(!stack.getItem().useOn(groundContext(player, bottom)).consumesAction(),
                "Occupied raised MAIN caused adjacent placement instead of refusal");
        h.assertTrue(stack.getCount() == 3 && level.getBlockState(main).is(Blocks.STONE), "Refusal consumed or overwrote");
        for (BlockPos cell : cells) if (!cell.equals(main)) h.assertTrue(level.isEmptyBlock(cell), "Partial rejected umbrella");
        level.setBlock(main, Blocks.AIR.defaultBlockState(), 18);
        BlockPos obstruction = cells.stream().filter(p -> !p.equals(main)).findFirst().orElseThrow();
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 18);
        h.assertTrue(!stack.getItem().useOn(groundContext(player, bottom)).consumesAction(), "Occupied extension was ignored");
        h.assertTrue(stack.getCount() == 3 && level.isEmptyBlock(main)
                && level.getBlockState(obstruction).is(Blocks.STONE), "Extension refusal changed the world or hand");
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void blockingEntityIsNotBypassedAndCanceledPlacementRollsBackRaisedMain(GameTestHelper h) {
        flatFoundation(h);
        var level = h.getLevel();
        var block = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_resort_umbrella_coral");
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        BlockPos main = bottom.above(block.placementAnchorYOffset());
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        face(player, Direction.NORTH);
        player.setPos(Vec3.atBottomCenterOf(bottom.north(10)));
        ItemStack stack = new ItemStack(block, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ArmorStand blocker = new ArmorStand(level, bottom.getX() + .5, bottom.getY(), bottom.getZ() + .5);
        h.assertTrue(level.addFreshEntity(blocker), "Entity blocker was not registered");
        try {
            h.assertTrue(!stack.getItem().useOn(groundContext(player, bottom)).consumesAction(), "Entity collision was bypassed");
            h.assertTrue(stack.getCount() == 3 && level.isEmptyBlock(main), "Rejected entity placement changed the hand/world");
        } finally {
            blocker.discard();
        }
        boolean[] canceled = {false};
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == player && event.getPlacedBlock().is(block)) {
                canceled[0] = true;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            h.assertTrue(!stack.useOn(groundContext(player, bottom)).consumesAction(), "Canceled placement still succeeded");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        h.assertTrue(canceled[0], "The real ItemStack path did not dispatch a placement event");
        h.assertTrue(stack.getCount() == 3, "Canceled placement consumed the held model");
        for (BlockPos cell : block.placementPositions(main, Direction.NORTH)) {
            h.assertTrue(level.isEmptyBlock(cell) && level.getBlockEntity(cell) == null, "Cancellation left MAIN/EXT/BE");
        }
        h.assertTrue(level.getBlockState(bottom.below()).is(Blocks.STONE), "Cancellation removed the foundation");
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 200)
    public static void bothBoatsUseRealWaterHandPlacementAllFacingsAndRestoreEveryFluidCell(GameTestHelper h) {
        waterBasin(h);
        var level = h.getLevel();
        BlockPos waterHit = h.absolutePos(new BlockPos(40, 8, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(waterHit.north(4).above()));
        h.assertTrue(level.addFreshEntity(player), "Placing player must be present in collision queries");
        try {
            for (String id : BOATS) {
                var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
                int worldStates = id.equals("ginger_willy_boat") ? 2 : 1;
                for (int worldState = 0; worldState < worldStates; worldState++) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        Direction heading = facing.getOpposite();
                        face(player, facing);
                        player.setPos(Vec3.atBottomCenterOf(waterHit.relative(heading.getOpposite(), 4).above()));
                        ItemStack stack = boatStack(block, 3);
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                        BlockPos main = waterHit.above().relative(heading, block.waterPlacementForwardOffset());
                        Set<BlockPos> cells = block.placementPositions(main, facing);
                        assertInside(h, cells);
                        Map<BlockPos, BlockState> before = snapshot(h, cells);
                        h.assertTrue(stack.getItem().useOn(waterContext(player, waterHit)).consumesAction(),
                                "Water hand placement failed: " + id + " / " + worldState + " / " + facing);
                        assertComplete(h, block, main, facing);
                        h.assertTrue(main.getY() == waterHit.getY() + 1 && stack.getCount() == 2,
                                "Boat was lifted by its keel or not consumed");
                        int entityCount = 0;
                        int wet = 0;
                        for (BlockPos cell : cells) {
                            BlockState placed = level.getBlockState(cell);
                            boolean wasWater = before.get(cell).getFluidState().is(Fluids.WATER);
                            h.assertTrue(placed.getValue(BlockStateProperties.WATERLOGGED) == wasWater,
                                    "Cell waterlogging differs from displaced fluid: " + cell);
                            if (wasWater) wet++;
                            if (level.getBlockEntity(cell) != null) {
                                entityCount++;
                                h.assertTrue(cell.equals(main), "Boat extension created a render/logic BE");
                            }
                        }
                        h.assertTrue(wet > 0, "Test did not exercise underwater keel cells");
                        h.assertTrue(entityCount == (id.equals("ginger_willy_boat") ? 1 : 0), "Boat has wrong BE owner count");
                        if (id.equals("ginger_willy_boat")) {
                            h.assertTrue(!level.getBlockState(main).getValue(GingerIslandStateDecorBlock.REPAIRED),
                                    "Plain hand item bypassed the boat repair process");
                            if (worldState == 1) {
                                level.setBlock(main, level.getBlockState(main)
                                        .setValue(GingerIslandStateDecorBlock.REPAIRED, true), 2 | 16);
                                h.assertTrue(level.getBlockState(main).getValue(GingerIslandStateDecorBlock.REPAIRED),
                                        "World repair transition did not retain the existing boat owner");
                                assertComplete(h, block, main, facing);
                            }
                        }
                        BlockPos broken = facing == Direction.NORTH || facing == Direction.EAST ? main
                                : cells.stream().filter(p -> !p.equals(main) && before.get(p).getFluidState().is(Fluids.WATER))
                                    .findFirst().orElseThrow();
                        destroyFrom(h, block, broken, player);
                        for (var entry : before.entrySet()) {
                            h.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                                    "MAIN/EXT destruction left a water hole or stray cell: " + id + " / " + entry.getKey());
                            h.assertTrue(level.getBlockEntity(entry.getKey()) == null, "Destroyed boat kept a BE");
                        }
                    }
                }
            }
        } finally {
            player.discard();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 200)
    public static void bothBoatsStillAnchorTheirWholeKeelAboveLand(GameTestHelper h) {
        flatFoundation(h);
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(bottom.north(20)));
        for (String id : BOATS) {
            var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                face(player, facing);
                ItemStack stack = boatStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                BlockPos main = bottom.above(block.placementAnchorYOffset());
                Set<BlockPos> cells = block.placementPositions(main, facing);
                assertInside(h, cells);
                h.assertTrue(stack.getItem().useOn(groundContext(player, bottom)).consumesAction(), "Boat land placement failed");
                assertComplete(h, block, main, facing);
                for (BlockPos cell : cells) h.assertTrue(cell.getY() >= bottom.getY()
                        && !level.getBlockState(cell).getValue(BlockStateProperties.WATERLOGGED), "Land keel invaded ground/fluid");
                MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
                for (BlockPos cell : cells) h.assertTrue(level.isEmptyBlock(cell), "Land boat cleanup left an extension");
                h.assertTrue(level.getBlockState(bottom.below()).is(Blocks.STONE), "Land boat ate its foundation");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 200)
    public static void normalWaterRayUsePlacesBothBoatsWithinReachOfThePlayer(GameTestHelper h) {
        waterBasin(h);
        var level = h.getLevel();
        BlockPos waterHit = h.absolutePos(new BlockPos(40, 8, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(waterHit.north(3).above()));
        player.setYRot(0);
        double surface = waterHit.getY() + level.getFluidState(waterHit).getHeight(level, waterHit);
        player.setXRot((float) Math.toDegrees(Math.atan2(player.getEyeY() - surface, 3)));
        h.assertTrue(level.addFreshEntity(player), "POV player was not added for entity collision");
        try {
            for (String id : BOATS) {
                var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
                ItemStack stack = boatStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                h.assertTrue(stack.getItem().use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                        "Normal right click cannot aim at source water: " + id);
                BlockPos main = waterHit.above().south(block.waterPlacementForwardOffset());
                assertComplete(h, block, main, Direction.NORTH);
                h.assertTrue(stack.getCount() == 1, "POV use did not consume exactly one boat");
                destroyFrom(h, block, main, player);
            }
        } finally {
            player.discard();
        }
        h.succeed();
    }

    private static void face(Player player, Direction facing) {
        player.setYRot(facing.getOpposite().toYRot());
        player.setXRot(0);
    }

    private static UseOnContext groundContext(Player player, BlockPos bottom) {
        BlockPos support = bottom.below();
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false));
    }

    private static UseOnContext waterContext(Player player, BlockPos water) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(water).add(0, .5, 0), Direction.UP, water, false));
    }

    private static ItemStack boatStack(MapDecorStaticBlock block, int count) {
        return new ItemStack(block, count);
    }

    private static void assertComplete(GameTestHelper h, MapDecorStaticBlock block, BlockPos main, Direction facing) {
        var level = h.getLevel();
        for (BlockPos cell : block.placementPositions(main, facing)) {
            BlockState state = level.getBlockState(cell);
            h.assertTrue(state.is(block) && state.getValue(MapDecorStaticBlock.FACING) == facing
                            && state.getValue(MapDecorStaticBlock.PART) == (cell.equals(main)
                                ? MapDecorStaticBlock.Part.MAIN : MapDecorStaticBlock.Part.EXTENSION),
                    "Registered item left incomplete/incorrect parts: " + block + " / " + cell);
            h.assertTrue(main.equals(block.findMainPos(level, cell, state)), "Part resolves to another or no MAIN");
        }
    }

    private static Map<BlockPos, BlockState> snapshot(GameTestHelper h, Set<BlockPos> cells) {
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        for (BlockPos cell : cells) before.put(cell, h.getLevel().getBlockState(cell));
        return before;
    }

    private static void destroyFrom(GameTestHelper h, MapDecorStaticBlock block, BlockPos cell, Player player) {
        var level = h.getLevel();
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            BlockState state = level.getBlockState(cell);
            block.playerWillDestroy(level, cell, state, player);
            level.removeBlock(cell, false);
        });
    }

    private static void assertInside(GameTestHelper h, Set<BlockPos> cells) {
        for (BlockPos cell : cells) {
            h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(cell)),
                    "Placement test escaped its actual structure bounds: " + cell);
        }
    }

    private static void flatFoundation(GameTestHelper h) {
        var level = h.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(10, 3, 10, 69, 23, 69)) {
            level.setBlock(h.absolutePos(relative), relative.getY() == 3
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
    }

    private static void waterBasin(GameTestHelper h) {
        var level = h.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(10, 3, 10, 69, 23, 69)) {
            level.setBlock(h.absolutePos(relative), relative.getY() == 3 ? Blocks.STONE.defaultBlockState()
                    : relative.getY() <= 8 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
    }
}
