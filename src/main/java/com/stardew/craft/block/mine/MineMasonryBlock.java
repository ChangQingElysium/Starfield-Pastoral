package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A one-pixel stone layer; the underlying soil remains a separate block. */
@SuppressWarnings("null")
public final class MineMasonryBlock extends Block {
    public static final MapCodec<MineMasonryBlock> CODEC = simpleCodec(MineMasonryBlock::new);
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 255);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    private static final int[][] NEIGHBORS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};

    public MineMasonryBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CONNECTIONS, 0).setValue(VARIANT, 0).setValue(MineBuildingTheme.PROPERTY, MineBuildingTheme.EARTH));
    }

    @Override
    public MapCodec<MineMasonryBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS, VARIANT, MineBuildingTheme.PROPERTY);
    }

    /** Diagonal corners matter only when both bordering cardinal neighbors exist. */
    public static int canonicalMask(int mask) {
        for (int corner = 0; corner < 4; corner++) {
            if ((mask & (1 << corner)) == 0 || (mask & (1 << ((corner + 1) % 4))) == 0) {
                mask &= ~(16 << corner);
            }
        }
        return mask;
    }

    public int connections(BlockGetter level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        return connections(level, pos, here.is(this) ? here.getValue(MineBuildingTheme.PROPERTY) : MineBuildingTheme.EARTH);
    }

    private int connections(BlockGetter level, BlockPos pos, MineBuildingTheme theme) {
        int mask = 0;
        for (int i = 0; i < NEIGHBORS.length; i++) {
            BlockState neighbor = level.getBlockState(pos.offset(NEIGHBORS[i][0], 0, NEIGHBORS[i][1]));
            if (neighbor.is(this) && neighbor.getValue(MineBuildingTheme.PROPERTY) == theme) mask |= 1 << i;
        }
        return canonicalMask(mask);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        int variant = fixed != null ? fixed : (!context.getLevel().isClientSide && context.getLevel().getRandom().nextInt(4) == 0 ? 1 : 0);
        var theme = MineBuildingTheme.forPlacement(context);
        return defaultBlockState().setValue(VARIANT, variant).setValue(MineBuildingTheme.PROPERTY, theme)
                .setValue(CONNECTIONS, connections(context.getLevel(), context.getClickedPos(), theme));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state).with(MineBuildingTheme.PROPERTY, state));
        return stack;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state.setValue(CONNECTIONS, connections(level, pos)) : Blocks.AIR.defaultBlockState();
    }

    // Vanilla's six-axis neighbor update does not reach diagonal tiles.
    private void refreshAround(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos target = pos.offset(x, 0, z);
            BlockState current = level.getBlockState(target);
            if (!current.is(this)) continue;
            BlockState connected = current.setValue(CONNECTIONS, connections(level, target));
            if (current != connected) level.setBlock(target, connected, 2);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this) || oldState.getValue(MineBuildingTheme.PROPERTY) != state.getValue(MineBuildingTheme.PROPERTY)) refreshAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!newState.is(this)) refreshAround(level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}
