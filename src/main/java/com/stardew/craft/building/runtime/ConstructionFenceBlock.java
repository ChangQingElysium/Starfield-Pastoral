package com.stardew.craft.building.runtime;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Internal work-site asset; geometry is the approved straight/corner/end/post model series. */
public final class ConstructionFenceBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    public static final BooleanProperty NOTICE = BooleanProperty.create("notice");
    private final VoxelShape[][] shapes = new VoxelShape[4][];

    public ConstructionFenceBlock(Properties properties) {
        super(properties.noOcclusion().strength(-1, 3_600_000).noLootTable());
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(NOTICE, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        String[] names = {"straight", "corner", "end", "post"};
        for (int i = 0; i < names.length; i++) shapes[i] = ModelVoxelShapeCache.horizontalShapes(
                "stardewcraft:block/construction/construction_fence_" + names[i], Direction.NORTH);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, NOTICE, BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(PART)][ModelVoxelShapeCache.horizontalIndex(state.getValue(BlockStateProperties.HORIZONTAL_FACING))];
    }
}
