package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.junimo.JunimoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.core.object.Color;

/**
 * Render layer that re-renders the Junimo model using the tint texture,
 * multiplied by the entity's color. This produces the characteristic
 * color-per-area look from the original game.
 */
@SuppressWarnings("null")
public class JunimoTintLayer extends GeoRenderLayer<JunimoEntity> {

    private static final ResourceLocation TINT_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/entity/junimo/junimo_tint.png");

    public JunimoTintLayer(GeoRenderer<JunimoEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, JunimoEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource,
                       @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        float[] rgb = animatable.getColorComponents();
        float alpha = animatable.getAlpha();
        RenderType tintRenderType = RenderType.entityTranslucent(TINT_TEXTURE);
        int a = Math.round(alpha * 255);
        int colour = Color.ofARGB(a, (int)(rgb[0] * 255), (int)(rgb[1] * 255), (int)(rgb[2] * 255)).argbInt();
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, tintRenderType,
                bufferSource.getBuffer(tintRenderType), partialTick, packedLight, packedOverlay, colour);
    }
}
