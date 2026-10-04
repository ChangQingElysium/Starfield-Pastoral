package com.stardew.craft.block.decor;

import com.stardew.craft.block.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Inert decorative plants: no growth, spread, decay, or random-tick rerolls. */
public class NaturalPlantBlock extends Block implements SimpleWaterloggedBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    public static final BooleanProperty IN_PLANTER = BooleanProperty.create("in_planter");
    public static final BooleanProperty TROPICAL = BooleanProperty.create("tropical");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final NaturalDecorKind kind;

    public NaturalPlantBlock(Properties properties, NaturalDecorKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0).setValue(IN_PLANTER, false)
                .setValue(TROPICAL, false).setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH));
    }

    public NaturalDecorKind kind() { return kind; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, IN_PLANTER, TROPICAL, WATERLOGGED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel(); var pos = context.getClickedPos();
        var fluid = level.getFluidState(pos);
        if (kind.habitat != NaturalDecorKind.Habitat.UNDERWATER && !fluid.isEmpty()) return null;
        Integer fixed = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        int variant = fixed == null ? (level.isClientSide ? 0 : level.random.nextInt(kind.variants))
                : Math.min(fixed, kind.variants - 1);
        BlockState state = defaultBlockState().setValue(VARIANT, variant)
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(IN_PLANTER, kind.habitat == NaturalDecorKind.Habitat.LAND
                        && level.getBlockState(pos.below()).is(ModBlocks.GARDEN_PLANTER.get()))
                .setValue(WATERLOGGED, kind.habitat == NaturalDecorKind.Habitat.UNDERWATER
                        && fluid.is(FluidTags.WATER) && fluid.isSource());
        return canSurvive(state, level, pos) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below(); BlockState support = level.getBlockState(below);
        if (kind.habitat == NaturalDecorKind.Habitat.SURFACE) {
            FluidState water = support.getFluidState();
            return support.getBlock() instanceof LiquidBlock && water.is(FluidTags.WATER) && water.isSource()
                    && level.getFluidState(pos).isEmpty();
        }
        if (kind.habitat == NaturalDecorKind.Habitat.UNDERWATER)
            return state.getValue(WATERLOGGED) && support.isFaceSturdy(level, below, Direction.UP);
        return !state.getValue(WATERLOGGED) && (support.isFaceSturdy(level, below, Direction.UP)
                || support.is(Blocks.FARMLAND) || com.stardew.craft.block.terrain.TerrainSoils.farmland(support)
                || support.is(ModBlocks.GARDEN_PLANTER.get()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (!canSurvive(state, level, pos)) return state.getValue(WATERLOGGED)
                ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        return state.setValue(IN_PLANTER, kind.habitat == NaturalDecorKind.Habitat.LAND
                && level.getBlockState(pos.below()).is(ModBlocks.GARDEN_PLANTER.get()));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(net.minecraft.world.entity.player.Player player, BlockGetter level,
            BlockPos pos, BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return kind.habitat == NaturalDecorKind.Habitat.UNDERWATER
                && SimpleWaterloggedBlock.super.canPlaceLiquid(player, level, pos, state, fluid);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double offset = state.getValue(IN_PLANTER) ? -4 : 0;
        if (kind.habitat == NaturalDecorKind.Habitat.SURFACE)
            offset = (level.getFluidState(pos.below()).getHeight(level, pos.below()) - 1) * 16;
        return Block.box(2, offset, 2, 14, kind.height + offset, 14);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return tropicalCopy(super.getCloneItemStack(level, pos, state), state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        return state.getValue(TROPICAL) ? drops.stream().map(stack -> tropicalCopy(stack, state)).toList() : drops;
    }

    private ItemStack tropicalCopy(ItemStack stack, BlockState state) {
        if (!state.getValue(TROPICAL) || !stack.is(asItem())) return stack;
        ItemStack copy = stack.copy();
        copy.set(DataComponents.BLOCK_STATE, copy.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).with(TROPICAL, state));
        return copy;
    }

    public static ItemStack fixedCopy(ItemStack stack, BlockState state) {
        if (!(state.getBlock() instanceof NaturalPlantBlock block)) return stack;
        ItemStack copy = block.tropicalCopy(stack, state);
        if (block.kind.variants == 1) return copy;
        copy = copy.copy();
        copy.set(DataComponents.BLOCK_STATE, copy.getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).with(VARIANT, state));
        return copy;
    }
}
