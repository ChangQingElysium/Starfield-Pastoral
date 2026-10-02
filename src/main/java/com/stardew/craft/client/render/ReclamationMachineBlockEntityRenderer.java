package com.stardew.craft.client.render;

import com.stardew.craft.port.PortCamera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.block.utility.ReclamationMachineBlock;
import com.stardew.craft.client.model.ReclamationMachineModels;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.client.model.data.ModelData;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.ReclamationMachineBlockEntity;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

import com.stardew.craft.port.PortVertex;
/**
 * Source-authored machine clips: root pulse, independent blades/apron, and actual feed item.
 */
public class ReclamationMachineBlockEntityRenderer implements BlockEntityRenderer<ReclamationMachineBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<ReclamationMachineBlockEntity> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/bubble.png");
    private static final float PX = 1.0f / 32.0f;

    public ReclamationMachineBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings({ "null", "deprecation" })
    @Override
    public void render(@Nonnull ReclamationMachineBlockEntity be, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        boolean ready = be.isReady();
        ItemStack product = be.getProduct();

        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level != null) {
            poseStack.pushPose();
            Minecraft mc = Minecraft.getInstance();
            ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
            if (be.isWorking() && !ready) {
                poseStack.translate(0.5, 0, 0.5);
                float yaw = switch (state.getValue(ReclamationMachineBlock.FACING)) {
                    case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0;
                };
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.translate(-0.5, 0, -0.5);
                var consumer = buffer.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
                double seconds = (level.getGameTime() - be.getStartedAtGameTick() + (double) partialTick) / 20.0;
                String name = be.isWoodChipper() ? "wood_chipper" : "deconstructor";
                var motion = ReclamationMachineModels.motion(name);
                String clip = "animation." + name + ".work";
                if (be.isWoodChipper()) {
                    draw(renderer, poseStack, consumer, state, "wood_chipper_stationary_housing", packedLight, packedOverlay);
                    poseStack.pushPose();
                    var offset = motion.sample(clip, "feed_apron", "position", seconds);
                    poseStack.translate(offset.x / 16, offset.y / 16, offset.z / 16);
                    draw(renderer, poseStack, consumer, state, "wood_chipper_feed_apron", packedLight, packedOverlay);
                    poseStack.popPose();
                    poseStack.pushPose();
                    var pivot = motion.pivot("rotating_blades");
                    poseStack.translate(pivot.x, pivot.y, pivot.z);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(motion.sample(clip, "rotating_blades", "rotation", seconds).z));
                    poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
                    draw(renderer, poseStack, consumer, state, "wood_chipper_rotating_blades", packedLight, packedOverlay);
                    poseStack.popPose();
                    if (seconds >= 0 && seconds < 1 && !be.getInput().isEmpty()) {
                        poseStack.pushPose();
                        var anchor = motion.pivot("input_item_anchor");
                        var feed = motion.sample("animation.wood_chipper.feed", "input_item_anchor", "position", seconds);
                        var scale = motion.sample("animation.wood_chipper.feed", "input_item_anchor", "scale", seconds);
                        poseStack.translate(anchor.x + feed.x / 16, anchor.y + feed.y / 16, anchor.z + feed.z / 16);
                        poseStack.scale(scale.x * .5f, scale.y * .5f, scale.z * .5f);
                        mc.getItemRenderer().renderStatic(be.getInput(), ItemDisplayContext.FIXED,
                            packedLight, packedOverlay, poseStack, buffer, level, 0);
                        poseStack.popPose();
                    }
                } else {
                    var pivot = motion.pivot("machine_root");
                    var scale = motion.sample(clip, "machine_root", "scale", seconds);
                    poseStack.translate(pivot.x, pivot.y, pivot.z);
                    poseStack.scale(scale.x, scale.y, scale.z);
                    poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
                    draw(renderer, poseStack, consumer, state, "deconstructor", packedLight, packedOverlay);
                }
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

        Minecraft.getInstance().getItemRenderer().renderStatic(
            product,
            ItemDisplayContext.GUI,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            be.getLevel(),
            0
        );
        poseStack.popPose();

        BubbleItemCountRenderer.renderCount(poseStack, buffer, packedLight, product, x0 + (3 * PX), y1 - (3 * PX), PX);

        poseStack.popPose();
    }

    private static void draw(ModelBlockRenderer renderer, PoseStack pose, VertexConsumer consumer,
                             BlockState state, String part, int light, int overlay) {
        renderer.renderModel(pose.last(), consumer, state, ReclamationMachineModels.part(part),
            1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ReclamationMachineBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 1, 0);
    }
}
