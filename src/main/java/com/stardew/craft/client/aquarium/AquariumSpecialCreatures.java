package com.stardew.craft.client.aquarium;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.client.render.AquariumHatRenderer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;

@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
final class AquariumSpecialCreatures {
    private final ModelPart frog;
    private static final ResourceLocation FROG = new ResourceLocation("textures/entity/frog/warm_frog.png");
    private static final ResourceLocation BUBBLE = new ResourceLocation("textures/particle/bubble.png");
    private static final int[] COLORS = {0xff81bf58,0xffb29470,0xff65ba99,0xff669ccc,0xffd17e6d,0xffe2ca67,0xff665481};
    AquariumSpecialCreatures(BlockEntityRendererProvider.Context context) { frog = context.bakeLayer(ModelLayers.FROG).getChild("root"); }

    void urchin(PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose(); pose.translate(0, -.92, 0);
        AquariumModels.render("sea_urchin", false, pose, buffers, light); pose.popPose();
    }
    void hat(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        AquariumHatRenderer.render(stack, pose, buffers, light);
    }
    void frog(ItemStack stack, double seconds, float yaw, float jump, PoseStack pose, MultiBufferSource buffers, int light) {
        int variant = Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("Variant"), 0, 7);
        int color = variant == 7 ? 0xff000000 | Mth.hsvToRgb((float)(seconds % 12 / 12), .4f, .9f) : COLORS[variant];
        frog.getAllParts().forEach(ModelPart::resetPose);
        var body = frog.getChild("body"); body.getChild("tongue").visible = false;
        body.getChild("croaking_body").visible = true;
        body.getChild("croaking_body").yScale = 1 + .15f * (float)Math.sin(seconds * 2.2);
        frog.getChild("left_leg").xRot = frog.getChild("right_leg").xRot = -.28f * jump;
        body.getChild("left_arm").xRot = body.getChild("right_arm").xRot = .16f * jump;
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-yaw + 90));
        pose.translate(0, -3.5, 0); pose.scale(8.8f, -8.8f, -8.8f); pose.translate(0, -1.5, 0);
        frog.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FROG)), light, OverlayTexture.NO_OVERLAY, color);
        pose.popPose();
    }
    void bubbles(long seed, double seconds, PoseStack pose, MultiBufferSource buffers, int light) {
        var consumer = buffers.getBuffer(RenderType.entityTranslucent(BUBBLE));
        for (int i = 0; i < 4; i++) {
            double p = (seconds / (7.5 + i) + Math.floorMod(seed + i * 173, 1000) / 1000.0) % 1;
            double x = -22 + i * 14 + Math.sin(seconds * 1.4 + i) * .5, y = 7.5 + p * 23;
            float size = (float) (.25 + p * .25);
            int alpha = (int) (170 * Math.min(1, (1 - p) * 12));
            // Two intersecting planes keep the tiny bubbles readable from either side of the tank.
            for (int axis = 0; axis < 2; axis++) {
                pose.pushPose(); pose.translate(x, y, 2 + i % 2 * 3); pose.mulPose(Axis.YP.rotationDegrees(axis * 90));
                float[][] corners = {{-size,-size,0,1},{size,-size,1,1},{size,size,1,0},{-size,size,0,0}};
                for (var v : corners) consumer.addVertex(pose.last().pose(),v[0],v[1],0).setColor(220,245,255,alpha)
                        .setUv(v[2],v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);
                pose.popPose();
            }
        }
    }
}
