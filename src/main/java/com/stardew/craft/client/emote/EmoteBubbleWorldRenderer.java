package com.stardew.craft.client.emote;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.stardew.craft.port.PortVertex;
@SuppressWarnings("null")
public final class EmoteBubbleWorldRenderer {

	private static final ResourceLocation EMOTES_TEXTURE = new ResourceLocation(StardewCraft.MODID, "textures/gui/emotes.png");
	private static final int FRAME_SIZE = 16;
	private static final int EMOTES_TEX_W = 64;
	private static final int EMOTES_TEX_H = 256;

	private EmoteBubbleWorldRenderer() {
	}

	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}

		Vec3 cameraPos = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

		for (Entity entity : level.entitiesForRendering()) {
			int frameIndex = EmoteBubbleClientState.getCurrentFrameIndex(entity.getId());
			if (frameIndex < 0) {
				continue;
			}

			poseStack.pushPose();
			float partial = event.getPartialTick();
			double x = Mth.lerp(partial, entity.xOld, entity.getX());
			double y = Mth.lerp(partial, entity.yOld, entity.getY()) + entity.getBbHeight() + 0.72D;
			double z = Mth.lerp(partial, entity.zOld, entity.getZ());
			poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
			poseStack.mulPose(event.getCamera().rotation());
			poseStack.scale(0.85F, 0.85F, 0.85F);
			renderFrame(poseStack, buffer, frameIndex);
			poseStack.popPose();
		}

		buffer.endBatch();
	}

	private static void renderFrame(PoseStack poseStack, MultiBufferSource buffer, int frameIndex) {
		int pixelX = (frameIndex * FRAME_SIZE) % EMOTES_TEX_W;
		int pixelY = ((frameIndex * FRAME_SIZE) / EMOTES_TEX_W) * FRAME_SIZE;

		float u0 = pixelX / (float) EMOTES_TEX_W;
		float v0 = pixelY / (float) EMOTES_TEX_H;
		float u1 = (pixelX + FRAME_SIZE) / (float) EMOTES_TEX_W;
		float v1 = (pixelY + FRAME_SIZE) / (float) EMOTES_TEX_H;

		VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(EMOTES_TEXTURE));
		Matrix4f matrix = poseStack.last().pose();
		float half = 0.5F;

		PortVertex.of(consumer).addVertex(matrix, -half, half, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1).endVertex();
		PortVertex.of(consumer).addVertex(matrix, half, half, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1).endVertex();
		PortVertex.of(consumer).addVertex(matrix, half, -half, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1).endVertex();
		PortVertex.of(consumer).addVertex(matrix, -half, -half, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1).endVertex();
	}
}
