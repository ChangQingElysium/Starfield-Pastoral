package com.stardew.craft.block.decor;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.PortBlockInteraction;

@SuppressWarnings("null")
public class MapDecorWallSwitchBlock extends Block implements PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    private static final VoxelShape FALLBACK_SHAPE = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private static final Map<String, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

    public MapDecorWallSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, true));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(ON, true);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        Direction supportDir = state.getValue(FACING).getOpposite();
        return direction == supportDir && !state.canSurvive(level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        Direction supportDir = state.getValue(FACING).getOpposite();
        BlockPos supportPos = pos.relative(supportDir);
        BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, state.getValue(FACING));
    }

    @Override
    public boolean canBeReplaced(@Nonnull BlockState state, @Nonnull Fluid fluid) {
        return false;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return resolveShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return resolveShape(state);
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(@Nonnull BlockState state,
                                               @Nonnull Level level,
                                               @Nonnull BlockPos pos,
                                               @Nonnull net.minecraft.world.entity.player.Player player,
                                               @Nonnull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        toggleState(level, pos, state);
        return InteractionResult.CONSUME;
    }

    @Override
    public com.stardew.craft.port.net.minecraft.world.ItemInteractionResult useItemOn(@Nonnull ItemStack stack,
                                                                  @Nonnull BlockState state,
                                                                  @Nonnull Level level,
                                                                  @Nonnull BlockPos pos,
                                                                  @Nonnull net.minecraft.world.entity.player.Player player,
                                                                  @Nonnull net.minecraft.world.InteractionHand hand,
                                                                  @Nonnull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return com.stardew.craft.port.net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
        }
        toggleState(level, pos, state);
        return com.stardew.craft.port.net.minecraft.world.ItemInteractionResult.sidedSuccess(false);
    }

    private void toggleState(Level level, BlockPos pos, BlockState state) {
        boolean next = !state.getValue(ON);
        level.setBlock(pos, state.setValue(ON, next), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.35F, next ? 0.6F : 0.5F);
    }

    private VoxelShape resolveShape(BlockState state) {
        String variant = "facing=" + state.getValue(FACING).getSerializedName() + ",on=" + state.getValue(ON);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return SHAPE_CACHE.computeIfAbsent(blockId + "|" + variant, key -> {
            VoxelShape shape = ModelVoxelShapeCache.variantShape(blockId, variant);
            return shape.isEmpty() ? FALLBACK_SHAPE : shape;
        });
    }
}
