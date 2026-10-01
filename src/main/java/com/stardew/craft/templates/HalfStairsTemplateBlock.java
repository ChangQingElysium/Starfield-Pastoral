package com.stardew.craft.templates;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Handedness is independent of facing and the upside-down placement. */
public final class HalfStairsTemplateBlock extends MaterialTemplateBlock {
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    public HalfStairsTemplateBlock(Properties properties) {
        super(TemplateShape.HALF_STAIRS, properties);
        registerDefaultState(defaultBlockState().setValue(MIRRORED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MIRRORED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction left = state.getValue(FACING).getCounterClockWise();
        double x = context.getClickLocation().x - context.getClickedPos().getX() - 0.5D;
        double z = context.getClickLocation().z - context.getClickedPos().getZ() - 0.5D;
        return state.setValue(MIRRORED, x * left.getStepX() + z * left.getStepZ() > 0D);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state
                : state.setValue(FACING, mirror.mirror(state.getValue(FACING))).cycle(MIRRORED);
    }

    public static List<TemplateBox> boxes(List<TemplateBox> boxes, boolean mirrored) {
        return !mirrored ? boxes : boxes.stream().map(box -> new TemplateBox(
                16F - box.maxX(), box.minY(), box.minZ(),
                16F - box.minX(), box.maxY(), box.maxZ())).toList();
    }
}
