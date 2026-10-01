package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.BubbleItemCountProvider;
import com.stardew.craft.blockentity.FishPondBucketBlockEntity;
import com.stardew.craft.client.fishpond.ClientFishPondSwimVisuals;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

import com.stardew.craft.port.PortVertex;
public class FishPondBucketBlockEntityRenderer implements BlockEntityRenderer<FishPondBucketBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<FishPondBucketBlockEntity> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/bubble.png");
    private static final float PX = 1.0f / 32.0f;

    public FishPondBucketBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(FishPondBucketBlockEntity be) {
        var box=new net.minecraft.world.phys.AABB(be.getBlockPos());
        for(long packed:be.getPondWaterCells())box=box.minmax(new net.minecraft.world.phys.AABB(BlockPos.of(packed)));
        return box.inflate(1,2,1);
    }

    @Override
    public void render(@Nonnull FishPondBucketBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ClientFishPondSwimVisuals.render(be, partialTick, poseStack, buffer, packedLight, packedOverlay);

        if (be.isReady()) {
            renderBubble(
                be,
                be.getProduct(),
                ((Object) be) instanceof BubbleItemCountProvider provider ? provider.getBubbleItemCount() : be.getProduct().getCount(),
                0.5D,
                BubbleYHelper.get(be.getBlockState(), be.getLevel(), be.getBlockPos()),
                0.5D,
                0.0D,
                poseStack,
                buffer,
                packedLight,
                packedOverlay
            );
        }

        if (be.hasPendingRequest()) {
            var level = be.getLevel();
            long gameTime = level != null ? level.getGameTime() : 0L;
            double bobOffset = Math.sin((gameTime + partialTick) / 4.0D) * 0.06D;
            renderBubble(
                be,
                be.getRequestPreview(),
                be.getRequestCount(),
                be.getRequestRenderX(),
                be.getRequestRenderY() + bobOffset,
                be.getRequestRenderZ(),
                0.002D,
                poseStack,
                buffer,
                packedLight,
                packedOverlay
            );
        }

        if (be.hasFishSign()) {
            renderFishSign(be, poseStack, buffer, packedLight, packedOverlay);
        }
    }

    private void renderBubble(FishPondBucketBlockEntity be,
                              ItemStack stack,
                              int count,
                              double x,
                              double y,
                              double z,
                              double itemZOffset,
                              PoseStack poseStack,
                              MultiBufferSource buffer,
                              int packedLight,
                              int packedOverlay) {
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(x, y, z);
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
        poseStack.translate(iconCenterX, iconCenterY, itemZOffset);
        poseStack.scale(innerW, innerW, 0.001f);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack,
            ItemDisplayContext.GUI,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            be.getLevel(),
            0
        );
        poseStack.popPose();

        BubbleItemCountRenderer.renderCountAlways(poseStack, buffer, packedLight, count, x0 + (3 * PX), y1 - (3 * PX), PX);
        poseStack.popPose();
    }

    private void renderFishSign(FishPondBucketBlockEntity be,
                                PoseStack poseStack,
                                MultiBufferSource buffer,
                                int packedLight,
                                int packedOverlay) {
        var level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos signPos = be.getFishSignPos();
        BlockState signState = level.getBlockState(signPos);
        SignPose signPose = resolveSignPose(signState);
        if (signPose == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(
            signPos.getX() - be.getBlockPos().getX() + signPose.x(),
            signPos.getY() - be.getBlockPos().getY() + signPose.y(),
            signPos.getZ() - be.getBlockPos().getZ() + signPose.z()
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(signPose.yawDegrees()));
        poseStack.translate(0.0D, 0.0D, signPose.frontOffset());

        poseStack.pushPose();
        poseStack.translate(-0.12D, 0.06D, 0.001D);
        poseStack.scale(0.32F, 0.32F, 0.001F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            be.getFishSignPreview(),
            ItemDisplayContext.GUI,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            be.getLevel(),
            0
        );
        poseStack.popPose();

        BubbleItemCountRenderer.renderCountAlways(poseStack, buffer, packedLight, be.getFishPopulation(), 0.02F, 0.13F, PX * 0.8F);
        poseStack.popPose();
    }

    private SignPose resolveSignPose(BlockState signState) {
        if (signState.getBlock() instanceof com.stardew.craft.block.utility.WoodSignBlock) {
            Direction facing = signState.getValue(com.stardew.craft.block.utility.WoodSignBlock.FACING);
            return new SignPose(0.5D, 14.0D/16.0D, 0.5D, -facing.toYRot(), 2.0D/16.0D+0.012D);
        }
        if (signState.hasProperty(BlockStateProperties.ROTATION_16)) {
            int rotation = signState.getValue(BlockStateProperties.ROTATION_16);
            float yawDegrees = -(rotation * 360.0F / 16.0F);
            return new SignPose(0.5D, 0.78D, 0.5D, yawDegrees, 0.06D);
        }
        if (signState.hasProperty(WallSignBlock.FACING)) {
            Direction facing = signState.getValue(WallSignBlock.FACING);
            return new SignPose(0.5D, 0.78D, 0.5D, 180.0F - facing.toYRot(), 0.03D);
        }
        return null;
    }

    private record SignPose(double x, double y, double z, float yawDegrees, double frontOffset) {
    }
}