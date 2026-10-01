package com.stardew.craft.client.model.nativebb;

import com.stardew.craft.client.npcnative.NativeNpcPose;
import net.minecraft.resources.ResourceLocation;

/** Resource selection for existing Blockbench exports, rendered by the native pose pipeline. */
public abstract class BlockbenchModel<T> {
    public abstract ResourceLocation getModelResource(T entity);
    public abstract ResourceLocation getTextureResource(T entity);
    public abstract ResourceLocation getAnimationResource(T entity);
    public void pose(T entity, NativeNpcPose pose, float partialTick) {}
}
