package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class IceSpineEffectRenderer extends EntityRenderer<IceSpineEffectEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        StardewCraft.MODID,
        "textures/entity/special_effect/ice_spine.png"
    );

    @SuppressWarnings("null")
    private static final ModelResourceLocation MODEL = new ModelResourceLocation(
        new ResourceLocation(StardewCraft.MODID, "entity/special_effect/ice_spine"),
        "standalone"
    );

    public IceSpineEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(@SuppressWarnings("null") IceSpineEffectEntity entity) {
        return TEXTURE;
    }

    @SuppressWarnings("null")
    @Override
    public void render(@SuppressWarnings("null") IceSpineEffectEntity entity, float entityYaw, float partialTicks,
                       @SuppressWarnings("null") PoseStack poseStack, @SuppressWarnings("null") MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float age = entity.tickCount + partialTicks;
        float pulse = 1.0f + 0.12f * Mth.sin(age * 0.6f);
        poseStack.scale(1.05f, pulse, 1.05f);
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        // 模型导出时方向已正确，不额外旋转

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(MODEL);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        mc.getItemRenderer().renderModelLists(
            model,
            ItemStack.EMPTY,
            entity.isGroundWave() ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            consumer
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

}
