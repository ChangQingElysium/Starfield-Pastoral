package com.stardew.craft.client.render;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CutsceneTextAboveHeadRenderer {
    private static final ResourceLocation LEFT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cutscene/text_above_head_left.png");
    private static final ResourceLocation MID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cutscene/text_above_head_mid.png");
    private static final ResourceLocation RIGHT = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cutscene/text_above_head_right.png");
    private static final ResourceLocation TAIL = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cutscene/text_above_head_tail.png");

    private static final int TEXT_COLOR = 0x4B2A12;
    private static final float WORLD_SCALE = 0.0125F;
    private static final float TEXT_SCALE = 1.7F;
    private static final Map<Integer, Bubble> BUBBLES = new LinkedHashMap<>();

    private CutsceneTextAboveHeadRenderer() {
    }

    public static void show(int entityId, String text, int durationTicks, double offsetY) {
        BUBBLES.put(entityId, new Bubble(text, Math.max(1, durationTicks), offsetY));
    }

    public static void tick(int entityId) {
        Bubble bubble = BUBBLES.get(entityId);
        if (bubble == null) {
            return;
        }
        bubble.elapsed++;
        int remaining = bubble.durationTicks - bubble.elapsed;
        if (remaining > 10) {
            bubble.alpha = Math.min(1.0F, bubble.alpha + 0.1F);
        } else {
            bubble.alpha = Math.max(0.0F, bubble.alpha - 0.04F);
        }
    }

    public static void hide(int entityId) {
        BUBBLES.remove(entityId);
    }

    public static void clear() {
        BUBBLES.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (BUBBLES.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            BUBBLES.clear();
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        for (var iterator = BUBBLES.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Integer, Bubble> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            Bubble bubble = entry.getValue();
            if (entity == null || entity.isRemoved() || bubble.elapsed >= bubble.durationTicks) {
                iterator.remove();
                continue;
            }

            double x = Mth.lerp(partial, entity.xOld, entity.getX());
            double y = Mth.lerp(partial, entity.yOld, entity.getY()) + entity.getBbHeight() + 0.75D + bubble.offsetY;
            double z = Mth.lerp(partial, entity.zOld, entity.getZ());

            poseStack.pushPose();
            poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
            poseStack.mulPose(event.getCamera().rotation());
            poseStack.scale(WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
            renderBubble(StardewFonts.dialogue(), poseStack, buffer, bubble);
            poseStack.popPose();
        }

        buffer.endBatch();
    }

    private static void renderBubble(Font font, PoseStack poseStack, MultiBufferSource.BufferSource buffer, Bubble bubble) {
        Component text = Component.literal(bubble.text).withStyle(ChatFormatting.BOLD);
        int rawTextWidth = Math.max(12, font.width(text));
        int textWidth = (int) Math.ceil(rawTextWidth * TEXT_SCALE);
        int midWidth = textWidth + 10;
        int leftWidth = 14;
        int rightWidth = 14;
        int bubbleHeight = 34;
        int bubbleWidth = leftWidth + midWidth + rightWidth;
        float left = -bubbleWidth / 2.0F;
        float top = -23.0F;
        int alpha = (int) (255 * bubble.alpha);

        drawSprite(poseStack, buffer, LEFT, left, top, leftWidth, bubbleHeight, alpha);
        drawSprite(poseStack, buffer, MID, left + leftWidth, top, midWidth, bubbleHeight, alpha);
        drawSprite(poseStack, buffer, RIGHT, left + leftWidth + midWidth, top, rightWidth, bubbleHeight, alpha);
        drawSprite(poseStack, buffer, TAIL, -6.0F, top + 26.0F, 12, 10, alpha);
        buffer.endBatch();

        int textColor = (alpha << 24) | TEXT_COLOR;
        float textX = -textWidth / 2.0F;
        float scaledTextHeight = font.lineHeight * TEXT_SCALE;
        float textY = top + (bubbleHeight - scaledTextHeight) / 2.0F;
        poseStack.pushPose();
        poseStack.translate(textX, textY, 0.01F);
        poseStack.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        font.drawInBatch(text, 0.0F, 0.0F, textColor, false,
                poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        poseStack.popPose();
    }

    private static void drawSprite(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture,
                                   float x, float y, float width, float height, int alpha) {
        if (alpha <= 0) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        Matrix4f matrix = poseStack.last().pose();
        consumer.addVertex(matrix, x, y, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x, y + height, 0.0F).setColor(255, 255, 255, alpha).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x + width, y + height, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1);
        consumer.addVertex(matrix, x + width, y, 0.0F).setColor(255, 255, 255, alpha).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1);
    }

    private static final class Bubble {
        private final String text;
        private final int durationTicks;
        private final double offsetY;
        private int elapsed;
        private float alpha;

        private Bubble(String text, int durationTicks, double offsetY) {
            this.text = text;
            this.durationTicks = durationTicks;
            this.offsetY = offsetY;
        }
    }
}
