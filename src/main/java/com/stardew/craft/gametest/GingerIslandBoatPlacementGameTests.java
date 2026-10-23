package com.stardew.craft.gametest;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.WillyBoatBlockEntity;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
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
    public static void futureNorthBoatAtZ77HasWalkableBoardingFromTheUnchangedSourcePier(GameTestHelper h) {
        basin(h, 5);
        var level = h.getLevel();
        BlockPos main = h.absolutePos(new BlockPos(40, 9, 40));
        var pierBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("stardewcraft", "oak_planks"));
        h.assertTrue(pierBlock != Blocks.AIR, "Use the project's actual source-pier material");
        Set<BlockPos> pier = new LinkedHashSet<>();
        // Same relative positions as MAIN=(30,64,77), pier=(34..36,64,70..80).
        for (int x = 4; x <= 6; x++) {
            for (int z = -7; z <= 3; z++) {
                BlockPos cell = main.offset(x, 0, z);
                pier.add(cell);
                level.setBlock(cell, pierBlock.defaultBlockState(), 2 | 16);
            }
        }
        var block = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_willy_boat");
        var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, Direction.NORTH)
                .setValue(GingerIslandStateDecorBlock.REPAIRED, true);
        h.assertTrue(block.placementPositions(main, Direction.NORTH).stream().noneMatch(pier::contains),
                "The new boat must retain all 33 existing source-pier floor cells");
        level.setBlock(main, state, 2 | 16);
        h.assertTrue(block.placeExtensions(level, main, state), "Future source-pier boat failed its actual footprint");
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(level.addFreshEntity(player), "Boarding player must participate in real world collisions");
        try {
            player.setPos(main.getX() + 5, main.getY() + 1.05, main.getZ() + 43D / 16);
            player.move(MoverType.SELF, new Vec3(0, -.1, 0));
            for (int step = 0; step < 18; step++) {
                player.move(MoverType.SELF, new Vec3(-.25, -.1, 0));
                h.assertTrue(Math.abs(player.getY() - (main.getY() + 1)) < .01,
                        "Player fell into a gap or required a one-block jump at boarding step " + step);
            }
            h.assertTrue(Math.abs(player.getX() - (main.getX() + .5)) < .001 && player.onGround(),
                    "The real player could not walk from source pier through the gate onto the deck");
            for (BlockPos cell : pier)
                h.assertTrue(level.getBlockState(cell).is(pierBlock), "Boarding construction changed a source-pier floor cell");
        } finally {
            player.discard();
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.setBlock(main, Blocks.AIR.defaultBlockState(), 2 | 16));
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void dismantlingOldWillyClearsLegacyCellsButKeepsOtherOwnersAndBEs(GameTestHelper h) {
        assertLegacyDismantling(h, "ginger_willy_boat", 328, 4);
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void dismantlingOldShipwreckClearsLegacyCellsButKeepsOtherOwners(GameTestHelper h) {
        assertLegacyDismantling(h, "ginger_island_shipwreck", 1722, 15);
        h.succeed();
    }

    private static void assertLegacyDismantling(GameTestHelper h, String id, int expectedCells, int otherX) {
        var level = h.getLevel();
        var block = (MapDecorStaticBlock) GingerIslandBlocks.get(id);
        BlockPos main = h.absolutePos(new BlockPos(40, 8, 40));
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> oldCells = legacyCells(id, main, facing);
            h.assertTrue(oldCells.size() == expectedCells, "Fixture must use the actual pre-correction boat footprint");
            BlockPos otherMain = main.offset(new BlockPos(otherX, 0, 0).rotate(rotation(facing)));
            Set<BlockPos> otherCells = new LinkedHashSet<>(block.placementPositions(otherMain, facing));
            // A surviving neighbor can still own pre-correction extensions.
            // Install and verify its entire legacy/current union, rather than
            // calling correctly protected old cells ghosts of the removed ship.
            otherCells.addAll(legacyCells(id, otherMain, facing));
            h.assertTrue(!oldCells.contains(otherMain) && otherCells.stream().anyMatch(oldCells::contains),
                    "Neighbor fixture must overlap the old footprint without replacing its MAIN");
            h.assertTrue(!otherCells.contains(main), "Neighbor fixture cannot overwrite the dismantled MAIN");
            BlockPos foreign = oldCells.stream().filter(p -> !p.equals(main) && p.getY() >= main.getY() + 1
                    && !otherCells.contains(p)).findFirst().orElseThrow();
            BlockPos unknownBe = id.equals("ginger_willy_boat") ? oldCells.stream().filter(p -> !p.equals(main)
                    && !p.equals(foreign) && p.getY() >= main.getY() + 1 && !otherCells.contains(p))
                    .findFirst().orElseThrow() : null;
            BlockPos wrongFacing = oldCells.stream().filter(p -> !p.equals(main) && !p.equals(foreign)
                    && !p.equals(unknownBe) && p.getY() >= main.getY() + 1 && !otherCells.contains(p))
                    .findFirst().orElseThrow();
            var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
            if (state.hasProperty(GingerIslandStateDecorBlock.REPAIRED))
                state = state.setValue(GingerIslandStateDecorBlock.REPAIRED, true);
            for (BlockPos cell : oldCells) {
                if (cell.equals(foreign)) {
                    level.setBlock(cell, Blocks.STONE.defaultBlockState(), 2 | 16);
                } else {
                    var cellState = cell.equals(main) ? state : state.setValue(MapDecorStaticBlock.PART,
                            MapDecorStaticBlock.Part.EXTENSION);
                    if (!cell.equals(main)) cellState = cellState.setValue(BlockStateProperties.WATERLOGGED,
                            cell.getY() < main.getY());
                    if (cell.equals(wrongFacing)) cellState = cellState.setValue(MapDecorStaticBlock.FACING,
                            facing.getOpposite());
                    level.setBlock(cell, cellState, 2 | 16);
                }
            }
            if (unknownBe != null)
                level.setBlockEntity(new WillyBoatBlockEntity(unknownBe, level.getBlockState(unknownBe)));
            var retainedBe = unknownBe == null ? null : level.getBlockEntity(unknownBe);
            var wrongState = level.getBlockState(wrongFacing);
            for (BlockPos cell : otherCells) {
                var cellState = cell.equals(otherMain) ? state : state.setValue(MapDecorStaticBlock.PART,
                        MapDecorStaticBlock.Part.EXTENSION).setValue(BlockStateProperties.WATERLOGGED,
                        cell.getY() < main.getY());
                level.setBlock(cell, cellState, 2 | 16);
            }
            Map<BlockPos, BlockState> otherBefore = snapshot(h, otherCells);
            var otherBe = level.getBlockEntity(otherMain);
            h.assertTrue(level.destroyBlock(main, true, player), "Old boat MAIN must actually be dismantled");
            h.assertTrue(level.getBlockEntity(main) == null, "Old removed boat kept its render BE");
            for (BlockPos cell : oldCells) {
                if (otherCells.contains(cell)) continue;
                if (cell.equals(foreign)) {
                    h.assertTrue(level.getBlockState(cell).is(Blocks.STONE), "Legacy removal destroyed foreign construction");
                } else if (cell.equals(unknownBe)) {
                    h.assertTrue(level.getBlockEntity(cell) == retainedBe && level.getBlockState(cell).is(block),
                            "Legacy removal deleted an unknown extension BE");
                } else if (cell.equals(wrongFacing)) {
                    h.assertTrue(level.getBlockState(cell).equals(wrongState), "Legacy removal deleted a different-facing cell");
                } else if (cell.getY() < main.getY()) {
                    h.assertTrue(level.getBlockState(cell).is(Blocks.WATER) && level.getFluidState(cell).isSource(),
                            "Legacy underwater cell did not restore its source: " + cell);
                } else {
                    h.assertTrue(level.getBlockState(cell).isAir(), "Legacy boat left a ghost EXT: " + cell);
                }
            }
            for (var entry : otherBefore.entrySet()) {
                var actual = level.getBlockState(entry.getKey());
                h.assertTrue(actual.equals(entry.getValue()), "Legacy removal changed neighboring boat at "
                        + entry.getKey() + ": expected " + entry.getValue() + ", actual " + actual);
            }
            h.assertTrue(level.getBlockEntity(otherMain) == otherBe,
                    "Neighbor's MAIN BE changed at " + otherMain + ": expected " + otherBe
                            + ", actual " + level.getBlockEntity(otherMain));
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(main).inflate(35),
                    e -> e.getItem().getItem() == block.asItem());
            h.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                    "Dismantling legacy MAIN must drop exactly one boat");
            drops.forEach(ItemEntity::discard);
            Set<BlockPos> all = new LinkedHashSet<>(oldCells); all.addAll(otherCells);
            MapDecorStaticBlock.runWithDropsSuppressed(() -> {
                level.setBlock(otherMain, Blocks.AIR.defaultBlockState(), 2 | 16);
                for (BlockPos cell : all) {
                    level.removeBlockEntity(cell);
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            });
        }
    }

    private static Set<BlockPos> legacyCells(String id, BlockPos main, Direction facing) {
        try (var stream = GingerIslandBoatPlacementGameTests.class.getResourceAsStream(
                "/data/stardewcraft/ginger_island/legacy_boat_footprints.json")) {
            if (stream == null) throw new IllegalStateException("Legacy footprint fixture missing");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<BlockPos> result = new LinkedHashSet<>();
            for (var value : root.getAsJsonObject("boats").getAsJsonObject(id).getAsJsonArray("north_offsets")) {
                var cell = value.getAsJsonArray();
                result.add(main.offset(new BlockPos(cell.get(0).getAsInt(), cell.get(1).getAsInt(),
                        cell.get(2).getAsInt()).rotate(rotation(facing))));
            }
            return result;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load old-profile regression fixture", error);
        }
    }

    private static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void reflectedWillyBoardingSideHasSupportAndARealOpenGateAllFacings(GameTestHelper h) {
        var block = GingerIslandBlocks.get("ginger_willy_boat");
        BlockPos main = h.absolutePos(new BlockPos(40, 8, 40));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing)
                    .setValue(GingerIslandStateDecorBlock.REPAIRED, true);
            var repaired = state.getCollisionShape(h.getLevel(), main);
            // This is the whole swept volume of a standard 0.6 x 1.8 player
            // walking from the new +X plank into the open deck at Z=43.
            AABB walk = rotatePixels(new AABB(3.2, 16.8, 38.2, 60.8, 45.6, 47.8), facing);
            h.assertTrue(repaired.toAabbs().stream().noneMatch(walk::intersects),
                    "The reflected repaired gate/rail seals its boarding path: " + facing);
            var damaged = state.setValue(GingerIslandStateDecorBlock.REPAIRED, false)
                    .getCollisionShape(h.getLevel(), main);
            h.assertTrue(damaged.toAabbs().stream().anyMatch(walk::intersects),
                    "The damaged closed gate no longer blocks the same boarding path: " + facing);

            AABB onNewPlank = rotatePixels(new AABB(51.2, 16.8, 38.2, 60.8, 45.6, 47.8), facing);
            double supported = repaired.collide(Direction.Axis.Y, onNewPlank, -.1);
            h.assertTrue(supported > -.1 && supported < 0,
                    "Mirrored boarding render has no matching +X support surface: " + facing);
            AABB oldPlank = rotatePixels(new AABB(-60.8, 16.8, 38.2, -51.2, 45.6, 47.8), facing);
            h.assertTrue(Math.abs(repaired.collide(Direction.Axis.Y, oldPlank, -.1) + .1) < 1e-7,
                    "An invisible old -X plank collision remains after reflection: " + facing);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = TEMPLATE_NS, template = "large_empty", timeoutTicks = 200)
    public static void turnedShipSectionsKeepTheCaptainEntryOnTheShoreAllFacings(GameTestHelper h) {
        var block = GingerIslandBlocks.get("ginger_island_shipwreck");
        BlockPos main = h.absolutePos(new BlockPos(40, 8, 40));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
            var collision = state.getCollisionShape(h.getLevel(), main);
            // The source threshold after its local reflection and section turn
            // sits at X=-127.24..-101.29, on the NORTH/shore side. This is a
            // standard player sweeping across that real aperture into the hull.
            AABB shoreEntry = rotatePixels(new AABB(-125.8, 22, -79.8, -116.2, 50.8, -20.2), facing);
            h.assertTrue(collision.toAabbs().stream().noneMatch(shoreEntry::intersects),
                    "Section turns sealed the shore captain-room entry: " + facing);
            AABB seaSide = rotatePixels(new AABB(-125.8, 22, 37, -116.2, 50.8, 65), facing);
            h.assertTrue(collision.toAabbs().stream().anyMatch(seaSide::intersects),
                    "The old unreflected portal was cut into the sea-side wall: " + facing);
        }
        h.succeed();
    }

    private static AABB rotatePixels(AABB pixels, Direction facing) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{pixels.minX / 16, pixels.maxX / 16}) {
            for (double z : new double[]{pixels.minZ / 16, pixels.maxZ / 16}) {
                double outX = switch (facing) {
                    case EAST -> 1 - z;
                    case SOUTH -> 1 - x;
                    case WEST -> z;
                    default -> x;
                };
                double outZ = switch (facing) {
                    case EAST -> x;
                    case SOUTH -> 1 - z;
                    case WEST -> 1 - x;
                    default -> z;
                };
                minX = Math.min(minX, outX); maxX = Math.max(maxX, outX);
                minZ = Math.min(minZ, outZ); maxZ = Math.max(maxZ, outZ);
            }
        }
        return new AABB(minX, pixels.minY / 16, minZ, maxX, pixels.maxY / 16, maxZ);
    }

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
        for (var entry : before.entrySet()) {
            var actual = h.getLevel().getBlockState(entry.getKey());
            var actualBe = h.getLevel().getBlockEntity(entry.getKey());
            h.assertTrue(actual.equals(entry.getValue()) && actualBe == null,
                    "Boat removal/refusal changed source water or seabed at " + entry.getKey()
                            + ": expected " + entry.getValue() + " without BE, actual " + actual + ", BE " + actualBe);
        }
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
