package com.stardew.craft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.bomb.StardewBombEntity;
import com.stardew.craft.item.bomb.BombType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * 炸弹实体渲染器 — 渲染 Blockbench 3D 模型 + SDV 引信效果。
 *
 * <p>SDV 视觉效果复刻：</p>
 * <ul>
 *   <li>颤抖：shakeIntensity 从 0.5 开始，每 tick 增加 0.002（加速颤抖）</li>
 *   <li>闪烁：最后 12 tick 每 2 tick 切换白色覆盖</li>
 * </ul>
 */
@SuppressWarnings("null")
public class StardewBombEntityRenderer extends EntityRenderer<StardewBombEntity> {

    private static final ModelResourceLocation CHERRY_BOMB_MODEL = new ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "entity/bomb/cherry_bomb"), "standalone");
    private static final ModelResourceLocation BOMB_MODEL = new ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "entity/bomb/bomb"), "standalone");
    private static final ModelResourceLocation MEGA_BOMB_MODEL = new ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "entity/bomb/mega_bomb"), "standalone");

    @SuppressWarnings("deprecation")
    private static final ResourceLocation BLOCK_ATLAS =
        TextureAtlas.LOCATION_BLOCKS;

    /** SDV shakeIntensity 起始值（SDV 像素 → MC 方块单位：0.5/64≈0.008，放大以适应 3D） */
    private static final float BASE_SHAKE = 0.015f;
    /** SDV shakeIntensityChange per tick（SDV 每帧 0.002 px，转换为 MC 单位） */
    private static final float SHAKE_ACCEL = 0.001f;
    /** SDV 引信总 ticks */
    private static final int TOTAL_FUSE = 48;

    public StardewBombEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(StardewBombEntity entity) {
        return BLOCK_ATLAS;
    }

    @Override
    public void render(StardewBombEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        BombType type = entity.getBombType();
        int fuse = entity.getFuse();

        poseStack.pushPose();

        // SDV 颤抖效果：shakeIntensity = 0.5f + elapsed * 0.002f
        int elapsed = TOTAL_FUSE - fuse;
        float shakeIntensity = BASE_SHAKE + elapsed * SHAKE_ACCEL;
        RandomSource rand = entity.getRandom();
        float shakeX = (rand.nextFloat() * 2.0f - 1.0f) * shakeIntensity;
        float shakeZ = (rand.nextFloat() * 2.0f - 1.0f) * shakeIntensity;
        poseStack.translate(shakeX, 0, shakeZ);

        // 模型居中
        poseStack.translate(-0.5, 0.0, -0.5);

        // SDV 引信闪烁：最后 12 tick 每 2 tick 切换白色闪烁
        boolean flash = fuse <= 12 && fuse > 0 && (fuse / 2) % 2 == 0;

        ModelResourceLocation modelLoc = getModelLocation(type);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLoc);

        if (model != null) {
            // baked model 的 UV 指向 block atlas，必须用 Sheets.cutoutBlockSheet()
            VertexConsumer vc = buffer.getBuffer(Sheets.cutoutBlockSheet());
            int overlay = flash
                ? OverlayTexture.pack(OverlayTexture.u(1.0f), 10) // 白色覆盖
                : OverlayTexture.NO_OVERLAY;
            renderBakedModel(poseStack, vc, model, packedLight, overlay);
        }

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderBakedModel(PoseStack poseStack, VertexConsumer consumer,
                                  BakedModel model, int packedLight, int overlay) {
        RandomSource renderRand = RandomSource.create();
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(null, null, renderRand,
            net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        PoseStack.Pose pose = poseStack.last();
        for (net.minecraft.client.renderer.block.model.BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, 1.0f, 1.0f, 1.0f, 1.0f, packedLight, overlay);
        }
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> dirQuads = model.getQuads(null, dir, renderRand,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : dirQuads) {
                consumer.putBulkData(pose, quad, 1.0f, 1.0f, 1.0f, 1.0f, packedLight, overlay);
            }
        }
    }

    private ModelResourceLocation getModelLocation(BombType type) {
        if (type == BombType.CHERRY_BOMB) {
            return CHERRY_BOMB_MODEL;
        }
        if (type == BombType.MEGA_BOMB) {
            return MEGA_BOMB_MODEL;
        }
        return BOMB_MODEL;
    }
}
