package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ground IceCrystal objects 319..321; independent of wall ice and counted mining stones. */
public final class MineIceDebrisBlock extends Block {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    // One enclosing AABB per approved model; the planted tips below the floor are excluded.
    private static final VoxelShape[] SHAPES = {
            box(5, 0, 5.25, 11, 11, 10.75),
            box(0.25, 0, 2.75, 13, 8.25, 10.75),
            box(0.25, 0, 2.75, 14.5, 9.5, 11.25)
    };

    public MineIceDebrisBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(VARIANT)];
    }

    /** Object.cutWeed: ice makes glass debris but grants no weed drops or haymaker rewards. */
    public static boolean breakBy(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockState(pos).getBlock() instanceof MineIceDebrisBlock)
                || OrdinaryMineRuntime.isArchitecture(level, pos)) return false;
        return level.destroyBlock(pos, false, player);
    }
}
