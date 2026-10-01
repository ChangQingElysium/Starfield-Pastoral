package com.stardew.craft.client.monsternative;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
final class NativeGhostRenderTypes {
    private NativeGhostRenderTypes(){}
    static final RenderType NORMAL=create("ghost"),CARBON=create("carbon_ghost"),CURSE=create("shaman_curse"),SQUID_FIREBALL=create("squid_fireball"),SQUID=create("squid_kid"),SQUID_BLINK=create("squid_kid_blink"),SQUID_FIRE=create("squid_kid_fire"),SQUID_HIT=create("squid_kid_hit");
    private static RenderType create(String id){return RenderType.create("stardew_"+id,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,true,true,RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(new ResourceLocation("stardewcraft:textures/entity/monster_native/"+id+".png"),false,false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP).setOverlayState(RenderStateShard.OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(true));}
}
