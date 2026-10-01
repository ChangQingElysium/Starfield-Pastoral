package com.stardew.craft.client.weapon;

import com.stardew.craft.port.PortRenderStateShards;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;


public final class WeaponEffectRenderTypes {
    /** Unlit, depth-tested ribbon/impact geometry. Only color is written; no scene copy is needed. */
    public static final RenderType MOLTEN_GLOW = RenderType.create(
            "stardewcraft_molten_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("molten_additive", () -> {
                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                        com.mojang.blaze3d.systems.RenderSystem.blendFunc(
                                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                    }, () -> {
                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    }))
                    .setCullState(PortRenderStateShards.NO_CULL)
                    .setWriteMaskState(PortRenderStateShards.COLOR_WRITE)
                    .createCompositeState(false));

    /** Dark/translucent edge preserves hit silhouettes against bright sky and pale targets. */
    public static final RenderType IMPACT_EDGE = RenderType.create(
            "stardewcraft_impact_edge", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                    .setTransparencyState(PortRenderStateShards.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(PortRenderStateShards.NO_CULL)
                    .setWriteMaskState(PortRenderStateShards.COLOR_WRITE)
                    .createCompositeState(false));

    /** Opaque projectile bodies keep their silhouette; their separate wakes supply the glow. */
    public static final RenderType PROJECTILE_BODY = RenderType.create(
            "stardewcraft_weapon_projectile_body", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                    .setCullState(PortRenderStateShards.NO_CULL)
                    .setWriteMaskState(PortRenderStateShards.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private WeaponEffectRenderTypes() {}
}
