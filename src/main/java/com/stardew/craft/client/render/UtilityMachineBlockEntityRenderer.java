package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.BubbleItemCountProvider;
import com.stardew.craft.blockentity.UtilityMachineInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

import com.stardew.craft.port.PortVertex;
public class UtilityMachineBlockEntityRenderer<T extends BlockEntity & UtilityMachineInfo> implements BlockEntityRenderer<T> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/bubble.png");
    private static final float PX = 1.0f / 32.0f;

    public UtilityMachineBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings({ "null", "deprecation" })
    @Override
    public void render(@Nonnull T be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level != null && com.stardew.craft.block.utility.UtilityMachineRenderState.rendersDynamicBody(state)) {
            poseStack.pushPose();
            if (shouldApplyWorkingAnimation(be)) {
                applyWorkingAnimation(be, partialTick, poseStack);
            }
            renderBlockModel(be, state, level, poseStack, buffer, packedOverlay);
            poseStack.popPose();
        }

        ItemStack product = be.getDisplayOutput();
        if (level != null && be.isReadyForDisplay() && !product.isEmpty()) {
            renderReadyBubble(be, product, state, level, poseStack, buffer, packedLight, packedOverlay);
        }
    }

    protected boolean shouldApplyWorkingAnimation(T be) {
        return be.isWorkingForDisplay();
    }

    protected void applyWorkingAnimation(T be, float partialTick, PoseStack poseStack) {
        UtilityWorkingAnimation.applyKegWorkingPose(poseStack, be.getLevel(), be.getBlockPos(), partialTick);
    }

    @SuppressWarnings({ "null", "deprecation" })
    protected void renderBlockModel(T be, BlockState state, Level level, PoseStack poseStack, MultiBufferSource buffer, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        RandomSource rand = RandomSource.create(0L);
        SpatialBlockModelRenderer.render(renderer, level, model, state, be.getBlockPos(), poseStack, buffer.getBuffer(renderType), true, rand, 0L, packedOverlay);
    }

    protected void renderReadyBubble(T be, ItemStack product, BlockState state, Level level, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        float bubbleY = BubbleYHelper.get(state, level, be.getBlockPos());

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

        float innerW = 14 * PX;
        float innerH = 14 * PX;
        float iconCenterX = x0 + (3 * PX) + innerW / 2.0f;
        float iconCenterY = y1 - (3 * PX) - innerH / 2.0f;

        poseStack.pushPose();
        poseStack.translate(iconCenterX, iconCenterY, 0.001f);
        poseStack.scale(innerW, innerW, 0.001f);
        Minecraft.getInstance().getItemRenderer().renderStatic(product, ItemDisplayContext.GUI, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, be.getLevel(), 0);
        poseStack.popPose();

        int bubbleCount = product.getCount();
        boolean customCount = false;
        if (be instanceof BubbleItemCountProvider provider) {
            bubbleCount = provider.getBubbleItemCount();
            customCount = true;
        }
        if (customCount) {
            BubbleItemCountRenderer.renderCountAlways(poseStack, buffer, packedLight, bubbleCount, x0 + (3 * PX), y1 - (3 * PX), PX);
        } else {
            BubbleItemCountRenderer.renderCount(poseStack, buffer, packedLight, bubbleCount, x0 + (3 * PX), y1 - (3 * PX), PX);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull T blockEntity) {
        return true;
    }
}
