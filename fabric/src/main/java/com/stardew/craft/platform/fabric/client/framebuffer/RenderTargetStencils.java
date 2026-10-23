package com.stardew.craft.platform.fabric.client.framebuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RenderTargetStencils {
    private RenderTargetStencils() {}
    public static void enableStencil(RenderTarget target) { ((StencilTarget) target).stardewcraft$enableStencil(); }
    public static boolean isStencilEnabled(RenderTarget target) { return ((StencilTarget) target).stardewcraft$isStencilEnabled(); }
}
