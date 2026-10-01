package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.model.entity.CoopAnimalGeoModel;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.client.model.nativebb.BlockbenchEntityRenderer;

import com.stardew.craft.port.PortVertex;
@SuppressWarnings("null")
public class CoopAnimalGeoRenderer<T extends BaseCoopAnimalEntity> extends BlockbenchEntityRenderer<T> {
	private static final ResourceLocation EMOTES_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/emotes.png");
	private static final int TEX_WIDTH = 64;
	private static final int TEX_HEIGHT = 256;
	private static final int ICON_SIZE = 16;
	private static final float EMOTE_SIZE = 0.8F;

	public CoopAnimalGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new CoopAnimalGeoModel<>());
		this.shadowRadius = 0.35F;
	}

	@Override
	public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

		int frameIndex = entity.getCurrentEmoteFrameIndex();
		if (frameIndex < 0) {
			return;
		}

		int frameX = (frameIndex * ICON_SIZE) % TEX_WIDTH;
		int frameY = ((frameIndex * ICON_SIZE) / TEX_WIDTH) * ICON_SIZE;
		float u0 = frameX / (float) TEX_WIDTH;
		float u1 = (frameX + ICON_SIZE) / (float) TEX_WIDTH;
		float v0 = frameY / (float) TEX_HEIGHT;
		float v1 = (frameY + ICON_SIZE) / (float) TEX_HEIGHT;

		poseStack.pushPose();
		poseStack.translate(0.0D, entity.getBbHeight() + 0.22D, 0.0D);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

		float half = EMOTE_SIZE * 0.5F;
		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(EMOTES_TEX));
		PortVertex.of(vc).addVertex(poseStack.last().pose(), -half, half, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1).endVertex();
		PortVertex.of(vc).addVertex(poseStack.last().pose(), half, half, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1).endVertex();
		PortVertex.of(vc).addVertex(poseStack.last().pose(), half, -half, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1).endVertex();
		PortVertex.of(vc).addVertex(poseStack.last().pose(), -half, -half, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1).endVertex();
		poseStack.popPose();
	}
}
