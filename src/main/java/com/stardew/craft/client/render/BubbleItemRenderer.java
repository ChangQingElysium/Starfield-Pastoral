package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Native item icons whose generated texture layers retain their GUI drawing order in the world. */
public final class BubbleItemRenderer {
    private static final RenderType ORDERED_ENTITY_LAYERS = orderedType(false);
    private static final RenderType ORDERED_ITEM_LAYERS = orderedType(true);

    private BubbleItemRenderer() {}

    public static void render(ItemStack stack, int packedLight, PoseStack poseStack,
                              MultiBufferSource buffer, @Nullable Level level) {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, orderedLayers(buffer), level, 0);
    }

    static MultiBufferSource orderedLayers(MultiBufferSource buffer) {
        // A world buffer otherwise sorts these nearly coplanar, flattened GUI faces by camera distance.
        // Keep the model's base/colored-overlay order while retaining native tint, alpha and depth tests.
        return type -> buffer.getBuffer(type == Sheets.translucentCullBlockSheet() ? ORDERED_ENTITY_LAYERS
                : type == Sheets.translucentItemSheet() ? ORDERED_ITEM_LAYERS : type);
    }

    private static RenderType orderedType(boolean itemTarget) {
        var state = RenderType.CompositeState.builder()
                .setShaderState(itemTarget ? RenderStateShard.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER
                        : RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY);
        if (itemTarget) state.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET);
        return RenderType.create(itemTarget ? "stardewcraft_bubble_item" : "stardewcraft_bubble_entity",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
                state.createCompositeState(true));
    }
}
