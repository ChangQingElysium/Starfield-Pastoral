package com.stardew.craft.templates.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * PORT(1.20.1): NeoForge 1.21 resolves block ambient occlusion with the block's ModelData
 * ({@code BakedModel#useAmbientOcclusion(state, data, renderType)}); Forge 1.20.1 drops the data.
 * Models implementing this receive it via {@code TemplateModelAmbientOcclusionMixin}.
 */
public interface ModelDataAmbientOcclusion {
    boolean stardewcraft$useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType);
}
