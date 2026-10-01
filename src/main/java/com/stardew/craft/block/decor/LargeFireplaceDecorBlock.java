package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.LargeFireplaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class LargeFireplaceDecorBlock extends MapDecorStaticBlock implements EntityBlock {
    private static final int LIGHT_LEVEL = 15;

    // bone2 top anchor in Bedrock model units (1 block = 16 units).
    private static final double BONE2_TOP_LOCAL_X = 8.0D / 16.0D;
    private static final double BONE2_TOP_LOCAL_Y = 11.8D / 16.0D;
    private static final double BONE2_TOP_LOCAL_Z = -4.5D / 16.0D;

    public LargeFireplaceDecorBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** 壁炉所有格都不可通行，防止 Junimo 等实体寻路穿过 */
    @Override
    public boolean isPathfindable(@Nonnull BlockState state, BlockGetter level, BlockPos pos, @Nonnull PathComputationType type) {
        return false;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new LargeFireplaceBlockEntity(pos, state);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return LIGHT_LEVEL;
    }

    @Override
    public void animateTick(@Nonnull BlockState state,
                            @Nonnull Level level,
                            @Nonnull BlockPos pos,
                            @Nonnull RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (state.getValue(PART) != Part.MAIN) {
            return;
        }

        Direction facing = state.getValue(FACING);
        Vec3 flameAnchor = resolveFlameAnchor(pos, facing);
        
        for(int i = 0; i < 3; i++) {
            double x = flameAnchor.x + (random.nextDouble() - 0.5D) * 0.4D;
            double y = flameAnchor.y + random.nextDouble() * 0.2D;
            double z = flameAnchor.z + (random.nextDouble() - 0.5D) * 0.4D;

            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.1D, z, 0.0D, 0.02D, 0.0D);
            }
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.01D, 0.0D);
        }
    }

    // Resolve model-space bone2 top anchor to world-space, respecting block facing.
    private static Vec3 resolveFlameAnchor(BlockPos mainPos, Direction facing) {
        double dx = BONE2_TOP_LOCAL_X - 0.5D;
        double dz = BONE2_TOP_LOCAL_Z - 0.5D;

        double rx = dx;
        double rz = dz;
        switch (facing) {
            case EAST -> {
                rx = -dz;
                rz = dx;
            }
            case SOUTH -> {
                rx = -dx;
                rz = -dz;
            }
            case WEST -> {
                rx = dz;
                rz = -dx;
            }
            default -> {
            }
        }

        return new Vec3(
            mainPos.getX() + 0.5D + rx,
            mainPos.getY() + BONE2_TOP_LOCAL_Y,
            mainPos.getZ() + 0.5D + rz
        );
    }
}


