package com.stardew.craft.client.renderer.entity;

import com.stardew.craft.client.model.entity.JunimoGeoModel;
import com.stardew.craft.entity.junimo.JunimoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

@SuppressWarnings("null")
public class JunimoGeoRenderer extends BlockbenchEntityRenderer<JunimoEntity> {

    private final JunimoBundleLayer bundle = new JunimoBundleLayer();

    public JunimoGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new JunimoGeoModel());
        this.shadowRadius = 0.25F;

    }

    /**
     * SDV parity: apply entity alpha (fadeIn/fadeOut) to the render color.
     */
    @Override
    public int getRenderColor(JunimoEntity animatable, float partialTick, int packedLight) {
        return (Math.clamp(Math.round(animatable.getAlpha() * 255), 0, 255) << 24) | 0xFFFFFF;
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
    @Override protected void renderExtras(JunimoEntity entity,
            com.stardew.craft.client.model.nativebb.BlockbenchFrame frame,
            com.mojang.blaze3d.vertex.PoseStack pose, MultiBufferSource buffers,
            float partialTick, int light, int overlay) {
        JunimoTintLayer.render(entity, frame, pose, buffers, light, overlay);
        bundle.render(entity, frame, pose, buffers, light, overlay);
    }

}
