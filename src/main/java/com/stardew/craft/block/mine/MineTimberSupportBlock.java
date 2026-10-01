package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One item places a four-high column; neighboring columns join without changing identity. */
@SuppressWarnings("null")
public final class MineTimberSupportBlock extends Block {
    public static final MapCodec<MineTimberSupportBlock> CODEC = simpleCodec(MineTimberSupportBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 3);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    private static final Map<BlockState, VoxelShape> OUTLINES = new ConcurrentHashMap<>();

    public MineTimberSupportBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(TIER, 0)
                .setValue(LEFT, false).setValue(RIGHT, false).setValue(VARIANT, 0)
                .setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.EARTH));
    }

    @Override public MapCodec<MineTimberSupportBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER, LEFT, RIGHT, VARIANT, MineBuildingTheme.PROPERTY);
    }

    @Nullable
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() + 3 >= context.getLevel().getMaxBuildHeight()) return null;
        for (int i = 1; i < 4; i++) {
            if (!context.getLevel().getBlockState(pos.above(i)).canBeReplaced(context)) return null;
        }
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        Integer variant = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(VARIANT, variant == null ? 0 : variant)
                .setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.forPlacement(context));
        return state.canSurvive(context.getLevel(), pos) ? connections(state, context.getLevel(), pos) : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int i = 1; i < 4; i++) {
            BlockState part = state.setValue(TIER, i);
            level.setBlock(pos.above(i), connections(part, level, pos.above(i)), 3);
        }
    }

    private BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        Direction right = state.getValue(FACING).getCounterClockWise();
        return state.setValue(LEFT, joins(state, level.getBlockState(pos.relative(right.getOpposite()))))
                .setValue(RIGHT, joins(state, level.getBlockState(pos.relative(right))));
    }

    private boolean joins(BlockState state, BlockState neighbor) {
        return neighbor.is(this) && neighbor.getValue(FACING) == state.getValue(FACING)
                && neighbor.getValue(MineBuildingTheme.PROPERTY) == state.getValue(MineBuildingTheme.PROPERTY)
                && neighbor.getValue(TIER).equals(state.getValue(TIER));
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        int tier = state.getValue(TIER);
        if (tier == 0) return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        BlockState root = level.getBlockState(pos.below(tier));
        return root.is(this) && root.getValue(TIER) == 0 && root.getValue(FACING) == state.getValue(FACING);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                                LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Structure imports may place upper pieces before their anchor.
        level.scheduleTick(pos, this, 1);
        BlockState root = level.getBlockState(pos.below(state.getValue(TIER)));
        if (state.getValue(TIER) > 0 && root.is(this)) state = state.setValue(VARIANT, root.getValue(VARIANT))
                .setValue(MineBuildingTheme.PROPERTY, root.getValue(MineBuildingTheme.PROPERTY));
        return connections(state, level, pos);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                  net.minecraft.util.RandomSource random) {
        if (!state.canSurvive(level, pos)) level.removeBlock(pos, false);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this) && !level.isClientSide) {
            BlockPos root = pos.below(state.getValue(TIER));
            for (int i = 0; i < 4; i++) {
                BlockPos partPos = root.above(i);
                if (partPos.equals(pos)) continue;
                BlockState part = level.getBlockState(partPos);
                if (part.is(this) && part.getValue(TIER) == i && part.getValue(FACING) == state.getValue(FACING)) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(TIER) > 0 && !player.isCreative()) {
            BlockPos root = pos.below(state.getValue(TIER));
            BlockState base = level.getBlockState(root);
            if (base.is(this)) dropResources(base, level, root, null, player, player.getMainHandItem());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state)
                .with(MineBuildingTheme.PROPERTY, state));
        return stack;
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
                .setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }

    private static VoxelShape columnShape(BlockState state) {
        boolean left = state.getValue(LEFT), right = state.getValue(RIGHT);
        int x = left == right ? 6 : left ? 8 : 4, start = left ? 0 : 2, end = right ? 16 : 14;
        boolean metal = state.getValue(MineBuildingTheme.PROPERTY) == MineBuildingTheme.LAVA
                || state.getValue(MineBuildingTheme.PROPERTY) == MineBuildingTheme.LAVA_DARK;
        if (metal) {
            VoxelShape shape = Shapes.or(box(x - 2, 0, 0, x + 6, 2, 7), box(x, 2, 0, x + 4, 64, 5),
                    box(start, 58, 4, end, 64, 10), box(x - 1, 58, 10, x + 5, 64, 11),
                    box(x + 1, 60, 11, x + 3, 62, 12));
            if (state.getValue(VARIANT) == 0) shape = Shapes.or(shape, box(start, 28, 4, end, 34, 10),
                    box(x - 1, 28, 10, x + 5, 34, 11), box(x + 1, 30, 11, x + 3, 32, 12));
            else shape = Shapes.or(shape, box(start, 28, 4, 5, 34, 10), box(10, 28, 4, end, 34, 10));
            int turns = switch (state.getValue(FACING)) { case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0; };
            return ModelVoxelShapeCache.rotateY(shape, turns).move(0, -state.getValue(TIER), 0);
        }
        VoxelShape shape = Shapes.or(box(x, 0, 0, x + 4, 64, 5), box(start, 58, 4, end, 64, 10),
                box(x + 1, 60, 10, x + 3, 62, 11));
        if (state.getValue(VARIANT) == 0) shape = Shapes.or(shape,
                box(start, 28, 4, end, 34, 10), box(x + 1, 30, 10, x + 3, 32, 11));
        else shape = Shapes.or(shape, box(start, 28, 4, 4, 34, 10), box(4, 31, 4, 6, 34, 8),
                box(11, 28, 4, end, 34, 10), box(9, 28, 4, 11, 30, 8));
        int turns = switch (state.getValue(FACING)) {
            case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> 0;
        };
        return ModelVoxelShapeCache.rotateY(shape, turns).move(0, -state.getValue(TIER), 0);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.computeIfAbsent(state, MineTimberSupportBlock::columnShape);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.join(getShape(state, level, pos, context), Shapes.block(), BooleanOp.AND);
    }

    @Override public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos,
                                            @Nullable net.minecraft.world.entity.Entity entity) {
        var theme = state.getValue(MineBuildingTheme.PROPERTY);
        return theme == MineBuildingTheme.LAVA || theme == MineBuildingTheme.LAVA_DARK ? SoundType.METAL : SoundType.WOOD;
    }
}
