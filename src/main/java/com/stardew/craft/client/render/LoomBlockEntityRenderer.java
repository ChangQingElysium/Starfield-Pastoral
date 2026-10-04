package com.stardew.craft.client.render;

import com.stardew.craft.port.PortCamera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.block.utility.LoomBlock;
import com.stardew.craft.client.model.LoomModels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.client.model.data.ModelData;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.LoomBlockEntity;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

import com.stardew.craft.port.PortVertex;
/**
 * Loom render: stable frame, independently rotating threaded wheel, and finished cloth.
 */
public class LoomBlockEntityRenderer implements BlockEntityRenderer<LoomBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<LoomBlockEntity> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/bubble.png");
    private static final float PX = 1.0f / 32.0f;

    public LoomBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings({ "null", "deprecation" })
    @Override
    public void render(@Nonnull LoomBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        boolean ready = be.isReady();
        ItemStack product = be.getProduct();

        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level != null && com.stardew.craft.block.utility.UtilityMachineRenderState.rendersDynamicBody(state)) {
            poseStack.pushPose();
            Minecraft mc = Minecraft.getInstance();
            ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
            if (be.isWorking() && !ready) {
                // Parts are authored north-facing; rotate the assembly once, then its wheel locally.
                poseStack.translate(0.5, 0, 0.5);
                float yaw = switch (state.getValue(LoomBlock.FACING)) {
                    case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
                };
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.translate(-0.5, 0, -0.5);
                var consumer = buffer.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
                renderer.renderModel(poseStack.last(), consumer, state, LoomModels.frame(),
                    1, 1, 1, packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout());
                var motion = LoomModels.motion();
                poseStack.translate(motion.x(), motion.y(), motion.z());
                poseStack.mulPose(Axis.ZP.rotationDegrees(motion.angle((level.getGameTime() + (double) partialTick) / 20.0)));
                poseStack.translate(-motion.x(), -motion.y(), -motion.z());
                renderer.renderModel(poseStack.last(), consumer, state, LoomModels.wheel(),
                    1, 1, 1, packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout());
            } else {
                BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                renderer.renderModel(poseStack.last(), buffer.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS)),
                    state, model, 1, 1, 1, packedLight, packedOverlay, ModelData.EMPTY, RenderType.cutout());
            }
            poseStack.popPose();
        }

        if (!ready || product.isEmpty() || level == null) {
            return;
        }

        float bubbleY = BubbleYHelper.get(state, level, be.getBlockPos());

        poseStack.pushPose();
        poseStack.translate(0.5f, bubbleY, 0.5f);
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

        BubbleItemCountRenderer.renderCount(poseStack, buffer, packedLight, product, x0 + (3 * PX), y1 - (3 * PX), PX);

        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(LoomBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 1, 0);
    }
}
