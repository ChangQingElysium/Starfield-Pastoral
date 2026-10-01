package com.stardew.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import com.stardew.craft.port.net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Render-context field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(LevelRenderer.class)
public interface TownDoorLevelRendererAccessor {
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
