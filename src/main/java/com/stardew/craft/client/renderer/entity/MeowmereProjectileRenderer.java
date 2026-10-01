package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;


public class MeowmereProjectileRenderer extends EntityRenderer<MeowmereProjectileEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("stardewcraft", "textures/gui/weapon_skill/meowmere_head.png");

    public MeowmereProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(@SuppressWarnings("null") MeowmereProjectileEntity entity) {
        return TEXTURE;
    }

    @SuppressWarnings("null")
    @Override
    public void render(@SuppressWarnings("null") MeowmereProjectileEntity entity, float entityYaw, float partialTicks, @SuppressWarnings("null") PoseStack poseStack, @SuppressWarnings("null") MultiBufferSource buffer, int packedLight) {
        renderTrail(entity, partialTicks, poseStack, buffer, packedLight);

        poseStack.pushPose();
        
        // 缩放和定位
        poseStack.scale(0.8f, 0.8f, 0.8f);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // The original cat face is an intentional exception to procedural weapon effects.
        VertexConsumer out = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (float[] corner : new float[][]{{-0.5f, -0.5f, 0, 1}, {0.5f, -0.5f, 1, 1},
                {0.5f, 0.5f, 1, 0}, {-0.5f, 0.5f, 0, 0}}) {
            out.addVertex(pose.pose(), corner[0], corner[1], 0).setColor(255, 255, 255, 255)
                    .setUv(corner[2], corner[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0).setNormal(pose, 0, 0, 1);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderTrail(MeowmereProjectileEntity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.isRemoved() || !com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        Vec3 head = entity.getPosition(partialTicks);
        if (head.distanceToSqr(camera) > 48 * 48) return;
        var samples = new java.util.ArrayList<com.stardew.craft.client.weapon.RainbowTrailGeometry.Sample>();
        for (var point : entity.getTrailPoints()) {
            // Ignore the tick-end sample ahead of the interpolated projectile head.
            if (point.age < 1) continue;
            samples.add(new com.stardew.craft.client.weapon.RainbowTrailGeometry.Sample(point.position, point.age - 1 + partialTicks));
        }
        samples.add(new com.stardew.craft.client.weapon.RainbowTrailGeometry.Sample(head, 0));
        poseStack.pushPose(); poseStack.translate(-head.x, -head.y, -head.z);
        com.stardew.craft.client.weapon.RainbowTrailGeometry.render(
                buffer.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW),
                poseStack.last().pose(), samples, camera, MeowmereProjectileEntity.getTrailMaxAge());
        poseStack.popPose();
    }
}
