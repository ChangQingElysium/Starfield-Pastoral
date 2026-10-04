package com.stardew.craft.client.render;

import com.stardew.craft.port.PortCamera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.BubbleItemCountProvider;
import com.stardew.craft.blockentity.MushroomBoxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.stardew.craft.port.PortVertex;
/**
 * 蘑菇培养盆就绪时显示气泡（与 Utility 设施一致）。
 */
public class MushroomBoxBlockEntityRenderer implements BlockEntityRenderer<MushroomBoxBlockEntity> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/bubble.png");
    private static final float PX = 1.0f / 32.0f;

    public MushroomBoxBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings("null")
    @Override
    public void render(@SuppressWarnings("null") MushroomBoxBlockEntity be, float partialTick,
                       @SuppressWarnings("null") PoseStack poseStack,
                       @SuppressWarnings("null") MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (be == null || !be.isReady()) {
            return;
        }
        ItemStack product = be.getProduct();
        if (product.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5f, BubbleYHelper.get(be.getBlockState(), be.getLevel(), be.getBlockPos()), 0.5f);
        poseStack.mulPose(PortCamera.cameraOrientation(Minecraft.getInstance().getEntityRenderDispatcher()));

        float w = 20 * PX;
        float h = 24 * PX;
        float x0 = -w / 2.0f;
        float x1 = w / 2.0f;
        float y0 = 0.0f;
        float y1 = h;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(BUBBLE_TEX));
        PortVertex.of(vc).addVertex(poseStack.last().pose(), x0, y1, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(vc).addVertex(poseStack.last().pose(), x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(vc).addVertex(poseStack.last().pose(), x1, y0, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(vc).addVertex(poseStack.last().pose(), x0, y0, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();

        float innerW = 14 * PX;
        float innerH = 14 * PX;
        float iconCenterX = x0 + (3 * PX) + innerW / 2.0f;
        float iconCenterY = y1 - (3 * PX) - innerH / 2.0f;

        poseStack.pushPose();
        poseStack.translate(iconCenterX, iconCenterY, 0.001f);
        float scale = innerW;
        poseStack.scale(scale, scale, 0.001f);

        BubbleItemRenderer.render(product, packedLight, poseStack, buffer, be.getLevel());
        poseStack.popPose();

        int bubbleCount = product.getCount();
        if (((Object) be) instanceof BubbleItemCountProvider provider) {
            bubbleCount = provider.getBubbleItemCount();
        }
        BubbleItemCountRenderer.renderCountAlways(poseStack, buffer, packedLight, bubbleCount, x0 + (3 * PX), y1 - (3 * PX), PX);

        poseStack.popPose();
    }
}
