package com.stardew.craft.gametest;

import com.google.gson.JsonParser;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.mastery.StatueOfBlessingsBlock;
import com.stardew.craft.model.ModelGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModelPipelineGameTests {
    private ModelPipelineGameTests() {}

    @GameTest(batch = "model_pipeline", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 160)
    public static void streetLampIsGroundedAndOnlyItsHeadEmitsLight(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var block = (MapDecorStaticBlock) ModBlocks.STREET_LAMP.get();
        var state = block.defaultBlockState();
        level.setBlock(origin, state, 3);
        level.setBlock(origin.above(2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!block.placeExtensions(level, origin, state), "Blocked lamp head must prevent placement");
        helper.assertTrue(!level.getBlockState(origin.above()).is(block), "Failed placement left a pole extension");
        level.removeBlock(origin.above(2), false);
        helper.assertTrue(block.placeExtensions(level, origin, state), "Three lamp cells should fit");
        var whole = state.getShape(level, origin);
        helper.assertTrue(whole.bounds().minY == 0 && whole.bounds().maxY == 47.5 / 16,
            "Lamp base must sit on the ground with its full three-block height above it");
        for (int y = 0; y < 3; y++) {
            BlockPos cell = origin.above(y);
            var part = level.getBlockState(cell);
            helper.assertTrue(part.is(block), "Lamp must reserve all three cells");
            helper.assertTrue(part.getLightEmission(level, cell) == (y == 2 ? 15 : 0), "Only the head may be a light source");
            helper.assertTrue(!Shapes.joinIsNotEmpty(whole, part.getShape(level, cell).move(0, y, 0), BooleanOp.NOT_SAME),
                "Lamp outline must stay whole when targeting any section");
        }
        helper.assertTrue(!level.getBlockState(origin.below()).is(block) && !level.getBlockState(origin.above(3)).is(block),
            "Lamp must occupy exactly three vertical cells");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, origin.above(2)) == 15,
                "Head must actually illuminate the world");
            helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, origin.above(2).east()) >= 14,
                "Lamp light must spread to the surrounding area");
        }).thenExecute(() -> {
            level.destroyBlock(origin.above(), true);
            for (int y = 0; y < 3; y++) helper.assertTrue(!level.getBlockState(origin.above(y)).is(block), "Broken pole left lamp cells behind");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(origin).inflate(3), item -> item.getItem().is(block.asItem()));
            helper.assertTrue(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1, "Broken lamp must drop exactly once");
        }).thenSucceed();
    }

    @GameTest(batch = "model_pipeline", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void allStatueCellsHaveOneWorldShapeAndCleanTogether(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        for (Block block : new Block[]{ModBlocks.STATUE_OF_BLESSINGS.get(), ModBlocks.STATUE_OF_DWARF_KING.get(), ModBlocks.UNCERTAINTY_STATUE.get(), ModBlocks.SHRINE.get(), ModBlocks.PILLAR.get(),
                ModBlocks.BOOKSHELF_TALL_1.get(), ModBlocks.BOOKSHELF_TALL_2.get()}) {
            var decor = (MapDecorStaticBlock) block;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
                level.setBlock(origin, state, 2 | 16);
                helper.assertTrue(decor.placeExtensions(level, origin, state), "Statue footprint should fit");
                var whole = state.getShape(level, origin);
                helper.assertTrue(!whole.isEmpty(), "Statue collision cannot be empty");
                if (block == ModBlocks.UNCERTAINTY_STATUE.get()) {
                    helper.assertTrue(level.getBlockState(origin.relative(facing.getCounterClockWise())).is(block),
                        "Uncertainty statue extension must rotate clockwise with the model");
                }
                BlockPos lastExtension = null;
                for (BlockPos cell : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(2, 3, 2))) {
                    var part = level.getBlockState(cell);
                    if (!part.is(block)) continue;
                    var shifted = part.getShape(level, cell).move(cell.getX() - origin.getX(), cell.getY() - origin.getY(), cell.getZ() - origin.getZ());
                    helper.assertTrue(!Shapes.joinIsNotEmpty(whole, shifted, BooleanOp.NOT_SAME), "Outline changed between main and extension");
                    helper.assertTrue(!Shapes.joinIsNotEmpty(part.getShape(level, cell), part.getCollisionShape(level, cell, CollisionContext.empty()), BooleanOp.NOT_SAME), "Outline/collision diverged");
                    helper.assertTrue(origin.equals(decor.findMainPos(level, cell, part)), "Extension lost its owner");
                    if (!cell.equals(origin)) lastExtension = cell.immutable();
                }
                helper.assertTrue(lastExtension != null, "Tall statue must have an extension");
                if (block instanceof StatueOfBlessingsBlock) {
                    StatueOfBlessingsBlock.setActivated(level, origin, state, true);
                    helper.assertTrue(!Shapes.joinIsNotEmpty(whole, level.getBlockState(origin).getShape(level, origin), BooleanOp.NOT_SAME), "Activation must preserve collision");
                    helper.assertTrue(level.getBlockState(origin.above()).getValue(StatueOfBlessingsBlock.ACTIVATED), "Activation must reach the extension");
                }
                MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(origin, false));
                for (BlockPos cell : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(2, 3, 2))) {
                    helper.assertTrue(!level.getBlockState(cell).is(block), "Orphan extension after main removal");
                }
                // Repeat with the extension removed first, including the multi-column statue.
                level.setBlock(origin, state, 2 | 16);
                decor.placeExtensions(level, origin, state);
                BlockPos remove = lastExtension;
                MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(remove, false));
                helper.assertTrue(!level.getBlockState(origin).is(block), "Extension removal must remove main");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "model_pipeline", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void blockedExtensionPlacementDoesNotOverwriteOrPartiallyPlace(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var block = (MapDecorStaticBlock) ModBlocks.UNCERTAINTY_STATUE.get();
        var state = block.defaultBlockState();
        level.setBlock(origin, state, 2 | 16);
        level.setBlock(origin.above(), Blocks.STONE.defaultBlockState(), 2 | 16);
        helper.assertTrue(!block.placeExtensions(level, origin, state), "Obstructed placement must fail");
        helper.assertTrue(level.getBlockState(origin.above()).is(Blocks.STONE), "Obstacle was overwritten");
        helper.assertTrue(!level.getBlockState(origin.south()).is(block), "Partial extension was left behind");
        level.removeBlock(origin, false);
        helper.succeed();
    }

    @GameTest(batch = "model_pipeline", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void genericRotationAndVoxelShapeUseTheSameCoordinates(GameTestHelper helper) {
        var cubes = JsonParser.parseString("""
            [{"from":[0,0,0],"to":[16,8,4],
              "transform":[0,0,-1,0,0,1,0,0,1,0,0,0,4,0,16,1]}]
            """).getAsJsonArray();
        var aabb = ModelGeometry.shape(cubes, false);
        var voxel = ModelGeometry.shape(cubes, true);
        var expected = Block.box(4, 0, 0, 8, 8, 16);
        helper.assertTrue(!Shapes.joinIsNotEmpty(expected, aabb, BooleanOp.NOT_SAME), "Generic matrix bounds mismatch");
        helper.assertTrue(!Shapes.joinIsNotEmpty(expected, voxel, BooleanOp.NOT_SAME), "Voxel and generic renderer coordinates diverged");
        helper.succeed();
    }
    @GameTest(batch = "model_pipeline", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void extensionDropsExactlyOnceAndCreativeDropsNothing(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        var block = (MapDecorStaticBlock) ModBlocks.STATUE_OF_DWARF_KING.get();
        var state = block.defaultBlockState();
        level.setBlock(origin, state, 2 | 16);
        block.placeExtensions(level, origin, state);
        level.destroyBlock(origin.above(), true);
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(origin).inflate(3), item -> item.getItem().is(block.asItem()));
        helper.assertTrue(items.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1, "Extension destruction must drop exactly one statue");
        items.forEach(net.minecraft.world.entity.Entity::discard);
        level.setBlock(origin, state, 2 | 16);
        block.placeExtensions(level, origin, state);
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        var gameModeBefore = player.gameMode.getGameModeForPlayer();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            block.playerWillDestroy(level, origin.above(), level.getBlockState(origin.above()), player);
            level.removeBlock(origin.above(), false);
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(origin).inflate(3), item -> item.getItem().is(block.asItem())).isEmpty(), "Creative destruction must not drop a statue");
        } finally {
            player.setGameMode(gameModeBefore);
        }
        helper.succeed();
    }

}
