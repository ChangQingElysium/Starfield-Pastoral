package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.PineCanopyConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_tree_seasons")
@PrefixGameTestTemplate(false)
public final class WildTreeSeasonGameTests {
    private WildTreeSeasonGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_tree_seasons", template = "ring_utilities")
    public static void snowOnlyCoversExposedPineShelvesAndUpdatesAfterRemoval(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(5, 3, 5));
        var pine = ModBlocks.PINE_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        level.setBlock(pos, pine, 2);
        level.setBlock(pos.above(), pine, 2);
        level.setBlock(pos.east(), pine, 2);
        var covered = PineCanopyConnections.inspect(level::getBlockState, pos);
        h.assertTrue(!covered.snowExposed() && covered.hidden(Direction.UP) && covered.hidden(Direction.EAST),
                "Stacked pine leaves acquired snow or retained an internal face");
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        var shelf = PineCanopyConnections.inspect(level::getBlockState, pos);
        h.assertTrue(shelf.snowExposed() && !shelf.hidden(Direction.UP) && shelf.hidden(Direction.EAST),
                "Newly exposed shelf did not refresh snow");
        h.assertTrue((shelf.snowMask() & 2) != 0, "Shared snow edge not connected");
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
        h.assertTrue(!PineCanopyConnections.inspect(level::getBlockState, pos).snowExposed(), "Snow formed beneath a solid cover");
        level.setBlock(pos.above(), Blocks.WATER.defaultBlockState(), 2);
        h.assertTrue(!PineCanopyConnections.inspect(level::getBlockState, pos).snowExposed(), "Snow formed beneath water");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tree_seasons", template = "ring_utilities")
    public static void pineSnowDistinguishesInnerCornersAndKeepsItsVariation(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(5, 3, 5));
        var pine = ModBlocks.PINE_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (BlockPos tile : new BlockPos[]{pos, pos.north(), pos.east()}) level.setBlock(tile, pine, 2);
        var corner = PineCanopyConnections.inspect(level::getBlockState, pos);
        h.assertTrue(corner.snowMask() == 3, "L-shaped snow shelf lost its inner corner");
        level.setBlock(pos.north().east(), pine, 2);
        var square = PineCanopyConnections.inspect(level::getBlockState, pos);
        h.assertTrue(square.snowMask() == 19, "2x2 shelf retained an inner corner seam");
        h.assertTrue(corner.variant() == square.variant(), "Neighbour placement shuffled the snow texture");
        level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 2);
        var detached = PineCanopyConnections.inspect(level::getBlockState, pos);
        h.assertTrue(detached.snowMask() == 1, "Detached diagonal falsely bridged a missing neighbour");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tree_seasons", template = "ring_utilities")
    public static void winterDormancyReturnsInSpringWithoutChangingBlocks(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimension = Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        var oldDimension = dimension.get(level);
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        try {
            dimension.set(level, ModDimensions.STARDEW_VALLEY);
            int x = 2;
            for (var tree : WildTrees.ALL) {
                var pos = h.absolutePos(new BlockPos(x++, 3, 3));
                var state = tree.modernLeaves().get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                level.setBlock(pos, state, 2);
                for (int season : new int[]{0, 1, 2, 3, 0}) {
                    clock.setCurrentSeason(season);
                    com.stardew.craft.tree.SeasonalLeafLighting.refreshChunk(level, level.getChunkAt(pos));
                    var seasonal = level.getBlockState(pos);
                    boolean hidden = season == 3 && tree != WildTrees.PINE && tree != WildTrees.MYSTIC_TREE;
                    h.assertTrue(seasonal.getCollisionShape(level, pos).isEmpty() == hidden,
                            tree.id() + " collision is stale for season " + season);
                    assertLeafLight(h, seasonal, hidden, pos);
                    h.assertTrue(seasonal.getShape(level, pos).isEmpty() == hidden,
                            tree.id() + " selection is stale for season " + season);
                    h.assertTrue(level.getBlockState(pos).setValue(com.stardew.craft.block.tree.StardewLeavesBlock.DORMANT, false).equals(state), "Dormancy changed the saved tree");
                }
            }
            for (var leaf : java.util.List.of(ModBlocks.BLOSSOM_LEAVES, ModBlocks.FINE_LEAVES, ModBlocks.POINTED_LEAVES,
                    ModBlocks.FOREST_LEAVES, ModBlocks.BROADLEAF_LEAVES)) {
                var pos = h.absolutePos(new BlockPos(x++, 3, 5));
                var state = leaf.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                level.setBlock(pos, state, 2);
                for (int season : new int[]{0, 1, 2, 3, 0}) {
                    clock.setCurrentSeason(season);
                    com.stardew.craft.tree.SeasonalLeafLighting.refreshChunk(level, level.getChunkAt(pos));
                    var seasonal = level.getBlockState(pos);
                    boolean hidden = false;
                    h.assertTrue(seasonal.getCollisionShape(level, pos).isEmpty() == hidden,
                            "Decorative leaf collision disagrees with its winter canopy");
                    assertLeafLight(h, seasonal, hidden, pos);
                    h.assertTrue(seasonal.getShape(level, pos).isEmpty() == hidden,
                            "Decorative leaf selection did not return with spring");
                    h.assertTrue(level.getBlockState(pos).setValue(com.stardew.craft.block.tree.StardewLeavesBlock.DORMANT, false).equals(state), "Season change replaced a decorative leaf");
                }
            }
        } finally {
            dimension.set(level, oldDimension);
            clock.setCurrentSeason(oldSeason);
        }
        h.succeed();
    }

    private static void assertLeafLight(GameTestHelper h, net.minecraft.world.level.block.state.BlockState state,
                                        boolean hidden, BlockPos pos) {
        // Chunk mesh builders pass a BlockGetter snapshot, not a Level. That used to retain a full shadow cube.
        var snapshot = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
        h.assertTrue(state.getLightBlock(snapshot, pos) == (hidden ? 0 : 1), "Hidden leaves still absorb light");
        h.assertTrue(state.getShadeBrightness(snapshot, pos) == (hidden ? 1.0F : 0.2F), "Snapshot AO disagrees with leaf visibility");
        h.assertTrue(state.getOcclusionShape(snapshot, pos).isEmpty() == hidden, "Snapshot retained hidden face occlusion");
        h.assertTrue(!hidden || (state.propagatesSkylightDown(snapshot, pos) && !state.isViewBlocking(snapshot, pos)),
                "Dormant leaves block skylight or the view");
    }

    @GameTest(templateNamespace = "stardewcraft_tree_seasons", template = "ring_utilities")
    public static void forestCanopiesArePersistentAndSnowRespectsMixedCover(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        for (var leaf : java.util.List.of(ModBlocks.FOREST_LEAVES, ModBlocks.BROADLEAF_LEAVES)) {
            var state = leaf.get().defaultBlockState();
            h.assertTrue(state.getValue(LeavesBlock.PERSISTENT), "Map-placed forest canopy would decay without logs");
            h.assertTrue(!com.stardew.craft.block.tree.StardewLeavesBlock.losesLeavesInWinter(state), "Forest canopy loses winter leaves");
            h.assertTrue(com.stardew.craft.item.catalog.StardewItemCatalog.tabForItem(leaf.get().asItem())
                    == com.stardew.craft.item.catalog.StardewCatalogTab.NATURE, "Forest canopy missing from Nature tab");
            level.setBlock(pos, state, 2);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            h.assertTrue(com.stardew.craft.tree.ForestCanopySnow.exposed(level, pos), "Open canopy has no snow");
            for (var cover : java.util.List.of(ModBlocks.FOREST_LEAVES.get(), ModBlocks.BROADLEAF_LEAVES.get(),
                    Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.STONE, Blocks.WATER)) {
                level.setBlock(pos.above(), cover.defaultBlockState(), 2);
                h.assertTrue(!com.stardew.craft.tree.ForestCanopySnow.exposed(level, pos), "Snow beneath cover: " + cover);
            }
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            h.assertTrue(com.stardew.craft.tree.ForestCanopySnow.exposed(level, pos), "Removing cover did not expose canopy");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tree_seasons", template = "ring_utilities")
    public static void winterPlacementAndLoadedStatesRefreshWithoutLosingLeaves(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var field = Level.class.getDeclaredField("dimension");
        field.setAccessible(true);
        var originalDimension = field.get(level);
        var clock = StardewTimeManager.get();
        int season = clock.getCurrentSeason();
        var dormant = com.stardew.craft.block.tree.StardewLeavesBlock.DORMANT;
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        try {
            field.set(level, ModDimensions.STARDEW_VALLEY);
            clock.setCurrentSeason(3);
            for (var leaf : java.util.List.of(ModBlocks.OAK_LEAVES, ModBlocks.MAPLE_LEAVES,
                    ModBlocks.MAHOGANY_LEAVES, ModBlocks.OAK_LEAVES_QUESTION, ModBlocks.POINTED_LEAVES)) {
                level.removeBlock(pos, false);
                var base = leaf.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                level.setBlock(pos, base, 2);
                boolean expectedDormant = leaf != ModBlocks.POINTED_LEAVES;
                h.assertTrue(level.getBlockState(pos).getValue(dormant) == expectedDormant,
                        "New winter leaf has the wrong seasonal visibility: " + leaf.getId());
                assertLeafLight(h, level.getBlockState(pos), expectedDormant, pos);
                // Existing block, stale data from a pre-fix chunk: onPlace does not run for same-block updates.
                level.setBlock(pos, base, 2);
                com.stardew.craft.tree.SeasonalLeafLighting.refreshChunk(level, level.getChunkAt(pos));
                h.assertTrue(level.getBlockState(pos).getValue(dormant) == expectedDormant,
                        "Loaded winter leaf has the wrong seasonal visibility: " + leaf.getId());
                clock.setCurrentSeason(0);
                com.stardew.craft.tree.SeasonalLeafLighting.refreshChunk(level, level.getChunkAt(pos));
                h.assertTrue(level.getBlockState(pos).equals(base), "Spring did not restore saved leaves");
                clock.setCurrentSeason(3);
                com.stardew.craft.tree.SeasonalLeafLighting.refreshChunk(level, level.getChunkAt(pos));
                field.set(level, originalDimension);
                com.stardew.craft.tree.SeasonalLeafLighting.refreshChunk(level, level.getChunkAt(pos));
                h.assertTrue(level.getBlockState(pos).equals(base), "Dormancy leaked into other dimensions");
                field.set(level, ModDimensions.STARDEW_VALLEY);
            }
        } finally {
            field.set(level, originalDimension);
            clock.setCurrentSeason(season);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_tree_seasons", template = "ring_utilities")
    public static void otherDimensionsKeepAllFiveTreeCrowns(GameTestHelper h) {
        var clock = StardewTimeManager.get();
        int oldSeason = clock.getCurrentSeason();
        try {
            clock.setCurrentSeason(3);
            for (var tree : WildTrees.ALL) {
                var state = tree.modernLeaves().get().defaultBlockState();
                h.assertTrue(!state.getCollisionShape(h.getLevel(), h.absolutePos(new BlockPos(3, 3, 3))).isEmpty(),
                        "Winter dormancy leaked outside the valley: " + tree.id());
            }
            for (var leaf : java.util.List.of(ModBlocks.BLOSSOM_LEAVES, ModBlocks.FINE_LEAVES, ModBlocks.POINTED_LEAVES)) {
                h.assertTrue(!leaf.get().defaultBlockState().getCollisionShape(h.getLevel(), BlockPos.ZERO).isEmpty(),
                        "Decorative tree dormancy leaked outside the valley");
            }
            h.assertTrue(ModBlocks.OAK_LEAVES_QUESTION.get() != ModBlocks.OAK_LEAVES.get(), "Legacy decorative leaves aliased");
        } finally {
            clock.setCurrentSeason(oldSeason);
        }
        h.succeed();
    }
}
