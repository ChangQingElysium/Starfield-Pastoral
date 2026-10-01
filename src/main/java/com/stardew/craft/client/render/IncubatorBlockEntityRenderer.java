package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;

import java.util.Map;

import com.stardew.craft.port.PortVertex;
public class IncubatorBlockEntityRenderer implements BlockEntityRenderer<IncubatorBlockEntity>, com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<IncubatorBlockEntity> {
    private static final ResourceLocation BUBBLE_TEX = new ResourceLocation(StardewCraft.MODID, "textures/gui/bubble.png");
    private static final float PX = 1.0f / 32.0f;
    private static final ModelResourceLocation EGG = partModel("egg");
    private static final ModelResourceLocation STRAW_FRONT = partModel("straw_front");
    private static final ModelResourceLocation STRAW_BACK = partModel("straw_back");
    // Seconds, pitch, roll. The quiet interval is added after each brief gesture.
    private static final float[][] PROBE = {
        {0, 0, 0}, {.35f, 0, -6}, {.65f, 0, -6}, {.83f, 0, 5},
        {1.05f, 0, -2}, {1.3f, 0, 0}
    };
    private static final float[][] PECK = {
        {0, 0, 0}, {.1f, -4, 1}, {.24f, 1, 0}, {.38f, -5, -1},
        {.54f, 2, 0}, {.85f, 0, 0}
    };

    private static ModelResourceLocation partModel(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
            "block/utility/incubator_" + name), "standalone");
    }

    private static float angle(float[][] keys, double time, int component) {
        if (time < 0 || time >= keys[keys.length - 1][0]) return 0;
        for (int i = 1; i < keys.length; i++) {
            if (time > keys[i][0]) continue;
            float[] a = keys[i - 1], b = keys[i];
            float u = (float) ((time - a[0]) / (b[0] - a[0]));
            float smooth = u * u * (3 - 2 * u);
            return a[component] + (b[component] - a[component]) * smooth;
        }
        return 0;
    }

    @Override
    public AABB getRenderBoundingBox(IncubatorBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 2, 0).inflate(.15);
    }

    private static void renderPart(IncubatorBlockEntity be, PoseStack pose, MultiBufferSource buffer,
                                   ModelResourceLocation id, float pitch, float roll, double pivotZ,
                                   int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, 26.0 / 16.0, pivotZ);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(-.5, -26.0 / 16.0, -pivotZ);
        var minecraft = Minecraft.getInstance();
        var model = minecraft.getModelManager().getModel(id);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
            buffer.getBuffer(Sheets.cutoutBlockSheet()), be.getBlockState(), model,
            1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    private static void renderNest(IncubatorBlockEntity be, float partialTick, PoseStack pose,
                                   MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) return;
        var remaining = be.getRemainingTime();
        boolean late = be.isReady() || (be.isWorking() && remaining.days() == 0 && remaining.hours() < 3);
        float[][] keys = late ? PECK : PROBE;
        long seed = be.getBlockPos().asLong();
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;
        long period = (late ? 160 : 300) + Math.floorMod(seed, late ? 101 : 201);
        double time = (Math.floorMod(be.getLevel().getGameTime() + Math.floorMod(seed >>> 16, period), period)
            + partialTick) / 20.0;
        boolean active = be.hasInput() && (be.isWorking() || be.isReady());
        float pitch = active ? angle(keys, time, 1) : 0;
        float roll = active ? angle(keys, time, 2) : 0;
        float delayedPitch = active ? angle(keys, time - .1, 1) : 0;
        float delayedRoll = active ? angle(keys, time - .1, 2) : 0;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - be.getBlockState().getValue(IncubatorBlock.FACING).toYRot()));
        pose.translate(-.5, 0, -.5);
        renderPart(be, pose, buffer, STRAW_FRONT, delayedPitch * .35f + delayedRoll * .2f,
            0, .25, light, overlay);
        renderPart(be, pose, buffer, STRAW_BACK, -delayedPitch * .2f + delayedRoll * .12f,
            0, .75, light, overlay);
        if (be.hasInput()) renderPart(be, pose, buffer, EGG, pitch, roll, .5, light, overlay);
        pose.popPose();
    }

    private static final Map<String, ResourceLocation> ICONS = Map.of(
        "white_chicken", new ResourceLocation(StardewCraft.MODID, "textures/gui/animal_query/icon_white_chicken.png"),
        "golden_chicken", new ResourceLocation(StardewCraft.MODID, "textures/gui/animal_query/icon_golden_chicken.png"),
        "duck", new ResourceLocation(StardewCraft.MODID, "textures/gui/animal_query/icon_duck.png"),
        "void_chicken", new ResourceLocation(StardewCraft.MODID, "textures/gui/animal_query/icon_void_chicken.png"),
        "dinosaur", new ResourceLocation(StardewCraft.MODID, "textures/gui/animal_query/icon_dinosaur.png")
    );

    public IncubatorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @SuppressWarnings("null")
    @Override
    public void render(@SuppressWarnings("null") IncubatorBlockEntity be, float partialTick, @SuppressWarnings("null") PoseStack poseStack, @SuppressWarnings("null") MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be == null) return;
        renderNest(be, partialTick, poseStack, buffer, packedLight, packedOverlay);
        if (!be.isReady()) {
            return;
        }

        String animalTypeId = be.getReadyAnimalTypeId();
        if (animalTypeId == null) {
            return;
        }
        ResourceLocation iconTex = ICONS.get(animalTypeId);
        if (iconTex == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5f, 2.1f, 0.5f);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        float w = 20 * PX;
        float h = 24 * PX;
        float x0 = -w / 2.0f;
        float x1 = w / 2.0f;
        float y0 = 0.0f;
        float y1 = h;

        @SuppressWarnings("null")
        VertexConsumer bubble = buffer.getBuffer(RenderType.entityTranslucent(BUBBLE_TEX));
        PortVertex.of(bubble).addVertex(poseStack.last().pose(), x0, y1, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(bubble).addVertex(poseStack.last().pose(), x1, y1, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(bubble).addVertex(poseStack.last().pose(), x1, y0, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(bubble).addVertex(poseStack.last().pose(), x0, y0, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();

        float iconW = 14 * PX;
        float iconH = 14 * PX;
        float ix0 = x0 + (3 * PX);
        float ix1 = ix0 + iconW;
        float iy1 = y1 - (3 * PX);
        float iy0 = iy1 - iconH;

        VertexConsumer icon = buffer.getBuffer(RenderType.entityCutoutNoCull(iconTex));
        PortVertex.of(icon).addVertex(poseStack.last().pose(), ix0, iy1, 0.001f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(icon).addVertex(poseStack.last().pose(), ix1, iy1, 0.001f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(icon).addVertex(poseStack.last().pose(), ix1, iy0, 0.001f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();
        PortVertex.of(icon).addVertex(poseStack.last().pose(), ix0, iy0, 0.001f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1).endVertex();

        poseStack.popPose();
    }
}
