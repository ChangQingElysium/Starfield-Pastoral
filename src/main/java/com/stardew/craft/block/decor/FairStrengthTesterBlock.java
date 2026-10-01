package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.FairStrengthTesterBlockEntity;
import com.stardew.craft.festival.fair.FairStrengthGameService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class FairStrengthTesterBlock extends MapDecorStaticBlock implements EntityBlock {
    private static final VoxelShape BASE_FULL_SHAPE = Block.box(-16.0D, 0.0D, -1.0D, 23.0D, 48.0D, 16.0D);

    public FairStrengthTesterBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean isPathfindable(@Nonnull BlockState state, BlockGetter level, BlockPos pos, @Nonnull PathComputationType type) {
        return false;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos,
                               @Nonnull CollisionContext context) {
        return fullShapeForPart(state, level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos,
                                        @Nonnull CollisionContext context) {
        return fullShapeForPart(state, level, pos);
    }

    private VoxelShape fullShapeForPart(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        VoxelShape fullShape = rotateShapeForFacing(BASE_FULL_SHAPE, facing);
        if (state.getValue(PART) == Part.MAIN) {
            return fullShape;
        }
        CellOffset offset = findOffsetForExtension(level, pos, state);
        if (offset == null) {
            return Shapes.empty();
        }
        return shiftShape(fullShape, -offset.dx(), -offset.dy(), -offset.dz());
    }

    private static VoxelShape shiftShape(VoxelShape shape, int dx, int dy, int dz) {
        if (shape.isEmpty()) {
            return Shapes.empty();
        }
        final VoxelShape[] shifted = new VoxelShape[] { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> shifted[0] = Shapes.or(
            shifted[0],
            Shapes.box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz)
        ));
        return shifted[0].optimize();
    }

    @Override
    public void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                           @Nonnull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) {
            return;
        }

        Direction facing = state.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx(), offset.dy(), offset.dz());
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(this)
                && extensionState.getValue(PART) == Part.EXTENSION
                && extensionState.getValue(FACING) == facing) {
                continue;
            }
            if (!extensionState.canBeReplaced()) {
                continue;
            }
            level.setBlock(extensionPos, state.setValue(PART, Part.EXTENSION), 3);
        }
    }

    @Override
    public ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level,
                                             @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand,
                                             @Nonnull BlockHitResult hit) {
        BlockPos mainPos = mainPosForInteraction(level, pos, state);
        if (mainPos == null && state.is(this)) {
            mainPos = pos;
        }
        if (mainPos == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FairStrengthGameService.open(serverPlayer);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                               @Nonnull Player player, @Nonnull BlockHitResult hit) {
        BlockPos mainPos = mainPosForInteraction(level, pos, state);
        if (mainPos == null && state.is(this)) {
            mainPos = pos;
        }
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FairStrengthGameService.open(serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    private BlockPos mainPosForInteraction(Level level, BlockPos pos, BlockState state) {
        BlockPos mainPos = state.getValue(PART) == Part.EXTENSION ? findMainPos(level, pos, state) : pos;
        if (mainPos == null) {
            return null;
        }
        BlockState mainState = level.getBlockState(mainPos);
        return mainState.is(this) && mainState.getValue(PART) == Part.MAIN ? mainPos : null;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new FairStrengthTesterBlockEntity(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        return List.of();
    }
}
