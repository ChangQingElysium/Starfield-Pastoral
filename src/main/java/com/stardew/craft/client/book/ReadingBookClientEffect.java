package com.stardew.craft.client.book;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.network.payload.ReadBookVisualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class ReadingBookClientEffect {
    private static final ResourceLocation BOOK_TEXTURE = new ResourceLocation(
            "textures/entity/enchanting_table_book.png"
    );
    private static final ResourceLocation[] STAR_FRAMES = frames("star");
    private static final ResourceLocation[] CENTER_FRAMES = frames("center");
    private static final int STAR_DURATION_TICKS = 16;
    private static final int FULL_LIGHT = 0xF000F0;

    private static final Map<Integer, BookEffect> BOOKS = new HashMap<>();
    private static final List<RainbowExplosion> EXPLOSIONS = new ArrayList<>();
    private static BookModel bookModel;

    private ReadingBookClientEffect() {
    }

    public static void apply(ReadBookVisualPayload payload) {
        Vec3 fallback = new Vec3(payload.x(), payload.y(), payload.z());
        if (payload.complete()) {
            EXPLOSIONS.add(new RainbowExplosion(payload.entityId(), fallback, payload.yRot()));
            return;
        }

        int duration = Math.max(1, payload.durationTicks());
        StardewMusicManager.duckMusic(80);
        BOOKS.put(payload.entityId(), new BookEffect(payload.entityId(), fallback, payload.yRot(), duration));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!BOOKS.isEmpty()) {
            Iterator<BookEffect> bookIterator = BOOKS.values().iterator();
            while (bookIterator.hasNext()) {
                BookEffect effect = bookIterator.next();
                effect.age++;
                if (effect.age > effect.durationTicks + 6) {
                    bookIterator.remove();
                }
            }
            freezeLocalReader();
        }

        if (!EXPLOSIONS.isEmpty()) {
            Iterator<RainbowExplosion> explosionIterator = EXPLOSIONS.iterator();
            while (explosionIterator.hasNext()) {
                RainbowExplosion explosion = explosionIterator.next();
                explosion.age++;
                if (explosion.age > STAR_DURATION_TICKS) {
                    explosionIterator.remove();
                }
            }
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (BOOKS.isEmpty() && EXPLOSIONS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera renderCamera = event.getCamera();
        Vec3 camera = renderCamera.getPosition();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        float partial = event.getPartialTick();

        renderBooks(minecraft, poseStack, buffer, camera, renderCamera.getYRot(), partial);
        renderExplosions(minecraft, poseStack, buffer, camera, renderCamera.getYRot(), partial);
    }

    private static void renderBooks(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                    Vec3 camera, float cameraYaw, float partial) {
        if (BOOKS.isEmpty()) {
            return;
        }

        BookModel model = getBookModel(minecraft);
        if (model == null) {
            return;
        }

        RenderType renderType = RenderType.entityCutoutNoCull(BOOK_TEXTURE);
        VertexConsumer consumer = buffer.getBuffer(renderType);

        for (BookEffect effect : BOOKS.values()) {
            Entity entity = minecraft.level != null ? minecraft.level.getEntity(effect.entityId) : null;
            Vec3 worldPos;
            if (entity != null) {
                worldPos = entity.getEyePosition(partial)
                        .add(entity.getViewVector(partial).normalize().scale(0.92D))
                        .add(0.0D, -0.22D, 0.0D);
            } else {
                worldPos = effect.fallbackPos.add(forward(effect.yRot).scale(0.92D)).add(0.0D, 1.36D, 0.0D);
            }
            Vec3 renderPos = worldPos.subtract(camera);

            float age = effect.age + partial;
            float progress = Mth.clamp(age / Math.max(1.0F, effect.durationTicks), 0.0F, 1.0F);
            float open = openCurve(progress);
            float pageFlip = age * 0.42F;
            float leftFlip = Mth.frac(pageFlip + 0.25F) * open;
            float rightFlip = Mth.frac(pageFlip + 0.75F) * open;

            poseStack.pushPose();
            poseStack.translate(renderPos.x, renderPos.y, renderPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - cameraYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(16.0F));
            poseStack.scale(0.86F, 0.86F, 0.86F);
            model.setupAnim(age * 0.18F, rightFlip, leftFlip, open);
            model.renderToBuffer(poseStack, consumer, FULL_LIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            poseStack.popPose();
        }

        buffer.endBatch(renderType);
    }

    private static BookModel getBookModel(Minecraft minecraft) {
        if (bookModel == null) {
            bookModel = new BookModel(minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
        }
        return bookModel;
    }

    private static void renderExplosions(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffer,
                                         Vec3 camera, float cameraYaw, float partial) {
        if (EXPLOSIONS.isEmpty()) {
            return;
        }

        float cameraYawRadians = cameraYaw * Mth.DEG_TO_RAD;
        Vec3 cameraRight = new Vec3(Mth.cos(cameraYawRadians), 0.0D, Mth.sin(cameraYawRadians));
        Vec3 cameraUp = new Vec3(0.0D, 1.0D, 0.0D);

        for (RainbowExplosion explosion : EXPLOSIONS) {
            Entity entity = minecraft.level != null ? minecraft.level.getEntity(explosion.entityId) : null;
            Vec3 base;
            if (entity != null) {
                base = entity.getEyePosition(partial)
                        .add(entity.getViewVector(partial).normalize().scale(1.48D))
                        .add(0.0D, -0.48D, 0.0D);
            } else {
                base = explosion.fallbackPos.add(forward(explosion.yRot).scale(1.48D)).add(0.0D, 1.14D, 0.0D);
            }
            Vec3 center = base.subtract(camera);
            float age = explosion.age + partial;
            float progress = Mth.clamp(age / STAR_DURATION_TICKS, 0.0F, 1.0F);
            int centerFrame = Math.min(CENTER_FRAMES.length - 1, (int) (progress * CENTER_FRAMES.length));
            float centerAlpha = fadeAlpha(progress);
            renderBillboard(poseStack, buffer, minecraft, CENTER_FRAMES[centerFrame], center, 0.78F, centerAlpha);

            for (int index = 0; index < 8; index++) {
                float angle = explosion.rotationOffset + (Mth.TWO_PI / 7.0F) * index;
                float outward = 0.0625F + age * 0.14F - age * age * 0.0045F;
                float radius = Math.max(0.0625F, outward);
                Vec3 offset = cameraRight.scale(Mth.sin(angle) * radius)
                        .add(cameraUp.scale(Mth.cos(angle) * radius));
                int starFrame = Math.min(STAR_FRAMES.length - 1, (int) (progress * STAR_FRAMES.length));
                float size = 0.62F;
                renderBillboard(poseStack, buffer, minecraft, STAR_FRAMES[starFrame], center.add(offset), size, fadeAlpha(progress));
            }
        }
    }

    private static void renderBillboard(PoseStack poseStack, MultiBufferSource.BufferSource buffer, Minecraft minecraft,
                                        ResourceLocation texture, Vec3 pos, float size, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }

        RenderType renderType = RenderType.entityTranslucent(texture);
        VertexConsumer consumer = buffer.getBuffer(renderType);
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        float half = size * 0.5F;

        poseStack.pushPose();
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        vertex(consumer, matrix, -half, -half, 0.0F, 1.0F, a);
        vertex(consumer, matrix, half, -half, 1.0F, 1.0F, a);
        vertex(consumer, matrix, half, half, 1.0F, 0.0F, a);
        vertex(consumer, matrix, -half, half, 0.0F, 0.0F, a);
        poseStack.popPose();
        buffer.endBatch(renderType);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v, int alpha) {
        consumer.addVertex(matrix, x, y, 0.0F)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static Vec3 forward(float yRot) {
        float radians = yRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians));
    }

    private static float openCurve(float progress) {
        if (progress < 0.16F) {
            return smooth(progress / 0.16F);
        }
        if (progress > 0.84F) {
            return smooth(Math.max(0.0F, (1.0F - progress) / 0.16F));
        }
        return 1.0F;
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void freezeLocalReader() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !BOOKS.containsKey(player.getId())) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, motion.y, 0.0D);
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.setSprinting(false);
    }

    private static float fadeAlpha(float progress) {
        if (progress < 0.12F) {
            return progress / 0.12F;
        }
        if (progress > 0.82F) {
            return Math.max(0.0F, (1.0F - progress) / 0.18F);
        }
        return 1.0F;
    }

    private static ResourceLocation[] frames(String prefix) {
        ResourceLocation[] frames = new ResourceLocation[8];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new ResourceLocation(
                    StardewCraft.MODID,
                    "textures/particle/rainbow_star/" + prefix + "_0" + index + ".png"
            );
        }
        return frames;
    }

    private static final class BookEffect {
        private final int entityId;
        private final Vec3 fallbackPos;
        private final float yRot;
        private final int durationTicks;
        private int age;

        private BookEffect(int entityId, Vec3 fallbackPos, float yRot, int durationTicks) {
            this.entityId = entityId;
            this.fallbackPos = fallbackPos;
            this.yRot = yRot;
            this.durationTicks = durationTicks;
        }
    }

    private static final class RainbowExplosion {
        private final int entityId;
        private final Vec3 fallbackPos;
        private final float yRot;
        private final float rotationOffset;
        private int age;

        private RainbowExplosion(int entityId, Vec3 fallbackPos, float yRot) {
            this.entityId = entityId;
            this.fallbackPos = fallbackPos;
            this.yRot = yRot;
            this.rotationOffset = yRot * Mth.DEG_TO_RAD + (float) (Math.random() * Mth.TWO_PI);
        }
    }
}