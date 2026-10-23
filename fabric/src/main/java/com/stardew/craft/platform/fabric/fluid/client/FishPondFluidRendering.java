package com.stardew.craft.platform.fabric.fluid.client;

import com.stardew.craft.platform.fabric.fluid.PondWaterTint;
import java.util.Objects;
import java.util.function.BiFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

@Environment(EnvType.CLIENT)
public final class FishPondFluidRendering {
    private static BiFunction<BlockAndTintGetter, BlockPos, Integer> colorOverrides = (view, position) -> null;

    private FishPondFluidRendering() {}

    public static void register(FlowingFluid source, FlowingFluid flowing,
            BiFunction<BlockAndTintGetter, BlockPos, Integer> overrides) {
        colorOverrides = Objects.requireNonNull(overrides, "overrides");
        FluidRenderHandlerRegistry.INSTANCE.register(source, flowing, new SimpleFluidRenderHandler(
                SimpleFluidRenderHandler.WATER_STILL, SimpleFluidRenderHandler.WATER_FLOWING,
                SimpleFluidRenderHandler.WATER_OVERLAY) {
            @Override
            public int getFluidColor(BlockAndTintGetter view, BlockPos position, FluidState state) {
                return 0xFF000000 | color(view, position);
            }
        });
        BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), source, flowing);
    }

    public static int color(BlockAndTintGetter view, BlockPos position) {
        return PondWaterTint.resolve(colorOverrides.apply(view, position));
    }
}
