package com.stardew.craft.platform.fabric.client.model;

import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface IDynamicBakedModel extends ExtendedBakedModel, FabricBakedModel {
    @Override List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer);
    @Override default boolean isVanillaAdapter() { return false; }
    @Override default void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos,
            Supplier<RandomSource> random, RenderContext context) {
        DynamicModelEmitter.emitBlock(this, view, state, pos, random, context);
    }
    @Override default void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
        DynamicModelEmitter.emitItem(this, stack, random, context);
    }
}
