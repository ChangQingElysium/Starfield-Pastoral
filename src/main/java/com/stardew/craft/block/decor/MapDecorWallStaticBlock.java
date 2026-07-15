package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("null")
public class MapDecorWallStaticBlock extends MapDecorStaticBlock {
    private final Map<Direction, VoxelShape> integratedShapes;

    public MapDecorWallStaticBlock(Properties properties, String modelId) {
        super(properties, modelId);
        this.integratedShapes = null;
    }

    public MapDecorWallStaticBlock(Properties properties, String modelId,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
        this.integratedShapes = new EnumMap<>(Direction.class);
        VoxelShape northShape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            integratedShapes.put(facing, rotateShapeForFacing(northShape, facing));
        }
    }

    public MapDecorWallStaticBlock(Properties properties, String modelId, int extensionOffsetX, int extensionOffsetY, int extensionOffsetZ) {
        super(properties, modelId, extensionOffsetX, extensionOffsetY, extensionOffsetZ);
        this.integratedShapes = null;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (integratedShapes == null) {
            return super.getShape(state, level, pos, context);
        }
        return integratedShapeForCell(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        if (integratedShapes == null) {
            return super.getCollisionShape(state, level, pos, context);
        }
        return integratedShapeForCell(state, level, pos);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        for (CellOffset offset : occupiedOffsets(facing)) {
            if ((offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0) || isSupportSideOverflow(offset, facing)) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx(), offset.dy(), offset.dz());
            if (!level.getWorldBorder().isWithinBounds(extensionPos)) {
                return null;
            }
            if (!level.getBlockState(extensionPos).canBeReplaced(context)) {
                return null;
            }
        }
        BlockState state = defaultBlockState().setValue(PART, Part.MAIN).setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public void setPlacedBy(@Nonnull Level level,
                            @Nonnull BlockPos pos,
                            @Nonnull BlockState state,
                            @Nullable LivingEntity placer,
                            @Nonnull ItemStack stack) {
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if ((offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0) || isSupportSideOverflow(offset, facing)) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx(), offset.dy(), offset.dz());
            if (level.getBlockState(extensionPos).canBeReplaced()) {
                level.setBlock(extensionPos, state.setValue(PART, Part.EXTENSION), 3);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
                                               @Nonnull BlockPos pos, @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        if (integratedShapes != null && state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) {
                return InteractionResult.PASS;
            }
            BlockHitResult mainHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside());
            return useWithoutItem(level.getBlockState(mainPos), level, mainPos, player, mainHit);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state,
                                              @Nonnull Level level, @Nonnull BlockPos pos,
                                              @Nonnull Player player, @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        if (integratedShapes != null && state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            BlockHitResult mainHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside());
            return useItemOn(stack, level.getBlockState(mainPos), level, mainPos, player, hand, mainHit);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) {
            return false;
        }
        BlockPos mainPos = state.getValue(PART) == Part.MAIN ? pos : findMainPos(level, pos, state);
        if (mainPos == null) {
            return false;
        }
        BlockState mainState = level.getBlockState(mainPos);
        Direction facing = mainState.is(this) && mainState.hasProperty(FACING) ? mainState.getValue(FACING) : state.getValue(FACING);
        BlockPos supportPos = mainPos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    private boolean isSupportSideOverflow(CellOffset offset, Direction facing) {
        return switch (facing) {
            case NORTH -> offset.dz() > 0;
            case SOUTH -> offset.dz() < 0;
            case EAST -> offset.dx() < 0;
            case WEST -> offset.dx() > 0;
            default -> false;
        };
    }

    private VoxelShape integratedShapeForCell(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape wholeShape = integratedShapes.getOrDefault(state.getValue(FACING), Shapes.empty());
        if (state.getValue(PART) == Part.MAIN) {
            return wholeShape;
        }
        CellOffset offset = findOffsetForExtension(level, pos, state);
        if (offset == null) {
            return Shapes.empty();
        }
        final VoxelShape[] shifted = {Shapes.empty()};
        wholeShape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
            shifted[0] = Shapes.or(shifted[0], Shapes.box(
                minX - offset.dx(), minY - offset.dy(), minZ - offset.dz(),
                maxX - offset.dx(), maxY - offset.dy(), maxZ - offset.dz()
            ))
        );
        return shifted[0].optimize();
    }
}
