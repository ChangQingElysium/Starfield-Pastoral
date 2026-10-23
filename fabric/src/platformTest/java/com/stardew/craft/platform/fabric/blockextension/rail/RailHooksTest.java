package com.stardew.craft.platform.fabric.blockextension.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RailHooksTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void flatMineRailsRejectCurvesAndAscentsAndKeepTheOriginalFallbackDirection() throws Exception {
        var rail = flatRail();
        assertFalse(rail.canMakeSlopes(null, null, BlockPos.ZERO)); assertFalse(rail.isFlexibleRail(null, null, BlockPos.ZERO));
        for (RailShape shape : RailShape.values()) assertEquals(shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST, RailHooks.isValid(rail, shape));
        assertEquals(RailShape.EAST_WEST, RailHooks.placementShape(rail, RailShape.ASCENDING_NORTH, RailShape.EAST_WEST));
        assertEquals(RailShape.NORTH_SOUTH, RailHooks.placementShape(rail, RailShape.NORTH_SOUTH, RailShape.EAST_WEST));
    }
    @Test void theDirectionAdapterReadsTheNativeRailPropertyWithoutChangingTheQueriedState() throws Exception {
        var rail = flatRail();
        var state = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST);
        assertEquals(RailShape.EAST_WEST, RailHooks.getRailDirection(rail, state, null, BlockPos.ZERO, null));
        assertSame(Blocks.RAIL, state.getBlock()); assertEquals(RailShape.EAST_WEST, state.getValue(RailBlock.SHAPE));
    }
    @Test void ordinaryRailDirectionsAndNativeConnectionGeometryRemainAvailable() {
        var rail = (RailBlock) Blocks.RAIL; var pos = new BlockPos(8, 10, -7);
        for (var shape : RailShape.values()) assertTrue(RailHooks.isValid(rail, shape));
        assertEquals(List.of(pos.north(), pos.south()), new RailState(null, pos, rail.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH)).getConnections());
        assertEquals(List.of(pos.west(), pos.east()), new RailState(null, pos, rail.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST)).getConnections());
    }
    private static FlatRail flatRail() throws Exception { var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return (FlatRail) ((sun.misc.Unsafe) field.get(null)).allocateInstance(FlatRail.class); }
    private static final class FlatRail extends RailBlock implements RailBehaviorProvider {
        FlatRail() { super(Properties.of()); }
        @Override public boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) { return false; }
        @Override public boolean isFlexibleRail(BlockState state, BlockGetter level, BlockPos pos) { return false; }
        @Override public boolean isValidRailShape(RailShape shape) { return shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST; }
    }
}
