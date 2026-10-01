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
import net.minecraft.util.Mth;

/** Native bomb models with a small, frame-rate-independent fuse tremor and 100 ms flicker. */
@SuppressWarnings("null")
public class StardewBombEntityRenderer extends EntityRenderer<StardewBombEntity> {

    private static final ModelResourceLocation CHERRY_BOMB_MODEL = new ModelResourceLocation(
        new ResourceLocation(StardewCraft.MODID, "entity/bomb/cherry_bomb"), "standalone");
    private static final ModelResourceLocation BOMB_MODEL = new ModelResourceLocation(
        new ResourceLocation(StardewCraft.MODID, "entity/bomb/bomb"), "standalone");
    private static final ModelResourceLocation MEGA_BOMB_MODEL = new ModelResourceLocation(
        new ResourceLocation(StardewCraft.MODID, "entity/bomb/mega_bomb"), "standalone");

    @SuppressWarnings("deprecation")
    private static final ResourceLocation BLOCK_ATLAS =
        TextureAtlas.LOCATION_BLOCKS;

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

        float elapsed = Mth.clamp(type.getFuseTicks() - fuse + partialTick, 0, type.getFuseTicks());
        var shake = entity.getVisualShake(partialTick);
        poseStack.translate(shake.x, 0, shake.z);
        poseStack.translate(-0.5, 0.0, -0.5);

        // TemporaryAnimatedSprite toggles flicker on every 100 ms fuse frame.
        boolean flash = fuse > 0 && ((int) elapsed / 2) % 2 == 1;

        ModelResourceLocation modelLoc = getModelLocation(type);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLoc);

        if (model != null) {
            // baked model 的 UV 指向 block atlas，必须用 Sheets.cutoutBlockSheet()
            VertexConsumer vc = buffer.getBuffer(Sheets.cutoutBlockSheet());
            renderBakedModel(poseStack, vc, model, packedLight, flash);
        }

        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderBakedModel(PoseStack poseStack, VertexConsumer consumer,
                                  BakedModel model, int packedLight, boolean flash) {
        // Source fuse flicker multiplies the shell by LightBlue * 0.85; it is not a white TNT overlay.
        float red = flash ? 173 / 255.0f * 0.85f : 1;
        float green = flash ? 216 / 255.0f * 0.85f : 1;
        float blue = flash ? 230 / 255.0f * 0.85f : 1;
        RandomSource renderRand = RandomSource.create();
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(null, null, renderRand,
            net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        PoseStack.Pose pose = poseStack.last();
        for (net.minecraft.client.renderer.block.model.BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, red, green, blue, 1.0f, packedLight, OverlayTexture.NO_OVERLAY);
        }
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> dirQuads = model.getQuads(null, dir, renderRand,
                net.minecraftforge.client.model.data.ModelData.EMPTY, null);
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : dirQuads) {
                consumer.putBulkData(pose, quad, red, green, blue, 1.0f, packedLight, OverlayTexture.NO_OVERLAY);
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
