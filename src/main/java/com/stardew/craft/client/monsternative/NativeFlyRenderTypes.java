package com.stardew.craft.client.monsternative;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Two-sided geometry supplies its own back face; preserve culling and alpha without depth writes. */
final class NativeFlyRenderTypes {
    private NativeFlyRenderTypes() {}
    static final RenderType MEMBRANE=RenderType.create("stardew_fly_membrane",DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,1536,true,true,RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation("stardewcraft:textures/entity/monster_native/fly.png"),false,false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true));
}
