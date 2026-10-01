package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.manager.SprinklerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.particles.ParticleTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SprinklerBlock extends Block {
    private static final VoxelShape SHAPE_BASIC = ModelVoxelShapeCache.shape("stardewcraft:block/utility/sprinkler");
    private static final VoxelShape SHAPE_QUALITY = ModelVoxelShapeCache.shape("stardewcraft:block/utility/quality_sprinkler");
    private static final VoxelShape SHAPE_IRIDIUM = ModelVoxelShapeCache.shape("stardewcraft:block/utility/iridium_sprinkler");

    private final SprinklerTier tier;

    public SprinklerBlock(SprinklerTier tier, @SuppressWarnings("null") Properties properties) {
        super(Objects.requireNonNull(properties, "properties"));
        this.tier = tier;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return com.stardew.craft.block.terrain.TerrainSoils.sandy(
                context.getLevel().getBlockState(context.getClickedPos().below()))
                ? null : super.getStateForPlacement(context);
    }

    public SprinklerTier getTier() {
        return tier;
    }

    @Override
    @SuppressWarnings("null")
    public void onPlace(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level,
                           @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState oldState, boolean isMoving) {
        if (!state.is(oldState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                SprinklerManager.get(serverLevel).addSprinkler(serverLevel, pos);
            }
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    @SuppressWarnings("null")
    public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level,
                            @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                SprinklerManager.get(serverLevel).removeSprinkler(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @SuppressWarnings("null")
    @Override
    public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state,
                                       @SuppressWarnings("null") LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level,
                               @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getTierShape();
    }

    @Override
    public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level,
                                        @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getTierShape();
    }

    private VoxelShape getTierShape() {
        return switch (tier) {
            case BASIC -> SHAPE_BASIC;
            case QUALITY -> SHAPE_QUALITY;
            case IRIDIUM -> SHAPE_IRIDIUM;
        };
    }

    public static List<BlockPos> getWateredPositions(BlockPos center, SprinklerTier tier) {
        BlockPos base = center.below();
        List<BlockPos> positions = new ArrayList<>();
        switch (tier) {
            case BASIC -> {
                positions.add(base.north());
                positions.add(base.south());
                positions.add(base.west());
                positions.add(base.east());
            }
            case QUALITY -> addSquare(base, 1, positions);
            case IRIDIUM -> addSquare(base, 2, positions);
        }
        return positions;
    }

    private static void addSquare(BlockPos center, int radius, List<BlockPos> positions) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                positions.add(center.offset(dx, 0, dz));
            }
        }
    }

    @SuppressWarnings("null")
    public static void waterNow(ServerLevel level, BlockPos sprinklerPos, SprinklerTier tier) {
        waterNow(level, sprinklerPos, tier, true);
    }

    /**
     * 浇水。quiet=true 时跳过粒子和音效（用于日结算，无人可见）。
     */
    @SuppressWarnings("null")
    public static void waterNow(ServerLevel level, BlockPos sprinklerPos, SprinklerTier tier, boolean withEffects) {
        for (BlockPos target : getWateredPositions(sprinklerPos, tier)) {
            if (com.stardew.craft.block.terrain.TerrainSoils.blocksSprinklers(level.getBlockState(target))) continue;
            waterTile(level, target);
            if (withEffects) {
                spawnWaterParticles(level, target);
            }
        }
        if (withEffects) {
            level.playSound(null, sprinklerPos, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }

    @SuppressWarnings("null")
    private static void waterTile(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FarmBlock)) {
            return;
        }
        int moisture = state.getValue(FarmBlock.MOISTURE);
        if (moisture < 7) {
            level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), 3);
        }
    }

    @SuppressWarnings("null")
    private static void spawnWaterParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.SPLASH, x, y, z, 5, 0.25, 0.1, 0.25, 0.01);
        level.sendParticles(ParticleTypes.FALLING_WATER, x, y + 0.1, z, 3, 0.2, 0.05, 0.2, 0.01);
    }
}
