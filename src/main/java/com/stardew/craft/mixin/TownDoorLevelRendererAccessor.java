package com.stardew.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.joml.Matrix4f;

/** Render-context field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(LevelRenderer.class)
public interface TownDoorLevelRendererAccessor {
    @Invoker("renderChunkLayer")
    void stardewcraft$renderSectionLayer(RenderType layer,
                                       com.mojang.blaze3d.vertex.PoseStack view, double x, double y, double z, Matrix4f projection);

    @Accessor("renderBuffers")
    RenderBuffers stardewcraft$getRenderBuffers();

    @Accessor("renderBuffers") @Mutable
    void stardewcraft$setRenderBuffers(RenderBuffers buffers);

    // PORT(1.20.1): 1.21 visibleSections (RenderSection list) is renderChunksInFrustum, a list of
    // LevelRenderer.RenderChunkInfo (package-private, hence Object elements).
    @Accessor("renderChunksInFrustum")
    ObjectArrayList<Object> stardewcraft$getVisibleSections();

    @Accessor("renderChunksInFrustum") @Mutable
    void stardewcraft$setVisibleSections(ObjectArrayList<Object> sections);

    @Accessor("cullingFrustum")
    Frustum stardewcraft$getCullingFrustum();

    @Accessor("cullingFrustum")
    void stardewcraft$setCullingFrustum(Frustum frustum);

    @Accessor("transparencyChain")
    PostChain stardewcraft$getTransparencyChain();

    @Accessor("transparencyChain")
    void stardewcraft$setTransparencyChain(PostChain chain);
}
