package com.stardew.craft.client.renderer.layer;

import com.stardew.craft.client.weapon.YetiFreezeClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class YetiFreezeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation ICE_TEXTURE = new ResourceLocation(
        "minecraft",
        "textures/block/ice.png"
    );

    public YetiFreezeLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    @SuppressWarnings("null")
    public void render(@SuppressWarnings("null") PoseStack poseStack, @SuppressWarnings("null") MultiBufferSource buffer,
                       int packedLight, @SuppressWarnings("null") T entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        long nowTick = entity.level().getGameTime();
        if (!YetiFreezeClientState.isFrozen(entity.getId(), nowTick)) {
            return;
        }
        if (entity.getType() == EntityType.WARDEN) {
            return;
        }

        RenderType renderType = RenderType.entityTranslucentCull(ICE_TEXTURE);
        int color = FastColor.ARGB32.color(92, 185, 225, 255);
        getParentModel().renderToBuffer(
            poseStack,
            buffer.getBuffer(renderType),
            packedLight,
            OverlayTexture.NO_OVERLAY,
            // PORT(1.20.1): float RGBA; c / 255F round-trips to the same vertex color bytes as the 1.21 ARGB int.
            FastColor.ARGB32.red(color) / 255.0F,
            FastColor.ARGB32.green(color) / 255.0F,
            FastColor.ARGB32.blue(color) / 255.0F,
            FastColor.ARGB32.alpha(color) / 255.0F
        );
    }
}
