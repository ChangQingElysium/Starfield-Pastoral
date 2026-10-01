package com.stardew.craft.block.nature;

import com.stardew.craft.manager.ArtifactSpotSpawnService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Animated marker above an independent ground block; digging never tills that ground. */
public final class SurfaceArtifactSpotBlock extends Block {
    private static final VoxelShape OUTLINE = Block.box(2, 0, 3, 14, 6, 12);
    private final boolean seedSpot;

    public SurfaceArtifactSpotBlock(Properties properties, boolean seedSpot) {
        super(properties.noCollission().noOcclusion().noLootTable().strength(-1, 3600000));
        this.seedSpot = seedSpot;
    }

    public boolean isSeedSpot() { return seedSpot; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return ArtifactSpotSpawnService.isDiggableSurface(level.getBlockState(pos.below()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server) ArtifactSpotSpawnService.track(server, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (level instanceof ServerLevel server && state.getBlock() != replacement.getBlock())
            ArtifactSpotSpawnService.untrack(server, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
