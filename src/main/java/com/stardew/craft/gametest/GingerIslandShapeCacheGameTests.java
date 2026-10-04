package com.stardew.craft.gametest;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_ginger_collision")
@PrefixGameTestTemplate(false)
public final class GingerIslandShapeCacheGameTests {
    private GingerIslandShapeCacheGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty")
    public static void stateShapesReuseGeometryAndFollowMainAcrossCacheClears(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = (GingerIslandStateDecorBlock) GingerIslandBlocks.get("ginger_field_office_fossil_display");
        BlockPos main = helper.absolutePos(new BlockPos(10, 3, 10));
        BlockPos extension = main.above();
        // The empty stand, full skeleton and low fossil have deliberately different heights.
        double[] expectedHeights = {1, 58.0778 / 16, 21.0 / 16};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var base = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing)
                    .setValue(GingerIslandStateDecorBlock.VARIANT, 0);
            level.setBlock(main, base, 2 | 16);
            helper.assertTrue(block.placeExtensions(level, main, base), "Fossil union footprint did not fit: " + facing);
            var staleExtension = level.getBlockState(extension);
            helper.assertTrue(staleExtension.is(block)
                    && staleExtension.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION,
                    "Tall fossil state did not reserve its upper cell: " + facing);

            for (int variant = 0; variant <= 2; variant++) {
                var state = base.setValue(GingerIslandStateDecorBlock.VARIANT, variant);
                // Change only MAIN. An extension retaining variant 0 must still resolve its owner's current model.
                level.setBlock(main, state, 2 | 16);
                helper.assertTrue(level.getBlockState(extension).equals(staleExtension),
                        "Fixture unexpectedly updated the extension variant: " + facing + "/" + variant);
                var whole = state.getShape(level, main);
                helper.assertTrue(whole == state.getShape(level, main),
                        "Repeated MAIN query rebuilt its shape: " + facing + "/" + variant);
                helper.assertTrue(Math.abs(whole.max(Direction.Axis.Y) - expectedHeights[variant]) < 1E-6,
                        "MAIN reused another variant's geometry: " + facing + "/" + variant);
                var part = staleExtension.getShape(level, extension);
                helper.assertTrue(part == staleExtension.getShape(level, extension),
                        "Repeated EXT query rebuilt its translated shape: " + facing + "/" + variant);
                helper.assertTrue(!Shapes.joinIsNotEmpty(whole, part.move(0, 1, 0), BooleanOp.NOT_SAME),
                        "Stale EXT variant did not follow MAIN world geometry: " + facing + "/" + variant);

                var source = ModelVoxelShapeCache.shapeFromModelId(block.modelForState(state));
                ModelVoxelShapeCache.clearAll();
                var reloadedSource = ModelVoxelShapeCache.shapeFromModelId(block.modelForState(state));
                helper.assertTrue(source != reloadedSource, "Model-cache clear did not reload the source shape");
                var reloadedWhole = state.getShape(level, main);
                var reloadedPart = staleExtension.getShape(level, extension);
                helper.assertTrue(reloadedWhole != whole && reloadedPart != part,
                        "Oriented/translated cache retained the old model source: " + facing + "/" + variant);
                helper.assertTrue(!Shapes.joinIsNotEmpty(whole, reloadedWhole, BooleanOp.NOT_SAME)
                        && !Shapes.joinIsNotEmpty(reloadedWhole, reloadedPart.move(0, 1, 0), BooleanOp.NOT_SAME),
                        "Cache reload changed geometry or separated EXT from MAIN: " + facing + "/" + variant);
                helper.assertTrue(reloadedWhole == state.getShape(level, main)
                        && reloadedPart == staleExtension.getShape(level, extension),
                        "Reloaded shapes were not cached: " + facing + "/" + variant);
            }
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
            helper.assertTrue(!level.getBlockState(extension).is(block), "Fossil cleanup left an extension behind");
        }
        helper.succeed();
    }
}
