package com.stardew.craft.platform.fabric.light;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class LightEmissionHooks {
    private LightEmissionHooks() {}

    public static int getLightEmission(BlockState state, BlockGetter view, BlockPos pos) {
        return resolve(state, view, pos, state.getLightEmission());
    }

    public static int resolve(BlockState state, BlockGetter view, BlockPos pos, int nativeValue) {
        return state.getBlock() instanceof LightEmissionProvider provider
                ? Mth.clamp(provider.getLightEmission(state, view, pos), 0, 15) : nativeValue;
    }

    public static int staticEmission(BlockState state, int nativeValue) {
        if (state.getBlock() instanceof LightEmissionProvider provider && !provider.hasDynamicLightEmission(state)) {
            return Mth.clamp(provider.getLightEmission(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO), 0, 15);
        }
        return nativeValue;
    }

    public static boolean dynamic(BlockState state) {
        return state.getBlock() instanceof LightEmissionProvider provider && provider.hasDynamicLightEmission(state);
    }
}
