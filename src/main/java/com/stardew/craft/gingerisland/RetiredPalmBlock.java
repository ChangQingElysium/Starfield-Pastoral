package com.stardew.craft.gingerisland;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Save-only identity for the withdrawn whole-tree art. No tree geometry or footprint remains. */
public final class RetiredPalmBlock extends Block {
    public RetiredPalmBlock(Properties properties) {
        super(properties.randomTicks().noOcclusion());
        registerDefaultState(defaultBlockState().setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.NORTH).setValue(GingerIslandStateDecorBlock.VARIANT, 0));
    }

    @Override public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MapDecorStaticBlock.PART, MapDecorStaticBlock.FACING, GingerIslandStateDecorBlock.VARIANT);
    }

    private static boolean extension(BlockState state) {
        return state.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return extension(state) ? Shapes.empty() : Shapes.block();
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return extension(state);
    }

    @Override public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return extension(state) ? 0 : 15;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return null; // The old item is hidden; builders use the new individual blocks.
    }

    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.PALM_LOG.get());
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        migrate(state, level, pos);
    }

    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        migrate(state, level, pos);
    }

    private void migrate(BlockState state, ServerLevel level, BlockPos pos) {
        // Only this exact saved cell is owned: never clear a bounding box around a player build.
        if (level.getBlockState(pos).is(this)) {
            level.setBlock(pos, extension(state) ? Blocks.AIR.defaultBlockState() : ModBlocks.PALM_LOG.get().defaultBlockState(), 3);
        }
    }
}
