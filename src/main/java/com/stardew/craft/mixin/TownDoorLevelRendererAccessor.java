package com.stardew.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.joml.Matrix4f;

/** Render-context field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(LevelRenderer.class)
public interface TownDoorLevelRendererAccessor {
    @Invoker("renderSectionLayer")
    void stardewcraft$renderSectionLayer(RenderType layer,
                                       double x, double y, double z, Matrix4f view, Matrix4f projection);

    @Accessor("renderBuffers")
    RenderBuffers stardewcraft$getRenderBuffers();

    @Accessor("renderBuffers") @Mutable
    void stardewcraft$setRenderBuffers(RenderBuffers buffers);

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> stardewcraft$getVisibleSections();

    @Accessor("visibleSections") @Mutable
    void stardewcraft$setVisibleSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections);

    @Accessor("cullingFrustum")
    Frustum stardewcraft$getCullingFrustum();

    @Accessor("cullingFrustum")
    void stardewcraft$setCullingFrustum(Frustum frustum);

    @Accessor("transparencyChain")
    PostChain stardewcraft$getTransparencyChain();

    @Accessor("transparencyChain")
    void stardewcraft$setTransparencyChain(PostChain chain);
}
