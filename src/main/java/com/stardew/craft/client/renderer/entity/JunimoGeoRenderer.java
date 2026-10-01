package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.JunimoGeoModel;
import com.stardew.craft.entity.junimo.JunimoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.core.object.Color;

@SuppressWarnings("null")
public class JunimoGeoRenderer extends GeoEntityRenderer<JunimoEntity> {

    public JunimoGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new JunimoGeoModel());
        this.shadowRadius = 0.25F;
        addRenderLayer(new JunimoTintLayer(this));
        addRenderLayer(new JunimoBundleLayer(this));
    }

    /**
     * SDV parity: apply entity alpha (fadeIn/fadeOut) to the render color.
     */
    @Override
    public Color getRenderColor(JunimoEntity animatable, float partialTick, int packedLight) {
        Color base = super.getRenderColor(animatable, partialTick, packedLight);
        float alpha = animatable.getAlpha();
        if (alpha >= 1.0f) return base;
        int a = Math.round(alpha * 255);
        return Color.ofARGB(a, base.getRed(), base.getGreen(), base.getBlue());
    }

    /**
     * Use entityTranslucent render type so alpha blending works for fadeIn/fadeOut.
     */
    @Nullable
    @Override
    public RenderType getRenderType(JunimoEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        if (animatable.getAlpha() < 1.0f) {
            return RenderType.entityTranslucent(texture);
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }
}
