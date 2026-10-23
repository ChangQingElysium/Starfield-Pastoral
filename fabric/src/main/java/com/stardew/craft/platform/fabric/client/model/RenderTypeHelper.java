package com.stardew.craft.platform.fabric.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.Minecraft;

/** Terrain-to-item atlas sheets used by the existing composite item models. */
public final class RenderTypeHelper {
    private RenderTypeHelper() {}

    public static RenderType getEntityRenderType(RenderType layer, boolean direct) {
        return getEntityRenderType(layer, direct, layer == RenderType.translucent() && !direct && Minecraft.useShaderTransparency());
    }

    static RenderType getEntityRenderType(RenderType layer, boolean direct, boolean shaderTransparency) {
        if (layer != RenderType.translucent()) return Sheets.cutoutBlockSheet();
        return direct || !shaderTransparency ? Sheets.translucentCullBlockSheet() : Sheets.translucentItemSheet();
    }
}
