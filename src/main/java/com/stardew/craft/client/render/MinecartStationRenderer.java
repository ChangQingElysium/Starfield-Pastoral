package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.data.ModelData;

@SuppressWarnings("null")
public final class MinecartStationRenderer extends EntityRenderer<MinecartStationEntity> {
    private static final ModelResourceLocation EMPTY = model("empty");
    private static ModelResourceLocation model(String name) {
        return new ModelResourceLocation(new ResourceLocation(StardewCraft.MODID,
                "entity/minecart/" + name), "standalone");
    }
    public MinecartStationRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.6F; }

    @Override
    public void render(MinecartStationEntity cart, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-cart.getYRot()));
        pose.translate(-0.5D, 0.0D, -0.5D);
        var baked = Minecraft.getInstance().getModelManager().getModel(EMPTY);
        var vertices = buffer.getBuffer(Sheets.cutoutBlockSheet());
        var random = RandomSource.create(42);
        for (int side = 0; side <= 6; side++) {
            Direction direction = side == 6 ? null : Direction.values()[side];
            for (var quad : baked.getQuads(null, direction, random, ModelData.EMPTY, null)) {
                vertices.putBulkData(pose.last(), quad, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
            }
        }
        pose.popPose();
        super.render(cart, yaw, partialTicks, pose, buffer, light);
    }

    @Override public ResourceLocation getTextureLocation(MinecartStationEntity cart) { return TextureAtlas.LOCATION_BLOCKS; }
}
