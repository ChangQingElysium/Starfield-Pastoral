package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.AutoFeedTroughBlockEntity;
import com.stardew.craft.blockentity.UtilityDropHelper;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public class AutoFeedTroughBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LEFT_CONNECTED = BooleanProperty.create("left_connected");
    public static final BooleanProperty RIGHT_CONNECTED = BooleanProperty.create("right_connected");
    public static final BooleanProperty HAS_HAY = BooleanProperty.create("has_hay");

    private static final VoxelShape FALLBACK_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);
    private static final Map<String, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("null")
    public AutoFeedTroughBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LEFT_CONNECTED, false)
            .setValue(RIGHT_CONNECTED, false)
            .setValue(HAS_HAY, false));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT_CONNECTED, RIGHT_CONNECTED, HAS_HAY);
    }

    @SuppressWarnings("null")
    @Override
    protected List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        return List.of(new ItemStack(ModBlocks.AUTOFEED_TROUGH.get()));
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getModelShape(state);
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getModelShape(state);
    }

    @SuppressWarnings("null")
    private static VoxelShape getModelShape(BlockState state) {
        String variant = "facing=" + state.getValue(FACING).getSerializedName()
            + ",left_connected=" + state.getValue(LEFT_CONNECTED)
            + ",right_connected=" + state.getValue(RIGHT_CONNECTED)
            + ",has_hay=false";
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return SHAPE_CACHE.computeIfAbsent(variant, key -> {
            VoxelShape shape = ModelVoxelShapeCache.variantShape(blockId, key);
            return shape.isEmpty() ? FALLBACK_SHAPE : shape;
        });
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        return new AutoFeedTroughBlockEntity(pos, state);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState placed = defaultBlockState().setValue(FACING, facing);
        return updateConnections(context.getLevel(), context.getClickedPos(), placed);
    }

    @SuppressWarnings("null")
    @Override
    public void onPlace(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.is(oldState.getBlock())) {
            return;
        }

        BlockState updatedSelf = updateConnections(level, pos, state);
        if (updatedSelf != state) {
            level.setBlock(pos, updatedSelf, 3);
        }
        refreshNeighbors(level, pos);
        reorientIsolatedNeighbors(level, pos, updatedSelf.getValue(FACING));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    protected BlockState updateShape(@SuppressWarnings("null") BlockState state,
                                     @SuppressWarnings("null") Direction direction,
                                     @SuppressWarnings("null") BlockState neighborState,
                                     @SuppressWarnings("null") LevelAccessor level,
                                     @SuppressWarnings("null") BlockPos pos,
                                     @SuppressWarnings("null") BlockPos neighborPos) {
        return updateConnections(level, pos, state);
    }

    @SuppressWarnings("null")
    private static BlockState updateConnections(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        boolean straightLeft = isTroughWithFacing(level.getBlockState(pos.relative(left)), facing);
        boolean straightRight = isTroughWithFacing(level.getBlockState(pos.relative(right)), facing);

        boolean sideCornerLeft = isTroughWithFacing(level.getBlockState(pos.relative(left)), left.getOpposite());
        boolean sideCornerRight = isTroughWithFacing(level.getBlockState(pos.relative(right)), right.getOpposite());

        boolean cornerLeft = hasLeftCornerConnection(level, pos, facing);
        boolean cornerRight = hasRightCornerConnection(level, pos, facing);

        boolean leftConnected = straightLeft || sideCornerLeft || (!straightRight && !sideCornerRight && cornerLeft);
        boolean rightConnected = straightRight || sideCornerRight || (!straightLeft && !sideCornerLeft && cornerRight);

        return state.setValue(LEFT_CONNECTED, leftConnected).setValue(RIGHT_CONNECTED, rightConnected);
    }

    private static boolean hasLeftCornerConnection(LevelAccessor level, BlockPos pos, Direction facing) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockState front = level.getBlockState(pos.relative(facing));
        if (isTroughWithFacing(front, left)) {
            return true;
        }

        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        return isTroughWithFacing(back, right);
    }

    private static boolean hasRightCornerConnection(LevelAccessor level, BlockPos pos, Direction facing) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockState front = level.getBlockState(pos.relative(facing));
        if (isTroughWithFacing(front, right)) {
            return true;
        }

        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        return isTroughWithFacing(back, left);
    }

    private static boolean isTroughWithFacing(BlockState state, Direction facing) {
        return state.getBlock() instanceof AutoFeedTroughBlock && state.getValue(FACING) == facing;
    }

    private static void refreshNeighbors(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            refreshNeighbor(level, pos.relative(direction));
        }
    }

    private static void refreshNeighbor(Level level, BlockPos neighborPos) {
        BlockState neighborState = level.getBlockState(neighborPos);
        if (!(neighborState.getBlock() instanceof AutoFeedTroughBlock)) {
            return;
        }
        BlockState updated = updateConnections(level, neighborPos, neighborState);
        if (updated != neighborState) {
            level.setBlock(neighborPos, updated, 3);
        }
    }

    private static void reorientIsolatedNeighbors(Level level, BlockPos placedPos, Direction placedFacing) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = placedPos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof AutoFeedTroughBlock)) {
                continue;
            }
            if (countAdjacentTroughs(level, neighborPos) != 1) {
                continue;
            }

            Direction currentFacing = neighborState.getValue(FACING);
            if (currentFacing == placedFacing) {
                continue;
            }

            BlockState rotated = neighborState.setValue(FACING, placedFacing);
            rotated = updateConnections(level, neighborPos, rotated);
            level.setBlock(neighborPos, rotated, 3);
            refreshNeighbors(level, neighborPos);
        }
    }

    private static int countAdjacentTroughs(LevelAccessor level, BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof AutoFeedTroughBlock) {
                count++;
            }
        }
        return count;
    }

    public static List<BlockPos> collectConnectedTroughs(LevelAccessor level, BlockPos origin) {
        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof AutoFeedTroughBlock)) {
            return List.of();
        }

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            result.add(current);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = current.relative(direction);
                if (visited.contains(neighborPos)) {
                    continue;
                }
                if (!areVisuallyConnected(level, current, neighborPos)) {
                    continue;
                }
                visited.add(neighborPos);
                queue.add(neighborPos);
            }
        }

        result.sort(Comparator
            .comparingInt((BlockPos p) -> manhattan(p, origin))
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ));
        return result;
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static boolean areVisuallyConnected(LevelAccessor level, BlockPos aPos, BlockPos bPos) {
        if (aPos.distManhattan(bPos) != 1 || aPos.getY() != bPos.getY()) {
            return false;
        }
        BlockState a = level.getBlockState(aPos);
        BlockState b = level.getBlockState(bPos);
        if (!(a.getBlock() instanceof AutoFeedTroughBlock) || !(b.getBlock() instanceof AutoFeedTroughBlock)) {
            return false;
        }
        return connectsTo(aPos, a, bPos, b) || connectsTo(bPos, b, aPos, a);
    }

    private static boolean connectsTo(BlockPos fromPos, BlockState fromState, BlockPos toPos, BlockState toState) {
        Direction facing = fromState.getValue(FACING);
        Direction toDir = Direction.fromDelta(
            Integer.signum(toPos.getX() - fromPos.getX()),
            0,
            Integer.signum(toPos.getZ() - fromPos.getZ())
        );
        if (toDir == null) {
            return false;
        }

        if (toDir == facing.getCounterClockWise() || toDir == facing.getClockWise()) {
            return toState.getValue(FACING) == facing;
        }
        if (toDir == facing || toDir == facing.getOpposite()) {
            Direction toFacing = toState.getValue(FACING);
            return toFacing == facing.getCounterClockWise() || toFacing == facing.getClockWise();
        }
        return false;
    }

    @SuppressWarnings("null")
    @Override
    protected ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack,
                                              @SuppressWarnings("null") BlockState state,
                                              @SuppressWarnings("null") Level level,
                                              @SuppressWarnings("null") BlockPos pos,
                                              @SuppressWarnings("null") Player player,
                                              @SuppressWarnings("null") InteractionHand hand,
                                              @SuppressWarnings("null") BlockHitResult hit) {
        if (!stack.is(ModItems.HAY.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AutoFeedTroughBlockEntity trough)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!trough.insertOneHay(false)) {
            return ItemInteractionResult.CONSUME;
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.9f, 0.95f + level.random.nextFloat() * 0.1f);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AutoFeedTroughBlockEntity trough)) {
            return InteractionResult.PASS;
        }

        ItemStack extracted = trough.takeOneFromSelf(false);
        if (extracted.isEmpty()) {
            extracted = trough.extractAutomation(1, false);
        }
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!player.addItem(extracted)) {
            player.drop(extracted, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
        level.sendBlockUpdated(pos, state, state, 11);
        return InteractionResult.CONSUME;
    }

    @SuppressWarnings("null")
    @Override
    public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving) {
            UtilityDropHelper.dropAutomationContents(level, pos);
            if (!level.isClientSide) {
                refreshNeighbors(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
