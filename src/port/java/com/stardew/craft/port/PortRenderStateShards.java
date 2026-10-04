package com.stardew.craft.port;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * NeoForge 1.21.1 widens the {@link RenderStateShard} constants to public (access transformer); on Forge 1.20.1
 * they are protected. These are the same vanilla instances, re-exported for render types built outside a
 * {@code RenderType} subclass. Add constants here as call sites need them.
 */
@OnlyIn(Dist.CLIENT)
public final class PortRenderStateShards extends RenderStateShard {
    public static final ShaderStateShard RENDERTYPE_ENTITY_TRANSLUCENT_SHADER = RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
    public static final ShaderStateShard RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER = RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER;
    public static final ShaderStateShard RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER = RenderStateShard.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER;
    public static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = RenderStateShard.TRANSLUCENT_TRANSPARENCY;
    public static final CullStateShard CULL = RenderStateShard.CULL;
    public static final CullStateShard NO_CULL = RenderStateShard.NO_CULL;
    public static final LightmapStateShard LIGHTMAP = RenderStateShard.LIGHTMAP;
    public static final OverlayStateShard OVERLAY = RenderStateShard.OVERLAY;
    public static final WriteMaskStateShard COLOR_WRITE = RenderStateShard.COLOR_WRITE;
    public static final WriteMaskStateShard COLOR_DEPTH_WRITE = RenderStateShard.COLOR_DEPTH_WRITE;
    public static final OutputStateShard ITEM_ENTITY_TARGET = RenderStateShard.ITEM_ENTITY_TARGET;
    public static final DepthTestStateShard LEQUAL_DEPTH_TEST = RenderStateShard.LEQUAL_DEPTH_TEST;

    private PortRenderStateShards() {
        super("stardewcraft_port_unused", () -> {}, () -> {});
    }
}
