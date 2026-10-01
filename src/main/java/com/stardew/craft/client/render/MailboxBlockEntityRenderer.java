package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.MailboxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import com.stardew.craft.port.PortVertex;
/**
 * 信箱气泡渲染 — 有邮件时在信箱上方显示星露谷原版气泡和信封。
 * 信件由有信状态的方块模型以翻页书动画呈现。
 */
@SuppressWarnings("null")
public class MailboxBlockEntityRenderer implements BlockEntityRenderer<MailboxBlockEntity> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/mailbox_bubble.png");
    private static final float PX = 1.0f / 32.0f;
    private static final float BUBBLE_RAISE = 6 * PX;

    public MailboxBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MailboxBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.hasMail() || be.getLevel() == null) return;

        float bubbleY = BubbleYHelper.get(be.getBlockState(), be.getLevel(), be.getBlockPos()) + BUBBLE_RAISE;

        poseStack.pushPose();
        poseStack.translate(0.5f, bubbleY, 0.5f);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

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

        poseStack.popPose();
    }
}
