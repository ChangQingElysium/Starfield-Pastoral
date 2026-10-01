package com.stardew.craft.templates;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One material and one item: stacked sections leave a cap only at the top. */
public final class ChimneyTemplateBlock extends MaterialTemplateBlock {
    public static final BooleanProperty CAPPED = BooleanProperty.create("capped");
    private static final List<TemplateBox> CAPPED_BOXES = List.of(
            new TemplateBox(3, 0, 3, 13, 12, 6),
            new TemplateBox(3, 0, 10, 13, 12, 13),
            new TemplateBox(3, 0, 6, 6, 12, 10),
            new TemplateBox(10, 0, 6, 13, 12, 10),
            new TemplateBox(2, 12, 2, 14, 13, 6),
            new TemplateBox(2, 12, 10, 14, 13, 14),
            new TemplateBox(2, 12, 6, 6, 13, 10),
            new TemplateBox(10, 12, 6, 14, 13, 10),
            new TemplateBox(1, 13, 1, 15, 14, 15),
            new TemplateBox(1, 14, 1, 5, 16, 15),
            new TemplateBox(11, 14, 11, 15, 16, 15),
            new TemplateBox(5, 14, 1, 11, 16, 5),
            new TemplateBox(5, 14, 11, 11, 16, 15));
    private static final List<TemplateBox> PIPE_BOXES = List.of(
            new TemplateBox(3, 0, 3, 13, 16, 6),
            new TemplateBox(3, 0, 10, 13, 16, 13),
            new TemplateBox(3, 0, 6, 6, 16, 10),
            new TemplateBox(10, 0, 6, 13, 16, 10));
    private static final VoxelShape CAPPED_SHAPE = shape(CAPPED_BOXES);
    private static final VoxelShape PIPE_SHAPE = shape(PIPE_BOXES);

    public ChimneyTemplateBlock(Properties properties) {
        super(TemplateShape.CHIMNEY, properties);
        registerDefaultState(defaultBlockState().setValue(CAPPED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CAPPED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(CAPPED,
                !context.getLevel().getBlockState(context.getClickedPos().above()).is(this));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos other) {
        return direction == Direction.UP ? state.setValue(CAPPED, !neighbor.is(this)) : state;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.is(asItem()) ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    public static List<TemplateBox> cappedBoxes() {
        return CAPPED_BOXES;
    }

    public static List<TemplateBox> boxes(BlockState state) {
        return state.getValue(CAPPED) ? CAPPED_BOXES : PIPE_BOXES;
    }

    static VoxelShape outline(BlockState state) {
        return state.getValue(CAPPED) ? CAPPED_SHAPE : PIPE_SHAPE;
    }

    private static VoxelShape shape(List<TemplateBox> boxes) {
        VoxelShape shape = Shapes.empty();
        for (TemplateBox box : boxes) {
            shape = Shapes.or(shape, Block.box(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()));
        }
        return shape.optimize();
    }
}
